package io.kensu.redis_streams_zio.redis.streams

import java.time.Instant

import io.kensu.redis_streams_zio.common.Scheduling
import io.kensu.redis_streams_zio.config.StreamConsumerConfig
import io.kensu.redis_streams_zio.redis.streams.RedisStream.{ CreateGroupStrategy, ListGroupStrategy, RedisStream }
import org.redisson.api.StreamMessageId
import zio._
import zio.clock._
import zio.duration.Duration
import zio.logging._
import zio.stream.{ Stream, ZStream }

object RedisZStream {

  type StreamInput[S <: StreamInstance, C <: StreamConsumerConfig] =
    ZStream[RedisStream[S] with Has[C] with Logging with Clock, Throwable, ReadGroupResult]
  type StreamOutput[R, S <: StreamInstance, C <: StreamConsumerConfig] =
    ZStream[R with RedisStream[S] with Has[C] with Logging with Clock, Throwable, Option[StreamMessageId]]

  def executeFor[R, A, S <: StreamInstance: Tag, C <: StreamConsumerConfig: Tag](
    shutdownHook: Promise[Throwable, Unit],
    eventsProcessor: StreamInput[S, C] => StreamOutput[R, S, C],
    repeatStrategy: Schedule[R, Any, Unit] = Schedule.forever.unit
  ): ZIO[R with RedisStream[S] with Has[C] with Logging with Clock, Throwable, Long] =
    ZIO.service[C].flatMap { config =>
      (for {
        streamStatus <- initialStreamStatus
        _            <- assureStreamGroup
        stream = asStream(getEvents(streamStatus))
          .via(eventsProcessor)
          .mapM(acknowledge[S, C]) //Fails to compile without explicit types
          .repeat(repeatStrategy)
          .haltWhen(shutdownHook)
        result <- stream.runSum

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
    ZIO.service[C].zip(ZIO.service[RedisStream.Service[S]]).flatMap {
      case (config, redisStream) =>
        val groupName = config.groupName
        for {
          _      <- log.info(s"Looking for Redis group $groupName")
          exists <- redisStream.listGroups.map(_.exists(_.getName == groupName.value))
          res <- if (exists) log.info(s"Redis consumer group $groupName already created")
                else {
                  log.info(s"Creating Redis consumer group $groupName") *>
                    redisStream.createGroup(groupName, CreateGroupStrategy.Newest)
                }
        } yield res
    }

  private def getEvents[R, S <: StreamInstance: Tag, C <: StreamConsumerConfig: Tag](
    streamStatus: Ref[StreamSourceStatus]
  ) =
    ZIO.service[C].zip(ZIO.service[RedisStream.Service[S]]).flatMap {
      case (config, redisStream) =>
        val group    = config.groupName
        val consumer = config.consumerName

        def loadEvents(checkPending: Boolean, checkedMessages: Set[StreamMessageId]) = {
          val (logMsgType, msgType) =
            if (checkPending) "pending" -> ListGroupStrategy.Pending
            else "new" -> ListGroupStrategy.New
          val commonLogMsg = s"$logMsgType events for group $group and consumer $consumer"

          log.debug(s"Attempt to check $commonLogMsg") *>
            redisStream
              .readGroup(group, consumer, 10, Duration.fromScala(config.readTimeout), msgType)
              .map { events =>
                //Process PENDING messages only once in case they keep failing
                if (checkPending) {
                  events.filterNot(e => checkedMessages.contains(e.messageId))
                } else events
              }
              .tap(result => log.info(s"Got ${result.size} $commonLogMsg").when(result.nonEmpty))
              .tapCause(c => log.error(s"Failed to consume $commonLogMsg", c))
        }

        def shouldCheckPending(status: StreamSourceStatus, currentInstant: Instant) =
          status.keepPending || status.lastPendingAttempt
            .plusSeconds(config.checkPendingEvery.toSeconds)
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

  private def asStream[R, E, A](messages: ZIO[R, E, Chunk[A]]): ZStream[R, E, A] =
    ZStream
      .fromEffect(messages)
      .flatMap(events => Stream.fromChunk(events))

  private def acknowledge[S <: StreamInstance: Tag, C <: StreamConsumerConfig: Tag](msgId: Option[StreamMessageId]) =
    ZIO.service[C].zip(ZIO.service[RedisStream.Service[S]]).flatMap {
      case (config, redisStream) =>
        val commonLogMsg = s"for group ${config.groupName} and consumer ${config.consumerName}"
        msgId match {
          case Some(redisId) =>
            log.debug(s"Attempt to acknowledge Redis event $redisId $commonLogMsg") *>
              redisStream
                .ack(config.groupName, NonEmptyChunk.single(redisId))
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
