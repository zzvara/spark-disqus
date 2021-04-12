package hu.sztaki.spark

trait Datum {
  def forum: String
  def thread: String
  def created: Option[Long]
  def ID: Stringifiable
}
