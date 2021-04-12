package hu.sztaki.spark

import hu.sztaki.spark.Comment.{Identifier, Metrics}

case class Comment(
  forum: String,
  thread: String,
  content: String,
  internalID: Option[String],
  parent: Option[Comment.Parent],
  created: Option[Long],
  metrics: Option[Metrics],
  author: Option[Comment.Author],
  flags: Option[Comment.Flags]
)
 extends Unique[Identifier](Comment.Identifier.create(forum, thread, content))
   with Datum {}

object Comment {

  case class Identifier(stringified: String) extends Stringifiable {
    override def stringify: String = stringified
  }

  object Identifier {

    def create(forum: String, thread: String, content: String): Identifier =
      Identifier(
        Barcode.byHashingFrom(forum) + "." +
          Barcode.byHashingFrom(thread) + "." +
          Barcode.byHashingFrom(content)
      )

  }

  case class Metrics(
    negative: Option[Int],
    positive: Option[Int],
    reported: Option[Int])

  case class Author(
    identifier: Option[String] = None,
    alias: Option[String] = None,
    name: Option[String] = None,
    mail: Option[String] = None,
    resource: Option[String] = None,
    created: Option[Long] = None)

  case class Parent(
    internal: String,
    author: Option[Author] = None)

  case class Flags(
    spam: Option[Boolean] = None,
    deleted: Option[Boolean] = None,
    approved: Option[Boolean] = None,
    flagged: Option[Boolean] = None,
    highlighted: Option[Boolean] = None,
    edited: Option[Boolean] = None)

}
