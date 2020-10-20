package io.kensu.redis_streams_zio.redis.streams.notifications

import io.kensu.redis_streams_zio.redis.streams.RedisStream.RedisStream
import io.kensu.redis_streams_zio.redis.streams.{ RedisZCollector, StreamInstance }
import zio.clock.Clock
import zio.logging.LogAnnotation.Name
import zio.logging.{ log, Logging }
import zio.{ Has, ZIO }

object NotificationsStreamCollector {

  type Input = Logging
    with Clock
    with Has[NotificationsStreamConsumerConfig]
    with RedisStream[StreamInstance.Notifications]

  def run(): ZIO[Input, Throwable, Long] =
    log.locally(Name(List(getClass.getName))) {
      RedisZCollector.executeFor[StreamInstance.Notifications, NotificationsStreamConsumerConfig]()
    }
}
