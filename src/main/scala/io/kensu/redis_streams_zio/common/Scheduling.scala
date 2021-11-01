package io.kensu.redis_streams_zio.common

import zio.Schedule
import zio.duration.{Duration, *}

object Scheduling:

  def exponentialBackoff[E](
    min: Duration,
    max: Duration,
    factor: Double         = 2.0,
    maxRecurs: Option[Int] = None
  ): Schedule[Any, E, Long] =
    ((Schedule.exponential(min, factor).whileOutput(_ <= max) `andThen` Schedule.fixed(max)) && maxRecurs
      .map(Schedule.recurs)
      .getOrElse(Schedule.forever)).map(_._2)
