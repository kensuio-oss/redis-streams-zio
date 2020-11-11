import Common._
import Dependencies._

lazy val root =
  (project in file(".")).commonSettings.scalafmtSettings
    .settings(
      libraryDependencies ++= zio ++ logback ++ redisson
    )

addCommandAlias("fmt", "; scalafmt; scalafmtSbt; test:scalafmt")
addCommandAlias("checkFormatAll", "; scalafmtSbtCheck; scalafmtCheck; test:scalafmtCheck")
addCommandAlias("compileAll", "; clean; compile; test:compile")
addCommandAlias("checkAll", "; checkFormatAll; compileAll; test")
