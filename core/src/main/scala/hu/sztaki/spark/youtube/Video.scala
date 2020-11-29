package hu.sztaki.spark.youtube

import com.google.api.services.youtube.model.SearchResult

case class Video(
  channelID: String,
  videoID: String,
  description: String,
  channelTitle: String,
  publishedAt: Long,
  title: String)
 extends Datum

object Video {

  def apply(searchResult: SearchResult): Video =
    Video(
      searchResult.getSnippet.getChannelId,
      searchResult.getId.getVideoId,
      searchResult.getSnippet.getDescription,
      searchResult.getSnippet.getChannelTitle,
      searchResult.getSnippet.getPublishedAt.getValue,
      searchResult.getSnippet.getTitle
    )

}
