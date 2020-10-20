package io.kensu.redis_streams_zio.services.producers

import io.kensu.redis_streams_zio.config.{ StreamKey, StreamName }
import io.kensu.redis_streams_zio.redis.RedisClient
import io.kensu.redis_streams_zio.redis.RedisClient.RedisClient
import org.redisson.api.RedissonClient
import zio.Schedule.Decision
import zio._
import zio.clock.Clock
import zio.duration._
import zio.logging.{ Logger, Logging }
import zio.macros.accessible

trait EventSerializable[E] {
  def serialize(e: E): Array[Byte]
}

object EventSerializable {

  def apply[E](implicit bc: EventSerializable[E]): EventSerializable[E] = bc

  implicit val StringEventSerializable: EventSerializable[String] =
    new EventSerializable[String] {
      override def serialize(e: String): Array[Byte] = e.getBytes("UTF-8")
    }
}

final case class PublishedEventId(value: String) extends AnyVal {
  override def toString: String = value
}

@accessible()
object EventProducer {

  type EventProducer = Has[Service]

  trait Service {

    /**
      * Publishes a message.
      * @param streamName stream name under which to store the event
      * @param streamKey key name under which to store the event
      * @param event anything that satisfies EventPublishable type class
      * @tparam E EventSerializable type class
      * @return a computed message id
      */
    def publish[E: EventSerializable: Tag](
      streamName: StreamName,
      streamKey: StreamKey,
      event: E
    ): Task[PublishedEventId]
  }

  val redis: ZLayer[RedisClient with Clock with Logging, Throwable, EventProducer] =
    ZLayer.fromServices[RedissonClient, Clock.Service, Logger[String], Service] { (redisClient, clock, logger) =>
      new Service {
        private val env = ZLayer.succeed(clock) ++ ZLayer.succeed(redisClient)

        override def publish[E: EventSerializable: Tag](
          streamName: StreamName,
          key: StreamKey,
          event: E
        ): Task[PublishedEventId] = {
          val send =
            logger.info(s"Producing event to $streamName -> $key") *>
              RedisClient
                .getStream[Array[Byte], Array[Byte]](streamName)
                .flatMap(rstream =>
                  Task.fromCompletionStage(
                    rstream.addAsync(key.value.getBytes("UTF-8"), EventSerializable[E].serialize(event))
                  )
                )
                .map(redisId => PublishedEventId(redisId.toString))
                .tapBoth(
                  ex => logger.throwable(s"Failed to produce an event to $streamName -> $key", ex),
                  msgId => logger.info(s"Successfully produced an event to $streamName -> $key. RedisId: $msgId")
                )

          val retryPolicy =
            Schedule.exponential(3.seconds) *> Schedule
              .recurs(3)
              .onDecision({
                case Decision.Done(_)                 => logger.info(s"An event is done retrying publishing")
                case Decision.Continue(attempt, _, _) => logger.info(s"An event will be retried #${attempt + 1}")
              })

          send.retry(retryPolicy).provideLayer(env)
        }
      }
    }
}
