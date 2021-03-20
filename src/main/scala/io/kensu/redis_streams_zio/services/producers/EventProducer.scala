package io.kensu.redis_streams_zio.services.producers

import io.kensu.redis_streams_zio.config.StreamKey
import io.kensu.redis_streams_zio.redis.streams.{RedisStream, StreamInstance}
import io.kensu.redis_streams_zio.redis.streams.RedisStream.RedisStream
import zio._
import zio.Schedule.Decision
import zio.clock.Clock
import zio.duration._
import zio.logging._
import zio.macros.accessible

trait EventSerializable[E] {
  def serialize(e: E): Array[Byte]
}

object EventSerializable {

  def apply[E](implicit bc: EventSerializable[E]): EventSerializable[E] = bc

  implicit val StringEventSerializable: EventSerializable[String] =
    (e: String) => e.getBytes("UTF-8")
}

final case class PublishedEventId(value: String) extends AnyVal {
  override def toString: String = value
}

@accessible()
object EventProducer {

  type EventProducer[S <: StreamInstance] = Has[Service[S]]

  trait Service[S <: StreamInstance] {

    /**
     * Publishes a message.
     * @param streamKey key name under which to store the event
     * @param event anything that satisfies EventPublishable type class
     * @tparam E EventSerializable type class
     * @return a computed message id
     */
    def publish[E: EventSerializable: Tag](
      streamKey: StreamKey,
      event: E
    ): Task[PublishedEventId]
  }

  def live[S <: StreamInstance: Tag]: ZLayer[RedisStream[S] with Clock with Logging, Throwable, EventProducer[S]] =
    ZLayer.fromFunction { env =>
      new Service[S] {

        override def publish[E: EventSerializable: Tag](
          key: StreamKey,
          event: E
        ): Task[PublishedEventId] = RedisStream.streamInstance[S].map(_.name).flatMap { streamName =>
          val send =
            log.debug(s"Producing event to $streamName -> $key") *>
              RedisStream
                .add[S](key, Chunk.fromArray(EventSerializable[E].serialize(event)))
                .map(redisId => PublishedEventId(redisId.toString))
                .tapBoth(
                  ex => log.throwable(s"Failed to produce an event to $streamName -> $key", ex),
                  msgId => log.info(s"Successfully produced an event to $streamName -> $key. StreamMessageId: $msgId")
                )

          val retryPolicy =
            Schedule.exponential(3.seconds) *> Schedule
              .recurs(3)
              .onDecision({
                case Decision.Done(_)                 => log.warn(s"An event is done retrying publishing")
                case Decision.Continue(attempt, _, _) => log.info(s"An event will be retried #${attempt + 1}")
              })

          send.retry(retryPolicy)
        }.provide(env)
      }
    }
}
