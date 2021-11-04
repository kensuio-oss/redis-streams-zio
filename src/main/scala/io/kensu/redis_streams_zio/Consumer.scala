package io.kensu.redis_streams_zio

import io.kensu.redis_streams_zio.config.Configs
import io.kensu.redis_streams_zio.logging.KensuLogAnnotation
import io.kensu.redis_streams_zio.redis.RedisClient
import io.kensu.redis_streams_zio.redis.streams.{NotificationsRedisStream, StreamInstance}
import io.kensu.redis_streams_zio.redis.streams.notifications.{NotificationsConsumer, NotificationsStaleEventsCollector}
import zio.*
import zio.clock.Clock
import zio.config.syntax.*
import zio.duration.*
import zio.logging.*
import zio.logging.slf4j.Slf4jLogger

object Consumer extends App:

  override def run(args: List[String]): URIO[ZEnv, ExitCode] =
    streams.useForever
      .provideCustomLayer(liveEnv)
      .exitCode

  private val streams =
    ZManaged.makeInterruptible {
      for
        shutdownHook            <- Promise.make[Throwable, Unit]
        notificationStreamFiber <- notificationsStream(shutdownHook)
      yield (shutdownHook, notificationStreamFiber)
    } { (shutdownHook, notificationStreamFiber) =>
      (for
        _ <- log.info("Halting streams")
        _ <- shutdownHook.succeed(())
        _ <- shutdownHook.await
        _ <- log.info("Shutting down streams... this may take a few seconds")
        _ <- notificationStreamFiber.join `race` ZIO.sleep(5.seconds)
        _ <- log.info("Streams shut down")
      yield ()).ignore
    }

  private def notificationsStream(shutdownHook: Promise[Throwable, Unit]) =
    for
      fork <- NotificationsConsumer.run(shutdownHook).fork
      _    <- NotificationsStaleEventsCollector.run().fork
    yield fork

  private val liveEnv =
    val appConfig = Configs.appConfig

    val logging: ULayer[Logging] = Slf4jLogger.makeWithAnnotationsAsMdc(
      mdcAnnotations = List(KensuLogAnnotation.CorrelationId),
      logFormat      = (_, msg) => msg
    ) >>> Logging.modifyLogger(_.derive(KensuLogAnnotation.InitialLogContext))

    val redisClient = appConfig.narrow(_.redis) >>> RedisClient.live

    val notificationsStream =
      val streamInstance = appConfig.narrow(_.redisStreams.consumers).map(hasConsumers =>
        Has(StreamInstance.Notifications(hasConsumers.get.notifications.streamName))
      )
      (streamInstance ++ redisClient) >>> NotificationsRedisStream.redisson

    val clock = ZLayer.identity[Clock]

    clock ++ logging ++ appConfig.narrow(_.redisStreams.consumers.notifications) ++ notificationsStream
