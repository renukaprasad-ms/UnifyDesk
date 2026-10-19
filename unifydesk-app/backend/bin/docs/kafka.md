# Kafka setup

The backend uses Apache Kafka for durable asynchronous work queues. Jobs are split by priority so urgent work is isolated from bulk work:

- `messaging.jobs.critical` for OTP and password reset work.
- `messaging.jobs.high` for user-facing notification work.
- `messaging.jobs.medium` for normal async domain work.
- `messaging.jobs.low` for cleanup, analytics, and non-urgent jobs.

Each priority has its own topic, retry topic, DLT topic, consumer group, concurrency, partition count, and max poll size. The values are configured through `.env`, mapped in `docker-compose.yml`, and bound under `app.kafka.*` in `application.yaml`.

## Runtime behavior

- Producers publish a `KafkaEvent` envelope with event id, type, version, priority, company id, user id, correlation id, causation id, timestamp, and payload.
- Message keys are stable: company plus user when available, then user, company, correlation id, and finally event id. This preserves ordering for related work.
- Producer config uses `acks=all`, idempotence, bounded in-flight requests, batching, linger, delivery timeout, and compression.
- Consumers use manual acknowledgments. Offsets are acknowledged only after successful dispatch and Redis idempotency marking.
- Redis idempotency stores processed event ids with a TTL to avoid duplicate side effects after retries or redelivery.
- Retryable failures use bounded exponential retry and then publish to the matching DLT. Non-retryable failures go straight to DLT.
- Logs include event id, event type, priority, topic, partition, offset, group, company id, correlation id, and processing duration. Payloads are not logged.

## Local Docker commands

Start local Kafka:

```bash
docker compose up -d kafka
```

Start the backend so Spring creates the configured topics:

```bash
docker compose up -d backend
```

List topics:

```bash
docker exec messaging-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9002 --list
```

Inspect a topic:

```bash
docker exec messaging-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9002 --describe --topic messaging.jobs.critical
```

Read a DLT:

```bash
docker exec messaging-kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka:9002 --topic messaging.jobs.critical.dlt --from-beginning
```

Check consumer lag:

```bash
docker exec messaging-kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server kafka:9002 --describe --group messaging-critical-workers
```

## Production notes

- Use replication factor `3` and min in-sync replicas `2` in multi-broker environments.
- Keep `CRITICAL` and `HIGH` on dedicated consumer groups and worker pools so bulk jobs cannot starve OTP or notification work.
- Add concrete `KafkaEventHandler` implementations per event type. Handlers should be small, idempotent, and delegate to the correct domain service.
- Use the transactional outbox pattern before publishing events from database writes that must be atomically captured.
