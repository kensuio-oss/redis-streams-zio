package io.kensu.redis_streams_zio.common

opaque type CorrelationId = String

object CorrelationId:
  def apply(value: String): CorrelationId = value
