package io.kensu.redis_streams_zio.redis.streams

import java.util.concurrent.TimeUnit

import io.kensu.redis_streams_zio.common.Accessible
import io.kensu.redis_streams_zio.config.{StreamConsumerName, StreamGroupName, StreamKey}
import io.kensu.redis_streams_zio.redis.RedisClient.RedisClient
import org.redisson.api.{PendingEntry, RedissonClient, StreamGroup, StreamMessageId}
import org.redisson.api.stream.StreamAddArgs
import org.redisson.client.RedisException
import zio._
import zio.duration.Duration

import scala.jdk.CollectionConverters._

sealed trait CreateGroupStrategy

object CreateGroupStrategy {
  case object Newest extends CreateGroupStrategy
  case object All extends CreateGroupStrategy
}

sealed trait ListGroupStrategy

object ListGroupStrategy {
  case object New extends ListGroupStrategy
  case object Pending extends ListGroupStrategy
}

final case class ReadGroupData(
  key: StreamKey,
  payload: Chunk[Byte]
)

final case class ReadGroupResult(
  messageId: StreamMessageId,
  data: Chunk[ReadGroupData]
)

trait RedisStream[S <: StreamInstance] {

  val streamInstance: UIO[StreamInstance]

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

final case class RedissonRedisStream[S <: StreamInstance: Tag](
  instance: S,
  redisson: RedissonClient
) extends RedisStream[S] {

  private val redissonStream = redisson.getStream[Array[Byte], Array[Byte]](instance.name.value)

  override val streamInstance: UIO[StreamInstance] = UIO(instance)

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
      ).flatMap { messages =>
        ZIO.effect {
          if messages == null then Chunk.empty
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
    Task.fromCompletionStage(redissonStream.addAsync(StreamAddArgs.entry(key.value.getBytes("UTF-8"), payload.toArray)))
}

object RedisStream {

  def streamInstance[S <: StreamInstance: Tag]: RIO[Has[RedisStream[S]], StreamInstance] =
    ZIO.serviceWith[RedisStream[S]](_.streamInstance)

  def listGroups[S <: StreamInstance: Tag]: RIO[Has[RedisStream[S]], Chunk[StreamGroup]] =
    ZIO.serviceWith[RedisStream[S]](_.listGroups)

  def createGroup[S <: StreamInstance: Tag](
    groupName: StreamGroupName,
    strategy: CreateGroupStrategy
  ): RIO[Has[RedisStream[S]], Unit] =
    ZIO.serviceWith[RedisStream[S]](_.createGroup(groupName, strategy))

  def readGroup[S <: StreamInstance: Tag](
    groupName: StreamGroupName,
    consumerName: StreamConsumerName,
    count: Int,
    timeout: Duration,
    strategy: ListGroupStrategy
  ): RIO[Has[RedisStream[S]], Chunk[ReadGroupResult]] =
    ZIO.serviceWith[RedisStream[S]](_.readGroup(groupName, consumerName, count, timeout, strategy))

  def ack[S <: StreamInstance: Tag](
    groupName: StreamGroupName,
    ids: NonEmptyChunk[StreamMessageId]
  ): RIO[Has[RedisStream[S]], Long] =
    ZIO.serviceWith[RedisStream[S]](_.ack(groupName, ids))

  def fastClaim[S <: StreamInstance: Tag](
    groupName: StreamGroupName,
    consumerName: StreamConsumerName,
    maxIdleTime: Duration,
    ids: NonEmptyChunk[StreamMessageId]
  ): RIO[Has[RedisStream[S]], Chunk[StreamMessageId]] =
    ZIO.serviceWith[RedisStream[S]](_.fastClaim(groupName, consumerName, maxIdleTime, ids))

  def listPending[S <: StreamInstance: Tag](
    groupName: StreamGroupName,
    count: Int
  ): RIO[Has[RedisStream[S]], Chunk[PendingEntry]] =
    ZIO.serviceWith[RedisStream[S]](_.listPending(groupName, count))

  def add[S <: StreamInstance: Tag](key: StreamKey, payload: Chunk[Byte]): RIO[Has[RedisStream[S]], StreamMessageId] =
    ZIO.serviceWith[RedisStream[S]](_.add(key, payload))

}

/** An additional, stream instance predefined definition for easier API usage and future refactoring. */
object NotificationsRedisStream extends Accessible[RedisStream[StreamInstance.Notifications]] {

  type NotificationsRedisStream = Has[RedisStream[StreamInstance.Notifications]]

  val redisson: URLayer[Has[StreamInstance.Notifications] & RedisClient, NotificationsRedisStream] =
    (RedissonRedisStream[StreamInstance.Notifications](_, _)).toLayer
}
