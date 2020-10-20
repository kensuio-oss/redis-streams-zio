package io.kensu.redis_streams_zio

import io.kensu.redis_streams_zio.config.RedisConfig
import io.kensu.redis_streams_zio.logging.KensuLogAnnotation
import io.kensu.redis_streams_zio.redis.RedisClient
import io.kensu.redis_streams_zio.redis.streams.notifications.NotificationsStreamProducerConfig
import io.kensu.redis_streams_zio.services.producers.EventProducer
import pureconfig.generic.auto._
import pureconfig.{ ConfigObjectSource, ConfigSource }
import zio._
import zio.clock.Clock
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
      config <- ZIO.service[NotificationsStreamProducerConfig]
      str    <- nextString(10)
      _      <- EventProducer.publish(config.streamName, config.addKey, str)
    } yield ()

  private val liveEnv = {
    val config: ConfigObjectSource = ConfigSource.default

    val logging: ULayer[Logging] = Slf4jLogger.makeWithAnnotationsAsMdc(
      mdcAnnotations = List(KensuLogAnnotation.CorrelationId),
      logFormat      = (_, msg) => msg
    ) >>> Logging.modifyLogger(_.derive(KensuLogAnnotation.InitialLogContext))

    val redisClient = ZLayer.succeedMany(config.at("kensu.redis").loadOrThrow[RedisConfig]) >>> RedisClient.live

    val clock = ZLayer.identity[Clock]

    val notificationsProducerConfig =
      config.at("kensu.redis-streams.producers.notifications").loadOrThrow[NotificationsStreamProducerConfig]

//    val notificationsStream = {
//      notificationsConsumerConfig.streamName match {
//        case s @ StreamName("notifications") =>
//          redisClient >>> RedisStream.buildFor(StreamInstance.Notifications(s))
//        case s => ZLayer.fail(new IllegalStateException(s"Unsupported stream $s"))
//      }
//    }

    val eventProducer = (redisClient ++ clock) ++ logging >+> EventProducer.redis

    clock ++ logging ++ ZLayer.succeed(notificationsProducerConfig) ++ eventProducer

  }
}
