package io.kensu.redis_streams_zio.redis.streams.dto

import org.redisson.api.StreamMessageId

sealed trait Event {
  val streamMessageId: StreamMessageId
}

final case class IncorrectEvent(
  streamMessageId: StreamMessageId
) extends Event

final case class NotificationAddedEvent(
  streamMessageId: StreamMessageId,
  payload: String
) extends Event
