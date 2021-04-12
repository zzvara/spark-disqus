package hu.sztaki.spark

trait Logger {
  @transient protected lazy val log = com.typesafe.scalalogging.Logger(this.getClass)
}
