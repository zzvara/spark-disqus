import sbt._

object Dependencies {
  val currentScalaVersion = "2.12.12"

  val coreDependencies = Seq(
    "org.scala-lang" % "scala-library" % currentScalaVersion,
    "org.scala-lang" % "scala-compiler" % currentScalaVersion,
    "org.scalatest" %% "scalatest" % "3.3.0-SNAP3" % "test",
    "com.typesafe" % "config" % "1.4.1",
    "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2",
    "org.slf4j" % "slf4j-api" % "1.7.30",
    "org.slf4j" % "slf4j-log4j12" % "1.7.30",
    "com.typesafe.akka" %% "akka-actor" % "2.6.13",
    "com.typesafe.akka" %% "akka-stream" % "2.6.13",
    "com.typesafe.akka" %% "akka-http" % "10.2.4",
    "org.apache.spark" %% "spark-core" % "3.1.1",
    "com.softwaremill.retry" %% "retry" % "0.3.3",
    "org.apache.logging.log4j" % "log4j-slf4j-impl" % "2.14.1",
    ("org.apache.spark" %% "spark-streaming" % "3.1.1")
      .exclude("org.scalatest", "scalatest_2.12")
      .excludeAll(
        /**
          * To fix Spark Web UI method-not-found errors.
          */
        ExclusionRule("com.sun.jersey"),
        ExclusionRule("org.mortbay.jetty", "servlet-api")
      )
  )

}
