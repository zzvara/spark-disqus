package hu.sztaki.spark.youtube

trait Logger {
  @transient protected lazy val log = com.typesafe.scalalogging.Logger(this.getClass)
}
