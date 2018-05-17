import sbt._

object Dependencies {
  val currentScalaVersion = "2.12.12"

  val scalaLanguage = "org.scala-lang" % "scala-library" % currentScalaVersion
  val scalaCompiler = "org.scala-lang" % "scala-compiler" % currentScalaVersion
  val scalaTest = "org.scalatest" %% "scalatest" % "3.2.3" % "test"

  val coreDependencies = Seq(
    scalaLanguage,
    scalaCompiler,
    scalaTest
  )

}
