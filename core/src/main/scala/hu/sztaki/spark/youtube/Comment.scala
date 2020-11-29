package hu.sztaki.spark.youtube

import com.google.api.services.youtube.model.CommentThread

case class Comment(
  channelID: String,
  videoID: String,
  content: String,
  commentID: String,
  parentCommentID: Option[String],
  updatedAt: Option[Long],
  positiveVotes: Option[Long],
  authorName: Option[String])
 extends Datum {}

object Comment {

  import scala.collection.JavaConverters.asScalaBufferConverter

  def apply(video: Video, commentThread: CommentThread): List[Comment] =
    Comment(
      video.channelID,
      video.videoID,
      commentThread.getId,
      commentThread.getSnippet.getTopLevelComment.getSnippet.getTextOriginal,
      Option(commentThread.getSnippet.getTopLevelComment.getSnippet.getParentId),
      Option(commentThread.getSnippet.getTopLevelComment.getSnippet.getUpdatedAt.getValue),
      Option(commentThread.getSnippet.getTopLevelComment.getSnippet.getLikeCount),
      Option(commentThread.getSnippet.getTopLevelComment.getSnippet.getAuthorDisplayName)
    ) ::
      Option(commentThread.getReplies).toList.flatMap(_.getComments.asScala.map {
        commentReply =>
          Comment(
            video.channelID,
            video.videoID,
            commentReply.getId,
            commentReply.getSnippet.getTextOriginal,
            Option(commentReply.getSnippet.getParentId),
            Option(commentReply.getSnippet.getUpdatedAt.getValue),
            Option(commentReply.getSnippet.getLikeCount),
            Option(commentReply.getSnippet.getAuthorDisplayName)
          )
      })

}
