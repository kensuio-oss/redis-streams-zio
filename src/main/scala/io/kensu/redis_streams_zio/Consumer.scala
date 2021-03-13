package io.kensu.redis_streams_zio

import io.kensu.redis_streams_zio.config.Configs
import io.kensu.redis_streams_zio.logging.KensuLogAnnotation
import io.kensu.redis_streams_zio.redis.RedisClient
import io.kensu.redis_streams_zio.redis.streams.{ RedisStream, StreamInstance }
import io.kensu.redis_streams_zio.redis.streams.notifications.{ NotificationsStream, NotificationsStreamCollector }
import zio._
import zio.clock.Clock
import zio.config.syntax._
import zio.duration._
import zio.logging._
import zio.logging.slf4j.Slf4jLogger

object Consumer extends App {

  override def run(args: List[String]): URIO[ZEnv, ExitCode] =
    streams.useForever
      .provideCustomLayer(liveEnv)
      .exitCode

  private val streams =
    ZManaged.makeInterruptible {
      for {
        shutdownHook            <- Promise.make[Throwable, Unit]
        notificationStreamFiber <- notificationsStream(shutdownHook)
      } yield (shutdownHook, notificationStreamFiber)
    } {
      case (shutdownHook, notificationStreamFiber) =>
        (for {
          _ <- log.info("Halting streams")
          _ <- shutdownHook.succeed(())
          _ <- shutdownHook.await
          _ <- log.info("Shutting down streams... this may take a few seconds")
          _ <- notificationStreamFiber.join race ZIO.sleep(5.seconds)
          _ <- log.info("Streams shut down")
        } yield ()).ignore
    }

  private def notificationsStream(shutdownHook: Promise[Throwable, Unit]) =
    for {
      fork <- NotificationsStream.run(shutdownHook).fork
      _    <- NotificationsStreamCollector.run().fork
    } yield fork

  private val liveEnv = {
    val appConfig = Configs.appConfig

    val logging: ULayer[Logging] = Slf4jLogger.makeWithAnnotationsAsMdc(
      mdcAnnotations = List(KensuLogAnnotation.CorrelationId),
      logFormat      = (_, msg) => msg
    ) >>> Logging.modifyLogger(_.derive(KensuLogAnnotation.InitialLogContext))

    val redisClient = appConfig.narrow(_.redis) >>> RedisClient.live

    val notificationsStream = {
      val notificationsStream = StreamInstance.Notifications
      appConfig
        .narrow(_.redisStreams.consumers)
        .map { hasConsumers =>
          hasConsumers.get.notifications.streamName match {
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

    val clock = ZLayer.identity[Clock]

    clock ++ logging ++ appConfig.narrow(_.redisStreams.consumers.notifications) ++ notificationsStream
  }
}
