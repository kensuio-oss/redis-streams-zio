package io.kensu.redis_streams_zio.common

import zio.Schedule
import zio.duration.{ Duration, _ }

import scala.concurrent.duration.FiniteDuration

object Scheduling {

  def exponentialBackoff[R, E](
    min: FiniteDuration,
    max: FiniteDuration,
    factor: Double         = 2.0,
    maxRecurs: Option[Int] = None
  ): Schedule[R, E, Any] = {
    val zmin = Duration.fromScala(min)
    val zmax = Duration.fromScala(max)
    (Schedule.exponential(zmin, factor).whileOutput(_ <= zmax) andThen Schedule.fixed(zmax)) && maxRecurs
      .map(Schedule.recurs)
      .getOrElse(Schedule.forever)
  }
}
