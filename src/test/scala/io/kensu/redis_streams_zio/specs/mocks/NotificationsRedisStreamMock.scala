package io.kensu.redis_streams_zio.specs.mocks

import io.kensu.redis_streams_zio.config.{StreamConsumerName, StreamGroupName, StreamKey}
import io.kensu.redis_streams_zio.redis.streams.NotificationsRedisStream
import io.kensu.redis_streams_zio.redis.streams.{
  CreateGroupStrategy,
  ListGroupStrategy,
  ReadGroupResult,
  RedisStream,
  StreamInstance
}
import org.redisson.api.{PendingEntry, StreamGroup, StreamMessageId}
import zio.test.mock.*
import zio.{Chunk, NonEmptyChunk, Task, UIO, URLayer, ZLayer}
import zio.Duration

object NotificationsRedisStreamMock extends Mock[RedisStream[StreamInstance.Notifications]]:

  object StreamInstance extends Effect[Unit, Nothing, StreamInstance]

  object ListGroups extends Effect[Unit, Throwable, Chunk[StreamGroup]]

  object CreateGroup extends Effect[(StreamGroupName, CreateGroupStrategy), Throwable, Unit]

  object ReadGroup
    extends Effect[
      (StreamGroupName, StreamConsumerName, Int, Duration, ListGroupStrategy),
      Throwable,
      Chunk[ReadGroupResult]
    ]

  object Ack extends Effect[(StreamGroupName, NonEmptyChunk[StreamMessageId]), Throwable, Long]

  object FastClaim
    extends Effect[
      (StreamGroupName, StreamConsumerName, Duration, NonEmptyChunk[StreamMessageId]),
      Throwable,
      Chunk[StreamMessageId]
    ]

  object ListPending extends Effect[(StreamGroupName, Int), Throwable, Chunk[PendingEntry]]

  object Add extends Effect[(StreamKey, Chunk[Byte]), Throwable, StreamMessageId]

  override val compose: URLayer[
    Proxy,
    RedisStream[io.kensu.redis_streams_zio.redis.streams.StreamInstance.Notifications]
  ] =
    ZLayer.fromServiceM { proxy =>
      withRuntime.as {
        new RedisStream[io.kensu.redis_streams_zio.redis.streams.StreamInstance.Notifications] {

          override val streamInstance: UIO[StreamInstance] =
            proxy(StreamInstance)

          override def listGroups: Task[Chunk[StreamGroup]] =
            proxy(ListGroups)

          override def createGroup(groupName: StreamGroupName, strategy: CreateGroupStrategy): Task[Unit] =
            proxy(CreateGroup, groupName, strategy)

          override def readGroup(
            groupName: StreamGroupName,
            consumerName: StreamConsumerName,
            count: Int,
            timeout: Duration,
            strategy: ListGroupStrategy
          ): Task[Chunk[ReadGroupResult]] =
            proxy(ReadGroup, groupName, consumerName, count, timeout, strategy)

          override def ack(groupName: StreamGroupName, ids: NonEmptyChunk[StreamMessageId]): Task[Long] =
            proxy(Ack, groupName, ids)

          override def fastClaim(
            groupName: StreamGroupName,
            consumerName: StreamConsumerName,
            maxIdleTime: Duration,
            ids: NonEmptyChunk[StreamMessageId]
          ): Task[Chunk[StreamMessageId]] =
            proxy(FastClaim, groupName, consumerName, maxIdleTime, ids)

          override def listPending(groupName: StreamGroupName, count: Int): Task[Chunk[PendingEntry]] =
            proxy(ListPending, groupName, count)

          override def add(key: StreamKey, payload: Chunk[Byte]): Task[StreamMessageId] =
            proxy(Add, key, payload)
        }
      }
    }
