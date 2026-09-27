# Notification Performance and Scale Plan

Status: frozen Round-1 performance requirements

## 1. Performance objectives

The system is designed so high notification history volume does not make normal workspace rendering progressively slower.

Hot operations:

- header unread count;
- first notification page;
- mark read/unread;
- event recording;
- coalesced materialization;
- fanout chunk;
- due delivery claim.

All have indexes/constant-sized queries independent of total retained history where practical.

## 2. Complexity summary

| Operation | Time | Working memory | Notes |
|---|---:|---:|---|
| record direct source event | O(log E) | O(1) | unique index insert |
| header unread count | O(log C), effectively O(1) rows | O(1) | 1–2 counter rows |
| list first/page | O(log N + pageSize) | O(pageSize) | seek index |
| mark one read/unread | O(log N) | O(1) | row + counter |
| mark all read | O(K) updated unread rows | O(batch) | can batch internally |
| direct materialize | O(log N) | O(1) | receipt/coalesce indexes |
| company fanout | O(members) total | O(batchSize) | 500 default |
| due worker claim | O(log Q + batch) | O(batch) | partial due index |
| digest aggregation | O(due intents) | O(batch) | no all-user scan |
| cleanup | O(expired rows) | O(batch) | keyset/indexed |

## 3. Event recording latency

Source business transaction must not:

- enumerate recipients;
- call Kafka synchronously as correctness boundary;
- call SMTP/push;
- render a digest.

It performs one validated `notification_events` insert with bounded JSON.

Expected complexity is one indexed unique insert.

## 4. Unread count

Forbidden normal implementation:

`select count(*) from user_notifications where read_at is null ...` on every header render.

Required:

- fetch ACCOUNT counter;
- optionally fetch selected COMPANY counter;
- sum.

Counter write amplification is intentionally accepted because reads are extremely frequent and read-state writes are comparatively less frequent.

Counter reconciliation is asynchronous diagnostics/recovery, not the normal count path.

## 5. Pagination

Use keyset:

```text
WHERE user_id=?
  AND visible scope predicate
  AND (
    last_event_at < :cursorTime
    OR (last_event_at=:cursorTime AND id < :cursorId)
  )
ORDER BY last_event_at DESC,id DESC
LIMIT :limit+1
```

No arbitrary page-number/deep OFFSET API.

Cursor includes filter version so a cursor from `unreadOnly=true` cannot accidentally be reused under a semantically different filter without rejection.

## 6. Coalescing economics

Inbox fanout can dominate volume.

Without coalescing:

```text
visible writes ~= inbound messages × eligible members
```

With 5-minute per-connection buckets:

```text
visible rows ~= active 5-minute buckets × eligible members
```

Event receipts still retain correctness per source event, but compact receipts are materially smaller than complete rendered notification rows and allow dedupe.

Future optimization trigger: if receipt fanout itself becomes dominant for very large companies, design audience-level cursors/fan-out-on-read; do not prematurely add that complexity in Round 1.

## 7. Fanout fairness

One massive company must not starve direct notifications.

Recommendations:

- Kafka handler only creates/awakens fanout job;
- fanout worker processes fixed chunks;
- scheduler orders due jobs then yields between chunks;
- cap per-job consecutive chunks;
- separate direct materialization path from company fanout worker threads;
- measure oldest fanout lag by priority.

Critical direct events should not queue behind large low-value company activity.

## 8. Database locking

Use `FOR UPDATE SKIP LOCKED` only for queue/claim tables.

Do not use it for user-visible consistent reads.

Provider calls occur after commit.

Optimistic/conditional completion uses claim token to prevent stale worker overwrite.

Coalesced notification update may contend on one hot user/group/bucket row. Keep transaction short; unique index plus insert-then-update/upsert semantics handle race.

## 9. Unread counter concurrency

Counter mutation must be tied to actual row state transition.

Examples:

- two concurrent mark-read calls: only one `read_at null -> non-null` transition decrements;
- two duplicate materializations: recipient receipt allows one increment;
- coalesced new event reopens an already-read bucket: only successful `read_at non-null -> null` transition increments.

Never “read counter, calculate in Java, save” without row-level atomicity.

Use SQL increment/decrement with guarded state transitions and `greatest(unread_count-1,0)` only as defense; correctness tests must prove no hidden drift.

## 10. Worker batch defaults

Initial defaults, configuration-driven:

- event publish claim: 100;
- fanout recipients/chunk: 500;
- delivery intent claim: 100;
- digest contribution aggregation: 250;
- push attempt claim: 100;
- retention cleanup delete: 1000;
- seen API max: 100;
- notification page max: 50.

Tune from metrics, not assumptions.

## 11. Kafka partitioning

Reuse existing priority topics.

Keying:

- user-specific => company:user key;
- company fanout => company key.

Do not add a notification-specific Kafka topic in Round 1 unless measured contention/retention requirements prove necessary. Existing priority topics already provide partitions, retries and DLT.

Monitor notification share so notification work cannot unexpectedly dominate generic worker capacity. If it does, the future change is a dedicated topic/consumer group while preserving the same event contract.

## 12. Redis usage

Redis is suitable for:

- realtime pub/sub;
- optional short cooldown/cache acceleration;
- existing Kafka duplicate hinting.

Do not store authoritative unread counts/read state solely in Redis.

If Redis is unavailable, correctness degrades to DB/realtime-less behavior rather than lost state.

## 13. Push fanout

One user can have up to 10 subscriptions.

Push cost per logical intent is O(active subscriptions), deliberately bounded.

Do not decrypt all subscriptions for all users at once; fetch/claim per intent or bounded batch.

404/410 cleanup reduces wasted sends.

## 14. Email rate limiting

Even a product bug must not become an email storm.

B2 tracks/queries accepted optional mail so it can enforce:

- max 3 optional notification mails/user/rolling 24h default;
- inbox digest minimum 4h cooldown;
- per-worker/platform concurrency bounded by configuration.

Mandatory mail should have its own safety quota/alerting rather than sharing optional caps.

## 15. Scheduled worker cadence

Suggested starting cadence:

- outbox publisher: 500ms–1s;
- fanout: 500ms–1s;
- delivery intent: 1s;
- digest aggregation/due send: 1m;
- subscription expiry cleanup: 1h;
- counter drift sample/reconciliation: 15m;
- retention cleanup: daily/off-peak with bounded loops.

Use fixed-delay and distributed claim semantics so multiple pods do not require one global scheduler leader.

## 16. Metrics

At minimum:

### Core

- `notification_event_recorded_total{type}`
- `notification_event_duplicate_total{type}`
- `notification_outbox_lag_seconds{priority}`
- `notification_materialization_lag_seconds{type}`
- `notification_fanout_lag_seconds{type}`
- `notification_visible_created_total{type}`
- `notification_coalesced_total{type}`
- `notification_unread_counter_drift_total`

### Delivery

- `notification_delivery_intents_total{channel,mode,state,type}`
- `notification_delivery_lag_seconds{channel,mode}`
- `notification_delivery_attempts_total{channel,outcome}`
- `notification_email_unknown_total`
- `notification_digest_sent_total{type}`
- `notification_digest_suppressed_total{reason}`
- `notification_push_subscriptions{status}`
- `notification_push_attempts_total{outcome,http_status_class}`

### Realtime

- notification realtime publish failures;
- connected sockets already available from realtime module where possible.

No high-cardinality user/company IDs as metric labels.

## 17. SLO-oriented alerts

Suggested operational alerts:

- oldest critical/high outbox > 60s;
- oldest normal materialization > 5m;
- delivery retry queue growing continuously;
- email UNKNOWN > small threshold;
- sudden >10x source event volume by type;
- push 401/403 spike;
- push 404/410 ratio spike;
- unread counter drift nonzero/repeated;
- DEAD intents > 0 for mandatory mail;
- digest lag beyond quiet-hours-adjusted eligibility by >15m.

Thresholds must be tuned to production baseline.

## 18. Logging/tracing

Carry:

- correlationId;
- causationId;
- eventId;
- intentId;
- notificationId;
- attempt number.

Use structured logs and safe error codes. Avoid logging payload bodies/secrets.

## 19. Retention strategy

Cleanup uses indexed cutoff + keyset ID, small transactions.

Order must respect constraints/semantics:

1. terminal attempts/digest items;
2. terminal intents;
3. recipient receipts/source events after retention;
4. user notifications after 180d, adjusting unread counters for any unread row;
5. revoked/expired push subscriptions.

Do not issue one unbounded DELETE across millions of rows.

## 20. Partitioning threshold

Do not partition Round 1.

Revisit monthly range partitioning when production evidence shows one or more:

- sustained tens/hundreds of millions retained rows;
- vacuum/index maintenance materially impacts latency;
- retention deletes dominate IO;
- working indexes no longer fit practical memory budget;
- keyset queries degrade despite correct plans/indexes.

Use `EXPLAIN (ANALYZE, BUFFERS)` before introducing partition complexity.

## 21. Load tests before large rollout

Synthetic tests should cover:

- 1K users × routine volume;
- 100K-user data cardinality in DB benchmark environment;
- one company with 10K members fanout;
- 100 inbound messages/min on one connection with coalescing;
- simultaneous read/read-all/realtime arrivals;
- 100 workers/pods simulated claim concurrency where practical;
- Kafka duplicate storm;
- Redis unavailable;
- SMTP/push transient failure queue.

Acceptance focuses on bounded memory, no duplicate logical effects, stable indexed query plans, and business transaction latency not scaling with recipient count.
