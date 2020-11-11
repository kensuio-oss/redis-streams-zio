package io.kensu.redis_streams_zio.redis.streams.notifications

import io.kensu.redis_streams_zio.config.NotificationsStreamConsumerConfig
import io.kensu.redis_streams_zio.redis.streams.{ RedisZCollector, StreamInstance }
import zio.logging.LogAnnotation.Name
import zio.logging.log

object NotificationsStreamCollector {

  def run() =
    log.locally(Name(List(getClass.getName))) {
      RedisZCollector.executeFor[StreamInstance.Notifications.type, NotificationsStreamConsumerConfig]()
    }
}
