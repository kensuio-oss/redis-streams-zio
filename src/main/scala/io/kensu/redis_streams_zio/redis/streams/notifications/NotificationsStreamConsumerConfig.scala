package io.kensu.redis_streams_zio.redis.streams.notifications

import io.kensu.redis_streams_zio.config._

import scala.concurrent.duration.FiniteDuration

final case class NotificationsStreamConsumerConfig(
  claiming: ClaimingConfig,
  retry: RetryConfig,
  readTimeout: FiniteDuration,
  checkPendingEvery: FiniteDuration,
  streamName: StreamName,
  addKey: StreamKey,
  groupName: StreamGroupName,
  consumerName: StreamConsumerName
) extends StreamConsumerConfig
