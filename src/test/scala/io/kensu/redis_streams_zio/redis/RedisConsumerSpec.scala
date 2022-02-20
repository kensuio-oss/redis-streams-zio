package io.kensu.redis_streams_zio.redis

import io.kensu.redis_streams_zio.config.*
import io.kensu.redis_streams_zio.redis.PropertyGenerators.*
import io.kensu.redis_streams_zio.redis.streams.RedisConsumer.StreamInput
import io.kensu.redis_streams_zio.redis.streams.*
import io.kensu.redis_streams_zio.specs.mocks.NotificationsRedisStreamMock
import org.redisson.api.{StreamGroup, StreamMessageId}
import zio.*
import zio.Schedule.Decision
import zio.clock.Clock
import zio.duration.{durationInt, Duration}
import zio.logging.Logging
import zio.test.*
import zio.test.Assertion.*
import zio.test.environment.{TestClock, TestEnvironment}
import zio.test.mock.Expectation.*

object RedisConsumerSpec extends DefaultRunnableSpec:

  import TestData.*

  override val spec: ZSpec[TestEnvironment, Failure] =
    import zio.duration.*
    suite("RedisZStream.executeFor")(
      testM("reuse consumer group if the requested one exists") {
        checkAllM(promise) {
          shutdownHook =>
            val redisStreamMock =
              NotificationsRedisStreamMock.ListGroups(
                value(Chunk(new StreamGroup(config.groupName.value, 0, 0, null)))
              ) ++
                NotificationsRedisStreamMock.ReadGroup(equalTo(pendingReadGroupCorrectArgs), value(Chunk.empty))

            RedisConsumer
              .executeFor[Any, StreamInstance.Notifications, StreamConsumerConfig](
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
              NotificationsRedisStreamMock.ListGroups(value(Chunk.empty)) ++
                NotificationsRedisStreamMock.CreateGroup(
                  equalTo((config.groupName, CreateGroupStrategy.Newest)),
                  unit
                ) ++
                NotificationsRedisStreamMock.ReadGroup(equalTo(pendingReadGroupCorrectArgs), value(Chunk.empty))

            RedisConsumer
              .executeFor[Any, StreamInstance.Notifications, StreamConsumerConfig](
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
            val redisStreamMock =
              NotificationsRedisStreamMock.ListGroups(value(Chunk(new StreamGroup("no-way", 0, 0, null)))) ++
                NotificationsRedisStreamMock.CreateGroup(
                  equalTo((config.groupName, CreateGroupStrategy.Newest)),
                  unit
                ) ++
                NotificationsRedisStreamMock.ReadGroup(equalTo(pendingReadGroupCorrectArgs), value(Chunk.empty))

            RedisConsumer
              .executeFor[Any, StreamInstance.Notifications, StreamConsumerConfig](
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
              NotificationsRedisStreamMock.ListGroups(
                value(Chunk(new StreamGroup(config.groupName.value, 0, 0, null)))
              ) ++
                NotificationsRedisStreamMock.ReadGroup(equalTo(pendingReadGroupCorrectArgs), value(Chunk.empty))

            RedisConsumer
              .executeFor[Any, StreamInstance.Notifications, StreamConsumerConfig](
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
              NotificationsRedisStreamMock.ListGroups(
                value(Chunk(new StreamGroup(config.groupName.value, 0, 0, null)))
              ) ++
                NotificationsRedisStreamMock.ReadGroup(
                  equalTo(pendingReadGroupCorrectArgs),
                  value(Chunk(redisData1))
                ) ++
                NotificationsRedisStreamMock.Ack(
                  equalTo((config.groupName, NonEmptyChunk(redisData1.messageId))),
                  value(1)
                ) ++
                NotificationsRedisStreamMock.ReadGroup(
                  equalTo(pendingReadGroupCorrectArgs),
                  value(Chunk(redisData2))
                ) ++
                NotificationsRedisStreamMock.Ack(
                  equalTo((config.groupName, NonEmptyChunk(redisData2.messageId))),
                  value(1)
                ) ++
                NotificationsRedisStreamMock.ReadGroup(equalTo(pendingReadGroupCorrectArgs), value(Chunk.empty)) ++
                NotificationsRedisStreamMock.ReadGroup(equalTo(newReadGroupCorrectArgs), value(Chunk.empty))

            RedisConsumer
              .executeFor[Any, StreamInstance.Notifications, StreamConsumerConfig](
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
              NotificationsRedisStreamMock.ListGroups(
                value(Chunk(new StreamGroup(config.groupName.value, 0, 0, null)))
              ) ++
                NotificationsRedisStreamMock.ReadGroup(
                  equalTo(pendingReadGroupCorrectArgs),
                  value(Chunk(redisData1, redisData2))
                ) ++
                NotificationsRedisStreamMock.ReadGroup(
                  equalTo(pendingReadGroupCorrectArgs),
                  value(Chunk(redisData1, redisData2))
                )

            RedisConsumer
              .executeFor[Any, StreamInstance.Notifications, StreamConsumerConfig](
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
              NotificationsRedisStreamMock.ListGroups(
                value(Chunk(new StreamGroup(config.groupName.value, 0, 0, null)))
              ) ++
                NotificationsRedisStreamMock.ReadGroup(equalTo(pendingReadGroupCorrectArgs), value(Chunk.empty)) ++
                NotificationsRedisStreamMock.ReadGroup(equalTo(newReadGroupCorrectArgs), value(Chunk(redisData1))) ++
                NotificationsRedisStreamMock.Ack(
                  equalTo((config.groupName, NonEmptyChunk(redisData1.messageId))),
                  value(1)
                ) ++
                NotificationsRedisStreamMock.ReadGroup(equalTo(newReadGroupCorrectArgs), value(Chunk(redisData2))) ++
                NotificationsRedisStreamMock.Ack(
                  equalTo((config.groupName, NonEmptyChunk(redisData2.messageId))),
                  value(1)
                ) ++
                NotificationsRedisStreamMock.ReadGroup(equalTo(newReadGroupCorrectArgs), value(Chunk.empty))

            RedisConsumer
              .executeFor[Any, StreamInstance.Notifications, StreamConsumerConfig](
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
              if e.id == redisData1.messageId then ZIO.none
              else ZIO.succeed(redisData2.messageId).asSome
            }

            val redisStreamMock =
              NotificationsRedisStreamMock.ListGroups(
                value(Chunk(new StreamGroup(config.groupName.value, 0, 0, null)))
              ) ++
                NotificationsRedisStreamMock.ReadGroup(equalTo(pendingReadGroupCorrectArgs), value(Chunk.empty)) ++
                NotificationsRedisStreamMock.ReadGroup(equalTo(newReadGroupCorrectArgs), value(Chunk(redisData1))) ++
                NotificationsRedisStreamMock.ReadGroup(equalTo(newReadGroupCorrectArgs), value(Chunk(redisData2))) ++
                NotificationsRedisStreamMock.Ack(
                  equalTo((config.groupName, NonEmptyChunk(redisData2.messageId))),
                  value(1)
                ) ++
                NotificationsRedisStreamMock.ReadGroup(equalTo(newReadGroupCorrectArgs), value(Chunk.empty))

            RedisConsumer
              .executeFor[Any, StreamInstance.Notifications, StreamConsumerConfig](
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
              NotificationsRedisStreamMock.ListGroups(
                value(Chunk(new StreamGroup(config.groupName.value, 0, 0, null)))
              ) ++
                NotificationsRedisStreamMock.ReadGroup(equalTo(pendingReadGroupCorrectArgs), value(Chunk.empty)) ++
                NotificationsRedisStreamMock.ReadGroup(equalTo(pendingReadGroupCorrectArgs), value(Chunk.empty)) ++
                NotificationsRedisStreamMock.ReadGroup(equalTo(pendingReadGroupCorrectArgs), value(Chunk.empty)) ++
                NotificationsRedisStreamMock.ReadGroup(equalTo(pendingReadGroupCorrectArgs), value(Chunk.empty))

            def stream(clock: TestClock.Service) =
              RedisConsumer
                .executeFor[Any, StreamInstance.Notifications, StreamConsumerConfig](
                  shutdownHook    = shutdownHook,
                  eventsProcessor = successfulEventProcessor(_, Chunk.empty),
                  repeatStrategy  = Schedule
                    .recurs(3)
                    .onDecision(_ => clock.adjust(config.checkPendingEvery.plusSeconds(1)))
                    .unit
                )

            (for
              clock  <- ZIO.service[TestClock.Service]
              result <- stream(clock)
            yield assert(result)(equalTo(0L)))
              .provideSomeLayer[TestEnvironment](testEnv(redisStreamMock))
        }
      },
      testM("acknowledge message with an empty value") {
        checkAllM(promise, redisData(streamKey)) { (shutdownHook, message) =>
          val messageWithEmptyValue = message.copy(data = Chunk.empty)
          val redisStreamMock       =
            NotificationsRedisStreamMock.ListGroups(
              value(Chunk(new StreamGroup(config.groupName.value, 0, 0, null)))
            ) ++
              NotificationsRedisStreamMock.ReadGroup(
                equalTo(pendingReadGroupCorrectArgs),
                value(Chunk.single(messageWithEmptyValue))
              ) ++
              NotificationsRedisStreamMock.Ack(
                equalTo((config.groupName, NonEmptyChunk(messageWithEmptyValue.messageId))),
                value(1)
              )

          RedisConsumer
            .executeFor[Any, StreamInstance.Notifications, StreamConsumerConfig](
              shutdownHook    = shutdownHook,
              eventsProcessor =
                failingEventProcessor, // This event processor will not be called for empty value message, thus fail if it used at all
              repeatStrategy  = Schedule.recurs(0).unit
            )
            .map(assert(_)(equalTo(1L)))
            .provideCustomLayer(testEnv(redisStreamMock))
        }
      } @@ TestAspect.nonFlaky,
      testM("retry in case of group listing failure") {
        checkAllM(promise) {
          shutdownHook =>
            val redisStreamMock =
              NotificationsRedisStreamMock.ListGroups(failure(new RuntimeException("BOOM"))) ++
                NotificationsRedisStreamMock.ListGroups(value(Chunk(new StreamGroup(
                  config.groupName.value,
                  0,
                  0,
                  null
                )))) ++
                NotificationsRedisStreamMock.ReadGroup(equalTo(pendingReadGroupCorrectArgs), value(Chunk.empty))

            val stream =
              RedisConsumer
                .executeFor[Any, StreamInstance.Notifications, StreamConsumerConfig](
                  shutdownHook    = shutdownHook,
                  eventsProcessor = successfulEventProcessor(_, Chunk.empty),
                  repeatStrategy  = Schedule.recurs(0).unit
                )

            (for
              forked <- stream.fork
              _      <- TestClock.adjust(config.retry.min.plusSeconds(1))
              result <- forked.join
            yield assert(result)(equalTo(0L)))
              .provideSomeLayer[TestEnvironment](testEnv(redisStreamMock))
        }
      },
      testM("retry in case of group creation failure") {
        checkAllM(promise) {
          shutdownHook =>
            val redisStreamMock =
              NotificationsRedisStreamMock.ListGroups(value(Chunk.empty)) ++
                NotificationsRedisStreamMock.CreateGroup(
                  equalTo((config.groupName, CreateGroupStrategy.Newest)),
                  failure(new RuntimeException("BOOM"))
                ) ++
                NotificationsRedisStreamMock.ListGroups(value(Chunk.empty)) ++
                NotificationsRedisStreamMock.CreateGroup(
                  equalTo((config.groupName, CreateGroupStrategy.Newest)),
                  unit
                ) ++
                NotificationsRedisStreamMock.ReadGroup(equalTo(pendingReadGroupCorrectArgs), value(Chunk.empty))

            val stream =
              RedisConsumer
                .executeFor[Any, StreamInstance.Notifications, StreamConsumerConfig](
                  shutdownHook    = shutdownHook,
                  eventsProcessor = successfulEventProcessor(_, Chunk.empty),
                  repeatStrategy  = Schedule.recurs(0).unit
                )

            (for
              forked <- stream.fork
              _      <- TestClock.adjust(config.retry.min.plusSeconds(1))
              result <- forked.join
            yield assert(result)(equalTo(0L)))
              .provideSomeLayer[TestEnvironment](testEnv(redisStreamMock))
        }
      },
      testM("retry in case of group reading failure") {
        checkAllM(promise) {
          shutdownHook =>
            val redisStreamMock =
              NotificationsRedisStreamMock.ListGroups(
                value(Chunk(new StreamGroup(config.groupName.value, 0, 0, null)))
              ) ++
                NotificationsRedisStreamMock
                  .ReadGroup(equalTo(pendingReadGroupCorrectArgs), failure(new RuntimeException("BOOM"))) ++
                NotificationsRedisStreamMock.ListGroups(value(Chunk(new StreamGroup(
                  config.groupName.value,
                  0,
                  0,
                  null
                )))) ++
                NotificationsRedisStreamMock.ReadGroup(equalTo(pendingReadGroupCorrectArgs), value(Chunk.empty))

            val stream =
              RedisConsumer
                .executeFor[Any, StreamInstance.Notifications, StreamConsumerConfig](
                  shutdownHook    = shutdownHook,
                  eventsProcessor = successfulEventProcessor(_, Chunk.empty),
                  repeatStrategy  = Schedule.recurs(0).unit
                )

            (for
              forked <- stream.fork
              _      <- TestClock.adjust(config.retry.min.plusSeconds(1))
              result <- forked.join
            yield assert(result)(equalTo(0L)))
              .provideSomeLayer[TestEnvironment](testEnv(redisStreamMock))
        }
      },
      testM("retry in case of acknowledge failure") {
        checkAllM(promise, redisData(streamKey)) {
          (shutdownHook, redisData) =>
            val redisStreamMock =
              NotificationsRedisStreamMock.ListGroups(
                value(Chunk(new StreamGroup(config.groupName.value, 0, 0, null)))
              ) ++
                NotificationsRedisStreamMock.ReadGroup(equalTo(pendingReadGroupCorrectArgs), value(Chunk(redisData))) ++
                NotificationsRedisStreamMock
                  .Ack(
                    equalTo((config.groupName, NonEmptyChunk(redisData.messageId))),
                    failure(new RuntimeException("BOOM"))
                  ) ++
                NotificationsRedisStreamMock.ListGroups(value(Chunk(new StreamGroup(
                  config.groupName.value,
                  0,
                  0,
                  null
                )))) ++
                NotificationsRedisStreamMock.ReadGroup(equalTo(pendingReadGroupCorrectArgs), value(Chunk(redisData))) ++
                NotificationsRedisStreamMock.Ack(
                  equalTo((config.groupName, NonEmptyChunk(redisData.messageId))),
                  value(1)
                )

            val stream =
              RedisConsumer
                .executeFor[Any, StreamInstance.Notifications, StreamConsumerConfig](
                  shutdownHook    = shutdownHook,
                  eventsProcessor = successfulEventProcessor(_, Chunk(redisData)),
                  repeatStrategy  = Schedule.recurs(0).unit
                )

            (for
              forked <- stream.fork
              _      <- TestClock.adjust(config.retry.min.plusSeconds(1))
              result <- forked.join
            yield assert(result)(equalTo(1L)))
              .provideSomeLayer[TestEnvironment](testEnv(redisStreamMock))
        }
      },
      testM("triggering the shutdown hook will stop stream processing") {
        checkAllM(promise, uniqueRedisData(streamKey)) {
          case (shutdownHook, (redisData1, redisData2)) =>
            val redisStreamMock =
              NotificationsRedisStreamMock.ListGroups(
                value(Chunk(new StreamGroup(config.groupName.value, 0, 0, null)))
              ) ++
                NotificationsRedisStreamMock.ReadGroup(
                  equalTo(pendingReadGroupCorrectArgs),
                  value(Chunk(redisData1))
                ) ++
                NotificationsRedisStreamMock.Ack(
                  equalTo((config.groupName, NonEmptyChunk(redisData1.messageId))),
                  value(1)
                ) ++
                NotificationsRedisStreamMock.ReadGroup(
                  equalTo(pendingReadGroupCorrectArgs),
                  value(Chunk(redisData2))
                ).optional ++
                NotificationsRedisStreamMock
                  .Ack(equalTo((config.groupName, NonEmptyChunk(redisData2.messageId))), value(1))
                  .optional ++
                NotificationsRedisStreamMock.ReadGroup(
                  equalTo(pendingReadGroupCorrectArgs),
                  value(Chunk.empty)
                ).optional

            val stream = RedisConsumer
              .executeFor[Any, StreamInstance.Notifications, StreamConsumerConfig](
                shutdownHook    = shutdownHook,
                eventsProcessor = successfulEventProcessor(_, Chunk(redisData1, redisData2)),
                repeatStrategy  = Schedule.forever
                  .onDecision {
                    case Decision.Continue(attempt, _, _) if attempt == 1 => shutdownHook.succeed(())
                    case _                                                => ZIO.unit
                  }
                  .unit
              )

            (for
              forked <- stream.fork
              _      <- shutdownHook.await
              _      <- forked.join
            yield assertCompletes)
              .provideSomeLayer[TestEnvironment](testEnv(redisStreamMock))
        }
      }
    ) @@ TestAspect.timeout(30.seconds)

  private def eventsMapper(rawData: ReadGroupResult) =
    ZIO.succeed(TestEvent(rawData.messageId, new String(rawData.data.head.payload.toArray, "UTF-8")))

  private def successfulEventProcessor(
    stream: StreamInput[StreamInstance.Notifications, StreamConsumerConfig],
    redisData: Chunk[ReadGroupResult]
  ) =
    stream.mapM(eventsMapper).map(e => redisData.find(_.messageId == e.id).map(_.messageId))

  private def failingEventProcessor(stream: StreamInput[StreamInstance.Notifications, StreamConsumerConfig]) =
    stream.mapM(_ => IO.fail(new IllegalStateException("I should not be called")))

  private def testEnv(redisStreamMock: ULayer[Has[RedisStream[StreamInstance.Notifications]]]) =
    ZLayer.succeed(config) ++ redisStreamMock ++ ZLayer.identity[Clock] ++ Logging.ignore

  private[redis] case class TestEvent(
    id: StreamMessageId,
    msg: String
  )

  private object TestData:

    val config: StreamConsumerConfig = new StreamConsumerConfig {

      override val claiming: ClaimingConfig = ClaimingConfig(
        initialDelay      = 5.seconds,
        repeatEvery       = 1.minute,
        maxNoOfDeliveries = 10,
        maxIdleTime       = 1.hour
      )

      override val retry: RetryConfig               = RetryConfig(
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

    val streamKey: StreamKey = StreamKey("test-event-key")

    val pendingReadGroupCorrectArgs: (
      StreamGroupName,
      StreamConsumerName,
      Int,
      Duration,
      ListGroupStrategy.Pending.type
    ) =
      (
        config.groupName,
        config.consumerName,
        10,
        config.readTimeout,
        ListGroupStrategy.Pending
      )

    val newReadGroupCorrectArgs: (StreamGroupName, StreamConsumerName, Int, Duration, ListGroupStrategy.New.type) =
      (
        config.groupName,
        config.consumerName,
        10,
        config.readTimeout,
        ListGroupStrategy.New
      )
