# WhatsApp Performance and Reliability

Status: FROZEN FOR ROUND 1

## Principles

- no Meta provider call on ordinary Inbox/Connections render;
- no provider call inside a long DB transaction;
- webhook HTTP path performs bounded verification + durable insert only;
- all hot lookups indexed;
- no unbounded raw provider payload retention;
- no N+1 provider queries.

## Webhook SLO target

Application processing target before response:
- normal signed callback p50 < 50ms
- p95 < 200ms under healthy DB
- hard internal target < 1s

Meta/provider retry policy is external; UnifyDesk should acknowledge well before typical provider timeout.

## Webhook flow complexity

For one HTTP callback with m provider effects:
- HMAC: O(body bytes)
- parse: O(body bytes)
- normalized extraction: O(m)
- inserts: O(m log n) from unique indexes

Bound m and body bytes by configuration.

Do not synchronously:
- download media;
- call Graph for message details;
- enumerate company members;
- send notifications;
- run template sync.

## DB hot indexes

Required:
- SenderIdentity external phone lookup (B1/L1 add index/constraint if current repo lacks external_id hot index)
- meta_whatsapp_connections(selected_phone_number_id)
- webhook_events(event_key unique)
- webhook_events(next_attempt_at,id partial)
- message_runtime(connection_id,provider_message_id unique)
- template_cache(connection_id,status)
- customer_sessions(connection_id,customer_key)
- existing inbox message unique(connection_id,external_message_id)
- existing inbox conversation unique(connection_id,external_conversation_id)

## Worker claims

Use small batches with SKIP LOCKED/claim lease patterns already used elsewhere in repository.

Suggested:
- webhook worker batch 100
- claim lease 60s
- exponential retry with jitter
- dead after configurable bounded attempts, e.g. 10

The exact scheduler frequency may be 1-5s; it must not require one scheduler row per company.

## Ordering

Kafka/topic ordering is not trusted for correctness.
Correctness is in DB idempotency + provider timestamp monotonic transitions.

## Inbox persistence

For a callback containing multiple messages/statuses:
- batch-resolve provider message IDs where practical;
- batch-fetch conversations/messages to avoid one query per item;
- flush once per batch;
- recompute unread count only for touched conversations.

Follow Gmail/Microsoft persistence patterns but remove email-specific semantics.

## Health reconciliation cost

Connections page reads local cached health O(1).

Background reconciliation:
- only states due at next_reconcile_at;
- bounded global batch;
- do not reconcile every ACTIVE connection every minute;
- healthy target cadence ~15m-60m depending provider limits;
- degraded/reauth can back off more aggressively;
- manual refresh rate-limited per connection/user.

## Template sync

Do not call Meta per composer render.
- local template cache;
- explicit sync;
- periodic optional sync later;
- send-time stale cache triggers one bounded refresh/fail-closed.

## Media

Inbound media download is worker-only.
- stream/download with byte cap where storage adapter allows;
- no unbounded byte[] for provider media larger than allowed local max;
- current StorageProvider readObject is byte[] oriented; B2 must not introduce a path that can fetch arbitrary huge objects.
- if streaming support is required for Meta max sizes, add focused adapter, not a second storage platform.

## Space growth

Webhook event storage:
- signed normalized payload only;
- purge processed rows after ~7d;
- dead metadata ~30d;
- metrics retained separately if needed.

Template cache:
- bounded by provider templates/connection;
- upsert, no append history per sync.

Customer sessions:
- one row per active customer/connection;
- retention cleanup after a configurable inactive horizon (e.g. 90d) if no Inbox requirement needs it.

Runtime message rows:
- one per WhatsApp message;
- retention should align with Inbox message retention; delete/cascade via L1 V78 if appropriate.

## Rate / provider limits

Meta throughput/quality limits vary by account/phone and provider state. Round 1 does not hardcode a single platform TPS as truth.

B2:
- honors provider 429/Retry-After where available;
- maintains a per-phone distributed limiter in Redis with conservative configurable rate;
- can lower rate on quality/throttling errors;
- never loops immediate retry.

Suggested initial application guard is configurable and intentionally below provider-advertised capacity until real-account load tests confirm safe throughput.

## Failure behavior

DB unavailable:
- webhook cannot durably accept -> return 5xx so provider retries.

Kafka/realtime unavailable:
- durable Inbox/event commit succeeds;
- realtime recovered by client snapshot/focus;
- Kafka publish retry/operational metric independent.

Redis unavailable:
- signup cannot safely mint state -> fail connect action;
- outbound limiter should fail closed or use a very conservative local path; architecture preference is fail closed for provider protection.

Meta unavailable:
- connection health becomes DEGRADED, not immediately DISCONNECTED;
- outbound definite pre-submit failures retry only under explicit policy;
- ambiguous send becomes UNKNOWN.

## Required load tests

QA/SEC or integration:
- 100 duplicate webhook deliveries -> one Inbox message
- concurrent status updates -> monotonic final state
- callback at configured max body
- webhook burst across multiple phone IDs
- two workers claiming same due queue
- Redis limiter across two backend instances if environment permits
- template list from local DB causes zero Graph calls
- Connections page causes zero Graph calls unless manual refresh
