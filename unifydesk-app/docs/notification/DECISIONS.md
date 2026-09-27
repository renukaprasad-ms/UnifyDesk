# Notification Architecture Decisions

Status: frozen for Round 1

## D01 — PostgreSQL is the notification source of truth

Kafka, Redis pub/sub, WebSocket and web push are transports/coordination layers. Durable history/read state/delivery intent is PostgreSQL.

Reason: restart/outage correctness and queryable recovery.

## D02 — Reuse the existing Kafka and realtime infrastructure

Do not build a second broker or WebSocket server.

- source transaction records a durable `notification_events` row;
- B1 outbox publisher emits through existing priority Kafka;
- B1 uses existing `RealtimeService` only after durable materialization commits;
- realtime failure never rolls back durable notification state.

## D03 — The event table is the transactional outbox

`notification_events` stores the immutable source event plus publication/materialization state. The originating transaction inserts it. Publication is asynchronous.

Duplicate broker publication is expected; consumers are idempotent.

## D04 — Durable DB uniqueness, not Redis, is the final idempotency boundary

Existing Kafka Redis idempotency remains useful but can expire and is not atomic with database effects.

Required database boundaries:

- unique `notification_events.event_key`;
- unique materialization receipt per `notification_event_id + user_id`;
- unique logical delivery intent key;
- unique push endpoint hash.

## D05 — Fan out asynchronously and in bounded chunks

No business transaction loops over all company users.

Direct targets materialize immediately in a small bounded operation. Large company/permission audiences use B1 fanout jobs and cursor/chunk processing. Round-1 default chunk is 500 recipients.

## D06 — Fan-out-on-write is the Round-1 read-state model

Each eligible recipient receives a compact `user_notifications` projection. Canonical event payload/content is not copied wholesale.

Reason: simple user-specific read/seen state, fast unread count, straightforward authorization. High-fanout work is background/bounded; future platform-wide announcements may use a separate fan-out-on-read model.

## D07 — Inbox notifications coalesce on write

`INBOX_ACTIVITY` uses a 5-minute UTC group bucket by connection/user/company. A later source event can update one notification row's aggregate count rather than create one row per message.

A durable event-recipient receipt prevents duplicate increments.

## D08 — Maintain unread counters transactionally

Do not run `COUNT(*)` across all unread rows on every header render.

A small `notification_unread_counters` row is updated in the same transaction as create/read/unread state changes. A reconciliation job/metric detects drift.

## D09 — Use keyset pagination

Notification history orders by `last_event_at DESC, id DESC` and uses an opaque seek cursor. Deep OFFSET pagination is forbidden.

## D10 — Personal notification APIs use current-user ownership plus company context

HTTP APIs are under `/api/v1/notifications`, with optional/required `companyId` depending on operation.

Server always derives the user from authentication. A caller never supplies a target user ID for personal reads/state changes.

## D11 — Record required permission on company notifications

A user who loses the source feature permission must not keep receiving sensitive future interruption. Query/delivery re-checks current membership and `required_permission_key`.

Notification copy itself must remain low sensitivity; deep-linked feature APIs remain authoritative.

## D12 — Keep legacy OTP notification flow compatible

Round 1 does not rewrite `NotificationService`/OTP behavior. B2 reuses the same platform `JavaMailSender`/branding configuration through a durable product-email adapter rather than treating the in-memory async facade as a delivery guarantee.

## D13 — Product notification email is not Broadcast/Managed Email

No customer sender, provider connection, campaign quota, managed SES tenant or DLT concept is used for UnifyDesk service notifications.

## D14 — External provider calls are outside DB transactions

Worker pattern:

1. claim/update state in a short DB transaction;
2. commit;
3. perform SMTP/web-push call;
4. persist result in a new short transaction.

Never hold `FOR UPDATE` across network I/O.

## D15 — SMTP cannot provide exactly-once external effects

The platform guarantees one logical email intent and durable attempt state. A crash/network ambiguity after remote acceptance can yield `UNKNOWN`; automatic replay of an ambiguous SMTP send is not allowed by default because it could duplicate mail.

Known pre-acceptance transient failures can retry with backoff.

## D16 — Realtime is best effort with deterministic recovery

Realtime payloads are hints. F1 re-fetches unread count + first page on reconnect, workspace switch and window focus after a disconnected interval.

## D17 — True browser background notification uses Web Push

Page `Notification()` alone is insufficient. Round 1 uses service worker + Push API + VAPID. Browser permission is explicit opt-in.

## D18 — Suppress OS push while UnifyDesk is focused

The service worker checks controlled window clients. If one is focused, it posts the notification ID/count to clients instead of showing another OS notification. WebSocket/in-app UX remains primary while active.

## D19 — Push subscription secrets are encrypted at rest

Endpoint/p256dh/auth are capability/secret material.

- endpoint hash (SHA-256) supports uniqueness/lookup;
- endpoint and keys are encrypted using the existing `CredentialCipher`/AES-GCM infrastructure;
- raw values never appear in logs/API list responses.

## D20 — Shared-browser ownership cannot transfer silently

If a push endpoint is already bound to another user, registration returns 409. F1 unsubscribes browser-side and creates a fresh subscription. Logout should revoke the current user's subscription where possible.

## D21 — Optional inbox digest is opt-in

In-app is default. Inbox-activity email is `DIGEST` only when the user enables it; never immediate.

This is deliberately conservative to prevent early notification fatigue.

## D22 — Quiet hours affect interruption, not history

In-app rows appear immediately. Optional push/email are deferred. Mandatory critical service/security mail may bypass quiet hours.

## D23 — Type registry is code-owned and closed

A registry owns:

- stable type key/category/severity;
- rendering template;
- action/deep-link builder;
- default channels;
- mandatory flags;
- coalesce/cooldown policy;
- safe payload schema.

No public “send arbitrary notification” API exists in Round 1.

## D24 — Browser payload is minimal and same-origin

No access token, email address, provider credential, full inbox subject/body or arbitrary URL is pushed.

Payload target is a same-origin notification-open bridge.

## D25 — One worker owns each migration/hot file

- B1: core schema + CompanyService + application configuration.
- B2: delivery schema + pom dependency.
- F1: frontend notification surfaces.
- Lead: post-merge cross-module constraints/migration-number reconciliation.

B2 consumes B1 tables through frozen SQL/JdbcTemplate contracts and MUST NOT import B1 Java entities.

## D26 — Reserve migration range V70–V72 for Round 1

Current frozen baseline reaches V61 and concurrent Managed SMS work has reserved nearby versions. Round-1 notification branches use:

- B1 `V70__notification_core.sql`;
- B2 `V71__notification_delivery.sql`;
- Lead `V72__notification_cross_module_constraints.sql` after integration.

Before merging to a moving dev branch, L1 MUST collision-check all Flyway versions. If another feature has reached this range, L1—not workers—renumbers the three notification migrations consistently before any notification migration is deployed.

## D27 — No premature partitioning

Round-1 uses targeted B-tree/partial indexes and retention cleanup. Monthly range partitioning is a documented scale migration, not an upfront complexity cost.

## D28 — Public IDs are strings

Snowflake IDs remain `Long` internally but all HTTP and realtime DTO IDs are JSON strings. Opaque list cursors are base64url-encoded/versioned.

## D29 — F1 works entirely from frozen mocks/contracts

F1 does not wait for B1/B2 and never imports generated backend DTOs. Mock fixtures reproduce `API-CONTRACT.md` and `EVENT-CONTRACT.md`.

## D30 — QA/SEC starts only from an integrated immutable SHA

The QA prompt is prepared in Round 0 but explicitly has no base SHA until L1 integrates B1+B2+F1, runs checks and publishes the exact integrated commit.
