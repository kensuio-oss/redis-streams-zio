package io.kensu.redis_streams_zio.redis

import io.kensu.redis_streams_zio.config._
import io.kensu.redis_streams_zio.redis.PropertyGenerators.{ redisData, _ }
import io.kensu.redis_streams_zio.redis.streams.RedisStream.{ CreateGroupStrategy, ListGroupStrategy, RedisStream }
import io.kensu.redis_streams_zio.redis.streams.RedisZStream.StreamInput
import io.kensu.redis_streams_zio.redis.streams.{ ReadGroupResult, RedisZStream, StreamInstance }
import io.kensu.redis_streams_zio.specs.mocks.RedisStreamMock
import org.redisson.api.{ StreamGroup, StreamMessageId }
import zio.Schedule.Decision
import zio._
import zio.clock.Clock
import zio.duration.{ durationInt, Duration }
import zio.logging.Logging
import zio.test.Assertion._
import zio.test._
import zio.test.environment.{ TestClock, TestEnvironment }
import zio.test.mock.Expectation._

object RedisZStreamSpec extends DefaultRunnableSpec {

  import TestData._

  override val spec: ZSpec[TestEnvironment, Failure] = {
    import zio.duration._
    suite("RedisZStream.executeFor")(
      testM("reuse consumer group if the requested one exists") {
        checkAllM(promise) {
          shutdownHook =>
            val redisStreamMock =
              RedisStreamMock.ListGroups(value(Chunk(new StreamGroup(config.groupName.value, 0, 0, null)))) ++
                RedisStreamMock.ReadGroup(equalTo(pendingReadGroupCorrectArgs), value(Chunk.empty))

            RedisZStream
              .executeFor[Any, StreamInstance.Notifications.type, StreamConsumerConfig](
                shutdownHook    = shutdownHook,
                eventsProcessor = _.mapM(_ => ZIO.none),
                repeatStrategy  = Schedule.recurs(0).unit
              )
              .map(assert(_)(equalTo(0L)))
              .provideCustomLayer(testEnv(redisStreamMock))
        }
      },
      testM("create consumer group if there is no one available") {
        checkAllM(promise) {
          shutdownHook =>
            val redisStreamMock =
              RedisStreamMock.ListGroups(value(Chunk.empty)) ++
                RedisStreamMock.CreateGroup(equalTo((config.groupName, CreateGroupStrategy.Newest)), unit) ++
                RedisStreamMock.ReadGroup(equalTo(pendingReadGroupCorrectArgs), value(Chunk.empty))

            RedisZStream
              .executeFor[Any, StreamInstance.Notifications.type, StreamConsumerConfig](
                shutdownHook    = shutdownHook,
                eventsProcessor = _.mapM(_ => ZIO.none),
                repeatStrategy  = Schedule.recurs(0).unit
              )
              .map(assert(_)(equalTo(0L)))
              .provideCustomLayer(testEnv(redisStreamMock))
        }
      },
      testM("create consumer group if there is no requested one available") {
        checkAllM(promise) {
          shutdownHook =>
            val redisStreamMock = RedisStreamMock.ListGroups(value(Chunk(new StreamGroup("no-way", 0, 0, null)))) ++
              RedisStreamMock.CreateGroup(equalTo((config.groupName, CreateGroupStrategy.Newest)), unit) ++
              RedisStreamMock.ReadGroup(equalTo(pendingReadGroupCorrectArgs), value(Chunk.empty))

            RedisZStream
              .executeFor[Any, StreamInstance.Notifications.type, StreamConsumerConfig](
                shutdownHook    = shutdownHook,
                eventsProcessor = _.mapM(_ => ZIO.none),
                repeatStrategy  = Schedule.recurs(0).unit
              )
              .map(assert(_)(equalTo(0L)))
              .provideCustomLayer(testEnv(redisStreamMock))
        }
      },
      testM("get PENDING messages initially") {
        checkAllM(promise) {
          shutdownHook =>
            val redisStreamMock =
              RedisStreamMock.ListGroups(value(Chunk(new StreamGroup(config.groupName.value, 0, 0, null)))) ++
                RedisStreamMock.ReadGroup(equalTo(pendingReadGroupCorrectArgs), value(Chunk.empty))

            RedisZStream
              .executeFor[Any, StreamInstance.Notifications.type, StreamConsumerConfig](
                shutdownHook    = shutdownHook,
                eventsProcessor = successfulEventProcessor(_, Chunk.empty),
                repeatStrategy  = Schedule.recurs(0).unit
              )
              .map(assert(_)(equalTo(0L)))
              .provideCustomLayer(testEnv(redisStreamMock))
        }
      },
      testM("get PENDING messages initially, keep asking till asked for all and then ask for NEW messages") {
        checkAllM(promise, redisData(streamKey), redisData(streamKey)) {
          (shutdownHook, redisData1, redisData2) =>
            val redisStreamMock =
              RedisStreamMock.ListGroups(value(Chunk(new StreamGroup(config.groupName.value, 0, 0, null)))) ++
                RedisStreamMock.ReadGroup(equalTo(pendingReadGroupCorrectArgs), value(Chunk(redisData1))) ++
                RedisStreamMock.Ack(equalTo((config.groupName, NonEmptyChunk(redisData1.messageId))), value(1)) ++
                RedisStreamMock.ReadGroup(equalTo(pendingReadGroupCorrectArgs), value(Chunk(redisData2))) ++
                RedisStreamMock.Ack(equalTo((config.groupName, NonEmptyChunk(redisData2.messageId))), value(1)) ++
                RedisStreamMock.ReadGroup(equalTo(pendingReadGroupCorrectArgs), value(Chunk.empty)) ++
                RedisStreamMock.ReadGroup(equalTo(newReadGroupCorrectArgs), value(Chunk.empty))

            RedisZStream
              .executeFor[Any, StreamInstance.Notifications.type, StreamConsumerConfig](
                shutdownHook    = shutdownHook,
                eventsProcessor = successfulEventProcessor(_, Chunk(redisData1, redisData2)),
                repeatStrategy  = Schedule.recurs(3).unit
              )
              .map(assert(_)(equalTo(2L)))
              .provideCustomLayer(testEnv(redisStreamMock))
        }
      },
      testM("do not process the same PENDING messages in case they cannot be acknowledged") {
        checkAllM(promise, redisData(streamKey), redisData(streamKey)) {
          (shutdownHook, redisData1, redisData2) =>
            val redisStreamMock =
              RedisStreamMock.ListGroups(value(Chunk(new StreamGroup(config.groupName.value, 0, 0, null)))) ++
                RedisStreamMock.ReadGroup(equalTo(pendingReadGroupCorrectArgs), value(Chunk(redisData1, redisData2))) ++
                RedisStreamMock.ReadGroup(equalTo(pendingReadGroupCorrectArgs), value(Chunk(redisData1, redisData2)))

            RedisZStream
              .executeFor[Any, StreamInstance.Notifications.type, StreamConsumerConfig](
                shutdownHook    = shutdownHook,
                eventsProcessor = _.mapM(_ => ZIO.none),
                repeatStrategy  = Schedule.once
              )
              .map(assert(_)(equalTo(0L)))
              .provideCustomLayer(testEnv(redisStreamMock))
        }
      },
      testM("keep getting NEW messages") {
        checkAllM(promise, redisData(streamKey), redisData(streamKey)) {
          (shutdownHook, redisData1, redisData2) =>
            val redisStreamMock =
              RedisStreamMock.ListGroups(value(Chunk(new StreamGroup(config.groupName.value, 0, 0, null)))) ++
                RedisStreamMock.ReadGroup(equalTo(pendingReadGroupCorrectArgs), value(Chunk.empty)) ++
                RedisStreamMock.ReadGroup(equalTo(newReadGroupCorrectArgs), value(Chunk(redisData1))) ++
                RedisStreamMock.Ack(equalTo((config.groupName, NonEmptyChunk(redisData1.messageId))), value(1)) ++
                RedisStreamMock.ReadGroup(equalTo(newReadGroupCorrectArgs), value(Chunk(redisData2))) ++
                RedisStreamMock.Ack(equalTo((config.groupName, NonEmptyChunk(redisData2.messageId))), value(1)) ++
                RedisStreamMock.ReadGroup(equalTo(newReadGroupCorrectArgs), value(Chunk.empty))

            RedisZStream
              .executeFor[Any, StreamInstance.Notifications.type, StreamConsumerConfig](
                shutdownHook    = shutdownHook,
                eventsProcessor = successfulEventProcessor(_, Chunk(redisData1, redisData2)),
                repeatStrategy  = Schedule.recurs(3).unit
              )
              .map(assert(_)(equalTo(2L)))
              .provideCustomLayer(testEnv(redisStreamMock))
        }
      },
      testM("keep getting NEW messages even if some are not acknowledged") {
        checkAllM(promise, uniqueRedisData(streamKey)) {
          case (shutdownHook, (redisData1, redisData2)) =>
            val eventProcessor: TestEvent => UIO[Option[StreamMessageId]] = e => {
              if (e.id == redisData1.messageId) ZIO.none
              else ZIO.succeed(redisData2.messageId).asSome
            }

            val redisStreamMock =
              RedisStreamMock.ListGroups(value(Chunk(new StreamGroup(config.groupName.value, 0, 0, null)))) ++
                RedisStreamMock.ReadGroup(equalTo(pendingReadGroupCorrectArgs), value(Chunk.empty)) ++
                RedisStreamMock.ReadGroup(equalTo(newReadGroupCorrectArgs), value(Chunk(redisData1))) ++
                RedisStreamMock.ReadGroup(equalTo(newReadGroupCorrectArgs), value(Chunk(redisData2))) ++
                RedisStreamMock.Ack(equalTo((config.groupName, NonEmptyChunk(redisData2.messageId))), value(1)) ++
                RedisStreamMock.ReadGroup(equalTo(newReadGroupCorrectArgs), value(Chunk.empty))

            RedisZStream
              .executeFor[Any, StreamInstance.Notifications.type, StreamConsumerConfig](
                shutdownHook    = shutdownHook,
                eventsProcessor = _.mapM(eventsMapper).mapM(eventProcessor),
                repeatStrategy  = Schedule.recurs(3).unit
              )
              .map(assert(_)(equalTo(1L)))
              .provideCustomLayer(testEnv(redisStreamMock))
        }
      },
      testM("get PENDING messages every defined period") {
        checkAllM(promise) {
          shutdownHook =>
            val redisStreamMock =
              RedisStreamMock.ListGroups(value(Chunk(new StreamGroup(config.groupName.value, 0, 0, null)))) ++
                RedisStreamMock.ReadGroup(equalTo(pendingReadGroupCorrectArgs), value(Chunk.empty)) ++
                RedisStreamMock.ReadGroup(equalTo(pendingReadGroupCorrectArgs), value(Chunk.empty)) ++
                RedisStreamMock.ReadGroup(equalTo(pendingReadGroupCorrectArgs), value(Chunk.empty)) ++
                RedisStreamMock.ReadGroup(equalTo(pendingReadGroupCorrectArgs), value(Chunk.empty))

            def stream(clock: TestClock.Service) =
              RedisZStream
                .executeFor[Any, StreamInstance.Notifications.type, StreamConsumerConfig](
                  shutdownHook    = shutdownHook,
                  eventsProcessor = successfulEventProcessor(_, Chunk.empty),
                  repeatStrategy = Schedule
                    .recurs(3)
                    .onDecision(_ => clock.adjust(config.checkPendingEvery.plusSeconds(1)))
                    .unit
                )

            (for {
              clock  <- ZIO.service[TestClock.Service]
              result <- stream(clock)
            } yield assert(result)(equalTo(0L)))
              .provideSomeLayer[TestEnvironment](testEnv(redisStreamMock))
        }
      },
      testM("retry in case of group listing failure") {
        checkAllM(promise) {
          shutdownHook =>
            val redisStreamMock =
              RedisStreamMock.ListGroups(failure(new RuntimeException("BOOM"))) ++
                RedisStreamMock.ListGroups(value(Chunk(new StreamGroup(config.groupName.value, 0, 0, null)))) ++
                RedisStreamMock.ReadGroup(equalTo(pendingReadGroupCorrectArgs), value(Chunk.empty))

            val stream =
              RedisZStream
                .executeFor[Any, StreamInstance.Notifications.type, StreamConsumerConfig](
                  shutdownHook    = shutdownHook,
                  eventsProcessor = successfulEventProcessor(_, Chunk.empty),
                  repeatStrategy  = Schedule.recurs(0).unit
                )

            (for {
              forked <- stream.fork
              _      <- TestClock.adjust(config.retry.min.plusSeconds(1))
              result <- forked.join
            } yield assert(result)(equalTo(0L)))
              .provideSomeLayer[TestEnvironment](testEnv(redisStreamMock))
        }
      },
      testM("retry in case of group creation failure") {
        checkAllM(promise) {
          shutdownHook =>
            val redisStreamMock =
              RedisStreamMock.ListGroups(value(Chunk.empty)) ++
                RedisStreamMock.CreateGroup(
                  equalTo((config.groupName, CreateGroupStrategy.Newest)),
                  failure(new RuntimeException("BOOM"))
                ) ++
                RedisStreamMock.ListGroups(value(Chunk.empty)) ++
                RedisStreamMock.CreateGroup(equalTo((config.groupName, CreateGroupStrategy.Newest)), unit) ++
                RedisStreamMock.ReadGroup(equalTo(pendingReadGroupCorrectArgs), value(Chunk.empty))

            val stream =
              RedisZStream
                .executeFor[Any, StreamInstance.Notifications.type, StreamConsumerConfig](
                  shutdownHook    = shutdownHook,
                  eventsProcessor = successfulEventProcessor(_, Chunk.empty),
                  repeatStrategy  = Schedule.recurs(0).unit
                )

            (for {
              forked <- stream.fork
              _      <- TestClock.adjust(config.retry.min.plusSeconds(1))
              result <- forked.join
            } yield assert(result)(equalTo(0L)))
              .provideSomeLayer[TestEnvironment](testEnv(redisStreamMock))
        }
      },
      testM("retry in case of group reading failure") {
        checkAllM(promise) {
          shutdownHook =>
            val redisStreamMock =
              RedisStreamMock.ListGroups(value(Chunk(new StreamGroup(config.groupName.value, 0, 0, null)))) ++
                RedisStreamMock
                  .ReadGroup(equalTo(pendingReadGroupCorrectArgs), failure(new RuntimeException("BOOM"))) ++
                RedisStreamMock.ListGroups(value(Chunk(new StreamGroup(config.groupName.value, 0, 0, null)))) ++
                RedisStreamMock.ReadGroup(equalTo(pendingReadGroupCorrectArgs), value(Chunk.empty))

            val stream =
              RedisZStream
                .executeFor[Any, StreamInstance.Notifications.type, StreamConsumerConfig](
                  shutdownHook    = shutdownHook,
                  eventsProcessor = successfulEventProcessor(_, Chunk.empty),
                  repeatStrategy  = Schedule.recurs(0).unit
                )

            (for {
              forked <- stream.fork
              _      <- TestClock.adjust(config.retry.min.plusSeconds(1))
              result <- forked.join
            } yield assert(result)(equalTo(0L)))
              .provideSomeLayer[TestEnvironment](testEnv(redisStreamMock))
        }
      },
      testM("retry in case of acknowledge failure") {
        checkAllM(promise, redisData(streamKey)) {
          (shutdownHook, redisData) =>
            val redisStreamMock =
              RedisStreamMock.ListGroups(value(Chunk(new StreamGroup(config.groupName.value, 0, 0, null)))) ++
                RedisStreamMock.ReadGroup(equalTo(pendingReadGroupCorrectArgs), value(Chunk(redisData))) ++
                RedisStreamMock
                  .Ack(
                    equalTo((config.groupName, NonEmptyChunk(redisData.messageId))),
                    failure(new RuntimeException("BOOM"))
                  ) ++
                RedisStreamMock.ListGroups(value(Chunk(new StreamGroup(config.groupName.value, 0, 0, null)))) ++
                RedisStreamMock.ReadGroup(equalTo(pendingReadGroupCorrectArgs), value(Chunk(redisData))) ++
                RedisStreamMock.Ack(equalTo((config.groupName, NonEmptyChunk(redisData.messageId))), value(1))

            val stream =
              RedisZStream
                .executeFor[Any, StreamInstance.Notifications.type, StreamConsumerConfig](
                  shutdownHook    = shutdownHook,
                  eventsProcessor = successfulEventProcessor(_, Chunk(redisData)),
                  repeatStrategy  = Schedule.recurs(0).unit
                )

            (for {
              forked <- stream.fork
              _      <- TestClock.adjust(config.retry.min.plusSeconds(1))
              result <- forked.join
            } yield assert(result)(equalTo(1L)))
              .provideSomeLayer[TestEnvironment](testEnv(redisStreamMock))
        }
      },
      testM("triggering the shutdown hook will stop stream processing") {
        checkAllM(promise, uniqueRedisData(streamKey)) {
          case (shutdownHook, (redisData1, redisData2)) =>
            val redisStreamMock =
              RedisStreamMock.ListGroups(value(Chunk(new StreamGroup(config.groupName.value, 0, 0, null)))) ++
                RedisStreamMock.ReadGroup(equalTo(pendingReadGroupCorrectArgs), value(Chunk(redisData1))) ++
                RedisStreamMock.Ack(equalTo((config.groupName, NonEmptyChunk(redisData1.messageId))), value(1)) ++
                RedisStreamMock.ReadGroup(equalTo(pendingReadGroupCorrectArgs), value(Chunk(redisData2))).optional ++
                RedisStreamMock
                  .Ack(equalTo((config.groupName, NonEmptyChunk(redisData2.messageId))), value(1))
                  .optional ++
                RedisStreamMock.ReadGroup(equalTo(pendingReadGroupCorrectArgs), value(Chunk.empty)).optional

            val stream = RedisZStream
              .executeFor[Any, StreamInstance.Notifications.type, StreamConsumerConfig](
                shutdownHook    = shutdownHook,
                eventsProcessor = successfulEventProcessor(_, Chunk(redisData1, redisData2)),
                repeatStrategy = Schedule.forever
                  .onDecision({
                    case Decision.Continue(attempt, _, _) if attempt == 1 => shutdownHook.succeed(())
                    case _                                                => ZIO.unit
                  })
                  .unit
              )

            (for {
              forked <- stream.fork
              _      <- shutdownHook.await
              _      <- forked.join
            } yield assertCompletes)
              .provideSomeLayer[TestEnvironment](testEnv(redisStreamMock))
        }
      }
    ) @@ TestAspect.timeout(30.seconds)
  }

  private def eventsMapper(rawData: ReadGroupResult) =
    ZIO.succeed(TestEvent(rawData.messageId, new String(rawData.data.head.payload.toArray, "UTF-8")))

  private def successfulEventProcessor(
    stream: StreamInput[StreamInstance.Notifications.type, StreamConsumerConfig],
    redisData: Chunk[ReadGroupResult]
  ) =
    stream.mapM(eventsMapper).map(e => redisData.find(_.messageId == e.id).map(_.messageId))

  private def testEnv(redisStreamMock: ULayer[RedisStream[StreamInstance.Notifications.type]]) =
    ZLayer.succeed(config) ++ redisStreamMock ++ ZLayer.identity[Clock] ++ Logging.ignore

  private[this] case class TestEvent(
    id: StreamMessageId,
    msg: String
  )

  private object TestData {
    val config = new StreamConsumerConfig {
      override val claiming: ClaimingConfig = ClaimingConfig(
        initialDelay      = 5.seconds,
        repeatEvery       = 1.minute,
        maxNoOfDeliveries = 10,
        maxIdleTime       = 1.hour
      )
      override val retry: RetryConfig = RetryConfig(
        min    = 5.seconds,
        max    = 1.minute,
        factor = 2
      )
      override val readTimeout: Duration            = 20.seconds
      override val checkPendingEvery: Duration      = 5.minutes
      override val streamName: StreamName           = StreamName("test-stream")
      override val groupName: StreamGroupName       = StreamGroupName("test-stream-group-name")
      override val consumerName: StreamConsumerName = StreamConsumerName("test-stream-consumer-name")
    }

    val streamKey = StreamKey("test-event-key")

    val pendingReadGroupCorrectArgs =
      (
        config.groupName,
        config.consumerName,
        10,
        config.readTimeout,
        ListGroupStrategy.Pending
      )
    val newReadGroupCorrectArgs =
      (
        config.groupName,
        config.consumerName,
        10,
        config.readTimeout,
        ListGroupStrategy.New
      )
  }
}
