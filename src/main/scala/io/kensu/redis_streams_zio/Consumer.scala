package io.kensu.redis_streams_zio

import io.kensu.redis_streams_zio.config.Configs
import io.kensu.redis_streams_zio.logging.KensuLogAnnotation
import io.kensu.redis_streams_zio.redis.RedisClient
import io.kensu.redis_streams_zio.redis.streams.notifications.{NotificationsConsumer, NotificationsStaleEventsCollector}
import io.kensu.redis_streams_zio.redis.streams.{NotificationsRedisStream, StreamInstance}
import zio.{Clock, ZIOAppDefault, *}
import zio.config.syntax.*
import zio.logging.*
import zio.logging.backend.SLF4J

object Consumer extends ZIOAppDefault:

  override def run =
    streams.forever // FIXME useForever?
      .provideSomeLayer[Scope](liveEnv) // TODO layer is bootstrap now
      .exitCode

  private val streams =
    ZIO.acquireRelease { // FIXME acquireReleaseInterruptible !!!
      for
        shutdownHook            <- Promise.make[Throwable, Unit]
        notificationStreamFiber <- notificationsStream(shutdownHook)
      yield (shutdownHook, notificationStreamFiber)
    } { (shutdownHook, notificationStreamFiber) =>
      (for
        _ <- ZIO.logInfo("Halting streams")
        _ <- shutdownHook.succeed(())
        _ <- shutdownHook.await
        _ <- ZIO.logInfo("Shutting down streams... this may take a few seconds")
        _ <- notificationStreamFiber.join `race` ZIO.sleep(5.seconds)
        _ <- ZIO.logInfo("Streams shut down")
      yield ()).ignore
    }

  private def notificationsStream(shutdownHook: Promise[Throwable, Unit]) =
    for
      fork <- NotificationsConsumer.run(shutdownHook).fork
      _    <- NotificationsStaleEventsCollector.run().fork
    yield fork

  private val liveEnv =
    val appConfig = Configs.appConfig

//    val logging: ULayer[Logging] = Runtime.removeDefaultLoggers >>> SLF4J.slf4j(
//      mdcAnnotations = List(KensuLogAnnotation.CorrelationId),
//      logFormat      = (_, msg) => msg
//    ) >>> Logging.modifyLogger(_.derive(KensuLogAnnotation.InitialLogContext))

    // https://zio.dev/guides/migrate/zio-2.x-migration-guide/#custom-runtime-for-mixed-applications
    // TODO Seems logger is built in the runtime, we replace it, when needed to reference use ZLogger[String, Any]. Or nothing?
    val logging = Runtime.removeDefaultLoggers >>> SLF4J.slf4j

    val redisClient = appConfig.narrow(_.redis) >>> RedisClient.live

    val notificationsStream =
      val streamInstance = appConfig.narrow(_.redisStreams.consumers).map(hasConsumers =>
        ZEnvironment(StreamInstance.Notifications(hasConsumers.get.notifications.streamName))
      )
      (streamInstance ++ redisClient) >>> NotificationsRedisStream.redisson

    logging ++ appConfig.narrow(_.redisStreams.consumers.notifications) ++ notificationsStream
