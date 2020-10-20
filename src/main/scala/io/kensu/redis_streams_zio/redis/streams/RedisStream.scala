package io.kensu.redis_streams_zio.redis.streams

import java.util.concurrent.TimeUnit

import io.kensu.redis_streams_zio.config.{ StreamConsumerName, StreamGroupName, StreamKey }
import io.kensu.redis_streams_zio.redis.RedisClient.RedisClient
import org.redisson.api.{ PendingEntry, RedissonClient, StreamGroup, StreamMessageId }
import org.redisson.client.RedisException
import zio.duration.Duration
import zio.macros.accessible
import zio._

final case class ReadGroupData(
  key: StreamKey,
  payload: Chunk[Byte]
)
final case class ReadGroupResult(
  messageId: StreamMessageId,
  data: Chunk[ReadGroupData]
)

@accessible()
object RedisStream {

  type RedisStream[S <: StreamInstance] = Has[Service[S]]

  trait Service[S <: StreamInstance] {
    def listGroups: Task[Chunk[StreamGroup]]
    def createGroup(groupName: StreamGroupName, strategy: CreateGroupStrategy): Task[Unit]
    def readGroup(
      groupName: StreamGroupName,
      consumerName: StreamConsumerName,
      count: Int,
      timeout: Duration,
      strategy: ListGroupStrategy
    ): Task[Chunk[ReadGroupResult]]
    def ack(groupName: StreamGroupName, ids: NonEmptyChunk[StreamMessageId]): Task[Long]
    def fastClaim(
      groupName: StreamGroupName,
      consumerName: StreamConsumerName,
      maxIdleTime: Duration,
      ids: NonEmptyChunk[StreamMessageId]
    ): Task[Chunk[StreamMessageId]]
    def listPending(groupName: StreamGroupName, count: Int): Task[Chunk[PendingEntry]]
    def add(key: StreamKey, payload: Chunk[Byte]): Task[StreamMessageId]
  }

  sealed trait CreateGroupStrategy
  object CreateGroupStrategy {
    final case object Newest extends CreateGroupStrategy
    final case object All extends CreateGroupStrategy
  }

  sealed trait ListGroupStrategy
  object ListGroupStrategy {
    final case object New extends ListGroupStrategy
    final case object Pending extends ListGroupStrategy
  }

  def buildFor[S <: StreamInstance: Tag](instance: S): ZLayer[RedisClient, Nothing, RedisStream[S]] =
    ZIO
      .service[RedissonClient]
      .map(redisson =>
        new Service[S] {
          import scala.jdk.CollectionConverters._

          private val redissonStream = redisson.getStream[Array[Byte], Array[Byte]](instance.name.value)

          override val listGroups: Task[Chunk[StreamGroup]] =
            Task
              .fromCompletionStage(redissonStream.listGroupsAsync())
              .map(l => Chunk.fromIterable(l.asScala))
              .catchSome {
                case e: RedisException if e.getMessage.contains("no such key") => ZIO.succeed(Chunk.empty)
              }

          override def createGroup(groupName: StreamGroupName, strategy: CreateGroupStrategy): Task[Unit] = {
            val redisStrategy = strategy match {
              case CreateGroupStrategy.Newest => StreamMessageId.NEWEST
              case CreateGroupStrategy.All    => StreamMessageId.ALL
            }
            Task.fromCompletionStage(redissonStream.createGroupAsync(groupName.value, redisStrategy)).unit
          }

          override def readGroup(
            groupName: StreamGroupName,
            consumerName: StreamConsumerName,
            count: Int,
            timeout: Duration,
            strategy: ListGroupStrategy
          ): Task[Chunk[ReadGroupResult]] = {
            val redisStrategy = strategy match {
              case ListGroupStrategy.New     => StreamMessageId.NEVER_DELIVERED
              case ListGroupStrategy.Pending => StreamMessageId.ALL
            }
            Task
              .fromCompletionStage(
                redissonStream
                  .readGroupAsync(
                    groupName.value,
                    consumerName.value,
                    count,
                    timeout.toMillis,
                    TimeUnit.MILLISECONDS,
                    redisStrategy
                  )
              )
              .flatMap { messages =>
                ZIO.effect {
                  if (messages == null) Chunk.empty
                  else {
                    Chunk.fromIterable(messages.asScala).map {
                      case (msgId, m) =>
                        val m0 = Chunk.fromIterable(m.asScala).map {
                          case (key, value) =>
                            ReadGroupData(StreamKey(new String(key, "UTF-8")), Chunk.fromArray(value))
                        }
                        ReadGroupResult(msgId, m0)
                    }
                  }
                }
              }
          }

          override def ack(groupName: StreamGroupName, ids: NonEmptyChunk[StreamMessageId]): Task[Long] =
            Task.fromCompletionStage(redissonStream.ackAsync(groupName.value, ids: _*)).map(_.longValue())

          override def fastClaim(
            groupName: StreamGroupName,
            consumerName: StreamConsumerName,
            maxIdleTime: Duration,
            ids: NonEmptyChunk[StreamMessageId]
          ): Task[Chunk[StreamMessageId]] =
            Task
              .fromCompletionStage(
                redissonStream
                  .fastClaimAsync(
                    groupName.value,
                    consumerName.value,
                    maxIdleTime.toMillis,
                    TimeUnit.MILLISECONDS,
                    ids: _*
                  )
              )
              .map(l => Chunk.fromIterable(l.asScala))

          override def listPending(groupName: StreamGroupName, count: Int): Task[Chunk[PendingEntry]] =
            Task
              .fromCompletionStage(
                redissonStream.listPendingAsync(groupName.value, StreamMessageId.MIN, StreamMessageId.MAX, count)
              )
              .map(l => Chunk.fromIterable(l.asScala))

          override def add(key: StreamKey, payload: Chunk[Byte]): Task[StreamMessageId] =
            Task.fromCompletionStage(redissonStream.addAsync(key.value.getBytes("UTF-8"), payload.toArray))
        }
      )
      .toLayer
}
