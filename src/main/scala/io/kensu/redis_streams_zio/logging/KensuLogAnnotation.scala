package io.kensu.redis_streams_zio.logging

import io.kensu.redis_streams_zio.common.CorrelationId
import zio.logging.LogAnnotation

object KensuLogAnnotation {

  val AppCorrelationId = io.kensu.redis_streams_zio.common.CorrelationId("application")

  val CorrelationId = LogAnnotation[CorrelationId](
    name         = "correlation_id",
    initialValue = AppCorrelationId,
    combine      = (_, r) => r,
    render       = _.value
  )

  val InitialLogContext = KensuLogAnnotation.CorrelationId(AppCorrelationId)
}
