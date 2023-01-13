package io.kensu.redis_streams_zio.redis.streams

import io.kensu.redis_streams_zio.common.Scheduling
import io.kensu.redis_streams_zio.config.StreamConsumerConfig
import org.redisson.api.StreamMessageId
import zio.*
import zio.config.getConfig
import zio.logging.*
import zio.stream.ZStream

import java.time.Instant
import zio.Clock

object RedisConsumer:

  type StreamInput[S <: StreamInstance, C <: StreamConsumerConfig] =
    ZStream[RedisStream[S] & C, Throwable, ReadGroupResult]

  type StreamOutput[R, S <: StreamInstance, C <: StreamConsumerConfig] =
    ZStream[R & RedisStream[S] & C, Throwable, Option[StreamMessageId]]

  def executeFor[R, S <: StreamInstance, C <: StreamConsumerConfig](
    shutdownHook: Promise[Throwable, Unit],
    eventsProcessor: StreamInput[S, C] => StreamOutput[R, S, C],
    repeatStrategy: Schedule[R, Any, Unit] = Schedule.forever.unit
  )(using Tag[RedisStream[S]], Tag[C]): ZIO[R & RedisStream[S] & C, Throwable, Long] =
    ZIO.service[C].flatMap { config =>
      def setupStream(status: Ref.Synchronized[StreamSourceStatus]) =
        ZStream
          .fromZIO(getEvents[Nothing, S, C](status))
          .flattenChunks
          .groupByKey(_.data.isEmpty) {
            case (true, stream)  =>
              stream
                .tap(r => ZIO.logDebug(s"Event ${r.messageId} has no value, will skip processing and acknowledge"))
                .map(r => Some(r.messageId))
            case (false, stream) =>
              stream.viaFunction(eventsProcessor)
          }
          .mapZIO(acknowledge[S, C])
          .repeat(repeatStrategy)
          .haltWhen(shutdownHook)

      (for
        streamStatus <- initialStreamStatus
        _            <- assureStreamGroup
        result       <- setupStream(streamStatus).runSum
      yield result)
        .tapErrorCause(t => ZIO.logErrorCause(s"Failed processing ${config.streamName} stream, will be retried", t))
        .retry(Scheduling.exponentialBackoff(config.retry.min, config.retry.max, config.retry.factor))
    }

  private val initialStreamStatus =
    Clock.instant
      .flatMap(t =>
        Ref.Synchronized.make(
          StreamSourceStatus(
            lastPendingAttempt = t,
            keepPending        = true,
            checkedMessages    = Set.empty
          )
        )
      )

  private def assureStreamGroup[S <: StreamInstance, C <: StreamConsumerConfig](
    using Tag[RedisStream[S]],
    Tag[C]
  ) =
    getConfig[C].flatMap { config =>
      val groupName = config.groupName
      for
        _      <- ZIO.logInfo(s"Looking for Redis group $groupName")
        exists <- RedisStream.listGroups[S].map(_.exists(_.getName == groupName.value))
        res    <- if exists then ZIO.logInfo(s"Redis consumer group $groupName already created")
                  else
                    ZIO.logInfo(s"Creating Redis consumer group $groupName") *>
                      RedisStream.createGroup[S](groupName, CreateGroupStrategy.Newest)
      yield res
    }

  private def getEvents[R, S <: StreamInstance, C <: StreamConsumerConfig](
    streamStatus: Ref.Synchronized[StreamSourceStatus]
  )(using Tag[RedisStream[S]], Tag[C]) =
    getConfig[C].flatMap { config =>
      val group    = config.groupName
      val consumer = config.consumerName

      def loadEvents(checkPending: Boolean, checkedMessages: Set[StreamMessageId]) =
        val (logMsgType, msgType) =
          if checkPending then "pending" -> ListGroupStrategy.Pending
          else "new" -> ListGroupStrategy.New
        val commonLogMsg          = s"$logMsgType events for group $group and consumer $consumer"

        ZIO.logDebug(s"Attempt to check $commonLogMsg") *>
          RedisStream
            .readGroup[S](group, consumer, 10, config.readTimeout, msgType)
            .map { events =>
              if checkPending then events.filterNot(e => checkedMessages.contains(e.messageId))
              else events
            }
            .tap(result => ZIO.logInfo(s"Got ${result.size} $commonLogMsg").when(result.nonEmpty))
            .tapErrorCause(c => ZIO.logErrorCause(s"Failed to consume $commonLogMsg", c))

      def shouldCheckPending(status: StreamSourceStatus, currentInstant: Instant) =
        status.keepPending || status.lastPendingAttempt
          .plusMillis(config.checkPendingEvery.toMillis)
          .isBefore(currentInstant)

      streamStatus.modifyZIO { oldStatus =>
        for {
          now         <- Clock.instant
          checkPending = shouldCheckPending(oldStatus, now)
          events      <- loadEvents(checkPending, oldStatus.checkedMessages)
        } yield {
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
    }

  private def acknowledge[S <: StreamInstance, C <: StreamConsumerConfig](msgId: Option[StreamMessageId])(
    using Tag[RedisStream[S]],
    Tag[C]
  ) =
    getConfig[C].flatMap { config =>
      val commonLogMsg = s"for group ${config.groupName} and consumer ${config.consumerName}"
      msgId match
        case Some(redisId) =>
          ZIO.logDebug(s"Attempt to acknowledge Redis event $redisId $commonLogMsg") *>
            RedisStream
              .ack[S](config.groupName, NonEmptyChunk.single(redisId))
              .tapBoth(
                t => ZIO.logErrorCause(s"Failed to acknowledge Redis event $redisId $commonLogMsg", Cause.fail(t)),
                _ => ZIO.logInfo(s"Successfully acknowledged Redis event $redisId $commonLogMsg")
              )
        case None          => ZIO.succeed(0L)
    }

  private[streams] case class StreamSourceStatus(
    lastPendingAttempt: Instant,
    keepPending: Boolean,
    checkedMessages: Set[StreamMessageId]
  )
