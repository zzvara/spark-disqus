package hu.sztaki.spark.disqus

import java.net.URL

import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.`Content-Type`
import akka.http.scaladsl.model.{ContentTypes, HttpMethods, HttpRequest, HttpResponse}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import org.json4s.JsonAST.JString
import org.json4s.jackson.JsonMethods.parse
import org.json4s.{DefaultFormats, JValue}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{Await, Future}

class Fetcher(implicit configuration: Configuration) extends Logger {
  protected lazy val client = Http(actor.System.get)
  implicit protected val timeout: FiniteDuration = 500 millis

  implicit protected lazy val materializer: Materializer = Materializer.createMaterializer(actor.System.get)

  protected val & = new {

    val `public-key` = System.getenv().getOrDefault(
      "SQUS_SEARCH_FETCHER_PUBLIC_KEY",
      configuration.get[String]("squs.search.fetcher.public-key")
    )

    val `private-key` = System.getenv().getOrDefault(
      "SQUS_SEARCH_FETCHER_PRIVATE_KEY",
      configuration.get[String]("squs.search.fetcher.private-key")
    )

  }

  @transient implicit protected lazy val defaultFormat: DefaultFormats.type = DefaultFormats

  def trending(forum: String): Future[JValue] = {
    val URL = s"https://disqus.com/api/3.0/trends/listThreads.json?" +
      s"api_key=${&.`public-key`}&" +
      s"api_secret=${&.`private-key`}&forum=$forum"
    log.trace("Getting trending shit from forum [{}] with URL [{}].", forum, URL)

    client.singleRequest(
      HttpRequest(
        HttpMethods.GET,
        URL
      )
        .withHeaders(`Content-Type`(ContentTypes.`application/json`))
    ).flatMap {
      case HttpResponse(status, _, entity, _) =>
        if (status.isSuccess()) {
          log.trace("Call was successful to Disqus with URL [{}]. Unmarshalling result.", URL)
          Unmarshal(entity).to[String]
        } else {
          log.trace(
            "Call was unsuccessful to Disqus with URL [{}] and status [{}] and reason [{}]. " +
              "Discarding result bytes.",
            URL,
            status.intValue(),
            status.reason()
          )
          entity.discardBytes()
          Future.failed[String](Fetcher.Exception.Failed)
        }
    }.map {
      responseString =>
        log.trace("Parsing response string from Disqus [{}].", responseString)
        parse(responseString) \ "response"
    }
  }

  @throws[IllegalArgumentException]("If cursor does not look like a valid Disqus cursor format.")
  def stepWithCursor(next: String, host: String): Future[Either[Throwable, List[Comment]]] = {
    require(next.split(""":""").length == 3, "Does not look like a valid Disqus cursor format!")

    val URL = s"https://disqus.com/api/3.0/posts/list.json?" +
      s"api_key=${&.`public-key`}&" +
      s"api_secret=${&.`private-key`}&cursor=$next"
    log.trace(
      "Stepping with cursor from Disqus using next [{}] with URL [{}] (host [{}]).",
      next,
      URL,
      host
    )

    try {
      client.singleRequest(
        HttpRequest(
          HttpMethods.GET,
          URL
        )
      ).flatMap {
        case HttpResponse(status, _, entity, _) =>
          val responseString = Unmarshal(entity).to[String]
          if (status.isSuccess() || status.intValue() == 400) {
            log.trace(
              "Call was [{}] to Disqus with URL [{}]. Unmarshalling result.",
              status.intValue(),
              URL
            )

            /**
              * If status is 400, probably a bad request that states: "invalid forum ID".
              */
            responseString.map(Right(_))
          } else {
            log.warn(
              "Cursor response failed. Seems odd. It supposed to work other than a network issue! " +
                s"Status is [${status.intValue()}]!"
            )
            entity.discardBytes()
            Future.successful[Either[Throwable, String]](Left(Fetcher.Exception.Failed))
          }
      }.map[Either[Throwable, List[Comment]]] {
        case Right(responseString) =>
          Right {
            log.trace("Parsing response string from Disqus [{}].", responseString)
            val parsedResponse = parse(responseString)
            val responseEntity = parsedResponse \ "response"
            val fetchedPosts = Comment(responseEntity, host)

            val morePosts =
              if (responseHasNext(parsedResponse)) {
                tryStepWithCursor(parsedResponse, host)
              } else {
                List.empty[Comment]
              }
            fetchedPosts ++ morePosts
          }
        case Left(x) => Left(x)
      }
    } catch {
      case t: Throwable =>
        log.info(
          s"Could not step with cursor [$next] due " +
            s"to error [${t.getClass.getName}]."
        )
        Future.successful(Right(List.empty[Comment]))
    }
  }

  protected def responseHasNext(parsedResponse: JValue): Boolean =
    util.Try((parsedResponse \ "cursor" \ "hasNext").extract[Boolean]).toOption.getOrElse(false)

  protected def tryStepWithCursor(parsedResponse: JValue, host: String): List[Comment] =
    util.Try(Await.result(
      stepWithCursor(
        (parsedResponse \ "cursor" \ "next").extract[String],
        host
      ),
      10 seconds
    )).toOption.flatMap(_.right.toOption).getOrElse(List.empty[Comment])

  def posts(
    thread: String,
    tryouts: Seq[String] = List.empty,
    threadIdentified: Thread.Identified.Value = Thread.Identified.byLink
  ): Future[Result] = {
    if (tryouts.isEmpty) {
      log.warn(s"Empty tryouts for thread [$thread]!")
      Future(Fail(thread, tryouts.toList))
    } else {
      val queryType = threadIdentified match {
        case Thread.Identified.byIdentifier => ""
        case Thread.Identified.byLink       => ":link"
      }

      val currentTryOut = tryouts.head
      val remainingToTryOut = tryouts.tail

      val URL =
        s"https://disqus.com/api/3.0/posts/list.json?" +
          s"api_key=${&.`public-key`}&" +
          s"api_secret=${&.`private-key`}&limit=100&forum=$currentTryOut&thread$queryType=$thread"

      log.trace(
        "Fetching posts from Disqus using thread [{}] with URL [{}].",
        thread,
        URL
      )

      try {
        client.singleRequest(
          HttpRequest(
            HttpMethods.GET,
            URL
          )
        ).flatMap {
          case HttpResponse(status, _, entity, _) =>
            if (status.isSuccess() || status.intValue() == 400) {
              log.trace(
                "Call was [{}] to Disqus with URL [{}]. Unmarshalling result.",
                status.intValue(),
                URL
              )

              /**
                * If status is 400, probably a bad request that states: "invalid forum ID".
                */
              Unmarshal(entity).to[String]
            } else {
              log.trace(
                "Call was unsuccessful to Disqus with URL [{}] and status [{}] and reason [{}]. " +
                  "Discarding result bytes.",
                URL,
                status.intValue(),
                status.reason()
              )
              entity.discardBytes()
              Future.failed[String](Fetcher.Exception.Failed)
            }
        }.map {
          responseString =>
            try {
              val parsedResponse = parse(responseString)
              val responseEntity = parsedResponse \ "response"
              val responseCode = (parsedResponse \ "code").extract[Int]
              responseEntity match {
                /**
                  * If Disqus "response" field is just a "string", then something is fishy.
                  * Usually this means that something is wrong and Disqus is trying to tell us something.
                  * Let's listen to it, and figure out what the heck.
                  */
                case s: JString if s.values.contains("limit of request") =>
                  /**
                    * In case of response [You have exceeded your hourly limit of requests].
                    */
                  log.info(
                    "Disqus says something like [You have exceeded your hourly limit of requestsYou have " +
                      "exceeded your hourly limit of requests]. Limiting."
                  )
                  Limited()
                case _ =>
                  val fetchedPosts = Comment(responseEntity, Fetcher.host(thread))

                  /**
                    * It's okay, it is just empty.
                    */
                  if (responseCode == 0 && fetchedPosts.isEmpty) {
                    Success(Fetcher.host(thread), currentTryOut, List.empty[Comment])

                    /**
                      * Might do a fallback automatically.
                      */
                  } else if (fetchedPosts.isEmpty && remainingToTryOut.nonEmpty) {

                    /**
                      * @todo Fallback wait time to be configurable!
                      */
                    val fallbackHead = remainingToTryOut.head
                    log.info(s"Could not retrieve Disqus posts for forum ID [$currentTryOut], " +
                      s"but falling back to [$fallbackHead].")
                    Await.result(posts(thread, remainingToTryOut), 10 seconds)
                  } else if (fetchedPosts.isEmpty) {
                    log.warn(
                      s"Could not fetch posts for Disqus thread [$thread]! There might be " +
                        s"no posts or the forum IDs (including fallback IDs) are wrong!"
                    )
                    Fail(thread, remainingToTryOut.toList)

                    /**
                      * If cursor is specified as has next, so that more comments can be extracted.
                      */
                  } else if (responseHasNext(parsedResponse)) {
                    Success(
                      Fetcher.host(thread),
                      currentTryOut,
                      fetchedPosts ++
                        tryStepWithCursor(parsedResponse, Fetcher.host(thread))
                    )
                  } else {
                    Success(Fetcher.host(thread), currentTryOut, fetchedPosts)
                  }
              }
            } catch {
              case t: Throwable =>
                log.warn(
                  s"Could not retrieve thread due to error [${t.getClass}] with message [${t.getMessage}]."
                )
                Fail(thread, remainingToTryOut.toList)
            }
        }
      } catch {
        case t: Throwable =>
          val fallbackHead = remainingToTryOut.head
          log.info(
            s"Could not retrieve Disqus posts for forum ID [$currentTryOut] due " +
              s"to error [${t.getClass.getName}], " +
              s"but falling back to [$fallbackHead]."
          )
          posts(thread, remainingToTryOut)
      }
    }
  }

}

object Fetcher {

  object Cache {
    protected var cache: Option[Fetcher] = None

    def get()(
      implicit configuration: Configuration
    ): Fetcher =
      synchronized {
        cache match {
          case Some(c) => c
          case None =>
            cache = Some(new Fetcher())
            cache.get
        }
      }

  }

  def host(thread: String): String = new URL(thread).getHost

  abstract class Exception(message: String) extends scala.Exception(message)

  object Exception {
    case object Failed extends Exception("Could not fetch from Disqus!")
    case object Parse extends Exception("Could not parse result JSON object!")
  }

}
