package hu.sztaki.spark

trait Datum {
  def source: Source.Value
  def forum: String
  def thread: String
  def created: Option[Long]
  def ID: Stringifiable
}
