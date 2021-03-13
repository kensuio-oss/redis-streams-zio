package io.kensu.redis_streams_zio

import io.kensu.redis_streams_zio.config.{ Configs, NotificationsStreamProducerConfig }
import io.kensu.redis_streams_zio.logging.KensuLogAnnotation
import io.kensu.redis_streams_zio.redis.RedisClient
import io.kensu.redis_streams_zio.redis.streams.{ RedisStream, StreamInstance }
import io.kensu.redis_streams_zio.services.producers.EventProducer
import zio._
import zio.clock.Clock
import zio.config.getConfig
import zio.config.syntax.ZIOConfigNarrowOps
import zio.duration.durationInt
import zio.logging.Logging
import zio.logging.slf4j.Slf4jLogger
import zio.random.nextString

object Producer extends App {

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    sentNotification
      .repeat(Schedule.spaced(5.seconds).jittered)
      .provideCustomLayer(liveEnv)
      .exitCode

  val sentNotification =
    for {
      config <- getConfig[NotificationsStreamProducerConfig]
      str    <- nextString(10)
      _      <- EventProducer.publish[StreamInstance.Notifications.type, String](config.addKey, str)
    } yield ()

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
      val notificationsStream = StreamInstance.Notifications
      val redisStream = {
        appConfig
          .narrow(_.redisStreams.producers)
          .map { hasProducers =>
            hasProducers.get.notifications.streamName match {
              case notificationsStream.name => ()
              case s                        => throw new IllegalStateException(s"Unsupported stream $s")
            }
          }
          .build
          .zipRight {
            val stream = redisClient >>> RedisStream.buildFor(notificationsStream)
            stream.build
          }
          .toLayerMany
      }

//      val redisStream = (redisClient >>> RedisStream.buildFor(StreamInstance.Notifications))
      (redisStream ++ clock ++ logging) >>> EventProducer.redisFor(notificationsStream)
    }

    clock ++ logging ++ producerConfig ++ notificationsStream

  }
}
