//https://scalameta.org/scalafmt/#sbt-scalafmt
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.2")

//https://scalacenter.github.io/scala-3-migration-guide/docs/tooling/scala-3-migrate-plugin.html
addSbtPlugin("ch.epfl.scala"      % "sbt-scala3-migrate" % "0.4.6")
addCompilerPlugin("org.scalameta" % "semanticdb-scalac"  % "4.4.28" cross CrossVersion.full)
scalacOptions += "-Yrangepos"
semanticdbVersion := "4.4.28"
