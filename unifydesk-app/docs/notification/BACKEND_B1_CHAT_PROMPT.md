# Prompt — B1 Backend Core / In-App Notifications — Round 1

You are **B1 — Backend Core / In-App Notification Engineer** for UnifyDesk Notification System.

Repository: `renukaprasad-ms/messaging`  
Application: `unifydesk-app/`  
Architecture docs: `unifydesk-app/docs/notification/`

Your branch:

`feature/notification-core-inapp-r1`

Your EXACT frozen Round-1 base SHA:

`b13c41919ade97c270ee2235035af3c20dadbe41`

Create your branch exactly from that SHA. Do not branch from later `dev`.

Read completely before coding:

- TASKS.md from `docs/notification-architecture-r0`
- README.md
- ARCHITECTURE.md
- DATA-MODEL.md
- EVENT-CONTRACT.md
- API-CONTRACT.md
- PRODUCT-POLICY.md
- DELIVERY-POLICY.md
- SECURITY.md
- PERFORMANCE.md
- DECISIONS.md

Your ownership is exactly the B1 section in TASKS.md.

## Mission

Implement the durable core notification platform and in-app experience backend:

- V70 core schema;
- event registry/recording;
- transactional source hooks for company-created and inbound inbox activity;
- durable outbox publication;
- Kafka materialization;
- bounded fanout;
- coalescing;
- event-recipient idempotency;
- user notification history;
- O(1) unread counters;
- seen/read/read-all;
- preferences;
- delivery intent creation;
- realtime change hints.

Do **not** implement SMTP/Web Push/digest sending. B2 owns delivery.

## Independence

You are intentionally independent from B2.

Do not import any B2 class. Do not wait for V71. Your V70 migration and all B1 tests must run from the frozen base alone.

The cross-worker runtime boundary is the exact `notification_delivery_intents` SQL contract in DATA-MODEL.md. B2 will read it later with its own JDBC code.

## Mandatory correctness points

- `CompanyService.create` records `company.created:<companyId>` in the same transaction after company + owner membership exist.
- Inbound inbox uses a new `InboxMessagePostProcessor`; do not edit Gmail/IMAP provider persistence.
- Inbox event key is local immutable message ID based; provider resync cannot double count.
- Business/source transaction never enumerates company members or calls external delivery.
- Kafka duplicate/replay cannot duplicate recipient materialization.
- Company fanout uses a durable job and bounded chunks.
- Coalescing is 5-minute UTC bucket by company/user/type/group.
- New unique activity reopens a read row in the same active bucket exactly once.
- unread counter transitions are atomic/concurrency-safe.
- list is keyset pagination, not deep OFFSET.
- current-user/company/required-permission checks on every API read/mutation.
- realtime only after DB commit; realtime failure cannot fail committed business state.
- delivery intents use deterministic idempotency keys.
- all HTTP/realtime IDs are strings.

## Existing files you may modify

Only as TASKS.md permits:

- `CompanyService.java`
- `application.yaml`
- `unifydesk-app/.env.example`

Prefer new B1 package/classes everywhere else.

Do not modify B2 `pom.xml`, V71 or frontend.

## Configuration

As sole writer, add a clear `app.notification-platform` block/environment examples for both workers, including reasonable defaults for:

- outbox/fanout/core worker batch/lease/recovery;
- retention;
- digest policy values;
- optional email rolling cap;
- push VAPID public/private config placeholders;
- push retry/TTL/subscription cap;
- frontend base URL if a dedicated value is required.

Never put a real secret in the repo. B2 will bind its own properties against these keys.

## Tests

At minimum cover:

- duplicate source event;
- rollback behavior where practical;
- company-created target;
- inbox postprocessor duplicate/resync;
- fanout active membership + permission;
- coalescing;
- read/unread/read-all races;
- counter transitions;
- pagination cursor;
- cross-company resource substitution;
- membership/permission revoked visibility;
- preferences validation/mandatory lock;
- realtime emitted only after commit or safely isolated.

Run:

```bash
cd unifydesk-app/backend
./mvnw spotless:check
./mvnw test
./mvnw package -DskipTests
```

Complete the full assignment, commit everything to your branch, and report the exact items required by TASKS.md.

Do not merge your branch.
