package hu.sztaki.spark.disqus

trait Entrypoint {
  implicit lazy val configuration: Configuration = new Configuration()
}
