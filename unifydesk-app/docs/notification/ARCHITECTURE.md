# Notification System Architecture

Status: frozen for Round 1  
Baseline: `c0c50579f2f823a264eefb525a48abb1b6120198`

## 1. Goals

The Notification System is a platform subsystem that converts product events into durable user-facing activity and optional interruption channels without coupling business modules to SMTP, Web Push or frontend presentation.

Round 1 must provide:

- company-scoped and account-scoped in-app notifications;
- header/sidebar unread indicators and notification history;
- realtime change hints over the existing WebSocket;
- read/unread + seen state;
- per-user interruption preferences;
- quiet hours/timezone;
- durable service-email delivery;
- intelligent inbox digests rather than message-by-message email;
- true Service Worker Web Push;
- restart-safe retries/recovery;
- strong tenant/user isolation;
- bounded storage and indexed hot paths.

## 2. Existing infrastructure reused

| Concern | Existing implementation | Decision |
|---|---|---|
| Authentication | JWT/cookie session + `CurrentUserService` | reuse |
| CSRF | cookie token + `X-XSRF-TOKEN` | reuse |
| Company auth | `CompanyAuthorizationService` | reuse |
| IDs | Snowflake `Long` internally | reuse; stringify externally |
| DB | PostgreSQL + Flyway | authoritative source |
| Queue | existing priority Kafka | reuse |
| Retry/DLT | Kafka error handler + DLT | reuse for transport |
| Multi-pod realtime | Redis pub/sub + WebSocket | reuse |
| Email transport | platform `JavaMailSender` | reuse through new durable adapter |
| Metrics | Micrometer/Prometheus | reuse |
| Encryption | `CredentialCipher` AES-GCM | reuse for push subscription secrets |
| Inbox hook | `InboxMessagePostProcessor` | use for inbound activity |
| Scheduling | Spring scheduled workers | reuse; no Quartz introduced |

## 3. Runtime topology

```text
                       ┌─────────────────────────┐
Business transaction ─► notification_events     │
(company/inbox/etc.)   │ immutable event/outbox │
                       └────────────┬────────────┘
                                    │ bounded publisher
                                    ▼
                         existing priority Kafka
                                    │
                                    ▼
                      notification event handler
                         │ direct recipient
                         │ or durable fanout job
                         ▼
              ┌──────────────────────────────┐
              │ B1 materialization          │
              │ - recipient eligibility     │
              │ - event-recipient receipt   │
              │ - coalesced/user row        │
              │ - unread counter            │
              │ - delivery intents          │
              └──────────────┬───────────────┘
                             │ commit
              ┌──────────────┼────────────────────┐
              │              │                    │
              ▼              ▼                    ▼
       RealtimeService  delivery intents      notification APIs
       Redis/WebSocket       │                    │
       best-effort hint      │                    └─ F1 durable fetch
                             ▼
                 ┌───────────────────────┐
                 │ B2 delivery runtime   │
                 │ - policy recheck      │
                 │ - email               │
                 │ - digest              │
                 │ - web push            │
                 │ - retry/recovery      │
                 └───────────────────────┘
```

## 4. Source-of-truth event model

Business modules do **not** call email/push adapters.

They call a small B1-owned recorder with a closed `NotificationEventDraft`. The recorder:

1. validates type + safe variables through the notification type registry;
2. derives or accepts a deterministic `eventKey`;
3. inserts one `notification_events` row;
4. relies on database uniqueness for replay safety.

The recorder is invoked inside the existing business transaction where practical.

### Round-1 producers

#### Company creation

Inside `CompanyService.create`, after company + owner membership exist but before transaction completion:

```text
eventKey = company.created:<companyId>
eventType = company.created
recipient = USER(<creatorUserId>)
subject = COMPANY(<companyId>)
```

If the transaction rolls back, the event rolls back.

#### Inbound inbox activity

A new `InboxMessagePostProcessor` records:

```text
eventKey = inbox.message.received:<localMessageId>
eventType = inbox.activity
recipient = COMPANY_PERMISSION(email.read)
subject = INBOX_MESSAGE(<localMessageId>)
groupKey = inbox:<connectionId>
```

The deterministic local message ID prevents resync/update duplicates.

## 5. Transactional outbox publication

A B1 scheduled publisher claims `PENDING` event rows in bounded batches using `FOR UPDATE SKIP LOCKED`.

Claim transaction:

- select due rows;
- set short lease/claim token;
- commit.

Publish outside transaction through existing `EventPublisher`.

On successful Kafka acknowledgement, mark `PUBLISHED`. If the process dies after Kafka accepted but before DB acknowledgement, the row can be republished. This is safe because materialization is idempotent.

A recovery sweep republishes old `PUBLISHED` events whose materialization has not completed.

Kafka transport event:

- envelope event type: `notification.materialize.requested`;
- priority maps from notification policy;
- Kafka key uses existing company/user resolver;
- payload follows EVENT-CONTRACT.md.

## 6. Recipient resolution and fanout

### Direct user

Direct `USER` recipient:

- verify user exists/active;
- for company event, verify active membership and current required permission when applicable;
- materialize in one transaction.

### Company permission audience

Do not enumerate all members inside the source transaction or Kafka listener.

The listener upserts one `notification_fanout_jobs` row and acknowledges Kafka. A B1 fanout worker:

- claims a job;
- selects active members ordered by user ID after `cursor_user_id`;
- filters by frozen `required_permission_key`;
- processes at most 500 recipients per chunk by default;
- commits materialization for the chunk;
- advances cursor;
- marks event materialized when exhausted.

This makes fanout O(N), bounded memory O(batch size), and restart-safe.

## 7. Materialization transaction

For each event-recipient pair:

1. attempt insert into `notification_event_recipients`;
2. if duplicate, return with no visible/delivery side effect;
3. revalidate active membership/permission;
4. apply type-specific coalescing;
5. insert/update `user_notifications`;
6. update the correct unread counter if the visible unread set changed;
7. create channel delivery intents allowed by default/effective preference;
8. write the recipient receipt's `notification_id`;
9. commit.

Only after commit does B1 send `notification.changed.v1` through existing `RealtimeService`.

If Redis/WebSocket publication fails, log/metric the failure and continue; durable API recovery remains correct.

## 8. Coalescing

Round-1 `INBOX_ACTIVITY` is coalesced:

```text
coalesce key =
companyId + userId + type + groupKey + floor(occurredAt to 5-minute UTC bucket)
```

The first event creates a user notification. Later unique source events in the same bucket:

- increment `aggregate_count`;
- update `last_event_at`;
- keep the row unread unless the user already marked it read and policy elects to reopen it.

Round-1 rule: a new unique inbox event reopens a previously read row in the still-open 5-minute bucket by clearing `read_at`, because new unseen activity arrived. The unread counter increments only when transitioning read -> unread.

Event-recipient receipt uniqueness prevents a duplicate source event from incrementing the aggregate twice.

## 9. In-app read/seen model

- `seen_at`: first time item was visibly rendered in bell/page.
- `read_at`: explicit click or read-state mutation.
- unread := `read_at IS NULL`.
- seen does not alter unread count.
- mark one read/unread is idempotent.
- mark-all-read uses a server/request cutoff so concurrently arriving notifications after the cutoff remain unread.

The API list includes account notifications plus selected company notifications. Company-scoped rows remain hidden if current membership/required permission is no longer valid.

## 10. Unread counters

`notification_unread_counters` stores at most one account-scope row and one row per user/company.

Counter updates are transactionally coupled to:

- notification creation;
- coalesced row reopening;
- mark read;
- mark unread;
- mark all read;
- retention deletion of an unread item.

Header total for a selected workspace is:

```text
account unread + selected company unread
```

which is O(1) in history size.

A low-frequency reconciliation job compares counters against indexed truth for sampled/changed users and records drift metrics; it is not the normal read path.

## 11. Delivery intents

B1 owns the durable cross-worker table `notification_delivery_intents`.

It contains one logical request for:

- EMAIL / IMMEDIATE;
- EMAIL / DELAYED;
- EMAIL / DIGEST;
- WEB_PUSH / IMMEDIATE-or-DEFERRED.

The unique `idempotency_key` guarantees one logical intent per policy decision.

B2 does not import B1 entities/repositories. It reads/updates this table through B2-owned JDBC/SQL matching DATA-MODEL.md.

At delivery time B2 **rechecks**:

- user active;
- company membership;
- required permission;
- current effective preference;
- quiet hours;
- cooldown/rate limit;
- whether delayed/digest relevance still exists;
- push subscription validity.

This closes the stale-membership/preference window between materialization and delivery.

## 12. Email architecture

Product notification email uses a B2-owned durable adapter around the application's platform `JavaMailSender`.

It is separate from:

- Broadcast senders;
- customer-managed providers;
- UnifyDesk Managed Email customer SES tenants;
- broadcast suppression/metering.

B2 owns deterministic notification templates for:

- company-created welcome;
- inbox activity digest;
- future product notification templates.

Rendering rules:

- plain-text + HTML alternative;
- all dynamic text escaped;
- same-origin absolute links derived from trusted route metadata;
- no arbitrary HTML/URL from event payload;
- message-id/idempotency metadata where the mail stack permits;
- generic subject lines for sensitive categories.

SMTP acceptance is not equivalent to inbox delivery. B2 records `ACCEPTED`, not “delivered”, unless a future authenticated provider-feedback integration proves later delivery.

## 13. Digest architecture

B1 creates `EMAIL/DIGEST` intents only for eligible type/user policy.

B2 does not scan every user. A digest aggregator queries only due pending digest intents using the due-state index, then atomically upserts an `notification_digest_buckets` row and attaches contribution intents.

Inbox digest bucket key:

```text
userId + companyId + INBOX_ACTIVITY
```

Eligibility obeys DELIVERY-POLICY.md.

Before send B2 performs final relevance checks. If no relevant unread/unseen activity remains, mark bucket + intents `SUPPRESSED`.

## 14. Web Push architecture

Round 1 implements true Web Push:

```text
F1 explicit enable action
  -> navigator.serviceWorker.ready
  -> PushManager.subscribe({userVisibleOnly:true, applicationServerKey})
  -> POST subscription to backend
  -> B2 encrypts endpoint/p256dh/auth at rest

B2 intent worker
  -> resolve active subscriptions
  -> web-push/VAPID send
  -> browser push service
  -> service worker push event
      -> if focused UnifyDesk client exists: postMessage only
      -> else showNotification()
  -> notificationclick
      -> focus/open /notification-open?... same origin
```

One user can have multiple active browser subscriptions.

The same logical notification can be sent to each active subscription, but each subscription attempt is idempotently recorded.

404/410 endpoint responses permanently deactivate that subscription.

## 15. Foreground/realtime vs push duplication

Realtime and Web Push are independent:

- realtime updates the in-app UI while a session is connected;
- web push is optional interruption.

The service worker checks controlled window clients. If any UnifyDesk client is focused, it does not show an OS notification. It posts a message so the page can refresh state.

This does not require backend awareness of tab visibility and avoids a fragile cross-device “user active” global flag.

## 16. Multiple tabs

F1 uses one existing WebSocket per tab as today.

For notification UI synchronization:

- realtime event triggers durable count/list refresh;
- mutation success broadcasts a small same-origin `BroadcastChannel` message to other tabs;
- other tabs refetch authoritative state;
- service worker can also `postMessage` clients;
- Badging API is optional/feature-detected.

Correctness never depends on BroadcastChannel.

## 17. Company switching

On selected company change F1:

1. clears company-scoped notification page cache;
2. fetches unread count for the new company (account + selected company);
3. fetches first notification page;
4. ignores realtime company events for UI insertion when payload company differs, but may refetch if account scope changed.

A push click carries the target company ID and notification ID only. The `/notification-open` page verifies that company is present in the user's workspace list before selecting it, then fetches the notification from the backend and navigates using the returned trusted action path.

## 18. Failure/degradation matrix

| Failure | Behavior |
|---|---|
| Kafka down | source event remains durable PENDING; publisher retries |
| duplicate Kafka | DB recipient/event uniqueness prevents duplicate effect |
| Redis down | realtime hints fail; API history/count remain correct |
| WebSocket down | F1 polling/focus/reconnect recovery |
| SMTP outage | delivery intent retry/dead/unknown rules; business action unaffected |
| push service outage | retry eligible push attempts; in-app remains available |
| backend restart | leases expire; workers reclaim |
| crash after DB event insert | publisher resumes |
| crash after Kafka send before mark | duplicate publish; materialization idempotent |
| crash after provider definite failure | attempt/state persists on recovery |
| crash/timeout after possible SMTP acceptance | mark/recover as UNKNOWN; no blind auto-repeat |
| company membership revoked | API hides row; B2 suppresses unsent interruption |
| preference changed | B2 send-time policy recheck honors new preference |

## 19. Retention

Default Round-1:

- source events/recipient receipts: 90 days after terminal materialization;
- user notifications: 180 days;
- delivery intents: 90 days terminal;
- delivery attempts: 30 days normal, 90 days DEAD/UNKNOWN operational records;
- digest buckets/items: 30 days terminal;
- revoked/expired push subscriptions: 30 days, then purge;
- preferences/settings retained with user account.

Security/account retention may be extended later by explicit policy; Round 1 does not overload notification history as the legal audit log.

Cleanup is batched/keyset-based and must correctly decrement unread counters before deleting unread rows.

## 20. Explicit Round-1 deferrals

- mobile native push;
- SMS/WhatsApp/Slack notification channels;
- marketing/subscription campaign engine;
- admin arbitrary-send console;
- user-authored HTML notification templates;
- full provider delivery-feedback loop for platform SMTP;
- global announcement fan-out-on-read;
- database table partitioning;
- cross-region active-active notification processing;
- machine-learning notification ranking;
- per-notification snooze/archive/dismiss state;
- daily/weekly custom digest schedule beyond frozen smart digest semantics.
