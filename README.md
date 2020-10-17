# redis-streams-zio

Redis Stream consumer implementation with ZIO-Streams.

Prerequisites

Run Redis and Redis Insight docker containers.

```bash
docker-compose -f docker/docker-compose.yaml up
```

### Redis Insight

1. Head to http://localhost:8002
1. Add a new instance:
   - host: `host.docker.internal`
   - port: `6377`
   - pass: `supersecret`  
