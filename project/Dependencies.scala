import sbt._

object Dependencies {
  object Version {
    val zio       = "1.0.3"
    val zioConfig = "1.0.0-RC29"
  }

  val zio = Seq(
    "dev.zio" %% "zio"                 % Version.zio,
    "dev.zio" %% "zio-streams"         % Version.zio,
    "dev.zio" %% "zio-macros"          % Version.zio,
    "dev.zio" %% "zio-logging-slf4j"   % "0.5.2",
    "dev.zio" %% "zio-config-magnolia" % Version.zioConfig,
    "dev.zio" %% "zio-config-typesafe" % Version.zioConfig,
    "dev.zio" %% "zio-test-sbt"        % Version.zio % Test
  )

  val redisson = Seq("org.redisson" % "redisson" % "3.13.6")

  val logback = Seq(
    "ch.qos.logback" % "logback-classic"  % "1.2.3",
    "org.slf4j"      % "log4j-over-slf4j" % "1.7.30"
  )
}
