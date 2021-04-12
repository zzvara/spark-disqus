package hu.sztaki.spark.disqus

import hu.sztaki.spark.Logger
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Seconds, Span}

trait stubeTest extends Logger with Matchers {
  System.setProperty("spark.master", "local[4]")
  System.setProperty("spark.app.name", "stubeTest")

  implicit var configuration: Configuration = new Configuration(silent = true)

  def now = System.currentTimeMillis()

  def eventually[U](f: => U)(implicit timeout: Span = Span(60, Seconds)): U =
    Eventually.eventually(Eventually.timeout(timeout), Eventually.interval(Span(2, Seconds))) {
      logException(f)
    }

  def eventually[U](timeout: Span, interval: Span)(f: => U): U =
    Eventually.eventually(Eventually.timeout(timeout), Eventually.interval(interval)) {
      logException(f)
    }

  protected def logException[U](f: => U): U =
    try {
      f
    } catch {
      case t: Throwable =>
        log.error(s"Eventually not satisfied due to [${t.getMessage}] of [${t.getClass.getName}]!")
        throw t
    }

}
