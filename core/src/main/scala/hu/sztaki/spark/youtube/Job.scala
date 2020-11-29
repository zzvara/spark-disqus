package hu.sztaki.spark.youtube

import java.util.concurrent.atomic.AtomicInteger

import org.apache.spark.streaming.dstream.DStream
import org.apache.spark.streaming.scheduler.{
  StreamingListener,
  StreamingListenerBatchCompleted,
  StreamingListenerBatchSubmitted
}
import org.apache.spark.streaming.{Seconds, StreamingContext, StreamingContextState}
import org.apache.spark.{SparkConf, SparkContext}
import retry.Success

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.{Await, Future}
import scala.language.{existentials, reflectiveCalls}

@SerialVersionUID(-1)
case class Job(outputs: Iterable[DStream[Datum] => Unit])(implicit configuration: Configuration)
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

  def initialize(): Unit = {
    val fetches = streaming
      .receiverStream[Video](new Receiver(&.key))
      .mapPartitions {
        videos =>
          if (videos.nonEmpty) {
            log.info("Loaded language detector profile. Creating Youtube search provider.")
            val provider = Provider.Cache.get()
            videos.map {
              video =>
                val comments =
                  try {
                    log.info("Performing comment search on Youtube.")
                    provider
                      .commentThreads()
                      .list("id,replies,snippet")
                      .setVideoId(video.videoID)
                      .setTextFormat("plainText")
                      .setOrder(&.comment.ordering)
                      .setKey(&.key)
                      .setFields("*")
                      .setMaxResults(&.comment.`maximum-results-per-search`)
                      .execute()
                      .getItems
                      .asScala
                  } catch {
                    case t: Throwable =>
                      log.warn(s"Could not fetch comments for video [${video.videoID}], " +
                        s"due to error [${t.getMessage}].")
                      List()
                  }

                (video, comments)
            }
          } else {
            Iterator.empty
          }
      }
      .flatMap[Datum] {
        case (video, comments) =>
          val extractedComments = comments.flatMap {
            c =>
              try {
                Comment(video, c)
              } catch {
                case t: Throwable =>
                  log.warn(
                    s"Could not convert comment to Comment object of video " +
                      s"[${video.videoID}] due to error [${t.getMessage}]."
                  )
                  t.printStackTrace()
                  Nil
              }
          }

          extractedComments :+ video
      }
      .cache()

    outputs.foreach(_.apply(fetches))
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
    implicit val success = new Success[Unit](_.isInstanceOf[Unit])
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

  def main(arguments: Array[String]): Unit = {
    Job(List(_.print()))
  }

}
