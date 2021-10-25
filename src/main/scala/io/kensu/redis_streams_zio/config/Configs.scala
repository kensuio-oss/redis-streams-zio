package io.kensu.redis_streams_zio.config

import zio.{Has, Layer}
//import zio.config.{given, _}
import zio.config._
//import zio.config.TupleConversion.{given, _}
import zio.config.ConfigDescriptor._
import zio.config.magnolia.{descriptor, Descriptor}
import zio.config.typesafe.TypesafeConfig
import zio.config.ReadError
import zio.duration.Duration

final case class RootConfig(
  kensu: AppConfig
)

final case class AppConfig(
  redis: RedisConfig,
  redisStreams: RedisStreamsConfig
)

final case class RedisStreamsConfig(
  consumers: ConsumersConfig,
  producers: ProducersConfig
)

final case class ConsumersConfig(
  notifications: NotificationsStreamConsumerConfig
)

final case class ProducersConfig(
  notifications: NotificationsStreamProducerConfig
)

final case class RedisConfig(
  url: String,
  password: String
)

final case class StreamName(value: String) {
  override def toString: String = value
}

object StreamName {

  implicit val desc: Descriptor[StreamName] =
    Descriptor.from(string.transform(StreamName(_), _.value))
}

final case class StreamGroupName(value: String) {
  override def toString: String = value
}

object StreamGroupName {

  implicit val desc: Descriptor[StreamGroupName] =
//    Descriptor.from(string.to[StreamGroupName])
    Descriptor.from(string.transform(StreamGroupName(_), _.value))
}

final case class StreamConsumerName(value: String) {
  override def toString: String = value
}

object StreamConsumerName {

  implicit val desc: Descriptor[StreamConsumerName] =
//    Descriptor.from(string.to[StreamConsumerName])
    Descriptor.from(string.transform(StreamConsumerName(_), _.value))
}

final case class StreamKey(value: String) {
  override def toString: String = value
}

object StreamKey {

  implicit val desc: Descriptor[StreamKey] =
    Descriptor.from(string.transform(StreamKey(_), _.value))
//    Descriptor.from(string.to[StreamKey])
}

final case class ClaimingConfig(
  initialDelay: Duration,
  repeatEvery: Duration,
  maxNoOfDeliveries: Long,
  maxIdleTime: Duration
)

final case class RetryConfig(
  min: Duration,
  max: Duration,
  factor: Double
)

trait StreamConsumerConfig {
  val claiming: ClaimingConfig
  val retry: RetryConfig
  val readTimeout: Duration
  val checkPendingEvery: Duration
  val streamName: StreamName
  val groupName: StreamGroupName
  val consumerName: StreamConsumerName
}

trait StreamProducerConfig {
  val streamName: StreamName
}

final case class NotificationsStreamConsumerConfig(
  claiming: ClaimingConfig,
  retry: RetryConfig,
  readTimeout: Duration,
  checkPendingEvery: Duration,
  streamName: StreamName,
  addKey: StreamKey,
  groupName: StreamGroupName,
  consumerName: StreamConsumerName
) extends StreamConsumerConfig

final case class NotificationsStreamProducerConfig(
  streamName: StreamName,
  addKey: StreamKey
) extends StreamProducerConfig

object Configs {
  import zio.config.syntax._

  val appConfig: Layer[ReadError[String], Has[AppConfig]] =
    TypesafeConfig.fromDefaultLoader(descriptor[RootConfig]).narrow(_.kensu)
}
