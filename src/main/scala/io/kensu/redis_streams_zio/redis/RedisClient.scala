package io.kensu.redis_streams_zio.redis

import io.kensu.redis_streams_zio.config.{RedisConfig, StreamName}
import org.redisson.Redisson
import org.redisson.api.{RStream, RedissonClient}
import org.redisson.client.codec.ByteArrayCodec
import org.redisson.config.Config
import zio._
import zio.config.getConfig

object RedisClient {

  type RedisClient = Has[RedissonClient]

  val live: ZLayer[Has[RedisConfig], Throwable, RedisClient] =
    ZLayer.fromManaged {
      ZManaged.make(
        getConfig[RedisConfig].map { config =>
          val redissonConfig = new Config()
          redissonConfig.setCodec(new ByteArrayCodec())
          redissonConfig
            .useSingleServer()
            .setAddress(config.url)
            .setPassword(config.password)
          Redisson.create(redissonConfig);
        }
      )(c => ZIO.effect(c.shutdown()).orDie)
    }

  def getStream[K, V](name: StreamName): ZIO[RedisClient, Nothing, RStream[K, V]] =
    ZIO.service[RedissonClient].flatMap(client => UIO(client.getStream[K, V](name.value)))
}
