package io.kensu.redis_streams_zio.common

final case class CorrelationId(value: String) extends AnyVal:
  override def toString: String = value
