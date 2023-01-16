package io.kensu.redis_streams_zio.redis

import io.kensu.redis_streams_zio.config.{RedisConfig, StreamName}
import org.redisson.Redisson
import org.redisson.api.{RStream, RedissonClient}
import org.redisson.client.codec.ByteArrayCodec
import org.redisson.config.Config
import zio.*
import zio.config.getConfig

object RedisClient:

  val live: ZLayer[RedisConfig, Throwable, RedissonClient] =
    ZLayer.scoped {
      ZIO.acquireRelease(
        getConfig[RedisConfig].map { config =>
          val redissonConfig = new Config()
          redissonConfig.setCodec(new ByteArrayCodec())
          redissonConfig
            .useSingleServer()
            .setAddress(config.url)
            .setPassword(config.password)
          Redisson.create(redissonConfig);
        }
      )(c => ZIO.logInfo("Shutting down Redisson") *> ZIO.attempt(c.shutdown()).orDie)
    }

  def getStream[K, V](name: StreamName): ZIO[RedissonClient, Nothing, RStream[K, V]] =
    ZIO.serviceWith[RedissonClient](_.getStream[K, V](name.value))
