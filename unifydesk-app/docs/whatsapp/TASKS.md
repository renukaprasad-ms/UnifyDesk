# Meta WhatsApp Round-1 Tasks and File Ownership

Status: FROZEN  
Architecture branch: `docs/whatsapp-architecture-r0`  
Frozen Round-1 worker base SHA: `cd5961362ddba0eda45d0ee1a881e93bab2f214c`

This manifest is intentionally committed after the immutable worker base so it can name that SHA. Workers branch from exactly `cd5961362ddba0eda45d0ee1a881e93bab2f214c` and read this manifest/their prompt from the architecture branch.

Do not branch from moving `dev`. Do not cherry-pick another worker branch.

## Branches

- B1: `feature/whatsapp-control-plane-r1`
- B2: `feature/whatsapp-runtime-r1`
- F1: `feature/whatsapp-ui-r1`
- QA/SEC later: `review/whatsapp-security-qa-r1` from an integrated SHA published by L1.

## Frozen contracts

All workers read:
- README.md
- RESEARCH-META-WHATSAPP.md
- ARCHITECTURE.md
- DECISIONS.md
- DATA-MODEL.md
- API-CONTRACT.md
- EVENT-CONTRACT.md
- WEBHOOK-CONTRACT.md
- MESSAGE-STATE.md
- TEMPLATE-POLICY.md
- SECURITY.md
- PERFORMANCE.md
- META-SETUP-RUNBOOK.md

Workers do not modify frozen architecture docs.

## Migration ownership

B1:
`V76__meta_whatsapp_control_plane.sql`

B2:
`V77__meta_whatsapp_runtime.sql`

L1 after integration:
`V78__meta_whatsapp_cross_module_constraints.sql` if required.

V77 must migrate from the frozen base without V76 and therefore cannot FK to V76-only tables.

## Frozen B1 -> B2 SQL/credential seam

B2 may query these V76 columns through B2-owned JdbcTemplate code at runtime:
- connection_id
- company_id
- legacy_waba_id
- whatsapp_account_id
- messaging_account_id
- selected_phone_number_id
- product_state
- auth_state
- registration_state
- subscription_state
- coexistence_state
- last_validated_at

B2 MUST NOT import B1 entities/repositories/services.

Provider credential JSON consumed through ProviderCredentialService remains backward-compatible with current fields:
- accessToken
- wabaId
- phoneNumberId
- graphApiVersion

B1 may add fields but must not rename/remove those during Round 1.

## B1 — Connection / Control Plane

Branch: `feature/whatsapp-control-plane-r1`  
Base: `cd5961362ddba0eda45d0ee1a881e93bab2f214c`

Mission:
- Embedded Signup v4 server/control-plane support;
- fresh one-time state;
- safe Graph errors;
- token + asset validation;
- recoverable registration/subscription;
- detailed state + health reconciliation;
- reconnect/reauthorize/disconnect;
- encrypted credential/PIN lifecycle.

B1 owns existing production files:
- `backend/src/main/java/com/messaging/channel/meta/MetaGraphClient.java`
- `MetaWhatsAppConnectionController.java`
- `MetaWhatsAppConnectionService.java`
- `MetaWhatsAppPersistenceService.java`
- `MetaWhatsAppProperties.java`
- `MetaWhatsAppProvider.java`
- `MetaWhatsAppSignupStateService.java`
- all existing `channel/meta/dto/**` connection DTOs
- `ChannelConnectionRepository.java`
- `SenderIdentityRepository.java`
- `backend/src/main/resources/application.yaml`
- `unifydesk-app/.env.example`

B1 owns new:
- `backend/src/main/java/com/messaging/channel/meta/control/**`
- B1 tests under matching test package
- migration V76

B1 owns existing:
- `MetaWhatsAppConnectionServiceTests.java`
and may add connection/control tests.

B1 MUST NOT modify:
- MetaWhatsAppWebhookController.java
- Inbox controller/service/repositories/entities
- Media subsystem
- frontend
- V77
- Ads
- Broadcast

Acceptance:
- POST signup-session and reauthorize-session mint state only on explicit call;
- state GETDEL/company/user/TTL tested;
- Graph v4 onboarding validates token assets;
- same phone cannot be transferred cross-company;
- local recoverable state precedes external registration/subscription;
- stable encrypted PIN across retry;
- health caches provider validation;
- Meta provider implements safe disconnect;
- branch compiles/tests without B2.

## B2 — Messaging / Webhook / Delivery

Branch: `feature/whatsapp-runtime-r1`  
Base: `cd5961362ddba0eda45d0ee1a881e93bab2f214c`

Mission:
- secure durable webhook intake;
- inbound -> unified Inbox;
- status reconciliation;
- outbound text/reply/template/image/PDF;
- template cache/sync;
- service-window enforcement;
- media ingestion/reuse;
- provider-aware Inbox authorization/query;
- realtime/notification integration.

B2 owns existing:
- `backend/src/main/java/com/messaging/channel/meta/MetaWhatsAppWebhookController.java`
- `backend/src/test/java/com/messaging/channel/meta/MetaWhatsAppWebhookControllerTests.java`
- `backend/src/main/java/com/messaging/inbox/controller/InboxController.java`
- `backend/src/main/java/com/messaging/inbox/service/InboxQueryService.java`
- Inbox repositories needed for provider-aware queries
- Inbox DTOs only when backward-compatible augmentation is necessary
- `backend/src/main/java/com/messaging/media/service/MediaService.java`
- `backend/src/main/java/com/messaging/media/provider/StorageProvider.java`
- S3/Supabase StorageProvider implementations only if required for trusted server-side inbound media ingestion
- associated focused tests

B2 owns new:
- `backend/src/main/java/com/messaging/channel/meta/runtime/**`
- `backend/src/test/java/com/messaging/channel/meta/runtime/**`
- migration V77

B2 MAY use but not modify:
- ProviderCredentialService
- RealtimeService
- InboxMessagePostProcessor
- MessageStorageMeter/StorageQuotaService
- B1-owned Channel/Sender repositories

B2 MUST NOT modify:
- B1-owned Meta connection files
- application.yaml/.env.example
- V76
- frontend
- Ads
- Broadcast execution

Config:
- B2 may bind safe-default runtime properties under `app.channel.meta-whatsapp.runtime` without editing application.yaml. L1/B1 config merge is handled during integration if production values need YAML/env examples.

Acceptance:
- unsigned/oversized webhook not durably stored;
- valid signed callback durably inserted before 200;
- duplicates no-op;
- inbound text creates one Inbox message;
- WhatsApp Inbox uses WhatsApp permissions rather than email permissions;
- mixed Inbox does not leak channels;
- text/reply/template/media send uses clientRequestId;
- ambiguous send UNKNOWN/no blind retry;
- template rule backend enforced;
- status monotonic;
- image/PDF private media;
- notification post-processors and realtime recover correctly;
- branch compiles/tests without B1 Java implementation/V76.

## F1 — Frontend + WhatsApp UX

Branch: `feature/whatsapp-ui-r1`  
Base: `cd5961362ddba0eda45d0ee1a881e93bab2f214c`

Mission:
- Embedded Signup v4 UX;
- fresh signup session at click;
- connection health/reconnect/disconnect;
- WhatsApp Inbox/composer/template/media UX;
- safe actionable errors;
- accessibility/responsive behavior.

F1 owns:
- `frontend/src/features/channel/metaWhatsAppApi.ts`
- `frontend/src/features/channel/metaWhatsAppSdk.ts`
- WhatsApp types in `frontend/src/features/channel/types.ts`
- `frontend/src/pages/company/CompanyConnections.tsx`
- `frontend/src/pages/inbox/EmailInbox.tsx` (may refactor/rename internally but routes must stay compatible)
- new `frontend/src/features/whatsapp/**`
- new `frontend/src/components/whatsapp/**`
- colocated frontend tests/fixtures

F1 MUST NOT modify:
- backend
- Flyway
- architecture docs
- deployment secrets
- Ads

Frozen UX:
- no Meta signup state on page load;
- Connect click -> signup-session -> SDK -> FB.login v4;
- no sessionInfoVersion 3;
- FINISH/CANCEL/ERROR/timeout/popup-close safely settle once and clean listeners;
- connection status cards show ACTIVE/DEGRADED/ACTION_REQUIRED/REAUTH_REQUIRED;
- reconnect obtains a fresh reauthorize session;
- disconnect confirmation explains UnifyDesk disconnect does not delete Meta business assets;
- WhatsApp conversation view is chat-oriented, not email To/CC/BCC/Subject;
- composer state API decides free-form/template UX;
- template picker variables;
- image/PDF existing media upload flow;
- UNKNOWN send warns user not to resend blindly.

Acceptance:
- frontend tests/build using mocked frozen APIs;
- no dependency on B1/B2 branch code;
- no secret/raw Graph error displayed.

## One hot file / dependency rule

If a worker needs another owner's file:
1. do not edit it;
2. code to frozen HTTP/SQL/interface contract;
3. report exact requested integration adapter;
4. L1 resolves after branches complete.

## L1 integration order

1. inspect B1 diff + tests;
2. inspect B2 diff + tests;
3. inspect F1 diff + tests;
4. integrate B1;
5. integrate B2;
6. add/renumber V78 constraints/adapters as required;
7. integrate F1;
8. run clean migrations/backend/frontend checks;
9. run controlled provider smoke where possible;
10. freeze exact integrated QA SHA;
11. update SECURITY_QA_CHAT_PROMPT.md with exact SHA;
12. activate QA/SEC.

## Required checks

Backend:
```bash
cd unifydesk-app/backend
./mvnw spotless:check
./mvnw test
./mvnw package -DskipTests
```

Frontend:
```bash
cd unifydesk-app/frontend
npm ci
npm run format:check
npm run lint
npm test
npm run build
```

Workers report environment/pre-existing failures separately.

## Worker final report

Each reports:
- branch
- final commit SHA
- exact files changed
- migration if applicable
- tasks completed
- tests/checks and results
- contract deviation/integration request
- blocker

Do not merge.
