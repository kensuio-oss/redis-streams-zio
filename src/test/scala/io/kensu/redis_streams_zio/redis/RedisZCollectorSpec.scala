package io.kensu.redis_streams_zio.redis

import io.kensu.redis_streams_zio.config._
import io.kensu.redis_streams_zio.redis.streams.RedisStream.RedisStream
import io.kensu.redis_streams_zio.redis.streams.{ RedisZCollector, StreamInstance }
import io.kensu.redis_streams_zio.specs.mocks.RedisStreamMock
import org.redisson.api.{ PendingEntry, StreamMessageId }
import zio._
import zio.clock.Clock
import zio.duration.{ durationInt, Duration }
import zio.logging.Logging
import zio.test.Assertion._
import zio.test.environment.{ TestClock, TestEnvironment }
import zio.test.mock.Expectation._
import zio.test.{ DefaultRunnableSpec, _ }

object RedisZCollectorSpec extends DefaultRunnableSpec {

  import TestData._

  override val spec: ZSpec[TestEnvironment, Failure] = {
    import zio.duration._
    suite("RedisZCollector.executeFor")(
      testM("does not begin processing before initial delay") {
        val redisStreamMock = RedisStreamMock.ListPending(equalTo(config.groupName, 100), value(Chunk.empty)).atMost(0)

        val collector = RedisZCollector.executeFor[StreamInstance.Notifications.type, StreamConsumerConfig]()
        (for {
          forked <- collector.fork
          _      <- TestClock.adjust(config.claiming.initialDelay.minusMillis(1))
          _      <- forked.interrupt
        } yield assertCompletes)
          .provideSomeLayer[TestEnvironment](testEnv(redisStreamMock))
      },
      testM("begin processing after initial delay") {
        val redisStreamMock = RedisStreamMock.ListPending(equalTo(config.groupName, 100), value(Chunk.empty))

        val collector = RedisZCollector.executeFor[StreamInstance.Notifications.type, StreamConsumerConfig]()

        (for {
          forked <- collector.fork
          _      <- TestClock.adjust(config.claiming.initialDelay)
          _      <- forked.interrupt
        } yield assertCompletes)
          .provideSomeLayer[TestEnvironment](testEnv(redisStreamMock))
      },
      testM("claim only messages with exceeded idle time from other consumers") {
        val goodEntry = new PendingEntry(new StreamMessageId(412323), config.consumerName.value, 1, 0)
        val otherConsumerExceededIdleTimeEntry = new PendingEntry(
          new StreamMessageId(65456345),
          "other-consumer",
          config.claiming.maxIdleTime.toMillis + 1,
          0
        )
        val sameConsumerExceededIdleTimeEntry = new PendingEntry(
          new StreamMessageId(546123213),
          config.consumerName.value,
          config.claiming.maxIdleTime.toMillis + 1,
          0
        )

        val redisStreamMock =
          RedisStreamMock.ListPending(
            equalTo(config.groupName, 100),
            value(Chunk(goodEntry, otherConsumerExceededIdleTimeEntry, sameConsumerExceededIdleTimeEntry))
          ) ++
            RedisStreamMock.FastClaim(
              equalTo(
                config.groupName,
                config.consumerName,
                config.claiming.maxIdleTime,
                NonEmptyChunk(otherConsumerExceededIdleTimeEntry.getId)
              ),
              value(Chunk(otherConsumerExceededIdleTimeEntry.getId))
            )

        val collector = RedisZCollector.executeFor[StreamInstance.Notifications.type, StreamConsumerConfig]()

        (for {
          forked <- collector.fork
          _      <- TestClock.adjust(config.claiming.initialDelay)
          _      <- forked.interrupt
        } yield assertCompletes)
          .provideSomeLayer[TestEnvironment](testEnv(redisStreamMock))
      },
      testM("claim only half of suitable messages") {
        val exceededIdleTimeEntry1 = new PendingEntry(
          new StreamMessageId(65456345),
          "other-consumer",
          config.claiming.maxIdleTime.toMillis + 1,
          0
        )
        val exceededIdleTimeEntry2 = new PendingEntry(
          new StreamMessageId(546123213),
          "other-consumer",
          config.claiming.maxIdleTime.toMillis + 1,
          0
        )

        val redisStreamMock =
          RedisStreamMock.ListPending(
            equalTo(config.groupName, 100),
            value(Chunk(exceededIdleTimeEntry1, exceededIdleTimeEntry2))
          ) ++
            RedisStreamMock.FastClaim(
              equalTo(
                config.groupName,
                config.consumerName,
                config.claiming.maxIdleTime,
                NonEmptyChunk(exceededIdleTimeEntry1.getId)
              ),
              value(Chunk(exceededIdleTimeEntry1.getId))
            )

        val collector = RedisZCollector.executeFor[StreamInstance.Notifications.type, StreamConsumerConfig]()

        (for {
          forked <- collector.fork
          _      <- TestClock.adjust(config.claiming.initialDelay)
          _      <- forked.interrupt
        } yield assertCompletes)
          .provideSomeLayer[TestEnvironment](testEnv(redisStreamMock))
      },
      testM("can keep repeating the claiming process") {
        val exceededIdleTimeEntry1 = new PendingEntry(
          new StreamMessageId(65456345),
          "other-consumer",
          config.claiming.maxIdleTime.toMillis + 1,
          0
        )
        val exceededIdleTimeEntry2 = new PendingEntry(
          new StreamMessageId(546123213),
          "other-consumer",
          config.claiming.maxIdleTime.toMillis + 1,
          0
        )

        val redisStreamMock =
          RedisStreamMock.ListPending(
            equalTo(config.groupName, 100),
            value(Chunk(exceededIdleTimeEntry1, exceededIdleTimeEntry2))
          ) ++
            RedisStreamMock.FastClaim(
              equalTo(
                config.groupName,
                config.consumerName,
                config.claiming.maxIdleTime,
                NonEmptyChunk(exceededIdleTimeEntry1.getId)
              ),
              value(Chunk(exceededIdleTimeEntry1.getId))
            ) ++
            RedisStreamMock.ListPending(
              equalTo(config.groupName, 100),
              value(Chunk(exceededIdleTimeEntry1))
            ) ++
            RedisStreamMock.FastClaim(
              equalTo(
                config.groupName,
                config.consumerName,
                config.claiming.maxIdleTime,
                NonEmptyChunk(exceededIdleTimeEntry1.getId)
              ),
              value(Chunk(exceededIdleTimeEntry1.getId))
            )

        val collector =
          RedisZCollector.executeFor[StreamInstance.Notifications.type, StreamConsumerConfig](Some(Schedule.once))

        (for {
          forked <- collector.fork
          _      <- TestClock.adjust(config.claiming.initialDelay)
          _      <- forked.join
        } yield assertCompletes)
          .provideSomeLayer[TestEnvironment](testEnv(redisStreamMock))
      },
      testM("acknowledge only messages with exceeded number of deliveries") {
        val goodEntry = new PendingEntry(new StreamMessageId(412323), config.consumerName.value, 1, 0)
        val otherConsumerExceededIdleTimeEntry = new PendingEntry(
          new StreamMessageId(65456345),
          "other-consumer",
          0,
          config.claiming.maxNoOfDeliveries + 1L
        )
        val sameConsumerExceededIdleTimeEntry = new PendingEntry(
          new StreamMessageId(546123213),
          config.consumerName.value,
          0,
          config.claiming.maxNoOfDeliveries + 1L
        )

        val redisStreamMock =
          RedisStreamMock.ListPending(
            equalTo(config.groupName, 100),
            value(Chunk(goodEntry, otherConsumerExceededIdleTimeEntry, sameConsumerExceededIdleTimeEntry))
          ) ++
            RedisStreamMock.Ack(
              equalTo(
                config.groupName,
                NonEmptyChunk(otherConsumerExceededIdleTimeEntry.getId, sameConsumerExceededIdleTimeEntry.getId)
              ),
              value(2)
            )

        val collector = RedisZCollector.executeFor[StreamInstance.Notifications.type, StreamConsumerConfig]()

        (for {
          forked <- collector.fork
          _      <- TestClock.adjust(config.claiming.initialDelay)
          _      <- forked.interrupt
        } yield assertCompletes)
          .provideSomeLayer[TestEnvironment](testEnv(redisStreamMock))
      },
      testM("can keep repeating the acknowledge process") {
        val goodEntry = new PendingEntry(new StreamMessageId(412323), config.consumerName.value, 1, 0)
        val otherConsumerExceededIdleTimeEntry = new PendingEntry(
          new StreamMessageId(65456345),
          "other-consumer",
          0,
          config.claiming.maxNoOfDeliveries + 1L
        )
        val sameConsumerExceededIdleTimeEntry = new PendingEntry(
          new StreamMessageId(546123213),
          config.consumerName.value,
          0,
          config.claiming.maxNoOfDeliveries + 1L
        )

        val redisStreamMock =
          RedisStreamMock
            .ListPending(
              equalTo(config.groupName, 100),
              value(Chunk(goodEntry, otherConsumerExceededIdleTimeEntry, sameConsumerExceededIdleTimeEntry))
            ) ++
            RedisStreamMock
              .Ack(
                equalTo(
                  config.groupName,
                  NonEmptyChunk(otherConsumerExceededIdleTimeEntry.getId, sameConsumerExceededIdleTimeEntry.getId)
                ),
                value(1)
              ) ++
            RedisStreamMock
              .ListPending(equalTo(config.groupName, 100), value(Chunk(goodEntry, otherConsumerExceededIdleTimeEntry))) ++
            RedisStreamMock
              .Ack(equalTo(config.groupName, NonEmptyChunk(otherConsumerExceededIdleTimeEntry.getId)), value(1))

        val collector = RedisZCollector
          .executeFor[StreamInstance.Notifications.type, StreamConsumerConfig](Some(Schedule.once))

        (for {
          forked <- collector.fork
          _      <- TestClock.adjust(config.claiming.initialDelay)
          _      <- forked.join
        } yield assertCompletes)
          .provideSomeLayer[TestEnvironment](testEnv(redisStreamMock))
      }
    ) @@ TestAspect.timeout(30.seconds)
  }

  private def testEnv(redisStreamMock: ULayer[RedisStream[StreamInstance.Notifications.type]]) =
    ZLayer.succeed(config) ++ redisStreamMock ++ ZLayer.identity[Clock] ++ Logging.ignore

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
  }
}
