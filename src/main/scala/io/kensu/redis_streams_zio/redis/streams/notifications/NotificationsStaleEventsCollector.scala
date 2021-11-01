package io.kensu.redis_streams_zio.redis.streams.notifications

import io.kensu.redis_streams_zio.config.NotificationsStreamConsumerConfig
import io.kensu.redis_streams_zio.redis.streams.{RedisStaleEventsCollector, StreamInstance}
import zio.logging.LogAnnotation.Name
import zio.logging.log
import io.kensu.redis_streams_zio.redis.streams.RedisStream
import zio.{Has, ZIO}
import zio.clock.Clock
import zio.logging.Logging

object NotificationsStaleEventsCollector:

  def run(): ZIO[Logging with Has[RedisStream[StreamInstance.Notifications]] with Has[
    NotificationsStreamConsumerConfig
  ] with Logging with Clock, Throwable, Long] =
    log.locally(Name(List(getClass.getName))) {
      RedisStaleEventsCollector.executeFor[StreamInstance.Notifications, NotificationsStreamConsumerConfig]()
    }
