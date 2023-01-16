package io.kensu.redis_streams_zio.redis.streams

import java.util.concurrent.TimeUnit

import io.kensu.redis_streams_zio.config.{StreamConsumerName, StreamGroupName, StreamKey}
import org.redisson.api.{PendingEntry, RedissonClient, StreamGroup, StreamMessageId}
import org.redisson.api.stream.StreamAddArgs
import org.redisson.client.RedisException
import zio.*
import zio.Duration

import scala.jdk.CollectionConverters.*

sealed trait CreateGroupStrategy

object CreateGroupStrategy:
  case object Newest extends CreateGroupStrategy
  case object All extends CreateGroupStrategy

sealed trait ListGroupStrategy

object ListGroupStrategy:
  case object New extends ListGroupStrategy
  case object Pending extends ListGroupStrategy

final case class ReadGroupData(
  key: StreamKey,
  payload: Chunk[Byte]
)

final case class ReadGroupResult(
  messageId: StreamMessageId,
  data: Chunk[ReadGroupData]
)

trait RedisStream[S <: StreamInstance]:

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

final case class RedissonRedisStream[S <: StreamInstance](
  instance: S,
  redisson: RedissonClient
) extends RedisStream[S]:

  private val redissonStream = redisson.getStream[Array[Byte], Array[Byte]](instance.name.value)

  override val streamInstance: UIO[StreamInstance] = ZIO.succeed(instance)

  override val listGroups: Task[Chunk[StreamGroup]] =
    ZIO
      .fromCompletionStage(redissonStream.listGroupsAsync())
      .map(l => Chunk.fromIterable(l.asScala))
      .catchSome {
        case e: RedisException if e.getMessage.contains("no such key") => ZIO.succeed(Chunk.empty)
      }

  override def createGroup(groupName: StreamGroupName, strategy: CreateGroupStrategy): Task[Unit] =
    val redisStrategy =
      strategy match
        case CreateGroupStrategy.Newest => StreamMessageId.NEWEST
        case CreateGroupStrategy.All    => StreamMessageId.ALL
    ZIO.fromCompletionStage(redissonStream.createGroupAsync(groupName.value, redisStrategy)).unit

  override def readGroup(
    groupName: StreamGroupName,
    consumerName: StreamConsumerName,
    count: Int,
    timeout: Duration,
    strategy: ListGroupStrategy
  ): Task[Chunk[ReadGroupResult]] =
    val redisStrategy =
      strategy match
        case ListGroupStrategy.New     => StreamMessageId.NEVER_DELIVERED
        case ListGroupStrategy.Pending => StreamMessageId.ALL
    ZIO
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
        ZIO.attempt {
          if messages == null then Chunk.empty
          else
            Chunk.fromIterable(messages.asScala).map { (msgId, m) =>
              val m0 = Chunk.fromIterable(m.asScala).map { (key, value) =>
                ReadGroupData(StreamKey(new String(key, "UTF-8")), Chunk.fromArray(value))
              }
              ReadGroupResult(msgId, m0)
            }
        }
      }

  override def ack(groupName: StreamGroupName, ids: NonEmptyChunk[StreamMessageId]): Task[Long] =
    ZIO.fromCompletionStage(redissonStream.ackAsync(groupName.value, ids*)).map(_.longValue())

  override def fastClaim(
    groupName: StreamGroupName,
    consumerName: StreamConsumerName,
    maxIdleTime: Duration,
    ids: NonEmptyChunk[StreamMessageId]
  ): Task[Chunk[StreamMessageId]] =
    ZIO
      .fromCompletionStage(
        redissonStream
          .fastClaimAsync(
            groupName.value,
            consumerName.value,
            maxIdleTime.toMillis,
            TimeUnit.MILLISECONDS,
            ids*
          )
      )
      .map(l => Chunk.fromIterable(l.asScala))

  override def listPending(groupName: StreamGroupName, count: Int): Task[Chunk[PendingEntry]] =
    ZIO
      .fromCompletionStage(
        redissonStream.listPendingAsync(groupName.value, StreamMessageId.MIN, StreamMessageId.MAX, count)
      )
      .map(l => Chunk.fromIterable(l.asScala))

  override def add(key: StreamKey, payload: Chunk[Byte]): Task[StreamMessageId] =
    ZIO.fromCompletionStage(redissonStream.addAsync(StreamAddArgs.entry(key.value.getBytes("UTF-8"), payload.toArray)))

object RedisStream:

  def streamInstance[S <: StreamInstance](using Tag[RedisStream[S]]): RIO[RedisStream[S], StreamInstance] =
    ZIO.serviceWithZIO[RedisStream[S]](_.streamInstance)

  def listGroups[S <: StreamInstance](using Tag[RedisStream[S]]): RIO[RedisStream[S], Chunk[StreamGroup]] =
    ZIO.serviceWithZIO[RedisStream[S]](_.listGroups)

  def createGroup[S <: StreamInstance](
    groupName: StreamGroupName,
    strategy: CreateGroupStrategy
  )(using Tag[RedisStream[S]]): RIO[RedisStream[S], Unit] =
    ZIO.serviceWithZIO[RedisStream[S]](_.createGroup(groupName, strategy))

  def readGroup[S <: StreamInstance](
    groupName: StreamGroupName,
    consumerName: StreamConsumerName,
    count: Int,
    timeout: Duration,
    strategy: ListGroupStrategy
  )(using Tag[RedisStream[S]]): RIO[RedisStream[S], Chunk[ReadGroupResult]] =
    ZIO.serviceWithZIO[RedisStream[S]](_.readGroup(groupName, consumerName, count, timeout, strategy))

  def ack[S <: StreamInstance](
    groupName: StreamGroupName,
    ids: NonEmptyChunk[StreamMessageId]
  )(using Tag[RedisStream[S]]): RIO[RedisStream[S], Long] =
    ZIO.serviceWithZIO[RedisStream[S]](_.ack(groupName, ids))

  def fastClaim[S <: StreamInstance](
    groupName: StreamGroupName,
    consumerName: StreamConsumerName,
    maxIdleTime: Duration,
    ids: NonEmptyChunk[StreamMessageId]
  )(using Tag[RedisStream[S]]): RIO[RedisStream[S], Chunk[StreamMessageId]] =
    ZIO.serviceWithZIO[RedisStream[S]](_.fastClaim(groupName, consumerName, maxIdleTime, ids))

  def listPending[S <: StreamInstance](
    groupName: StreamGroupName,
    count: Int
  )(using Tag[RedisStream[S]]): RIO[RedisStream[S], Chunk[PendingEntry]] =
    ZIO.serviceWithZIO[RedisStream[S]](_.listPending(groupName, count))

  def add[S <: StreamInstance](key: StreamKey, payload: Chunk[Byte])(
    using Tag[RedisStream[S]]
  ): RIO[RedisStream[S], StreamMessageId] =
    ZIO.serviceWithZIO[RedisStream[S]](_.add(key, payload))

/** An additional, stream instance predefined definition for easier API usage and future refactoring. */
object NotificationsRedisStream:

  val redisson: URLayer[
    StreamInstance.Notifications & RedissonClient,
    RedisStream[StreamInstance.Notifications]
  ] = ZLayer.fromFunction(RedissonRedisStream[StreamInstance.Notifications].apply)
