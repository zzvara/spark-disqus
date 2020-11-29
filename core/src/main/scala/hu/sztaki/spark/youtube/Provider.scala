package hu.sztaki.spark.youtube

import com.google.api.client.http.HttpRequest
import com.google.api.client.http.apache.ApacheHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.youtube.YouTube
import hu.sztaki.spark.youtube.Try.tryHard

object Provider {

  object Cache {
    protected var youTube: Option[YouTube] = None

    def get()(implicit configuration: Configuration): YouTube =
      synchronized {
        youTube match {
          case Some(y) => y
          case None =>
            youTube = tryHard {
              Some(new YouTube.Builder(
                new ApacheHttpTransport,
                JacksonFactory.getDefaultInstance,
                (request: HttpRequest) => {}
              ).setApplicationName(
                configuration.get[String]("stube.search.application-name")
              ).build())
            }
            youTube.get
        }
      }

  }

}
