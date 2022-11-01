package io.kensu.redis_streams_zio.redis.streams.notifications

import io.kensu.redis_streams_zio.common.RetryableStreamError
import io.kensu.redis_streams_zio.config.NotificationsStreamConsumerConfig
import io.kensu.redis_streams_zio.redis.streams.*
import io.kensu.redis_streams_zio.redis.streams.dto.{Event, IncorrectEvent, NotificationAddedEvent}
import zio.*
import zio.config.getConfig
import zio.logging.*
import zio.logging.backend.SLF4J

object NotificationsConsumer:

  def run(shutdownHook: Promise[Throwable, Unit]): ZIO[
    NotificationsStreamConsumerConfig & RedisStream[StreamInstance.Notifications] & NotificationsStreamConsumerConfig,
    Throwable,
    Long
  ] =
    RedisConsumer.executeFor[
      NotificationsStreamConsumerConfig,
      StreamInstance.Notifications,
      NotificationsStreamConsumerConfig
    ](
      shutdownHook    = shutdownHook,
      eventsProcessor = _.mapZIO(eventParser).flattenChunks.mapZIOPar(4)(eventProcessor)
    ) @@ SLF4J.loggerName("zio.logging.example.UserOperation")

  private def eventParser(rawResult: ReadGroupResult) =
    getConfig[NotificationsStreamConsumerConfig].flatMap { config =>
      val msgId = rawResult.messageId
      val data  = rawResult.data
      ZIO
        .foreach(data) {
          case ReadGroupData(key, value) =>
            key match
              case config.addKey =>
                ZIO.logInfo(s"Parsing add event $msgId") *>
                  ZIO.attempt(NotificationAddedEvent(msgId, new String(value.toArray, "UTF-8")))
              case _             =>
                ZIO.logInfo(s"Received unsupported stream key $key for event $msgId") *>
                  ZIO.succeed(IncorrectEvent(msgId))
        }
        .catchAllCause(ex =>
          ZIO.logErrorCause(s"Failed to deserialize event $msgId", ex)
            .as(Chunk(IncorrectEvent(msgId)))
        )
    }

  private def eventProcessor(event: Event) =
    val id = event.streamMessageId
    ZIO.logDebug(s"Processing event $event") *>
      additionalWork(event)
        .as(id)
        .asSome
        .catchAll {
          case RetryableStreamError =>
            ZIO.logWarning(s"StreamMessageId $id was not processed successfully, scheduled for pending")
              .as(None)
          case t                    =>
            ZIO.logErrorCause(s"StreamMessageId $id was not processed successfully and can't be retried", Cause.die(t))
              .as(id)
              .asSome
        }

  private def additionalWork(event: Event) = event match
    case IncorrectEvent(msgId)                  => ZIO.attempt(s"Nothing to do for event $msgId")
    case NotificationAddedEvent(msgId, payload) =>
      ZIO.attempt(s"Effectfully processed add notification event $msgId with data $payload")
