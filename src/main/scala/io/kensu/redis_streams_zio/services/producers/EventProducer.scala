package io.kensu.redis_streams_zio.services.producers

import io.kensu.redis_streams_zio.config.StreamKey
import io.kensu.redis_streams_zio.redis.streams.{RedisStream, StreamInstance}
import io.kensu.redis_streams_zio.redis.streams.NotificationsRedisStream
import zio.*
import zio.Schedule.Decision
import zio.Clock

trait EventSerializable[E]:
  def serialize(e: E): Array[Byte]

object EventSerializable:

  def apply[E](using es: EventSerializable[E]): EventSerializable[E] = es

  given EventSerializable[String] =
    (e: String) => e.getBytes("UTF-8")

opaque type PublishedEventId = String

object PublishedEventId:
  def apply(value: String): PublishedEventId = value

trait EventProducer[S <: StreamInstance]:

  //TODO replace `Tag` with `EnvironmentTag`?
  //  https://github.com/zio/zio-mock/blob/master/examples/shared/src/main/scala-2/zio/mock/examples/MockableMacroExample.scala

  /**
   * Publishes a message.
   * @param streamKey
   *   key name under which to store the event
   * @param event
   *   anything that satisfies EventPublishable type class
   * @tparam E
   *   EventSerializable type class
   * @return
   *   a computed message id
   */
  def publish[E: EventSerializable: Tag](
    streamKey: StreamKey,
    event: E
  ): Task[PublishedEventId]

final case class RedisEventProducer[S <: StreamInstance: Tag](stream: RedisStream[S]) extends EventProducer[S]:

  override def publish[E: EventSerializable: Tag](key: StreamKey, event: E): Task[PublishedEventId] =
    stream.streamInstance.map(_.name).flatMap { streamName =>
      val send =
        ZIO.logDebug(s"Producing event to $streamName -> $key") *>
          stream
            .add(key, Chunk.fromArray(EventSerializable[E].serialize(event)))
            .map(redisId => PublishedEventId(redisId.toString))
            .tapBoth(
              ex => ZIO.logErrorCause(s"Failed to produce an event to $streamName -> $key", Cause.fail(ex)),
              msgId => ZIO.logInfo(s"Successfully produced an event to $streamName -> $key. StreamMessageId: $msgId")
            )

      val retryPolicy =
        Schedule.exponential(3.seconds) *> Schedule
          .recurs(3)
          .onDecision { (attempt, _, decision) => // TODO (attempt???, ???, decision)
            decision match
              case Decision.Done        => ZIO.logWarning(s"An event is done retrying publishing")
              case Decision.Continue(_) => ZIO.logInfo(s"An event will be retried #${attempt + 1}")
          }

      send.retry(retryPolicy)
    }

/** An additional, stream instance predefined definition for easier API usage and future refactoring. */
object NotificationsEventProducer:

  val redis: URLayer[
    RedisStream[StreamInstance.Notifications],
    EventProducer[StreamInstance.Notifications]
  ] =
    ZLayer {
      for {
        stream <- ZIO.service[RedisStream[StreamInstance.Notifications]]
      } yield RedisEventProducer[StreamInstance.Notifications](stream)
    }
