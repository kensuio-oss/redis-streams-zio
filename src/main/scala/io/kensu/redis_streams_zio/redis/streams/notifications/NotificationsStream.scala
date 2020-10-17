package io.kensu.redis_streams_zio.redis.streams.notifications

import io.kensu.redis_streams_zio.common.RetryableStreamError
import io.kensu.redis_streams_zio.redis.streams.RedisStream.RedisStream
import io.kensu.redis_streams_zio.redis.streams.dto.{ Event, IncorrectEvent, NotificationAddedEvent }
import io.kensu.redis_streams_zio.redis.streams.{ ReadGroupData, ReadGroupResult, RedisZStream, StreamInstance }
import zio._
import zio.clock.Clock
import zio.logging.LogAnnotation.Name
import zio.logging._

object NotificationsStream {

  private type Input = Clock
    with Logging
    with Has[NotificationsStreamConsumerConfig]
    with RedisStream[StreamInstance.Notifications]

  def run(
    shutdownHook: Promise[Throwable, Unit]
  ): ZIO[Input, Throwable, Long] =
    log.locally(Name(List(getClass.getName))) {
      RedisZStream.executeFor[
        Input,
        Event,
        StreamInstance.Notifications,
        NotificationsStreamConsumerConfig
      ](
        shutdownHook    = shutdownHook,
        eventsProcessor = _.mapM(eventParser).flattenChunks.mapMPar(4)(eventProcessor)
      )
    }

  private def eventParser(rawResult: ReadGroupResult) =
    ZIO.service[NotificationsStreamConsumerConfig].flatMap { config =>
      val msgId = rawResult.messageId
      val data  = rawResult.data
      ZIO
        .foreach(data) {
          case ReadGroupData(key, value) =>
            key match {
              case config.addKey =>
                log.info(s"Parsing add event $msgId") *>
                  ZIO.effect(NotificationAddedEvent(msgId, new String(value.toArray, "UTF-8")))
              case _ =>
                log.info(s"Received unsupported stream key $key for event $msgId") *>
                  ZIO.effectTotal(IncorrectEvent(msgId))
            }
        }
        .catchAllCause(ex =>
          log
            .error(s"Failed to deserialize event $msgId", ex)
            .as(Chunk(IncorrectEvent(msgId)))
        )
    }

  private def eventProcessor(event: Event) = {
    val id = event.streamMessageId
    log.debug(s"Processing event $event") *>
      additionalWork(event)
        .as(id)
        .asSome
        .catchAll({
          case RetryableStreamError =>
            log
              .warn(s"StreamMessageId $id was not processed successfully, scheduled for pending")
              .as(None)
          case t =>
            log
              .throwable(s"StreamMessageId $id was not processed successfully and can't be retried", t)
              .as(id)
              .asSome
        })
  }

  private def additionalWork(event: Event): Task[String] = event match {
    case IncorrectEvent(msgId) => Task(s"Nothing to do for event $msgId")
    case NotificationAddedEvent(msgId, payload) =>
      Task.effect(s"Effectfully processed add notification event $msgId with data $payload")
  }
}
