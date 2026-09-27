# Notification System Round-1 Tasks and File Ownership

Status: FROZEN  
Architecture branch: `docs/notification-architecture-r0`  
Frozen Round-1 worker base SHA: `b13c41919ade97c270ee2235035af3c20dadbe41`

This manifest is intentionally committed after the frozen worker base so it can name the immutable SHA. B1, B2 and F1 branch from exactly `b13c41919ade97c270ee2235035af3c20dadbe41` and read this file/their prompt from `docs/notification-architecture-r0`.

Do not branch from moving `dev`. Do not cherry-pick another worker's implementation branch.

## 0. Branches

- B1: `feature/notification-core-inapp-r1`
- B2: `feature/notification-delivery-r1`
- F1: `feature/notification-ui-r1`
- QA/SEC later: `review/notification-security-qa-r1` from an integrated SHA supplied by L1.

## 1. Frozen contracts

All workers must read:

- `README.md`
- `RESEARCH.md`
- `PRODUCT-POLICY.md`
- `ARCHITECTURE.md`
- `DECISIONS.md`
- `DATA-MODEL.md`
- `EVENT-CONTRACT.md`
- `API-CONTRACT.md`
- `DELIVERY-POLICY.md`
- `SECURITY.md`
- `PERFORMANCE.md`

No worker edits these frozen docs in Round 1.

## 2. Migration ownership

B1 owns:

`unifydesk-app/backend/src/main/resources/db/migration/V70__notification_core.sql`

B2 owns:

`unifydesk-app/backend/src/main/resources/db/migration/V71__notification_delivery.sql`

L1 owns after integration:

`V72__notification_cross_module_constraints.sql`

V71 must migrate independently from the frozen base and therefore must not FK to V70-only tables. B2 accesses V70 tables through frozen SQL contracts in DATA-MODEL.md, not B1 Java classes.

If moving dev later collides with V70–V72, L1 alone renumbers during integration before deployment.

## 3. B1 — Backend Core / In-App

Branch: `feature/notification-core-inapp-r1`  
Base: `b13c41919ade97c270ee2235035af3c20dadbe41`

### B1 owns new code

Preferred package boundary:

`unifydesk-app/backend/src/main/java/com/messaging/notification/platform/core/**`

B1 owns:

- notification type registry and safe renderers;
- event recorder;
- outbox publisher/recovery;
- Kafka materialization handler;
- fanout job worker;
- event-recipient idempotency;
- coalesced user notification persistence;
- unread counter service/reconciliation;
- notification query/read/seen APIs;
- preferences/settings APIs;
- delivery-intent creation;
- realtime `notification.changed.v1` publication;
- company-created producer;
- inbox activity post-processor;
- core tests.

### Existing files B1 MAY modify — sole Round-1 writer

- `unifydesk-app/backend/src/main/java/com/messaging/company/service/CompanyService.java`
- `unifydesk-app/backend/src/main/resources/application.yaml`
- `unifydesk-app/.env.example`

B1 may add tests for CompanyService/inbox integration where needed.

B1 should **not** modify Gmail/IMAP persistence classes; use the existing `InboxMessagePostProcessor` seam.

B1 should use existing `EventPublisher`, `KafkaEvent`, `RealtimeService`, `CompanyAuthorizationService`, permission services and `IdGenerator` without editing their hot files unless an actual frozen-contract blocker is documented to L1.

### B1 APIs

Own:

- GET `/api/v1/notifications`
- GET `/api/v1/notifications/{id}`
- GET `/api/v1/notifications/unread-count`
- POST `/api/v1/notifications/seen`
- PATCH `/api/v1/notifications/{id}/read-state`
- POST `/api/v1/notifications/read-all`
- GET/PUT `/api/v1/notifications/preferences`

Exact request/response shapes: API-CONTRACT.md.

### B1 source producers

Round 1:

- `company.created` in the existing company transaction;
- `inbox.activity` from `InboxMessagePostProcessor`.

No provider send in source transaction.

### B1 acceptance

- V70 cleanly migrates from frozen base.
- Company creation succeeds independently if Kafka/SMTP/Redis interruption channels are down after event insert; event remains recoverable.
- Duplicate event key produces no duplicate logical effect.
- Bounded fanout; no source-transaction member enumeration.
- O(1) unread-count read path.
- Keyset notification pagination.
- Strong current-user/company/permission predicates.
- B1 branch compiles/tests with **no B2 classes or V71**.
- No B2/F1-owned files changed.

## 4. B2 — Backend Delivery / Email / Push / Digest

Branch: `feature/notification-delivery-r1`  
Base: `b13c41919ade97c270ee2235035af3c20dadbe41`

### B2 owns new code

Preferred boundary:

`unifydesk-app/backend/src/main/java/com/messaging/notification/platform/delivery/**`

B2 owns:

- V71 delivery tables/JPA/JDBC;
- B2-owned JDBC projection of V70 `notification_delivery_intents`, preferences/core data required by send-time recheck;
- claim/recovery services;
- durable product email gateway;
- welcome template;
- inbox digest aggregator/template;
- digest cooldown/rate caps;
- push subscription controller/service;
- VAPID Web Push gateway;
- subscription encryption/endpoint hashing;
- push attempt/retry/expiry cleanup;
- delivery metrics/tests.

### Existing files B2 MAY modify — sole Round-1 writer

- `unifydesk-app/backend/pom.xml`

B2 may add the validated Web Push dependency there.

B2 MUST NOT edit `application.yaml` or `.env.example`; B1 is the sole writer. B2 binds a B2-owned configuration-properties class to the frozen `app.notification-platform` namespace. Unit tests use safe defaults; production push fails closed if VAPID/encryption configuration is absent.

B2 MUST NOT import `com.messaging.notification.platform.core.*`. Use JdbcTemplate/SQL based on DATA-MODEL.md. Mock that runtime repository in B2 unit tests.

B2 may use existing `JavaMailSender`, `CredentialCipher`, `CompanyAuthorizationService`/repositories as base-code dependencies, but does not alter B1 hot files.

### B2 APIs

Own:

- GET `/api/v1/notifications/push/capability`
- POST `/api/v1/notifications/push/subscriptions`
- GET `/api/v1/notifications/push/subscriptions`
- DELETE `/api/v1/notifications/push/subscriptions/{id}`

Exact API-CONTRACT.md.

### B2 acceptance

- V71 migrates independently from frozen base without V70.
- Backend compiles/tests with no B1 Java code.
- Product service email stays separate from Broadcast/Managed Email.
- SMTP ambiguous outcome does not blindly retry.
- Digest meets 10-or-2h, 15m debounce, 4h cooldown, rolling cap, quiet hours and send-time relevance.
- Push data encrypted at rest; endpoint hash unique; cross-user endpoint collision 409.
- 404/410 subscription retirement, 429 Retry-After, bounded 5xx retries.
- No provider network call while DB claim transaction open.
- No B1/F1-owned file changed.

## 5. F1 — Frontend + UX

Branch: `feature/notification-ui-r1`  
Base: `b13c41919ade97c270ee2235035af3c20dadbe41`

F1 designs and implements UX against frozen mocks/contracts; it does not wait for B1/B2.

### F1 owns new code

Preferred:

- `unifydesk-app/frontend/src/features/notifications/**`
- `unifydesk-app/frontend/src/pages/notifications/**`
- `unifydesk-app/frontend/public/notification-sw.js` (or equivalent F1-owned service worker implementation)
- colocated tests/fixtures.

### Existing frontend files F1 MAY modify — F1 sole writer

- `src/components/Header.tsx`
- `src/components/navigation/AppSidebar.tsx`
- `src/layouts/MainLayout.tsx`
- `src/routes/AppRoutes.tsx`
- `src/pages/account/AccountSettings.tsx`
- `src/index.css`
- `src/main.tsx` only if required for service-worker registration/provider mounting
- `frontend/.env.example` only for public frontend notification configuration if truly required; prefer backend capability API.

F1 does not modify backend code or Flyway migrations.

### Required UX

- header bell and `99+` badge;
- sidebar Notifications entry + badge;
- accessible popover/recent list, 8 newest, View all;
- full `/notifications` keyset page;
- unread styling, mark read/unread, mark all read;
- mark seen when actually rendered;
- realtime refresh using existing `realtimeClient`;
- reconnect/focus/workspace-switch recovery;
- account + current-company scope;
- preferences UI in account settings;
- browser notification educational opt-in;
- permission default/granted/denied/unsupported states;
- service-worker PushSubscription registration/revocation;
- focused-window push suppression handling;
- `/notification-open` bridge;
- BroadcastChannel synchronization;
- Badging API only with feature detection;
- empty/loading/error/offline/revoked-workspace states;
- mobile + keyboard/accessibility.

### F1 acceptance

- frontend tests/build from frozen base with mocked APIs;
- no assumption that browser push is universally supported;
- no permission request on page load;
- no parsing of opaque cursor;
- no rendering arbitrary HTML;
- no direct provider/AWS/VAPID-private interaction;
- no backend edits.

## 6. Shared hot-file rule

If a worker discovers a needed change in another owner's file:

1. do not edit it;
2. document exact requested adapter/change in final report;
3. continue using the frozen contract/mock where possible;
4. L1 resolves at integration.

No worker solves a dependency by cherry-picking another implementation branch.

## 7. L1 integration sequence

1. review B1 against V70/core/API/event contracts;
2. review B2 against V71/delivery contracts;
3. review F1 against HTTP/realtime contracts;
4. merge B1;
5. merge B2, resolving only lead-owned integration seams;
6. collision-check/renumber notification migrations if needed;
7. add `V72__notification_cross_module_constraints.sql` and tiny adapters if truly necessary;
8. merge F1;
9. run clean migration + backend/frontend checks;
10. freeze exact integrated SHA;
11. only then launch QA/SEC from that SHA.

## 8. Required checks

Backend workers, at minimum:

```bash
cd unifydesk-app/backend
./mvnw spotless:check
./mvnw test
./mvnw package -DskipTests
```

F1:

```bash
cd unifydesk-app/frontend
npm ci
npm run format:check
npm run lint
npm test
npm run build
```

Workers report any pre-existing baseline failure distinctly from changes introduced by their branch.

## 9. Round-1 completion reports

B1 reports:
- branch;
- final commit SHA;
- files changed;
- migration;
- APIs;
- source-event/materialization/realtime work;
- tests/builds;
- blocker/deviation.

B2 reports:
- branch;
- final commit SHA;
- files changed;
- migration;
- email/digest/push/retry work;
- provider/library assumption;
- tests/builds;
- blocker/deviation.

F1 reports:
- branch;
- final commit SHA;
- files changed;
- screens/flows/service-worker UX;
- tests/builds;
- contract deviation/blocker.

Do not merge your branch.
