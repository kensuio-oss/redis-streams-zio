# redis-streams-zio

Redis Stream consumer implementation with ZIO-Streams.

Prerequisites

Run Redis and Redis Insight docker containers.

```bash
docker-compose -f docker/docker-compose.yaml up
```

### Redis Insight

You can take a look what is happening inside Redis with this tool.

1. Head to http://localhost:8002
1. Add a new instance:
   - host: `host.docker.internal`
   - port: `6377`
   - pass: `supersecret`  

### Running sample apps

The project is configured to work over `notifications` stream.

## Producer

You can run a sample event producer of notifications with `sbt runMain io.kensu.redis_streams_zio.Producer`.
This will produce a random String event to the `notifications` stream, under `add` key every ~5 seconds.

## Consumer

You can run a sample event producer of notifications with `sbt runMain io.kensu.redis_streams_zio.Consumer`.
This will keep consuming the `notifications` stream from `add` key, ignoring events under different keys with logged info about that.