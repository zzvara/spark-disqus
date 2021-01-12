package hu.sztaki.spark.disqus

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}
import scala.reflect.ClassTag
import scala.util.{Failure, Success}

/**
  * Utility methods for common error handling routines.
  */
object Try extends Logger {

  /**
    * Try to execute the give function really hard.
    */
  def tryHard[U](f: => U, awaitSeconds: Int = 20, maxTries: Int = 10): U = {
    implicit val success = retry.Success[Either[U, Throwable]](_.isLeft)
    Await.result[Either[U, Throwable]](
      retry.Directly(maxTries)(
        () =>
          Future {
            try {
              Left(f)
            } catch {
              case t: Throwable =>
                log.warn(s"Exception [${t.getClass.toString}] has been thrown. Retrying.")
                Right(t)
            }
          }
      ),
      awaitSeconds.seconds
    ) match {
      case Left(result) =>
        result
      case Right(throwable) =>
        throw throwable
    }
  }

  /**
    * While executing the [[Unit]] function, this method eats your [[Throwable]], so you don't
    * have to worry about that.
    */
  def eatShit[T <: Throwable : ClassTag](f: => Unit): Unit = {
    try {
      f
    } catch {
      case t: Throwable if implicitly[ClassTag[T]].runtimeClass.isInstance(t) =>
        ()
    }
  }

  /**
    * While executing the [[R]] function, this method eats all [[Throwable]], packs it
    * into the `outer` [[Throwable]] and rethrows the error.
    */
  def packShitAndDie[R](
    outer: Throwable => Throwable,
    logger: String => Unit = log.debug(_)
  )(f: => R): R =
    try {
      f
    } catch {
      case t: Throwable =>
        val packed = outer(t)
        logger(s"Packing shit [${t.getClass.getName}] with message [${t.getMessage}] into " +
          s"[${packed.getClass.getName}]; [${packed.getMessage}]!")
        throw packed
    }

  /**
    * While executing the [[R]] function, this method eats all [[Throwable]], so you don't
    * have to worry about that.
    */
  def eatAnyShit[R](f: => R): Option[R] =
    scala.util.Try(f) match {
      case scala.util.Failure(exception) =>
        log.error(s"Encountered exception [${exception.getClass.getName}] of message " +
          s"[${exception.getMessage}]!")
        None
      case scala.util.Success(v) => Some(v)
    }

  /**
    * While executing the [[R]] function, this method eats your [[T]] [[Throwable]], so you don't
    * have to worry about that.
    */
  @throws[Throwable]("When some throwable go through.")
  def eatShit[T <: Throwable : ClassTag, R](f: => R): Option[R] =
    try {
      Some(f)
    } catch {
      case t: Throwable if implicitly[ClassTag[T]].runtimeClass.isInstance(t) =>
        None
      case t: Throwable =>
        throw t
    }

  /**
    * While execution the [[U]] function, this method eats all the specified throwables and returns
    * a default value for them, but throws the exception out otherwise.
    */
  @throws[Throwable]("When some throwable go through.")
  def defaultOn[U](throwables: Class[_ <: Throwable]*)(default: U)(f: => U): U =
    try {
      f
    } catch {
      case t: Throwable =>
        if (throwables.contains(t.getClass)) {
          default
        } else {
          throw t
        }
    }

  def stacktraceAndThrow[R](f: => R): R =
    try {
      f
    } catch {
      case exception: Throwable =>
        log.error(
          s"Exception encountered with class [${exception.getClass.getName}] " +
            s"and message [${exception.getMessage}]!"
        )
        exception.printStackTrace()
        throw exception
    }

  def logAndThrow[R](t: scala.util.Try[R], logger: String => Unit = log.error(_)): R =
    t match {
      case scala.util.Failure(exception) =>
        logger(
          s"Exception encountered with class [${exception.getClass.getName}] " +
            s"and message [${exception.getMessage}]!"
        )
        throw exception
      case scala.util.Success(value) =>
        value
    }

}
