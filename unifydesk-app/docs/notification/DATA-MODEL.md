# Notification Data Model

Status: frozen Round-1 SQL contract  
Database: PostgreSQL / Flyway  
ID rule: Snowflake `bigint` internally; HTTP/realtime IDs serialized as strings.

## 1. Migration ownership

- B1: `V70__notification_core.sql`
- B2: `V71__notification_delivery.sql`
- L1 after integration: `V72__notification_cross_module_constraints.sql`

Workers MUST NOT renumber independently. L1 collision-checks against moving dev before integration.

B2's migration must be independently runnable from the frozen worker base. Therefore V71 MUST NOT declare foreign keys to tables created only by V70. L1 adds safe cross-module constraints in V72 after both branches are integrated.

## 2. V70 — core tables

### 2.1 `notification_events`

Immutable source event + transactional outbox metadata.

| Column | Type | Null | Notes |
|---|---|---:|---|
| id | bigint PK | no | Snowflake |
| event_key | varchar(255) | no | durable source idempotency key |
| event_type | varchar(100) | no | e.g. `company.created` |
| schema_version | integer | no | starts 1 |
| company_id | bigint | yes | null for account scope |
| actor_user_id | bigint | yes | actor if known |
| recipient_mode | varchar(32) | no | `USER`, `COMPANY_PERMISSION` |
| recipient_user_id | bigint | yes | required for USER |
| required_permission_key | varchar(160) | yes | required for permission audience |
| subject_type | varchar(64) | no | closed registry value |
| subject_id | varchar(160) | no | string to allow non-Snowflake future refs |
| priority | varchar(16) | no | CRITICAL/HIGH/MEDIUM/LOW |
| group_key | varchar(180) | yes | trusted server-generated |
| variables_json | jsonb | no | bounded allowlisted render vars |
| correlation_id | varchar(80) | no | tracing |
| causation_id | varchar(80) | yes | tracing |
| occurred_at | timestamptz | no | source event time |
| publish_state | varchar(24) | no | PENDING/CLAIMED/PUBLISHED/DEAD |
| publish_attempts | integer | no | default 0 |
| next_publish_at | timestamptz | no | due queue |
| claim_token | varchar(80) | yes | lease identity |
| claim_expires_at | timestamptz | yes | crash recovery |
| published_at | timestamptz | yes | Kafka ack observed |
| materialized_at | timestamptz | yes | all recipients terminal |
| last_error_code | varchar(80) | yes | sanitized classification |
| created_at | timestamptz | no | |
| updated_at | timestamptz | no | |

Constraints:

- unique `event_key`;
- USER => recipient_user_id not null;
- COMPANY_PERMISSION => company_id + required_permission_key not null;
- variables JSON serialized size <= 16 KiB enforced in application; payload schema is registry-specific.

Indexes:

```sql
create unique index uk_notification_events_event_key
  on notification_events(event_key);

create index idx_notification_events_publish_due
  on notification_events(next_publish_at, id)
  where publish_state in ('PENDING','CLAIMED');

create index idx_notification_events_materialization_recovery
  on notification_events(published_at, id)
  where publish_state = 'PUBLISHED' and materialized_at is null;

create index idx_notification_events_company_created
  on notification_events(company_id, created_at desc, id desc);
```

### 2.2 `notification_fanout_jobs`

Durable bounded company-audience fanout.

| Column | Type | Null | Notes |
|---|---|---:|---|
| id | bigint PK | no | |
| event_id | bigint | no | unique |
| company_id | bigint | no | denormalized scope |
| state | varchar(24) | no | PENDING/CLAIMED/RUNNING/COMPLETED/DEAD |
| cursor_user_id | bigint | yes | last processed user ID |
| batch_size | integer | no | default 500, bounded 50..1000 |
| processed_count | bigint | no | |
| suppressed_count | bigint | no | |
| attempts | integer | no | |
| next_attempt_at | timestamptz | no | |
| claim_token | varchar(80) | yes | |
| claim_expires_at | timestamptz | yes | |
| last_error_code | varchar(80) | yes | |
| created_at/updated_at/completed_at | timestamptz | mixed | |

Unique `event_id`.

Due index:

```sql
create index idx_notification_fanout_due
  on notification_fanout_jobs(next_attempt_at,id)
  where state in ('PENDING','RUNNING','CLAIMED');
```

### 2.3 `notification_event_recipients`

Permanent-with-event-lifetime receipt for exactly-once logical materialization.

| Column | Type | Null |
|---|---|---:|
| id | bigint PK | no |
| notification_event_id | bigint | no |
| company_id | bigint | yes |
| user_id | bigint | no |
| notification_id | bigint | yes |
| status | varchar(24) | no |
| suppression_reason | varchar(64) | yes |
| created_at | timestamptz | no |
| updated_at | timestamptz | no |

Statuses: `MATERIALIZED`, `SUPPRESSED_INELIGIBLE`, `SUPPRESSED_POLICY`.

Critical constraint:

```sql
unique(notification_event_id,user_id)
```

Indexes:

- `(user_id, created_at desc)`;
- `(notification_event_id)`.

### 2.4 `user_notifications`

Compact durable in-app projection.

| Column | Type | Null | Notes |
|---|---|---:|---|
| id | bigint PK | no | |
| company_id | bigint | yes | null = ACCOUNT scope |
| user_id | bigint | no | owner |
| notification_type | varchar(100) | no | stable registry key |
| category | varchar(64) | no | |
| severity | varchar(16) | no | |
| required_permission_key | varchar(160) | yes | rechecked |
| title | varchar(240) | no | safe plain text snapshot |
| body | varchar(1000) | no | safe plain text snapshot |
| aggregate_count | integer | no | default 1, >=1 |
| group_key | varchar(180) | yes | coalescing |
| bucket_start | timestamptz | yes | coalescing bucket |
| action_kind | varchar(32) | yes | `ROUTE` only Round 1 |
| action_path | varchar(512) | yes | relative route only |
| first_event_at | timestamptz | no | |
| last_event_at | timestamptz | no | list seek order |
| seen_at | timestamptz | yes | first visible render |
| read_at | timestamptz | yes | unread iff null |
| expires_at | timestamptz | yes | retention/lifecycle |
| created_at | timestamptz | no | |
| updated_at | timestamptz | no | |

Constraints:

- aggregate_count between 1 and 2,147,483,647;
- action_path must begin with `/` when non-null; application additionally rejects `//`, schemes and backslashes;
- coalesced company notification uniqueness:

```sql
create unique index uk_user_notifications_company_bucket
  on user_notifications(company_id,user_id,notification_type,group_key,bucket_start)
  where company_id is not null
    and group_key is not null
    and bucket_start is not null;
```

Hot indexes:

```sql
create index idx_user_notifications_company_seek
  on user_notifications(user_id,company_id,last_event_at desc,id desc)
  where company_id is not null;

create index idx_user_notifications_account_seek
  on user_notifications(user_id,last_event_at desc,id desc)
  where company_id is null;

create index idx_user_notifications_company_unread
  on user_notifications(user_id,company_id,last_event_at desc,id desc)
  where company_id is not null and read_at is null;

create index idx_user_notifications_account_unread
  on user_notifications(user_id,last_event_at desc,id desc)
  where company_id is null and read_at is null;

create index idx_user_notifications_expiry
  on user_notifications(expires_at,id)
  where expires_at is not null;
```

The list query merges account + selected-company rows in one SQL predicate, ordered by `last_event_at,id`.

### 2.5 `notification_unread_counters`

O(1) history-independent badge counters.

| Column | Type | Null |
|---|---|---:|
| id | bigint PK | no |
| user_id | bigint | no |
| scope_kind | varchar(16) | no |
| company_id | bigint | yes |
| unread_count | bigint | no |
| updated_at | timestamptz | no |

Scope:

- ACCOUNT => company_id null;
- COMPANY => company_id not null.

Constraints:

- unread_count >= 0;
- scope/company consistency check.

Uniqueness uses partial indexes to avoid NULL semantics ambiguity:

```sql
create unique index uk_notification_counter_account
  on notification_unread_counters(user_id)
  where scope_kind='ACCOUNT';

create unique index uk_notification_counter_company
  on notification_unread_counters(user_id,company_id)
  where scope_kind='COMPANY';
```

### 2.6 `notification_user_settings`

One settings row per account or company override scope.

| Column | Type | Null |
|---|---|---:|
| id | bigint PK | no |
| user_id | bigint | no |
| company_id | bigint | yes |
| timezone | varchar(80) | yes |
| quiet_hours_enabled | boolean | no |
| quiet_start | time | yes |
| quiet_end | time | yes |
| created_at/updated_at | timestamptz | no |

Default when no row:

- timezone = company timezone else UTC;
- quiet enabled = true;
- start 22:00;
- end 08:00.

Partial uniqueness:

- one account row per user where company_id is null;
- one company row per user/company where company_id is not null.

### 2.7 `notification_preference_overrides`

Sparse per-type interruption-channel overrides.

| Column | Type | Null |
|---|---|---:|
| id | bigint PK | no |
| user_id | bigint | no |
| company_id | bigint | yes |
| notification_type | varchar(100) | no |
| email_mode | varchar(24) | yes |
| browser_enabled | boolean | yes |
| created_at/updated_at | timestamptz | no |

`email_mode`: `OFF`, `IMMEDIATE`, `DELAYED`, `DIGEST`.

NULL means inherit registry default.

Two partial unique indexes enforce one account override and one company override per user/type.

Mandatory email types may store an override request only if the API rejects/discards illegal OFF; effective policy always reports mandatory locked state.

### 2.8 `notification_delivery_intents`

Cross-worker frozen queue contract. B1 writes logical intents; B2 claims/processes them.

| Column | Type | Null | Notes |
|---|---|---:|---|
| id | bigint PK | no | |
| notification_event_id | bigint | no | core event |
| notification_id | bigint | yes | projection |
| company_id | bigint | yes | |
| user_id | bigint | no | |
| notification_type | varchar(100) | no | |
| required_permission_key | varchar(160) | yes | send-time recheck |
| channel | varchar(24) | no | EMAIL/WEB_PUSH |
| delivery_mode | varchar(24) | no | IMMEDIATE/DELAYED/DIGEST |
| idempotency_key | varchar(255) | no | unique logical effect |
| state | varchar(24) | no | frozen state machine below |
| not_before | timestamptz | no | policy/quiet-hour gate |
| next_attempt_at | timestamptz | no | retry queue |
| attempt_count | integer | no | |
| claim_token | varchar(80) | yes | |
| claim_expires_at | timestamptz | yes | |
| last_error_code | varchar(80) | yes | sanitized |
| sent_at | timestamptz | yes | accepted by channel boundary |
| suppressed_at | timestamptz | yes | |
| created_at/updated_at | timestamptz | no | |

States:

```text
PENDING
  -> DEFERRED
  -> CLAIMED
  -> SENT
  -> SUPPRESSED
  -> FAILED_RETRYABLE -> PENDING/DEFERRED
  -> FAILED_PERMANENT
  -> UNKNOWN
  -> DEAD
```

Rules:

- `SENT`, `SUPPRESSED`, `FAILED_PERMANENT`, `UNKNOWN`, `DEAD` are terminal for normal automatic worker flow;
- UNKNOWN requires explicit reconciliation/operator-safe action;
- lease expiry returns CLAIMED work to PENDING only when no provider call may have produced an ambiguous external effect.

Indexes:

```sql
create unique index uk_notification_delivery_intent_key
  on notification_delivery_intents(idempotency_key);

create index idx_notification_delivery_intents_due
  on notification_delivery_intents(next_attempt_at,id)
  where state in ('PENDING','DEFERRED','FAILED_RETRYABLE','CLAIMED');

create index idx_notification_delivery_intents_user
  on notification_delivery_intents(user_id,created_at desc,id desc);
```

Example idempotency keys:

- `email:welcome:<companyId>:<userId>`
- `push:<notificationId>:<userId>`
- `digest:inbox:<notificationId>:<userId>`

## 3. V71 — B2 delivery tables

### 3.1 `notification_delivery_attempts`

Operational attempt history.

| Column | Type | Null |
|---|---|---:|
| id | bigint PK | no |
| intent_id | bigint | no |
| subscription_id | bigint | yes |
| attempt_no | integer | no |
| channel | varchar(24) | no |
| started_at | timestamptz | no |
| completed_at | timestamptz | yes |
| outcome | varchar(32) | no |
| provider_status_code | integer | yes |
| provider_error_code | varchar(80) | yes |
| provider_reference_hash | varchar(128) | yes |
| latency_ms | bigint | yes |
| created_at | timestamptz | no |

Outcomes:

- `ACCEPTED`
- `TRANSIENT_FAILURE`
- `PERMANENT_FAILURE`
- `RATE_LIMITED`
- `UNKNOWN`
- `SUPPRESSED_POLICY`

Unique `(intent_id,subscription_id,attempt_no)` with NULL handling implemented by application/appropriate indexes.

Never store email body, push endpoint, push keys or provider raw response.

### 3.2 `notification_push_subscriptions`

Account-owned browser/device subscriptions.

| Column | Type | Null |
|---|---|---:|
| id | bigint PK | no |
| user_id | bigint | no |
| endpoint_hash | varchar(64) | no |
| endpoint_ciphertext | text | no |
| p256dh_ciphertext | text | no |
| auth_ciphertext | text | no |
| expiration_time | timestamptz | yes |
| device_label | varchar(120) | yes |
| status | varchar(24) | no |
| created_at | timestamptz | no |
| last_success_at | timestamptz | yes |
| last_failure_at | timestamptz | yes |
| revoked_at | timestamptz | yes |
| updated_at | timestamptz | no |

Statuses: `ACTIVE`, `REVOKED`, `EXPIRED`, `INVALID`.

Constraints/indexes:

```sql
unique(endpoint_hash)

index(user_id,status,created_at desc)
index(expiration_time,id) where status='ACTIVE' and expiration_time is not null
```

Endpoint hash is SHA-256 lowercase hex over exact endpoint bytes.

### 3.3 `notification_push_attempts`

One intent can fan to multiple active browser subscriptions.

| Column | Type | Null |
|---|---|---:|
| id | bigint PK | no |
| intent_id | bigint | no |
| subscription_id | bigint | no |
| attempt_no | integer | no |
| state | varchar(24) | no |
| next_attempt_at | timestamptz | no |
| claim_token | varchar(80) | yes |
| claim_expires_at | timestamptz | yes |
| last_http_status | integer | yes |
| last_error_code | varchar(80) | yes |
| accepted_at | timestamptz | yes |
| created_at/updated_at | timestamptz | no |

Unique `(intent_id,subscription_id)` for logical delivery; `attempt_no` records retry count in row while general `notification_delivery_attempts` preserves each diagnostic attempt.

### 3.4 `notification_digest_buckets`

Durable digest aggregation.

| Column | Type | Null |
|---|---|---:|
| id | bigint PK | no |
| user_id | bigint | no |
| company_id | bigint | no |
| notification_type | varchar(100) | no |
| state | varchar(24) | no |
| contribution_count | integer | no |
| first_contribution_at | timestamptz | no |
| last_contribution_at | timestamptz | no |
| eligible_at | timestamptz | no |
| next_attempt_at | timestamptz | no |
| claim_token | varchar(80) | yes |
| claim_expires_at | timestamptz | yes |
| sent_at | timestamptz | yes |
| suppressed_at | timestamptz | yes |
| last_error_code | varchar(80) | yes |
| created_at/updated_at | timestamptz | no |

States: `OPEN`, `ELIGIBLE`, `CLAIMED`, `SENT`, `SUPPRESSED`, `FAILED_RETRYABLE`, `DEAD`, `UNKNOWN`.

At most one OPEN/ELIGIBLE/CLAIMED bucket per user/company/type, enforced with a partial unique index.

Due index: `(next_attempt_at,id)` for open/eligible/retryable states.

### 3.5 `notification_digest_items`

Maps digest bucket to B1 delivery intents.

| Column | Type | Null |
|---|---|---:|
| digest_bucket_id | bigint | no |
| delivery_intent_id | bigint | no |
| created_at | timestamptz | no |

Primary key `(digest_bucket_id,delivery_intent_id)`; unique `delivery_intent_id` prevents the same logical contribution entering two buckets.

V71 may declare FK from digest items to V71 bucket, but not to V70 delivery intents. V72 adds that cross-module FK.

## 4. V72 — lead-owned integration constraints

After B1+B2 integration, L1 adds validated FKs where lifecycle semantics are safe, at minimum:

- delivery_attempts.intent_id -> notification_delivery_intents.id;
- push_attempts.intent_id -> notification_delivery_intents.id;
- digest_items.delivery_intent_id -> notification_delivery_intents.id;
- core notification tables -> users/companies where deletion semantics have been reviewed.

Do **not** blindly cascade historical notification/delivery rows if that conflicts with account/company retention/deletion policy. L1 must align these FKs with existing deletion lifecycle behavior.

## 5. State-machine concurrency

### Claiming

Every worker uses short lease claims:

```sql
select id
from ...
where state in (...)
  and next_attempt_at <= now()
  and (claim_expires_at is null or claim_expires_at < now())
order by next_attempt_at,id
for update skip locked
limit :batch;
```

Within the claim transaction update:

- state = CLAIMED;
- claim_token = random/UUID;
- claim_expires_at = now + lease.

Subsequent completion update includes `WHERE id=? AND claim_token=?` so stale workers cannot overwrite a reclaimed job.

### Provider call

Never execute provider call under the claim transaction.

## 6. Retention and storage growth

### Assumptions

Illustrative planning assumptions, not billing/product promises:

- ordinary active user: 30 source events/day before coalescing;
- coalescing yields ~12 visible notifications/day;
- ~30% produce optional delivery intent;
- attempt average 1.05;
- compact user notification row + indexes roughly 0.8–1.5 KiB effective DB footprint depending PostgreSQL/index overhead;
- event/receipt/delivery rows add comparable order-of-magnitude metadata, not copied message bodies.

### 1K users

12 visible/user/day => ~12K notification rows/day.

180-day visible retention => ~2.16M rows worst case if every user active daily. At ~1 KiB/row+index order, low-single-digit GiB class.

### 100K users

At the same unrealistically uniform daily activity: ~1.2M visible rows/day and ~216M rows over 180 days.

That volume requires:

- aggressive coalescing;
- actual per-user activity distribution measurement;
- retention tuning;
- autovacuum/index monitoring;
- likely time-range partitioning or archival once sustained tens-of-millions scale is reached.

Round 1 deliberately keeps a schema compatible with monthly partition migration but does not impose it before measurements.

### High-volume inbox

Without coalescing, one shared busy mailbox could create O(messages × members) visible rows.

Round-1 reduces visible row creation to at most approximately:

```text
active recipients × active connection groups × 5-minute buckets
```

while the source event/recipient receipt remains O(actual source event fanout) for correctness.

If a company has very high member counts and shared inbox traffic, future optimization can add audience-level aggregation/fan-out-on-read. That is deferred until production measurements justify complexity.

### Attempt history

Attempts have much shorter retention than user-facing history because they are operational metadata. Normal terminal attempts purge after 30 days; exceptional DEAD/UNKNOWN retain 90 days.

## 7. Data-size constraints

Application must enforce:

- event variables JSON <= 16 KiB;
- title <= 240 chars;
- body <= 1000 chars;
- action path <= 512 chars;
- group key <= 180 chars;
- device label <= 120 chars;
- push plaintext JSON target <= 2 KiB;
- no message body/snippet/raw provider errors in notification/delivery records.

These constraints are defense-in-depth against accidental payload growth and sensitive-data retention.
