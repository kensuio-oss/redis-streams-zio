package io.kensu.redis_streams_zio

import io.kensu.redis_streams_zio.config.{Configs, NotificationsStreamProducerConfig}
import io.kensu.redis_streams_zio.logging.KensuLogAnnotation
import io.kensu.redis_streams_zio.redis.RedisClient
import io.kensu.redis_streams_zio.redis.streams.{NotificationsRedisStream, StreamInstance}
import io.kensu.redis_streams_zio.services.producers.{EventProducer, NotificationsEventProducer}
import zio.Random.nextString
import zio.config.getConfig
import zio.config.syntax.*
import zio.logging.backend.SLF4J
import zio.logging.backend.SLF4J.loggerNameAnnotationKey
import zio.{Clock, Random, ZIOAppDefault, ZIOAspect, *}

object Producer extends ZIOAppDefault:

  private val sentNotification =
    for
      config <- getConfig[NotificationsStreamProducerConfig]
      str    <- nextString(10)
      _      <- ZIO.serviceWithZIO[EventProducer[StreamInstance.Notifications]](_.publish(config.addKey, str))
    yield ()

  private val liveEnv =
    val appConfig      = Configs.appConfig
    val producerConfig = appConfig.narrow(_.redisStreams.producers.notifications)

    val logging = Runtime.removeDefaultLoggers >>> SLF4J.slf4j

    val redisClient = appConfig.narrow(_.redis) >>> RedisClient.live

    val notificationsStream =
      val redisStream =
        val streamInstance = appConfig.narrow(_.redisStreams.producers).map { hasProducers =>
          ZEnvironment(StreamInstance.Notifications(hasProducers.get.notifications.streamName))
        }
        (streamInstance ++ redisClient) >>> NotificationsRedisStream.redisson

      (redisStream ++ logging) >>> NotificationsEventProducer.redis

    ZLayer.make[NotificationsStreamProducerConfig & EventProducer[StreamInstance.Notifications]](
      logging,
      producerConfig,
      notificationsStream
    )

  override val run =
    sentNotification
      .repeat(Schedule.spaced(5.seconds).jittered)
      .provideLayer(liveEnv) @@ KensuLogAnnotation.InitialLogContext
