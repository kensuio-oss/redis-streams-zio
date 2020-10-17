import org.scalafmt.sbt.ScalafmtPlugin
import org.scalafmt.sbt.ScalafmtPlugin.autoImport._
import sbt.Keys.{ logBuffered, _ }
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
    "-Ymacro-annotations",
    "-Wdead-code",
    "-Werror",
    "-Wunused",
    "-Wnumeric-widen",
    "-Xlint:-infer-any"
  )

  implicit class ProjectFrom(project: Project) {

    def commonSettings: Project =
      project
        .settings(
          organization := "io.kensu",
          name := "redis-streams-zio",
          scalaVersion := "2.13.3",
          version := "1.0.0-SNAPSHOT",
          scalacOptions ++= commonScalacOptions,
          scalacOptions in (Compile, console) --= Seq("-Wunused:imports", "-Werror"),
          scalacOptions ++= Seq("-target:jvm-1.8"),
          javacOptions ++= Seq("-source", "1.8", "-target", "1.8"),
          cancelable in Global := true,
          parallelExecution in Test := true,
          fork := true,
          logBuffered in Test := false,
          testOptions in Test += Tests.Argument("-oDF"),
          testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
        )

    def scalafmtSettings: Project =
      project
        .enablePlugins(ScalafmtPlugin)
        .settings(scalafmtOnCompile := false)
  }
}
