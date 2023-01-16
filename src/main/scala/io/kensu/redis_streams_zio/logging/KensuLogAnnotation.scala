package io.kensu.redis_streams_zio.logging

import io.kensu.redis_streams_zio.common.CorrelationId
import zio.logging.{LogAnnotation, LogContext}

object KensuLogAnnotation:

  val AppCorrelationId: CorrelationId = io.kensu.redis_streams_zio.common.CorrelationId("application")

  val CorrelationId: LogAnnotation[CorrelationId] = LogAnnotation[CorrelationId](
    name    = "correlation_id",
    combine = (_, r) => r,
    render  = _.value
  )

  val InitialLogContext = KensuLogAnnotation.CorrelationId(AppCorrelationId)
