package hu.sztaki.spark.youtube

import com.google.api.services.youtube.YouTube
import org.apache.spark.storage.StorageLevel
import org.apache.spark.streaming.receiver

import scala.collection.JavaConverters.asScalaIteratorConverter
import scala.collection.mutable
import scala.concurrent.duration.Duration
import scala.language.reflectiveCalls
import scala.util.Random

class Receiver(protected val key: String)(implicit configuration: Configuration)
 extends receiver.Receiver[Video](StorageLevel.MEMORY_AND_DISK) with Serializable with Logger {

  protected var keywords: Array[String] = Array.empty

  protected var running = false

  protected var suppress: Option[Long] = None

  @transient var youTube: YouTube = _

  protected val & = new Serializable {
    val `keywords-path` = configuration.get[String]("stube.search.video.keywords-path")

    val `suppress-time` = configuration.get[Duration]("stube.search.suppress-time")

    val video = new Serializable {

      val `maximum-results-per-search` =
        configuration.get[Int]("stube.search.video.maximum-results-per-search")

      val `search-per-second` =
        configuration.get[Int]("stube.search.video.search-per-second")

    }

  }

  protected def signature: Double = math.random
  protected val buffer: mutable.Queue[Seq[String]] = new mutable.Queue[Seq[String]]
  protected val termProvider = new Receiver.Term.Provider(buffer, signature, this)

  @transient protected lazy val feederThread = new Thread(
    () => {
      log.info("Starting feeder thread.")

      youTube = Provider.Cache.get

      running = true
      while (running) {
        suppress match {
          case Some(s) =>
            log.debug(s"Suppressed until [$s], [${System.currentTimeMillis() - s}] more" +
              s"milliseconds to go.")
            if (System.currentTimeMillis() > s) {
              suppress = None
              log.info("Switching off suppressing!")
            }
          case None =>
            log.info(
              "Entered feeder loop and attempting to search a total of [{}] items.",
              &.video.`search-per-second`
            )
            (1 to &.video.`search-per-second`).foreach {
              _ =>
                nextTerm() match {
                  case Some(t) => search(t)
                  case None =>
                    log.debug("Could not retrieve terms for search, because there are no " +
                      "search terms available.")
                }
            }
        }

        Thread.sleep(1000)
      }
    }
  )

  protected def nextTerm(): Option[String] = termProvider.pick

  protected def search(terms: String): Unit = {
    log.info(s"Searching terms [$terms].")
    try {
      val search = youTube.search().list("id,snippet")
      search.setKey(key)
      search.setQ(terms)
      search.setType("video")
      search.setRelevanceLanguage("hu")
      search.setRegionCode("HU")
      search.setFields("*")
      search.setMaxResults(&.video.`maximum-results-per-search`)
      val response = search.execute()
      val items = response.getItems
      if (Option(items).isDefined) {
        items.iterator().asScala.foreach {
          searchResult => store(Video(searchResult))
        }
      }
    } catch {
      case t: Throwable =>
        if (t.getMessage.contains("usageLimits") || t.getMessage.contains("quota")) {
          log.warn(s"Detected usage limits! Suppressing for [${&.`suppress-time`}] milliseconds!")
          suppress = Some(System.currentTimeMillis() + &.`suppress-time`.toMillis)
        } else {
          t.printStackTrace()
        }
        log.warn(s"Could not complete video search, due to error [${t.getMessage}].")
    }
  }

  override def onStart(): Unit = {
    feederThread.start()
    keywords = scala.io.Source.fromInputStream(
      getClass.getResourceAsStream(&.`keywords-path`),
      "UTF-8"
    )
      .getLines().toArray
    log.info(
      "Got a total of [{}] number of keywords for keywords path [{}].",
      keywords.length,
      &.`keywords-path`
    )
  }

  override def onStop(): Unit = {
    running = false
  }

}

object Receiver {
  protected var bufferSize: Int = 100

  object Term {

    class Provider(
      buffer: => mutable.Queue[Seq[String]],
      signature: => Double,
      receiver: Receiver)
     extends Serializable
       with Logger {
      protected var currentSignature: Double = signature
      protected var generated: mutable.Stack[String] = new mutable.Stack[String]

      protected def randomKeyword: String =
        receiver.keywords(Random.nextInt(receiver.keywords.length))

      protected def maybeKeyword: Option[String] = {
        val keyword = randomKeyword
        log.trace("Getting keyword [{}].", keyword)
        Some(keyword)
      }

      def pick: Option[String] =
        if (signature == currentSignature) {
          log.trace("Signature changed to [{}].", currentSignature)

          /**
            * Buffer did not change.
            */
          if (generated.nonEmpty) {
            val nextTerm = generated.pop()
            Some((Some(nextTerm) ++ maybeKeyword).mkString(" "))
          } else {
            buffer synchronized {
              buffer.headOption.foreach {
                _ =>
                  generated = new mutable.Stack[String]()
                  log.trace("Buffer not empty and with size [{}].", buffer.size)
                  buffer.dequeue().toList.sliding(2).map(_.mkString(" ")).foreach {
                    generated.push(_)
                  }
                  log.trace("Changing signature.")
                  currentSignature = signature
              }

            }
            if (generated.nonEmpty) {
              log.trace("Got generated items, picking.")
              pick
            } else {
              log.trace("Generated items empty, picking.")
              maybeKeyword
            }
          }
        } else {

          /**
            * Buffer changed.
            */
          generated = new mutable.Stack[String]()
          if (buffer.nonEmpty) {
            buffer.dequeue().toList.sliding(2).map(_.mkString(" ")).foreach {
              generated.push(_)
            }
            currentSignature = signature
            pick
          } else {
            maybeKeyword
          }
        }

    }

  }

}
