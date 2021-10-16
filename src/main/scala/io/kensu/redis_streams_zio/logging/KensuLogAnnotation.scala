package io.kensu.redis_streams_zio.logging

import io.kensu.redis_streams_zio.common.CorrelationId
import zio.logging.LogAnnotation
import zio.logging.LogContext

object KensuLogAnnotation {

  val AppCorrelationId: CorrelationId = io.kensu.redis_streams_zio.common.CorrelationId("application")

  val CorrelationId: LogAnnotation[CorrelationId] = LogAnnotation[CorrelationId](
    name         = "correlation_id",
    initialValue = AppCorrelationId,
    combine      = (_, r) => r,
    render       = _.value
  )

  val InitialLogContext: LogContext => LogContext = KensuLogAnnotation.CorrelationId(AppCorrelationId)
}
