package hu.sztaki.spark.disqus

import org.json4s.{DefaultFormats, JValue}
import org.json4s.JsonAST.{JArray, JString}

case class Comment(
  forum: String,
  thread: String,
  content: String,
  parent: Option[Comment.Parent],
  internalID: Option[String],
  createdAt: Option[Long],
  negativeVotes: Option[Int],
  positiveVotes: Option[Int],
  nReported: Option[Int],
  author: Option[Comment.Author],
  flags: Option[Comment.Flags])

object Comment extends Logger {

  case class Author(
    ID: Option[String] = None,
    alias: Option[String] = None,
    name: Option[String] = None,
    mail: Option[String] = None,
    resource: Option[String] = None,
    createdAt: Option[Long] = None)

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

  implicit val parseFormat: DefaultFormats.type = DefaultFormats
  val dateFormat = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")

  def apply(fromJSON: JValue, host: String): List[Comment] =
    fromJSON match {
      case a: JArray =>
        a.arr.flatMap {
          v =>
            try {
              val content = (v \ "raw_message").extract[String]

              val author = v \ "author"
              List(Comment(
                (v \ "forum").extract[String],
                (v \ "thread").extract[String],
                content,
                parent = (v \ "parent").extractOpt[Int].map(_.toString).map(Comment.Parent(_)),
                internalID = (v \ "id").extractOpt[String],
                createdAt =
                  Try.eatShit[Throwable, java.util.Date](
                    dateFormat.parse((author \ "createdAt").extract[String])
                  ).map(_.getTime),
                negativeVotes = (v \ "dislikes").extractOpt[Int],
                positiveVotes = (v \ "likes").extractOpt[Int],
                nReported = (v \ "numReports").extractOpt[Int],
                author = Some(Comment.Author(
                  name = (author \ "name").extractOpt[String],
                  mail = (author \ "email").extract[Option[String]],
                  ID = (author \ "id").extractOpt[String],
                  alias = (author \ "username").extractOpt[String],
                  resource = (author \ "profileUrl").extractOpt[String],
                  createdAt =
                    Try.eatShit[Throwable, java.util.Date](
                      dateFormat.parse((author \ "joinedAt").extract[String])
                    ).map(_.getTime)
                )),
                flags = Some(Flags(
                  spam = (v \ "isSpam").extractOpt[Boolean],
                  deleted = (v \ "isDeleted").extractOpt[Boolean],
                  approved = (v \ "isApproved").extractOpt[Boolean],
                  flagged = (v \ "isFlagged").extractOpt[Boolean],
                  highlighted = (v \ "isHighlighted").extractOpt[Boolean],
                  edited = (v \ "isEdited").extractOpt[Boolean]
                ))
              ))
            } catch {
              case t: Throwable =>
                log.warn(
                  s"Could not construct post object from JSON due to " +
                    s"[${t.getMessage}] and error [${t.getClass.getName}]!"
                )
                t.printStackTrace()
                List.empty[Comment]
            }
        }
      case s: JString =>
        log.warn(s"Received the following response from Disqus, but not posts: [${s.values}]!")
        List.empty[Comment]
      case _ => List.empty[Comment]
    }

}
