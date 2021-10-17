package io.kensu.redis_streams_zio

import io.kensu.redis_streams_zio.config.{Configs, NotificationsStreamProducerConfig}
import io.kensu.redis_streams_zio.logging.KensuLogAnnotation
import io.kensu.redis_streams_zio.redis.RedisClient
import io.kensu.redis_streams_zio.redis.streams.{NotificationsRedisStream, StreamInstance}
import io.kensu.redis_streams_zio.services.producers.NotificationsEventProducer
import zio._
import zio.clock.Clock
import zio.config.getConfig
import zio.config.syntax.ZIOConfigNarrowOps
import zio.duration.durationInt
import zio.logging.Logging
import zio.logging.slf4j.Slf4jLogger
import zio.random.nextString
import io.kensu.redis_streams_zio.services.producers.EventProducer
import zio.random.Random

object Producer extends App {

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    sentNotification
      .repeat(Schedule.spaced(5.seconds).jittered)
      .provideCustomLayer(liveEnv)
      .exitCode

  val sentNotification: ZIO[
    Has[NotificationsStreamProducerConfig] with Random with Has[EventProducer[StreamInstance.Notifications]],
    Throwable,
    Unit
  ] =
    for
      config <- getConfig[NotificationsStreamProducerConfig]
      str    <- nextString(10)
      _      <- NotificationsEventProducer(_.publish(config.addKey, str))
    yield ()

  private val liveEnv = {
    val appConfig      = Configs.appConfig
    val producerConfig = appConfig.narrow(_.redisStreams.producers.notifications)

    val logging: ULayer[Logging] = Slf4jLogger.makeWithAnnotationsAsMdc(
      mdcAnnotations = List(KensuLogAnnotation.CorrelationId),
      logFormat      = (_, msg) => msg
    ) >>> Logging.modifyLogger(_.derive(KensuLogAnnotation.InitialLogContext))

    val redisClient = appConfig.narrow(_.redis) >>> RedisClient.live

    val clock = ZLayer.identity[Clock]

    val notificationsStream = {
      val redisStream = {
        val streamInstance = appConfig.narrow(_.redisStreams.producers).map { hasProducers =>
          Has(StreamInstance.Notifications(hasProducers.get.notifications.streamName))
        }
        (streamInstance ++ redisClient) >>> NotificationsRedisStream.redisson
      }

      (redisStream ++ clock ++ logging) >>> NotificationsEventProducer.redis
    }

    clock ++ logging ++ producerConfig ++ notificationsStream

  }
}
