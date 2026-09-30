# Prompt — B1 Backend Connection / Control Plane

You are B1 for UnifyDesk Meta WhatsApp.

Repository:
`renukaprasad-ms/messaging`

Application:
`unifydesk-app/`

Docs:
`unifydesk-app/docs/whatsapp/`

Your branch:
`feature/whatsapp-control-plane-r1`

Your EXACT frozen base SHA:
`cd5961362ddba0eda45d0ee1a881e93bab2f214c`

Create your branch from exactly that SHA. Do not branch from moving dev. Do not merge your branch.

## Read first

From docs/whatsapp:
- README.md
- RESEARCH-META-WHATSAPP.md
- ARCHITECTURE.md
- DECISIONS.md
- DATA-MODEL.md
- API-CONTRACT.md
- SECURITY.md
- PERFORMANCE.md
- META-SETUP-RUNBOOK.md
- TASKS.md

Then inspect current connection code and tests.

## Mission

Implement the complete Meta WhatsApp connection/control plane only.

You own:
- Embedded Signup v4 server contract
- fresh one-time signup state
- safe Graph provider error model
- authorization-code/token validation
- provider asset discovery/validation
- local recoverable provisioning state
- phone registration
- app/account subscription
- connection state machine
- credential/PIN lifecycle
- cached health/reconciliation
- reconnect/reauthorize
- provider-safe disconnect
- B1 migration/tests

## Exact ownership

Existing files you may modify:
- backend/src/main/java/com/messaging/channel/meta/MetaGraphClient.java
- MetaWhatsAppConnectionController.java
- MetaWhatsAppConnectionService.java
- MetaWhatsAppPersistenceService.java
- MetaWhatsAppProperties.java
- MetaWhatsAppProvider.java
- MetaWhatsAppSignupStateService.java
- backend/src/main/java/com/messaging/channel/meta/dto/**
- backend/src/main/java/com/messaging/channel/repository/ChannelConnectionRepository.java
- backend/src/main/java/com/messaging/channel/repository/SenderIdentityRepository.java
- backend/src/main/resources/application.yaml
- unifydesk-app/.env.example
- backend/src/test/java/com/messaging/channel/meta/MetaWhatsAppConnectionServiceTests.java

New code:
- backend/src/main/java/com/messaging/channel/meta/control/**
- matching B1 tests

Migration:
`V76__meta_whatsapp_control_plane.sql`

## Required implementation

1. Replace state-producing GET behavior with frozen POST signup-session semantics.
2. Keep state high entropy, company/user bound, <=10m TTL, atomic GETDEL.
3. Implement v4-compatible completion contract.
4. Never trust browser provider IDs without validating them using the exchanged token.
5. Preserve backward-compatible encrypted credential JSON fields:
   accessToken, wabaId, phoneNumberId, graphApiVersion.
6. Add safe Graph exception parsing: httpStatus/type/code/subcode/fbtraceId + retryability category; no raw token/body leak.
7. Persist local connection/control state and encrypted stable PIN before external registration/subscription side effects.
8. Make provisioning idempotent/reconcilable.
9. Add detailed Meta product/auth/registration/subscription/coexistence state from DATA-MODEL.md.
10. Implement cached provider health and manual reconcile.
11. Reauthorization must bind to expected existing asset and reject silent transfer.
12. Meta provider implements DisconnectableChannelProvider semantics safely; never delete customer-owned Meta assets.
13. Keep coexistence disabled by default.
14. Do not work on Ads.

## Worker independence

B2 does not exist on your branch. Do not import or anticipate B2 implementation classes.

Do not edit B2-owned webhook/Inbox/Media files.

## Required tests

At minimum:
- fresh state only per explicit call
- expiry/replay/company/user mismatch
- parallel state consume
- Graph error safe parsing
- token missing scope
- tampered WABA/phone
- multiple assets handling
- cross-company same phone race
- registration retry
- subscription retry
- provider success + local retry convergence
- stable encrypted PIN
- token expiry/revocation -> REAUTH_REQUIRED
- provider transient -> DEGRADED
- safe disconnect
- reconnect same asset
- reconnect different asset rejection
- no token/PIN in DTO/log assertions where feasible

Run:
```bash
cd unifydesk-app/backend
./mvnw spotless:check
./mvnw test
./mvnw package -DskipTests
```

## Forbidden

Do not modify:
- MetaWhatsAppWebhookController.java
- inbox/**
- media/**
- frontend/**
- V77
- Broadcast
- Ads
- frozen docs

If a frozen contract cannot be implemented without another owner's file, report an integration request rather than editing it.

## Final report

Report:
- branch
- final commit SHA
- files changed
- V76 migration
- APIs implemented
- connection/provisioning/health behavior
- tests/checks + results
- any contract deviation/integration request
- blocker

Do not merge.
