package io.kensu.redis_streams_zio.redis.streams

import java.time.Instant

import io.kensu.redis_streams_zio.common.Scheduling
import io.kensu.redis_streams_zio.config.StreamConsumerConfig
import io.kensu.redis_streams_zio.redis.streams.RedisStream.{ CreateGroupStrategy, ListGroupStrategy, RedisStream }
import org.redisson.api.StreamMessageId
import zio._
import zio.clock._
import zio.config.{ getConfig, ZConfig }
import zio.logging._
import zio.stream.ZStream

object RedisZStream {

  type StreamInput[S <: StreamInstance, C <: StreamConsumerConfig] =
    ZStream[RedisStream[S] with ZConfig[C] with Logging with Clock, Throwable, ReadGroupResult]
  type StreamOutput[R, S <: StreamInstance, C <: StreamConsumerConfig] =
    ZStream[R with RedisStream[S] with ZConfig[C] with Logging with Clock, Throwable, Option[StreamMessageId]]

  def executeFor[R, S <: StreamInstance: Tag, C <: StreamConsumerConfig: Tag](
    shutdownHook: Promise[Throwable, Unit],
    eventsProcessor: StreamInput[S, C] => StreamOutput[R, S, C],
    repeatStrategy: Schedule[R, Any, Unit] = Schedule.forever.unit
  ): ZIO[R with RedisStream[S] with ZConfig[C] with Logging with Clock, Throwable, Long] =
    ZIO.service[C].flatMap { config =>
      def setupStream(status: Ref[StreamSourceStatus]) =
        ZStream
          .fromEffect(getEvents(status))
          .flattenChunks
          .via(eventsProcessor)
          .mapM(acknowledge[S, C])
          .repeat(repeatStrategy)
          .haltWhen(shutdownHook)

      (for {
        streamStatus <- initialStreamStatus
        _            <- assureStreamGroup
        result       <- setupStream(streamStatus).runSum
      } yield result)
        .tapCause(t => log.error(s"Failed processing ${config.streamName} stream, will be retried", t))
        .retry(Scheduling.exponentialBackoff(config.retry.min, config.retry.max, config.retry.factor))
    }

  private val initialStreamStatus =
    clock.instant
      .flatMap(t =>
        Ref.make(
          StreamSourceStatus(
            lastPendingAttempt = t,
            keepPending        = true,
            checkedMessages    = Set.empty
          )
        )
      )

  private def assureStreamGroup[S <: StreamInstance: Tag, C <: StreamConsumerConfig: Tag] =
    getConfig[C].flatMap { config =>
      val groupName = config.groupName
      for {
        _      <- log.info(s"Looking for Redis group $groupName")
        exists <- RedisStream.listGroups[S].map(_.exists(_.getName == groupName.value))
        res <- if (exists) log.info(s"Redis consumer group $groupName already created")
              else {
                log.info(s"Creating Redis consumer group $groupName") *>
                  RedisStream.createGroup[S](groupName, CreateGroupStrategy.Newest)
              }
      } yield res
    }

  private def getEvents[R, S <: StreamInstance: Tag, C <: StreamConsumerConfig: Tag](
    streamStatus: Ref[StreamSourceStatus]
  ) =
    getConfig[C].flatMap { config =>
      val group    = config.groupName
      val consumer = config.consumerName

      def loadEvents(checkPending: Boolean, checkedMessages: Set[StreamMessageId]) = {
        val (logMsgType, msgType) =
          if (checkPending) "pending" -> ListGroupStrategy.Pending
          else "new" -> ListGroupStrategy.New
        val commonLogMsg = s"$logMsgType events for group $group and consumer $consumer"

        log.debug(s"Attempt to check $commonLogMsg") *>
          RedisStream
            .readGroup[S](group, consumer, 10, config.readTimeout, msgType)
            .map { events =>
              if (checkPending) events.filterNot(e => checkedMessages.contains(e.messageId))
              else events
            }
            .tap(result => log.info(s"Got ${result.size} $commonLogMsg").when(result.nonEmpty))
            .tapCause(c => log.error(s"Failed to consume $commonLogMsg", c))
      }

      def shouldCheckPending(status: StreamSourceStatus, currentInstant: Instant) =
        status.keepPending || status.lastPendingAttempt
          .plusMillis(config.checkPendingEvery.toMillis)
          .isBefore(currentInstant)

      for {
        now    <- clock.instant
        status <- streamStatus.get
        checkPending = shouldCheckPending(status, now)
        events <- loadEvents(checkPending, status.checkedMessages)
        _ <- streamStatus.update(st =>
              if (checkPending)
                st.copy(
                  lastPendingAttempt = now,
                  keepPending        = events.nonEmpty,
                  checkedMessages    = events.map(_.messageId).toSet
                )
              else st.copy(keepPending = false, checkedMessages = Set.empty)
            )
      } yield events
    }

  private def acknowledge[S <: StreamInstance: Tag, C <: StreamConsumerConfig: Tag](msgId: Option[StreamMessageId]) =
    getConfig[C].flatMap { config =>
      val commonLogMsg = s"for group ${config.groupName} and consumer ${config.consumerName}"
      msgId match {
        case Some(redisId) =>
          log.debug(s"Attempt to acknowledge Redis event $redisId $commonLogMsg") *>
            RedisStream
              .ack[S](config.groupName, NonEmptyChunk.single(redisId))
              .tapBoth(
                t => log.throwable(s"Failed to acknowledge Redis event $redisId $commonLogMsg", t),
                _ => log.info(s"Successfully acknowledged Redis event $redisId $commonLogMsg")
              )
        case None => UIO(0L)
      }
    }

  private[this] case class StreamSourceStatus(
    lastPendingAttempt: Instant,
    keepPending: Boolean,
    checkedMessages: Set[StreamMessageId]
  )
}
