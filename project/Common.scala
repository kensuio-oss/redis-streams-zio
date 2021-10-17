import org.scalafmt.sbt.ScalafmtPlugin
import org.scalafmt.sbt.ScalafmtPlugin.autoImport._
import sbt.Keys.{logBuffered, _}
import sbt._

object Common {

  private val commonScalacOptions = Seq(
    "-unchecked",
    "-deprecation",
    "-feature",
    "-language:existentials",
    "-language:higherKinds",
    "-language:implicitConversions",
    "-language:postfixOps",
    "-explain"
//    "-Werror"
  )

  implicit class ProjectFrom(project: Project) {

    def commonSettings: Project =
      project
        .settings(
          organization := "io.kensu",
          name := "redis-streams-zio",
          semanticdbVersion := "4.4.28",
          scalaVersion := "3.0.2",
          version := "1.0.0-SNAPSHOT",
          scalacOptions ++= commonScalacOptions,
          Compile / console / scalacOptions --= Seq("-Werror"),
          scalacOptions ++= Seq("-target:11"),
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
