package hu.sztaki.spark.youtube

trait Datum {
  def channelID: String
  def videoID: String
}
