import sbt._

object Dependencies {
  val currentScalaVersion = "2.12.12"

  val scalaLanguage = "org.scala-lang" % "scala-library" % currentScalaVersion
  val scalaCompiler = "org.scala-lang" % "scala-compiler" % currentScalaVersion
  val scalaTest = "org.scalatest" %% "scalatest" % "3.2.3" % "test"

  val coreDependencies = Seq(
    scalaLanguage,
    scalaCompiler,
    scalaTest,
    "com.typesafe" % "config" % "1.4.1",
    "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2",
    "org.slf4j" % "slf4j-api" % "1.7.30",
    "org.slf4j" % "slf4j-log4j12" % "1.7.30",
    "com.typesafe.akka" %% "akka-actor" % "2.6.11",
    "com.typesafe.akka" %% "akka-stream" % "2.6.11",
    "com.typesafe.akka" %% "akka-http" % "10.2.2",
    "org.apache.spark" %% "spark-core" % "3.1.0",
    "com.softwaremill.retry" %% "retry" % "0.3.3",
    ("org.apache.spark" %% "spark-streaming" % "3.1.0")
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
