package io.kensu.redis_streams_zio.redis.streams.notifications

import io.kensu.redis_streams_zio.config.NotificationsStreamConsumerConfig
import io.kensu.redis_streams_zio.redis.streams.{ RedisStaleEventsCollector, StreamInstance }
import zio.logging.LogAnnotation.Name
import zio.logging.log

object NotificationsStaleEventsCollector {

  def run() =
    log.locally(Name(List(getClass.getName))) {
      RedisStaleEventsCollector.executeFor[StreamInstance.Notifications.type, NotificationsStreamConsumerConfig]()
    }
}
