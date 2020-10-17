package io.kensu.redis_streams_zio.redis.streams

import io.kensu.redis_streams_zio.config.StreamName

sealed trait StreamInstance { val name: StreamName }

object StreamInstance {
  final case class Notifications(override val name: StreamName) extends StreamInstance
}
