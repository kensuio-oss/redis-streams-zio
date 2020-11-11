package io.kensu.redis_streams_zio.redis.streams

import io.kensu.redis_streams_zio.config.StreamName

sealed abstract class StreamInstance(val name: StreamName)

object StreamInstance {
  final case object Notifications extends StreamInstance(StreamName("notifications"))
}
