package io.kensu.redis_streams_zio.redis

import io.kensu.redis_streams_zio.config.*
import io.kensu.redis_streams_zio.redis.streams.{
  NotificationsRedisStream,
  RedisStaleEventsCollector,
  RedisStream,
  StreamInstance
}
import io.kensu.redis_streams_zio.specs.mocks.NotificationsRedisStreamMock
import org.redisson.api.{PendingEntry, StreamMessageId}
import zio.*
import zio.test.Assertion.*
import zio.mock.Expectation.*
import zio.test.*

object RedisStaleEventsCollectorSpec extends ZIOSpecDefault:

  import TestData.*

  override val spec =
    suite("RedisZCollector.executeFor")(
      test("does not begin processing before initial delay") {
        val redisStreamMock =
          NotificationsRedisStreamMock.ListPending(equalTo(config.groupName, 100), value(Chunk.empty)).atMost(0)

        val collector = RedisStaleEventsCollector.executeFor[StreamInstance.Notifications, StreamConsumerConfig]()
        (for
          forked <- collector.fork
          _      <- TestClock.adjust(config.claiming.initialDelay.minusMillis(1))
          _      <- forked.interrupt
        yield assertCompletes)
          .provideLayer(testEnv(redisStreamMock))
      },
      test("begin processing after initial delay") {
        val redisStreamMock =
          NotificationsRedisStreamMock.ListPending(equalTo(config.groupName, 100), value(Chunk.empty))

        val collector = RedisStaleEventsCollector.executeFor[StreamInstance.Notifications, StreamConsumerConfig]()

        (for
          forked <- collector.fork
          _      <- TestClock.adjust(config.claiming.initialDelay)
          _      <- forked.interrupt
        yield assertCompletes)
          .provideLayer(testEnv(redisStreamMock))
      },
      test("claim only messages & exceeded idle time from other consumers") {
        val goodEntry                          = new PendingEntry(new StreamMessageId(412323), config.consumerName.value, 1, 0)
        val otherConsumerExceededIdleTimeEntry = new PendingEntry(
          new StreamMessageId(65456345),
          "other-consumer",
          config.claiming.maxIdleTime.toMillis + 1,
          0
        )
        val sameConsumerExceededIdleTimeEntry  = new PendingEntry(
          new StreamMessageId(546123213),
          config.consumerName.value,
          config.claiming.maxIdleTime.toMillis + 1,
          0
        )

        val redisStreamMock =
          NotificationsRedisStreamMock.ListPending(
            equalTo(config.groupName, 100),
            value(Chunk(goodEntry, otherConsumerExceededIdleTimeEntry, sameConsumerExceededIdleTimeEntry))
          ) ++
            NotificationsRedisStreamMock.FastClaim(
              equalTo(
                config.groupName,
                config.consumerName,
                config.claiming.maxIdleTime,
                NonEmptyChunk(otherConsumerExceededIdleTimeEntry.getId)
              ),
              value(Chunk(otherConsumerExceededIdleTimeEntry.getId))
            )

        val collector = RedisStaleEventsCollector.executeFor[StreamInstance.Notifications, StreamConsumerConfig]()

        (for
          forked <- collector.fork
          _      <- TestClock.adjust(config.claiming.initialDelay)
          _      <- forked.interrupt
        yield assertCompletes)
          .provideLayer(testEnv(redisStreamMock))
      },
      test("claim only half of suitable messages") {
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
          NotificationsRedisStreamMock.ListPending(
            equalTo(config.groupName, 100),
            value(Chunk(exceededIdleTimeEntry1, exceededIdleTimeEntry2))
          ) ++
            NotificationsRedisStreamMock.FastClaim(
              equalTo(
                config.groupName,
                config.consumerName,
                config.claiming.maxIdleTime,
                NonEmptyChunk(exceededIdleTimeEntry1.getId)
              ),
              value(Chunk(exceededIdleTimeEntry1.getId))
            )

        val collector = RedisStaleEventsCollector.executeFor[StreamInstance.Notifications, StreamConsumerConfig]()

        (for
          forked <- collector.fork
          _      <- TestClock.adjust(config.claiming.initialDelay)
          _      <- forked.interrupt
        yield assertCompletes)
          .provideLayer(testEnv(redisStreamMock))
      },
      test("can keep repeating the claiming process") {
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
          NotificationsRedisStreamMock.ListPending(
            equalTo(config.groupName, 100),
            value(Chunk(exceededIdleTimeEntry1, exceededIdleTimeEntry2))
          ) ++
            NotificationsRedisStreamMock.FastClaim(
              equalTo(
                config.groupName,
                config.consumerName,
                config.claiming.maxIdleTime,
                NonEmptyChunk(exceededIdleTimeEntry1.getId)
              ),
              value(Chunk(exceededIdleTimeEntry1.getId))
            ) ++
            NotificationsRedisStreamMock.ListPending(
              equalTo(config.groupName, 100),
              value(Chunk(exceededIdleTimeEntry1))
            ) ++
            NotificationsRedisStreamMock.FastClaim(
              equalTo(
                config.groupName,
                config.consumerName,
                config.claiming.maxIdleTime,
                NonEmptyChunk(exceededIdleTimeEntry1.getId)
              ),
              value(Chunk(exceededIdleTimeEntry1.getId))
            )

        val collector =
          RedisStaleEventsCollector.executeFor[StreamInstance.Notifications, StreamConsumerConfig](
            Some(Schedule.once)
          )

        (for
          forked <- collector.fork
          _      <- TestClock.adjust(config.claiming.initialDelay)
          _      <- forked.join
        yield assertCompletes)
          .provideLayer(testEnv(redisStreamMock))
      },
      test("acknowledge only messages with exceeded number of deliveries") {
        val goodEntry                          = new PendingEntry(new StreamMessageId(412323), config.consumerName.value, 1, 0)
        val otherConsumerExceededIdleTimeEntry = new PendingEntry(
          new StreamMessageId(65456345),
          "other-consumer",
          0,
          config.claiming.maxNoOfDeliveries + 1L
        )
        val sameConsumerExceededIdleTimeEntry  = new PendingEntry(
          new StreamMessageId(546123213),
          config.consumerName.value,
          0,
          config.claiming.maxNoOfDeliveries + 1L
        )

        val redisStreamMock =
          NotificationsRedisStreamMock.ListPending(
            equalTo(config.groupName, 100),
            value(Chunk(goodEntry, otherConsumerExceededIdleTimeEntry, sameConsumerExceededIdleTimeEntry))
          ) ++
            NotificationsRedisStreamMock.Ack(
              equalTo(
                config.groupName,
                NonEmptyChunk(otherConsumerExceededIdleTimeEntry.getId, sameConsumerExceededIdleTimeEntry.getId)
              ),
              value(2)
            )

        val collector = RedisStaleEventsCollector.executeFor[StreamInstance.Notifications, StreamConsumerConfig]()

        (for
          forked <- collector.fork
          _      <- TestClock.adjust(config.claiming.initialDelay)
          _      <- forked.interrupt
        yield assertCompletes)
          .provideLayer(testEnv(redisStreamMock))
      },
      test("can keep repeating the acknowledge process") {
        val goodEntry                          = new PendingEntry(new StreamMessageId(412323), config.consumerName.value, 1, 0)
        val otherConsumerExceededIdleTimeEntry = new PendingEntry(
          new StreamMessageId(65456345),
          "other-consumer",
          0,
          config.claiming.maxNoOfDeliveries + 1L
        )
        val sameConsumerExceededIdleTimeEntry  = new PendingEntry(
          new StreamMessageId(546123213),
          config.consumerName.value,
          0,
          config.claiming.maxNoOfDeliveries + 1L
        )

        val redisStreamMock =
          NotificationsRedisStreamMock
            .ListPending(
              equalTo(config.groupName, 100),
              value(Chunk(goodEntry, otherConsumerExceededIdleTimeEntry, sameConsumerExceededIdleTimeEntry))
            ) ++
            NotificationsRedisStreamMock
              .Ack(
                equalTo(
                  config.groupName,
                  NonEmptyChunk(otherConsumerExceededIdleTimeEntry.getId, sameConsumerExceededIdleTimeEntry.getId)
                ),
                value(1)
              ) ++
            NotificationsRedisStreamMock
              .ListPending(
                equalTo(config.groupName, 100),
                value(Chunk(goodEntry, otherConsumerExceededIdleTimeEntry))
              ) ++
            NotificationsRedisStreamMock
              .Ack(equalTo(config.groupName, NonEmptyChunk(otherConsumerExceededIdleTimeEntry.getId)), value(1))

        val collector = RedisStaleEventsCollector
          .executeFor[StreamInstance.Notifications, StreamConsumerConfig](Some(Schedule.once))

        (for
          forked <- collector.fork
          _      <- TestClock.adjust(config.claiming.initialDelay)
          _      <- forked.join
        yield assertCompletes)
          .provideLayer(testEnv(redisStreamMock))
      }
    ) @@ TestAspect.timeout(30.seconds)

  private def testEnv(redisStreamMock: ULayer[RedisStream[StreamInstance.Notifications]]) =
    (Runtime.removeDefaultLoggers ++ ZLayer.succeed(config)) ++ redisStreamMock

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
