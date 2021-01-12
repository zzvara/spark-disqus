package hu.sztaki.spark.disqus

trait Logger {
  @transient protected lazy val log = com.typesafe.scalalogging.Logger(this.getClass)
}
