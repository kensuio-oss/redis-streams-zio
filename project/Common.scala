import org.scalafmt.sbt.ScalafmtPlugin
import org.scalafmt.sbt.ScalafmtPlugin.autoImport._
import sbt.Keys._
import sbt._

object Common {

  // https://docs.scala-lang.org/scala3/guides/migration/options-lookup.html
  // Many scalac 2.13 options are not available in scala 3
  private val commonScalacOptions = Seq(
    "-unchecked",
    "-deprecation",
    "-feature",
    "-Werror",
    "-explain",
    "-new-syntax", // First we need to use -new-syntax with rewrite, in the 2nd step 3.0-migration // Require `then` and `do` in control expressions.
//    "-indent", // Once done, comment out // Together with -rewrite, remove {...} syntax when possible due to significant indentation.
//    "-rewrite",
    "-source:future-migration" // Supports given/using etc.
    // "-language:strictEquality"
  )

  implicit class ProjectFrom(project: Project) {

    def commonSettings: Project =
      project
        .settings(
          organization := "io.kensu",
          name := "redis-streams-zio",
          scalaVersion := "3.2.0",
          version := "1.0.0-SNAPSHOT",
          scalacOptions ++= commonScalacOptions,
          Compile / console / scalacOptions --= Seq("-Werror"),
          scalacOptions ++= Seq("-release:11"),
          javacOptions ++= Seq("-source", "11", "-target", "11"),
          Global / cancelable := true,
          fork := true,
          Test / parallelExecution := true,
          Test / logBuffered := false,
          Test / testOptions += Tests.Argument("-oDF"),
          testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
        )

    def scalafmtSettings: Project =
      project
        .enablePlugins(ScalafmtPlugin)
        .settings(scalafmtOnCompile := false)
  }
}
