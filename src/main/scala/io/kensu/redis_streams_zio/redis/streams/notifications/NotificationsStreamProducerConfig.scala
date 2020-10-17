package io.kensu.redis_streams_zio.redis.streams.notifications

import io.kensu.redis_streams_zio.config.{ StreamKey, StreamName, StreamProducerConfig }

final case class NotificationsStreamProducerConfig(
  streamName: StreamName,
  createKey: StreamKey,
  updateKey: StreamKey
) extends StreamProducerConfig
