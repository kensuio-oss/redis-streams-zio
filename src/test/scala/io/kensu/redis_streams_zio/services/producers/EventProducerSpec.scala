package io.kensu.redis_streams_zio.services.producers

import java.util.concurrent.TimeUnit

import io.kensu.redis_streams_zio.config.{ StreamKey, StreamName }
import io.kensu.redis_streams_zio.specs.adapters.{ RStreamByteArrayAdapter, RedissonClientAdapter }
import io.kensu.redis_streams_zio.specs.zio.CustomAssertions
import org.redisson.api.{ RFuture, RStream, RedissonClient, StreamMessageId }
import org.redisson.misc.RedissonPromise
import zio.ZLayer
import zio.clock._
import zio.duration._
import zio.logging.Logging
import zio.test.Assertion._
import zio.test.Eql.eqlReflexive
import zio.test._
import zio.test.environment.{ TestClock, TestEnvironment }

object EventProducerSpec extends DefaultRunnableSpec with CustomAssertions {

  import TestData._

  private def testEnv(mockRedissonClient: RedissonClient) =
    ZLayer.succeed(mockRedissonClient) ++ ZLayer.identity[Clock] ++ Logging.ignore >>> EventProducer.redis

  override def spec: ZSpec[TestEnvironment, Failure] =
    suite("EventProducer.redis")(
      suite("publish")(
        testM("fail if cannot send an event") {
          val mockRedissonClient: RedissonClient = new RedissonClientAdapter() {
            override def getStream[K, V](name: String): RStream[K, V] =
              new RStreamByteArrayAdapter[K, V]() {
                override def addAsync(key: K, value: V): RFuture[StreamMessageId] =
                  RedissonPromise.newFailedFuture(new RuntimeException("BOOM"))
              }
          }

          (for {
            timeBefore <- currentTime(TimeUnit.SECONDS)
            forked     <- EventProducer.publish(testStreamName, testStreamKey, testEvent).run.fork
            _          <- TestClock.adjust(21.seconds) //3 retries for 3 sec exponential * 2
            msg        <- forked.join
            timeAfter  <- currentTime(TimeUnit.SECONDS)
          } yield {
            assert(msg)(fails(isSubtype[RuntimeException](anything))) &&
            assert(timeAfter - timeBefore)(isGreaterThanEqualTo(21L))
          }).provideSomeLayer[TestEnvironment](testEnv(mockRedissonClient))
        },
        testM("succeed if can send an event") {
          val mockRedissonClient: RedissonClient = new RedissonClientAdapter() {
            override def getStream[K, V](name: String): RStream[K, V] = new RStreamByteArrayAdapter[K, V]() {
              override def addAsync(key: K, value: V): RFuture[StreamMessageId] = {
                val k = key.asInstanceOf[Array[Byte]]
                val v = value.asInstanceOf[Array[Byte]]
                assert(new String(k, "UTF-8"))(equalTo("test")) &&
                assert(v)(equalTo(TestData.testEvent.asBytes))

                RedissonPromise.newSucceededFuture(new StreamMessageId(123L, 456L))
              }
            }
          }

          EventProducer
            .publish(testStreamName, testStreamKey, testEvent)
            .map(createdMsgId => assert(createdMsgId)(equalTo(PublishedEventId("123-456"))))
            .provideCustomLayer(testEnv(mockRedissonClient))
        }
      )
    )

  private object TestData {

    val testStreamName = StreamName("test-stream")
    val testStreamKey  = StreamKey("create")
    val testEvent      = TestEvent("Important delivery!")
  }

  final case class TestEvent(msg: String) {
    lazy val asBytes = msg.getBytes("UTF-8")
  }
  object TestEvent {
    implicit val eventSerializable: EventSerializable[TestEvent] = new EventSerializable[TestEvent] {
      override def serialize(e: TestEvent): Array[Byte] = e.asBytes
    }
  }
}
