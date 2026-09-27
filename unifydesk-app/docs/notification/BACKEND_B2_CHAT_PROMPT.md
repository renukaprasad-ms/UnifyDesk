# Prompt — B2 Backend Delivery / Email / Browser Push / Digest — Round 1

You are **B2 — Backend Delivery Engineer** for UnifyDesk Notification System.

Repository: `renukaprasad-ms/messaging`  
Application: `unifydesk-app/`  
Architecture docs: `unifydesk-app/docs/notification/`

Branch:

`feature/notification-delivery-r1`

EXACT frozen base:

`b13c41919ade97c270ee2235035af3c20dadbe41`

Branch from exactly that SHA. Do not use moving `dev`.

Read TASKS.md plus all frozen notification architecture/security/data/API/delivery docs before coding.

## Mission

Implement the delivery half independently:

- V71 delivery schema;
- B2-owned SQL runtime view of the frozen V70 delivery-intent/core contracts;
- claim/lease/recovery;
- platform service email delivery;
- company welcome email template;
- inbox digest aggregation and delivery;
- current membership/preference/relevance rechecks;
- browser Push subscription APIs;
- endpoint/key encryption;
- Web Push/VAPID;
- push retry/subscription expiry;
- metrics/tests.

Do not implement B1's event recorder, in-app APIs, materializer or V70.

## Hard independence rule

Your branch does not contain B1's Java code or V70 migration.

Therefore:

- do not import `com.messaging.notification.platform.core.*`;
- do not recreate V70 tables in V71;
- V71 must not have FKs to V70-only tables;
- create a B2-owned JdbcTemplate/runtime repository whose SQL exactly matches DATA-MODEL.md;
- unit-test orchestration by mocking that runtime repository;
- Lead adds cross-module V72 after integration.

## Email

Reuse the application's platform `JavaMailSender` underneath a B2-owned durable gateway.

Do not route product notifications through Broadcast/Managed Email customer sender infrastructure.

Implement:

- multipart text/HTML welcome;
- generic privacy-safe inbox digest;
- escaping/header-injection defenses;
- current verified user email resolution;
- typed retry classification;
- durable attempts;
- UNKNOWN state for ambiguous possible-acceptance failures and no blind replay.

Do not claim SMTP acceptance means inbox delivery.

## Digest

Implement frozen policy exactly:

- INBOX_ACTIVITY default OFF unless user preference enables DIGEST;
- 10 contributions OR 2h since first;
- 15m latest-activity debounce bounded by 2h max wait;
- 4h successful-send cooldown per user/company;
- default max 3 optional notification emails/user/rolling 24h;
- quiet-hours timezone deferral;
- final membership, email.read, preference and authoritative unread relevance check;
- zero relevant unread => suppress;
- no inbox message content in digest.

## Web Push

Use standards-based VAPID Web Push. B2 owns the dependency addition in `pom.xml`.

Preferred maintained library is documented in RESEARCH.md; verify it actually compiles/runs on Java 25/Spring Boot 4.1.1. If not, report a blocker/alternative—do not hand-roll cryptography casually.

Requirements:

- capability endpoint exposes public key only;
- subscription create/list/delete API exact;
- endpoint SHA-256 unique;
- endpoint/p256dh/auth encrypted using existing CredentialCipher;
- same endpoint other user => 409, never rebind;
- max active subscriptions default 10;
- payload target <=2KiB and never > protocol budget;
- 404/410 deactivates;
- 429 Retry-After;
- bounded 5xx/network retry;
- push-service 2xx = accepted, not displayed/delivered certainty;
- no network call under DB transaction.

## Existing file ownership

You may modify:

- `unifydesk-app/backend/pom.xml`

Do not edit:

- B1 V70;
- CompanyService;
- application.yaml;
- `.env.example`;
- frontend.

Bind your B2 properties to B1's frozen config namespace without requiring B1 classes.

## Tests

Cover:

- V71 migration independently;
- claim lease/reclaim;
- email transient/permanent/unknown;
- welcome recipient/render escaping;
- digest threshold/debounce/cooldown/quiet hours/cap;
- digest relevance suppression;
- permission/membership revocation before send;
- push encryption at rest;
- same-user idempotent registration;
- cross-user endpoint conflict;
- foreign subscription delete;
- 404/410, 429, 5xx classifications;
- no secret values in response/error/log-oriented DTO;
- multi-subscription intent idempotency.

Run backend checks from TASKS.md, commit, then report the required completion summary.

Do not merge your branch.
