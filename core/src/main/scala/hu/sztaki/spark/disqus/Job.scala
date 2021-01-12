package hu.sztaki.spark.disqus

import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

import akka.http.scaladsl.client.RequestBuilding.Post
import hu.sztaki.spark.disqus.Job.{Guessed, Registry}
import org.apache.spark.rdd.RDD
import org.apache.spark.streaming.dstream.DStream
import org.apache.spark.streaming.scheduler.{
  StreamingListener,
  StreamingListenerBatchCompleted,
  StreamingListenerBatchSubmitted
}
import org.apache.spark.streaming.{Seconds, StreamingContext, StreamingContextState}
import org.apache.spark.{SparkConf, SparkContext}

import scala.collection.{immutable, mutable}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.{Await, Future}
import scala.language.{existentials, reflectiveCalls}

@SerialVersionUID(-1)
case class Job(outputs: Iterable[RDD[Result] => Unit])(implicit configuration: Configuration)
 extends Logger with Serializable {
  @transient protected lazy val batch = new SparkContext(new SparkConf())

  @transient protected lazy val streaming =
    new StreamingContext(
      batch,
      Seconds(
        configuration.get[Duration]("stube.spark.streaming.batch-duration").toSeconds
      )
    )

  @transient protected val state = new {
    val batches = new AtomicInteger(0)
    var delay: Option[Long] = None
    var started = false
  }

  protected val & = new Serializable {

    val key = configuration.get[String]("stube.search.key")

    val comment = new Serializable {

      val `maximum-results-per-search` =
        configuration.get[Int]("stube.search.comment.maximum-results-per-search")

      val ordering =
        configuration.get[String]("stube.search.comment.ordering")
          .ensuring(List("time", "relevance").contains(_))

    }

  }

  streaming.addStreamingListener(new StreamingListener {

    override def onBatchSubmitted(submitted: StreamingListenerBatchSubmitted): Unit = {
      state.batches.incrementAndGet()
      state.batches synchronized {
        state.batches.notifyAll()
      }
    }

    override def onBatchCompleted(batchCompleted: StreamingListenerBatchCompleted): Unit = {
      state.delay = batchCompleted.batchInfo.totalDelay
    }

  })

  initialize()

  @transient protected var results: RDD[Result] = batch.emptyRDD[Result]

  @transient protected val limitedCounterConfiguration =
    configuration.get[Int]("grinder.feeders.disqus.limited-counter")

  protected var limitedForRounds = 0

  def isLimited: Boolean = limitedForRounds > 0

  def notLimited: Boolean = !isLimited

  def decreaseLimited(): Unit = limitedForRounds -= 1

  def limitDetected(): Unit = {
    log.info("Disqus API limited me. Sad.")
    limitedForRounds = limitedCounterConfiguration
  }

  protected var mappingsCheckpointFrequency =
    configuration.get[Int]("grinder.feeders.disqus.checkpoint-frequency")

  protected var mappingsUpdates = 0

  def initialize(): Unit = {
    var mappings = batch.makeRDD(Seq.empty[(String, Mapping)])

    streaming
      .queueStream(new mutable.Queue[RDD[Request]]())
      .map {
        x => Fetcher.host(x.thread) -> x
      }
      .foreachRDD {
        DD =>
          if (notLimited) {
            results.unpersist()
            results = DD
              /**
                * Join on `host` of the thread baseURLs.
                */
              .leftOuterJoin(mappings)
              .map {
                case (_, (request, Some(mapping))) =>
                  request.thread -> Registry(
                    List(mapping.forumID) ++ // First priority to try out is the mapping.
                      request.forum.toList ++ // Second priority is the parse from Nutch.
                      Job.generateTryouts(request.thread).toList // Then generated tryouts.
                  )
                case (_, (request, None)) =>
                  request.thread -> Guessed(
                    request.forum.toList ++ // First priority is the parse from Nutch.
                      Job.generateTryouts(request.thread).toList // Then generated tryouts.
                  )
              }
              .mapPartitions[Result] {
                partition =>
                  val fetcher = Fetcher.Cache.get()
                  partition.map[Result] {
                    request =>
                      val result = Await.result[Result](
                        fetcher.posts(request._1, request._2.forums),
                        1 minute
                      )

                      /**
                        * If success, and the returned successful forum ID is from the mappings.
                        * The first forumID is from the mappings if [[Registry]] type.
                        * @note Check the first map after the join above.
                        *
                        * We are looking for the case when the forumID is from [[Guessed]].
                        * We need to update it in the mapping (Couchbase).
                        */
                      result match {
                        case success: Success =>
                          request._2 match {
                            case _: Guessed =>
                              Mapping(
                                success.host,
                                success.forumID
                              )
                            case _: Registry if request._2.forums.head != success.forumID =>
                              log.warn(s"Success detected on fetch, and used the mapping " +
                                s"[${request._2.forums.head}] for host [${success.host}], but that " +
                                s"was incorrect! Updating mapping in database any way!")
                              Mapping(
                                success.host,
                                success.forumID
                              )
                            case _ =>
                          }
                        case _ =>
                      }
                      result
                  }
              }.cache()

            /**
              * Failed fetches.
              */
            val fails = results
              .filter {
                r => r.isInstanceOf[Fail]
              }
              .map(_.asInstanceOf[Fail])
              .collect()

            val successes = results
              .filter(_.isInstanceOf[Success])
              .count()

            /**
              * Currently the fetch is limited by the Disqus API. Postpone the few next fetches.
              */
            if (!results.filter(_.isInstanceOf[Limited]).isEmpty()) {
              limitDetected()
            }

            outputs.foreach(_.apply(results))
          } else {
            decreaseLimited()
          }
      }
  }

  def addStreamingListener(listener: StreamingListener): Unit = {
    log.info("Adding streaming listener.")
    streaming.addStreamingListener(listener)
  }

  def start(block: Boolean = false): Unit = {
    if (streaming.getState() != StreamingContextState.ACTIVE) {
      log.info("Starting up streaming context.")
      state.started = true
      streaming.start()
    }
    if (block) {
      streaming.awaitTermination()
    }
  }

  def awaitProcessing(): Job = {
    start()
    if (state.batches.get() < 1) {
      state.batches synchronized {
        state.batches.wait()
      }
    }
    this
  }

  def kill(): Unit = {
    stop()
    log.info("Killing program.")
    System.exit(0)
  }

  def stop(): Unit = {
    implicit val success = new retry.Success[Unit](_.isInstanceOf[Unit])
    Try.eatAnyShit(Await.result(
      retry.Directly(5) {
        () =>
          Future {
            Await.result(
              Future[Unit] {
                log.warn("Shutting down and destructing existing Spark Streaming engine!")
                streaming.stop(stopSparkContext = true, stopGracefully = false)
                batch.stop()
              },
              30.seconds
            )
          }
      }.recover {
        case _ =>
          log.warn("Could not stop Spark's context correctly in this try!")
          ()
      },
      60.seconds
    )).getOrElse {
      log.error("Could not shut down Spark context correctly! Not attempting again!")
    }
  }

  def processingStarted: Boolean = state.batches.intValue() > 0
}

object Job extends Entrypoint {

  abstract class Source(val forums: List[String])

  case class Registry(override val forums: List[String]) extends Source(forums)
  case class Guessed(override val forums: List[String]) extends Source(forums)

  def generateTryouts(thread: String): immutable.IndexedSeq[String] = {
    val hostParts = new URL(thread).getHost.split("""\.""").reverse
    val slices =
      (for (i <- 2 to hostParts.length) yield hostParts.slice(0, i).reverse).map(_.mkString(""))
    slices ++ hostParts.drop(1).headOption.toList
  }

  def main(arguments: Array[String]): Unit = {
    Job(List(_.count()))
  }

}
