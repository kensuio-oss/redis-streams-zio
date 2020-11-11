package io.kensu.redis_streams_zio.specs.mocks

import io.kensu.redis_streams_zio.redis.streams.{ RedisStream, StreamInstance }
import zio.test.mock.mockable

@mockable[RedisStream.Service[StreamInstance.Notifications.type]]()
object RedisStreamMock
