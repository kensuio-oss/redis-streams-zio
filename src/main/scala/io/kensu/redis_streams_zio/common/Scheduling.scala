package io.kensu.redis_streams_zio.common

import zio.Schedule
import zio.duration.{ Duration, _ }

object Scheduling {

  def exponentialBackoff[R, E](
    min: Duration,
    max: Duration,
    factor: Double         = 2.0,
    maxRecurs: Option[Int] = None
  ): Schedule[R, E, Any] =
    (Schedule.exponential(min, factor).whileOutput(_ <= max) andThen Schedule.fixed(max)) && maxRecurs
      .map(Schedule.recurs)
      .getOrElse(Schedule.forever)
}
