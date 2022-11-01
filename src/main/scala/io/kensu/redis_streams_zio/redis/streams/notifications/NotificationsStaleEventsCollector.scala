package io.kensu.redis_streams_zio.redis.streams.notifications

import io.kensu.redis_streams_zio.config.NotificationsStreamConsumerConfig
import io.kensu.redis_streams_zio.redis.streams.{RedisStaleEventsCollector, RedisStream, StreamInstance}
import zio.{Clock, ZIO}
import zio.logging.backend.SLF4J

object NotificationsStaleEventsCollector:

  def run(): ZIO[
    RedisStream[StreamInstance.Notifications] & NotificationsStreamConsumerConfig,
    Throwable,
    Long
  ] =
    RedisStaleEventsCollector.executeFor[
      StreamInstance.Notifications,
      NotificationsStreamConsumerConfig
    ]() @@ SLF4J.loggerName(getClass.getName)
