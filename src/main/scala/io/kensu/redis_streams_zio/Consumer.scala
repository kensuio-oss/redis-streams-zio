package io.kensu.redis_streams_zio

import io.kensu.redis_streams_zio.config.Configs
import io.kensu.redis_streams_zio.logging.KensuLogAnnotation
import io.kensu.redis_streams_zio.redis.RedisClient
import io.kensu.redis_streams_zio.redis.streams.notifications.{ NotificationsStream, NotificationsStreamCollector }
import io.kensu.redis_streams_zio.redis.streams.{ RedisStream, StreamInstance }
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
    ZManaged.make {
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
      fork <- NotificationsStream.run(shutdownHook).interruptible.fork
      _    <- NotificationsStreamCollector.run().interruptible.fork
    } yield fork

  private val liveEnv = {
    val rootConfig = Configs.loadOrFail

    val logging: ULayer[Logging] = Slf4jLogger.makeWithAnnotationsAsMdc(
      mdcAnnotations = List(KensuLogAnnotation.CorrelationId),
      logFormat      = (_, msg) => msg
    ) >>> Logging.modifyLogger(_.derive(KensuLogAnnotation.InitialLogContext))

    val redisClient = rootConfig.narrow(_.kensu.redis) >>> RedisClient.live

    val notificationsStream =
      redisClient >>> RedisStream.buildFor(StreamInstance.Notifications)

    val clock = ZLayer.identity[Clock]

    clock ++ logging ++ rootConfig.narrow(_.kensu.redisStreams.consumers.notifications) ++ notificationsStream
  }
}
