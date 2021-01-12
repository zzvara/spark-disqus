package hu.sztaki.spark.disqus

case class Request(
  thread: String,
  forum: Option[String] = None)
