package io.kensu.redis_streams_zio.specs.mocks

import io.kensu.redis_streams_zio.redis.streams.StreamInstance
import io.kensu.redis_streams_zio.services.producers.EventProducer
import zio.test.mock.mockable

@mockable[EventProducer.Service[StreamInstance.Notifications.type]]()
object EventProducerMock
