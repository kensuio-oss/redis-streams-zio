package io.kensu.redis_streams_zio.specs.mocks

import io.kensu.redis_streams_zio.config.StreamKey
import io.kensu.redis_streams_zio.redis.streams.StreamInstance
import io.kensu.redis_streams_zio.services.producers.{EventProducer, EventSerializable, PublishedEventId}
import zio.{Has, Tag, Task, URLayer, ZLayer}
import zio.test.mock.*

object NotificationsEventProducerMock extends Mock[Has[EventProducer[StreamInstance.Notifications]]]:

  object Publish extends Poly.Effect.Input[Throwable, PublishedEventId]

  override val compose: URLayer[Has[Proxy], Has[EventProducer[StreamInstance.Notifications]]] =
    ZLayer.fromServiceM { proxy =>
      withRuntime.map { rts =>
        new EventProducer[StreamInstance.Notifications] {
          override def publish[E: EventSerializable: Tag](streamKey: StreamKey, event: E): Task[PublishedEventId] =
            proxy(Publish.of[(StreamKey, E)], streamKey, event)
        }
      }
    }
