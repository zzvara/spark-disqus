package hu.sztaki.spark.youtube

trait Entrypoint {
  implicit lazy val configuration: Configuration = new Configuration()
}
