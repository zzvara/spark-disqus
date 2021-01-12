package hu.sztaki.spark.disqus

case class Fail(thread: String, forums: List[String] = List.empty) extends Result()

case class Success(host: String, forumID: String, comments: List[Comment] = List.empty)
 extends Result()

case class Limited() extends Result()

abstract class Result()
