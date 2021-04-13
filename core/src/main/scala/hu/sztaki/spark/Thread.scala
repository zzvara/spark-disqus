package hu.sztaki.spark

import hu.sztaki.spark.Thread.Identifier

case class Thread(
  source: Source.Value,
  forum: String,
  thread: String,
  description: Option[String],
  title: Option[String],
  created: Option[Long])
 extends Unique[Identifier](Thread.Identifier.create(forum, thread))
   with Datum {}

object Thread {

  case class Identifier(stringified: String) extends Stringifiable {
    override def stringify: String = stringified
  }

  object Identifier {

    def create(forum: String, thread: String): Identifier =
      Identifier(
        Barcode.byHashingFrom(forum) + "." +
          Barcode.byHashingFrom(thread)
      )

  }

}
