package io.kensu.redis_streams_zio.redis.streams

import io.kensu.redis_streams_zio.common.Scheduling
import io.kensu.redis_streams_zio.config.StreamConsumerConfig
import io.kensu.redis_streams_zio.redis.streams.RedisStream.RedisStream
import org.redisson.api.StreamMessageId
import zio._
import zio.clock.Clock
import zio.config.getConfig
import zio.logging._

object RedisZCollector {

  /**
    * Builds the Redis claiming logic for problematic messages.
    * The logic has to be delayed to not clash with stream processor, so we don't process very old
    * pending messages and also ack them here in the same time.
    */
  def executeFor[S <: StreamInstance: Tag, C <: StreamConsumerConfig: Tag](
    repeatStrategy: Option[Schedule[Any, Any, Unit]] = None
  ): ZIO[RedisStream[S] with Has[C] with Logging with Clock, Throwable, Long] =
    getConfig[C].flatMap { config =>
      getPendingEvents
        .repeat(
          repeatStrategy
            .getOrElse(Schedule.fixed(config.claiming.repeatEvery)) *> Schedule.collectAll
        )
        .delay(config.claiming.initialDelay)
        .tapCause(t => log.error(s"Failed claiming process for ${config.streamName} stream, will be retried", t))
        .retry(Scheduling.exponentialBackoff(config.retry.min, config.retry.max, config.retry.factor))
        .map(_.sum)
    }

  private def getPendingEvents[S <: StreamInstance: Tag, C <: StreamConsumerConfig: Tag] =
    getConfig[C].flatMap { config =>
      val group    = config.groupName
      val consumer = config.consumerName
      log.debug(s"Listing pending messages for group $group and consumer $consumer") *>
        RedisStream.listPending[S](group, 100).flatMap { pendingEntries =>
          val conf = config.claiming
          val (deliveriesExceededMessages, rest) =
            pendingEntries.partition(_.getLastTimeDelivered > conf.maxNoOfDeliveries)
          val messagesToClaim = {
            val all = Chunk.fromIterable(
              rest
                .filter(_.getConsumerName != consumer.value)
                .filter(_.getIdleTime >= conf.maxIdleTime.toMillis)
            )
            if (all.size > 1) all.take(all.size / 2) else all
          }.map(_.getId)
          acknowledge(deliveriesExceededMessages.map(_.getId)) *> claim(messagesToClaim)
        }
    }

  private def acknowledge[S <: StreamInstance: Tag, C <: StreamConsumerConfig: Tag](
    messageIds: Chunk[StreamMessageId]
  ) =
    getConfig[C].flatMap { config =>
      val group        = config.groupName
      val batchSize    = messageIds.size
      val commonLogMsg = s"batch of $batchSize messages for group $group"
      NonEmptyChunk.fromChunk(messageIds) match {
        case None => UIO(0L)
        case Some(ids) =>
          log.debug(s"Attempt to acknowledge $commonLogMsg") *>
            RedisStream
              .ack[S](group, ids)
              .tapBoth(
                t => log.throwable(s"Failed to acknowledge $commonLogMsg", t),
                _ => log.info(s"Successfully acknowledged $commonLogMsg")
              )
      }
    }

  private def claim[S <: StreamInstance: Tag, C <: StreamConsumerConfig: Tag](
    messageIds: Chunk[StreamMessageId]
  ) =
    getConfig[C].flatMap { config =>
      val group        = config.groupName
      val consumer     = config.consumerName
      val batchSize    = messageIds.size
      val commonLogMsg = s"for group $group to consumer $consumer"
      NonEmptyChunk.fromChunk(messageIds) match {
        case None => UIO(0L)
        case Some(ids) =>
          log.debug(s"Attempt to claim batch of $batchSize messages $commonLogMsg $ids") *>
            RedisStream
              .fastClaim[S](group, consumer, config.claiming.maxIdleTime, ids)
              .map(_.size.toLong)
              .tapBoth(
                t => log.throwable(s"Failed to claim $commonLogMsg (maybe an attempt to claim the same resource)", t),
                size => log.info(s"Successfully claimed batch of $size messages $commonLogMsg")
              )
      }
    }
}
