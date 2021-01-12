package hu.sztaki.spark.disqus

object Thread {

  case object Identified extends Enumeration {
    val byIdentifier, byLink = Value
  }

}
