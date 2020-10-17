package io.kensu.redis_streams_zio.specs.mocks

import io.kensu.redis_streams_zio.config.{ StreamKey, StreamName }
import io.kensu.redis_streams_zio.services.producers.EventProducer.EventProducer
import io.kensu.redis_streams_zio.services.producers.{ EventProducer, EventSerializable, PublishedEventId }
import zio._
import zio.test.mock
import zio.test.mock.Mock

object EventProducerMock extends Mock[EventProducer] {

  object Publish extends Poly.Effect.Input[Throwable, PublishedEventId]

  override val compose: URLayer[Has[mock.Proxy], EventProducer] =
    ZLayer.fromService { proxy =>
      new EventProducer.Service {
        override def publish[E: EventSerializable: Tag](
          streamName: StreamName,
          streamKey: StreamKey,
          event: E
        ): Task[PublishedEventId] =
          proxy(Publish.of[(StreamName, StreamKey, E)], streamName, streamKey, event)
      }
    }

  val empty: ULayer[EventProducer] =
    ZLayer.succeed(new EventProducer.Service {
      override def publish[E: EventSerializable: Tag](
        streamName: StreamName,
        streamKey: StreamKey,
        event: E
      ): Task[PublishedEventId] =
        Task.fail(new UnsupportedOperationException)
    })
}
