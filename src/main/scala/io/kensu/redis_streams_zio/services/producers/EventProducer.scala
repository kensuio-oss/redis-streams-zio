package io.kensu.redis_streams_zio.services.producers

import io.kensu.redis_streams_zio.config.StreamKey
import io.kensu.redis_streams_zio.redis.streams.{RedisStream, StreamInstance}
import io.kensu.redis_streams_zio.redis.streams.NotificationsRedisStream
import zio.*
import zio.Schedule.Decision
import zio.clock.Clock
import zio.duration.*
import zio.logging.{Logger, Logging}

trait EventSerializable[E]:
  def serialize(e: E): Array[Byte]

object EventSerializable:

  def apply[E](implicit bc: EventSerializable[E]): EventSerializable[E] = bc

  implicit val StringEventSerializable: EventSerializable[String] =
    (e: String) => e.getBytes("UTF-8")

opaque type PublishedEventId = String

object PublishedEventId:
  def apply(value: String): PublishedEventId = value

trait EventProducer[S <: StreamInstance]:

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

final case class RedisEventProducer[S <: StreamInstance: Tag](
  stream: RedisStream[S],
  clock: Clock.Service,
  log: Logger[String]
) extends EventProducer[S]:

  private val env = Has(clock)

  override def publish[E: EventSerializable: Tag](key: StreamKey, event: E): Task[PublishedEventId] =
    stream.streamInstance.map(_.name).flatMap { streamName =>
      val send =
        log.debug(s"Producing event to $streamName -> $key") *>
          stream
            .add(key, Chunk.fromArray(EventSerializable[E].serialize(event)))
            .map(redisId => PublishedEventId(redisId.toString))
            .tapBoth(
              ex => log.throwable(s"Failed to produce an event to $streamName -> $key", ex),
              msgId => log.info(s"Successfully produced an event to $streamName -> $key. StreamMessageId: $msgId")
            )

      val retryPolicy =
        Schedule.exponential(3.seconds) *> Schedule
          .recurs(3)
          .onDecision {
            case Decision.Done(_)                 => log.warn(s"An event is done retrying publishing")
            case Decision.Continue(attempt, _, _) => log.info(s"An event will be retried #${attempt + 1}")
          }

      send.retry(retryPolicy).provide(env)
    }

/** An additional, stream instance predefined definition for easier API usage and future refactoring. */
object NotificationsEventProducer extends Accessible[EventProducer[StreamInstance.Notifications]]:

  val redis: URLayer[
    Has[RedisStream[StreamInstance.Notifications]] & Clock & Logging,
    Has[EventProducer[StreamInstance.Notifications]]
  ] =
    (RedisEventProducer[StreamInstance.Notifications](_, _, _)).toLayer
