package hu.sztaki.spark.disqus

import hu.sztaki.spark
import hu.sztaki.spark.Comment
import hu.sztaki.spark.Comment.Flags
import org.json4s.{DefaultFormats, JValue}
import org.json4s.JsonAST.{JArray, JString}

object Comment extends Logger {

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
              List(spark.Comment(
                (v \ "forum").extract[String],
                (v \ "thread").extract[String],
                content,
                internalID = (v \ "id").extractOpt[String],
                parent =
                  (v \ "parent").extractOpt[Int].map(_.toString).map(spark.Comment.Parent(_)),
                created =
                  Try.eatShit[Throwable, java.util.Date](
                    dateFormat.parse((author \ "createdAt").extract[String])
                  ).map(_.getTime),
                metrics = Some(spark.Comment.Metrics(
                  negative = (v \ "dislikes").extractOpt[Int],
                  positive = (v \ "likes").extractOpt[Int],
                  reported = (v \ "numReports").extractOpt[Int]
                )),
                author = Some(spark.Comment.Author(
                  identifier = (author \ "id").extractOpt[String],
                  name = (author \ "name").extractOpt[String],
                  mail = (author \ "email").extract[Option[String]],
                  alias = (author \ "username").extractOpt[String],
                  resource = (author \ "profileUrl").extractOpt[String],
                  created =
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
