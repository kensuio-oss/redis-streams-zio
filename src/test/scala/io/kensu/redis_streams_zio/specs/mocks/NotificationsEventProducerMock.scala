package io.kensu.redis_streams_zio.specs.mocks

import io.kensu.redis_streams_zio.config.StreamKey
import io.kensu.redis_streams_zio.redis.streams.StreamInstance
import io.kensu.redis_streams_zio.services.producers.{EventProducer, EventSerializable, PublishedEventId}
import zio.{Tag, Task, URLayer, ZLayer}
import zio.test.mock.*

// TODO not used? remove?
object NotificationsEventProducerMock extends Mock[EventProducer[StreamInstance.Notifications]]:

  object Publish extends Poly.Effect.Input[Throwable, PublishedEventId]

  override val compose: URLayer[Proxy, EventProducer[StreamInstance.Notifications]] =
    ZLayer.fromServiceM { proxy =>
      withRuntime.map { rts =>
        new EventProducer[StreamInstance.Notifications] {
          override def publish[E: EventSerializable: Tag](streamKey: StreamKey, event: E): Task[PublishedEventId] =
            proxy(Publish.of[(StreamKey, E)], streamKey, event)
        }
      }
    }
