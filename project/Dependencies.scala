import sbt._

object Dependencies {

  object Version {
    val zio       = "2.0.5"
    val zioConfig = "3.0.6"
  }

  val zio = Seq(
    "dev.zio" %% "zio"                 % Version.zio,
    "dev.zio" %% "zio-streams"         % Version.zio,
    "dev.zio" %% "zio-logging-slf4j"   % "2.1.7",
    "dev.zio" %% "zio-config-typesafe" % Version.zioConfig,
    "dev.zio" %% "zio-config-magnolia" % Version.zioConfig,
    "dev.zio" %% "zio-test-sbt"        % Version.zio % Test,
    "dev.zio" %% "zio-mock"            % "1.0.0-RC9" % Test
  )

  val redisson = Seq("org.redisson" % "redisson" % "3.19.1")

  val logback = Seq(
    "ch.qos.logback" % "logback-classic"  % "1.4.5",
    "org.slf4j"      % "log4j-over-slf4j" % "2.0.6"
  )
}
