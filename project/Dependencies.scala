import sbt._

object Dependencies {

  object Version {
    val zio       = "2.0.2"
    val zioConfig = "3.0.2"
  }

  val zio = Seq(
    "dev.zio" %% "zio"                 % Version.zio,
    "dev.zio" %% "zio-streams"         % Version.zio,
    "dev.zio" %% "zio-logging-slf4j"   % "2.1.2",
    "dev.zio" %% "zio-config-typesafe" % Version.zioConfig,
    "dev.zio" %% "zio-config-magnolia" % Version.zioConfig,
    "dev.zio" %% "zio-test-sbt"        % Version.zio % Test
  )

  val redisson = Seq("org.redisson" % "redisson" % "3.17.7")

  val logback = Seq(
    "ch.qos.logback" % "logback-classic"  % "1.2.11",
    "org.slf4j"      % "log4j-over-slf4j" % "1.7.36"
  )
}
