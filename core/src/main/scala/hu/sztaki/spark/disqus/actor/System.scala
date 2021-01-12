package hu.sztaki.spark.disqus.actor

import akka.actor.ActorSystem

object System {
  val name = "hu-sztaki-spark-disqus"

  protected var system: Option[ActorSystem] = None

  def get: ActorSystem =
    synchronized {
      system match {
        case Some(s) => s
        case None => system synchronized {
            system = Some(
              ActorSystem(
                System.name
              )
            )
            system.get
          }
      }
    }

}
