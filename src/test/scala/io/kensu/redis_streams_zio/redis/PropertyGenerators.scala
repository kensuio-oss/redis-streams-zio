package io.kensu.redis_streams_zio.redis

import io.kensu.redis_streams_zio.config.StreamKey
import io.kensu.redis_streams_zio.redis.streams.{ReadGroupData, ReadGroupResult}
import org.redisson.api.StreamMessageId
import zio.test.Gen.*
import zio.test.Gen
import zio.{Chunk, Promise}
import zio.Random
import zio.test.Sized
import zio.test.Gen.string

object PropertyGenerators:

  val promise: Gen[Any, Promise[Throwable, Unit]]   = fromZIO(Promise.make[Throwable, Unit])
  val streamMessageId: Gen[Random, StreamMessageId] = long(1L, 99999999999L).map(new StreamMessageId(_))

  def redisData(
    streamKey: StreamKey
  ): Gen[Random & Sized, ReadGroupResult] =
    (string <*> streamMessageId).map { (msg, msgId) =>
      ReadGroupResult(msgId, Chunk(ReadGroupData(streamKey, Chunk.fromArray(msg.getBytes("UTF-8")))))
    }

  def uniqueRedisData(streamKey: StreamKey): Gen[Random & Sized, (ReadGroupResult, ReadGroupResult)] =
    (redisData(streamKey) <&> redisData(streamKey)).filter((a, b) => a.messageId != b.messageId)
