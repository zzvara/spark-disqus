package hu.sztaki.spark.disqus

import hu.sztaki.spark.Comment

case class Fail(thread: String, forums: List[String] = List.empty) extends Result() {
  def asSuccess: Success = throw new NullPointerException("This is a [Fail], not a [Success]!")
}

case class Success(host: String, forumID: String, comments: List[Comment] = List.empty)
 extends Result() {
  def asSuccess: Success = this
}

case class Limited() extends Result() {
  def asSuccess: Success = throw new NullPointerException("This is a [Limited], not a [Success]!")
}

abstract class Result() {
  def asSuccess: Success

}
