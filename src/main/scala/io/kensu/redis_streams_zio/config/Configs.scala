package io.kensu.redis_streams_zio.config

import scala.concurrent.duration.FiniteDuration

final case class RedisConfig(
  url: String,
  password: String
)

final case class StreamName(value: String) extends AnyVal {
  override def toString: String = value
}
final case class StreamGroupName(value: String) extends AnyVal {
  override def toString: String = value
}
final case class StreamConsumerName(value: String) extends AnyVal {
  override def toString: String = value
}
final case class StreamKey(value: String) extends AnyVal {
  override def toString: String = value
}

final case class ClaimingConfig(
  initialDelay: FiniteDuration,
  repeatEvery: FiniteDuration,
  maxNoOfDeliveries: Long,
  maxIdleTime: FiniteDuration
)

final case class RetryConfig(
  min: FiniteDuration,
  max: FiniteDuration,
  factor: Double
)

trait StreamConsumerConfig {
  val claiming: ClaimingConfig
  val retry: RetryConfig
  val readTimeout: FiniteDuration
  val checkPendingEvery: FiniteDuration
  val streamName: StreamName
  val groupName: StreamGroupName
  val consumerName: StreamConsumerName
}

trait StreamProducerConfig {
  val streamName: StreamName
}
