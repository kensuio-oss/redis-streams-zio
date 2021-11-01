package io.kensu.redis_streams_zio.redis.streams

import java.time.Instant

import io.kensu.redis_streams_zio.common.Scheduling
import io.kensu.redis_streams_zio.config.StreamConsumerConfig
import org.redisson.api.StreamMessageId
import zio.*
import zio.clock.*
import zio.config.getConfig
import zio.logging.*
import zio.stream.ZStream

object RedisConsumer:

  // TODO replace with &
  type StreamInput[S <: StreamInstance, C <: StreamConsumerConfig] =
    ZStream[Has[RedisStream[S]] with Has[C] with Logging with Clock, Throwable, ReadGroupResult]

  type StreamOutput[R, S <: StreamInstance, C <: StreamConsumerConfig] =
    ZStream[R with Has[RedisStream[S]] with Has[C] with Logging with Clock, Throwable, Option[StreamMessageId]]

  def executeFor[R, S <: StreamInstance, C <: StreamConsumerConfig](
    shutdownHook: Promise[Throwable, Unit],
    eventsProcessor: StreamInput[S, C] => StreamOutput[R, S, C],
    repeatStrategy: Schedule[R, Any, Unit] = Schedule.forever.unit
  )(
    implicit ts: Tag[RedisStream[S]],
    tscc: Tag[C]
  ): ZIO[R with Has[RedisStream[S]] with Has[C] with Logging with Clock, Throwable, Long] =
    ZIO.service[C].flatMap { config =>
      def setupStream(status: RefM[StreamSourceStatus]) =
        ZStream
          .fromEffect(getEvents[Nothing, S, C](status))
          .flattenChunks
          .via(eventsProcessor)
          .mapM(acknowledge[S, C])
          .repeat(repeatStrategy)
          .haltWhen(shutdownHook)

      (for
        streamStatus <- initialStreamStatus
        _            <- assureStreamGroup
        result       <- setupStream(streamStatus).runSum
      yield result)
        .tapCause(t => log.error(s"Failed processing ${config.streamName} stream, will be retried", t))
        .retry(Scheduling.exponentialBackoff(config.retry.min, config.retry.max, config.retry.factor))
    }

  private val initialStreamStatus =
    clock.instant
      .flatMap(t =>
        RefM.make(
          StreamSourceStatus(
            lastPendingAttempt = t,
            keepPending        = true,
            checkedMessages    = Set.empty
          )
        )
      )

  private def assureStreamGroup[S <: StreamInstance, C <: StreamConsumerConfig](
    implicit ts: Tag[RedisStream[S]],
    tscc: Tag[C]
  ) =
    getConfig[C].flatMap { config =>
      val groupName = config.groupName
      for
        _      <- log.info(s"Looking for Redis group $groupName")
        exists <- RedisStream.listGroups[S].map(_.exists(_.getName == groupName.value))
        res    <- if exists then log.info(s"Redis consumer group $groupName already created")
                  else
                    log.info(s"Creating Redis consumer group $groupName") *>
                      RedisStream.createGroup[S](groupName, CreateGroupStrategy.Newest)
      yield res
    }

  private def getEvents[R, S <: StreamInstance, C <: StreamConsumerConfig](
    streamStatus: RefM[StreamSourceStatus]
  )(implicit ts: Tag[RedisStream[S]], tscc: Tag[C]) =
    getConfig[C].flatMap { config =>
      val group    = config.groupName
      val consumer = config.consumerName

      def loadEvents(checkPending: Boolean, checkedMessages: Set[StreamMessageId]) =
        val (logMsgType, msgType) =
          if checkPending then "pending" -> ListGroupStrategy.Pending
          else "new" -> ListGroupStrategy.New
        val commonLogMsg          = s"$logMsgType events for group $group and consumer $consumer"

        log.debug(s"Attempt to check $commonLogMsg") *>
          RedisStream
            .readGroup[S](group, consumer, 10, config.readTimeout, msgType)
            .map { events =>
              if checkPending then events.filterNot(e => checkedMessages.contains(e.messageId))
              else events
            }
            .tap(result => log.info(s"Got ${result.size} $commonLogMsg").when(result.nonEmpty))
            .tapCause(c => log.error(s"Failed to consume $commonLogMsg", c))

      def shouldCheckPending(status: StreamSourceStatus, currentInstant: Instant) =
        status.keepPending || status.lastPendingAttempt
          .plusMillis(config.checkPendingEvery.toMillis)
          .isBefore(currentInstant)

      streamStatus.modify { oldStatus =>
        for
          now         <- clock.instant
          checkPending = shouldCheckPending(oldStatus, now)
          events      <- loadEvents(checkPending, oldStatus.checkedMessages)
        yield
          val newStatus =
            if checkPending then
              StreamSourceStatus(
                lastPendingAttempt          = now,
                keepPending                 = events.nonEmpty,
                checkedMessages             = events.map(_.messageId).toSet
              )
            else oldStatus.copy(keepPending = false, checkedMessages = Set.empty)
          events -> newStatus
      }
    }

  private def acknowledge[S <: StreamInstance, C <: StreamConsumerConfig](msgId: Option[StreamMessageId])(
    implicit ts: Tag[RedisStream[S]],
    tscc: Tag[C]
  ) =
    getConfig[C].flatMap { config =>
      val commonLogMsg = s"for group ${config.groupName} and consumer ${config.consumerName}"
      msgId match
        case Some(redisId) =>
          log.debug(s"Attempt to acknowledge Redis event $redisId $commonLogMsg") *>
            RedisStream
              .ack[S](config.groupName, NonEmptyChunk.single(redisId))
              .tapBoth(
                t => log.throwable(s"Failed to acknowledge Redis event $redisId $commonLogMsg", t),
                _ => log.info(s"Successfully acknowledged Redis event $redisId $commonLogMsg")
              )
        case None          => UIO(0L)
    }

  private[this] case class StreamSourceStatus(
    lastPendingAttempt: Instant,
    keepPending: Boolean,
    checkedMessages: Set[StreamMessageId]
  )
