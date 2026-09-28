# Product Brief — UnifyDesk Meta WhatsApp

## Product objective

Turn the existing Meta WhatsApp connection into a production-grade UnifyDesk messaging channel that a company can connect, operate, monitor, recover, and use from the unified inbox without needing to understand Meta Graph API internals.

The desired end-to-end experience is:

```
Company Connections
  -> Connect WhatsApp
  -> Meta Embedded Signup
  -> select/create business/WABA/phone as supported
  -> UnifyDesk validates and persists the connection
  -> webhook subscription is active
  -> inbound customer message appears in UnifyDesk Inbox
  -> agent replies from UnifyDesk
  -> customer receives the WhatsApp message
  -> sent/delivered/read/failed state is reflected in UnifyDesk
```

After this is reliable, support templates, media, reconnect/disconnect, token/revocation handling, observability, and operational recovery.

## Current implementation that must be inspected

At bootstrap time, the repository contains an existing Meta WhatsApp implementation under:

- `backend/src/main/java/com/messaging/channel/meta/`
- `frontend/src/features/channel/metaWhatsAppApi.ts`
- `frontend/src/features/channel/metaWhatsAppSdk.ts`
- company Connections UI
- existing channel/provider abstractions
- existing inbox/conversation/message models
- existing Media subsystem
- existing Kafka/Redis/background-processing infrastructure
- existing notification system
- existing Broadcast system where WhatsApp may later need integration

L1 must inspect the actual current code and tests rather than designing from this brief alone.

## Known audit observations — verify, do not blindly assume

These are starting hypotheses from the Product Manager audit. L1 must confirm them against the current frozen code and current official Meta documentation before turning them into tasks:

1. signup state appears to be created when the Connections page prepares Meta rather than immediately before the user starts Embedded Signup, creating an expiry/staleness risk;
2. frontend Embedded Signup currently contains `sessionInfoVersion: '3'`, which may require modernization for current Meta Embedded Signup requirements;
3. Meta Graph failures are reduced to generic HTTP errors and lose safe provider diagnostics such as Meta error code/subcode and `fbtrace_id`;
4. onboarding can perform Meta-side registration/subscription before local persistence, so partial-failure/retry/idempotency semantics need careful design;
5. connection health currently appears too dependent on local DB status rather than live Meta state;
6. webhook verification/auditing exists, but inbound message/status ingestion into the unified inbox appears incomplete;
7. the Meta WhatsApp provider currently appears incomplete as an outbound messaging capability;
8. token expiry/revocation, reconnect, disconnect, app subscription lifecycle, and recovery need a complete state model;
9. coexistence / existing WhatsApp Business App number behavior must be researched rather than guessed;
10. current test coverage appears much smaller than the full production surface.

These observations are not frozen requirements until L1 verifies them.

## Product requirements

The architecture must cover at least:

### Connection and onboarding

- current supported Meta Embedded Signup flow;
- one-time anti-CSRF/replay state;
- fresh state generated at the correct time;
- secure code exchange;
- WABA and phone discovery/selection;
- new/existing number scenarios supported by current Meta APIs;
- coexistence behavior if supported/needed;
- registration/subscription semantics;
- idempotent retry after partial failure;
- clear user-facing error states;
- reconnect/re-authorization;
- disconnect without accidentally destroying customer-owned Meta assets.

### Credential and connection lifecycle

- encrypted credential storage using existing secure infrastructure;
- token expiry and revoked-permission detection;
- no tokens/secrets in logs or frontend responses;
- connection state machine;
- live health/reconciliation;
- WABA/phone ownership validation;
- app subscription state;
- operational recovery.

### Inbound webhooks

- verification challenge;
- HMAC signature verification;
- bounded request/body handling;
- fast acknowledgement;
- durable asynchronous processing;
- deduplication/idempotency;
- company/connection resolution;
- inbound messages into Inbox;
- delivery/read/failure status updates;
- replay, malformed payload, unknown WABA/phone, duplicate and out-of-order handling;
- privacy-safe audit and metrics.

### Outbound messaging

- text/reply;
- template messaging;
- media types justified for Round 1;
- provider message ID persistence;
- retry/idempotency semantics;
- Meta conversation/template-window enforcement on the backend;
- rate/provider failure handling;
- clear final/ambiguous states.

### Templates and media

Research and design the correct current Meta model for:

- approved message templates;
- language/category/components/variables;
- template sync/create/edit flows that are actually supported;
- template status/rejection handling;
- inbound/outbound media;
- reuse of the existing private Media subsystem rather than creating parallel storage.

### Unified Inbox integration

- contact resolution;
- conversation creation/lookup;
- inbound/outbound message persistence;
- reply/context mapping;
- unread/read behavior;
- realtime updates;
- assignment/team semantics where current Inbox supports them;
- company isolation.

### Security and abuse resistance

Threat-model at least:

- cross-company WABA/phone access;
- forged webhooks;
- replay and duplicate events;
- stolen/expired/revoked Meta tokens;
- secret leakage through logs/errors;
- arbitrary provider IDs supplied by frontend;
- race conditions during connect/reconnect;
- webhook storage abuse;
- oversized/deep JSON payloads;
- outbound duplicate sends;
- ambiguous provider outcomes;
- permission revocation during operations;
- unsafe media;
- tenant isolation across inbox, templates and send paths.

### Performance and reliability

Use existing Kafka/Redis/DB patterns where appropriate. Avoid provider calls in long database transactions.

L1 must explicitly analyze:

- webhook acknowledgement latency;
- message processing throughput;
- idempotency storage;
- retry/backoff;
- DLQ/recovery;
- ordering assumptions;
- message/status concurrency;
- database indexes;
- space growth;
- connection health-check cost;
- provider API rate limiting.

## Product UX principles

The user should see product language, not raw Graph API concepts unless Meta requires a customer action.

The UI should distinguish states such as:

- not connected;
- connecting;
- connected;
- action required;
- authorization expired;
- phone registration problem;
- webhook/subscription problem;
- temporarily unhealthy;
- disconnected.

Errors should be actionable and safe. Never expose access tokens, app secrets, authorization codes, PINs or raw sensitive payloads.

## Round-1 worker shape

L1 must freeze work so three implementation chats can execute in parallel:

- **B1:** connection/control-plane/lifecycle/health/credential-side work;
- **B2:** webhook/runtime/inbound/outbound/status/template/media delivery-side work;
- **F1:** frontend connection/inbox/template/health UX according to frozen HTTP/state contracts.

The exact boundary is L1's responsibility after code research. One hot file has one owner. B2 must not compile against B1-only classes created after the frozen base; use frozen SQL/contracts/interfaces/seams where needed. F1 must work against frozen HTTP/DTO contracts without waiting for backend implementation.

## Out of scope for this project

- Meta Ads feature work;
- redesigning the entire Inbox unrelated to WhatsApp;
- replacing working generic channel abstractions without justification;
- a second media platform;
- a second messaging/broadcast engine when existing infrastructure can be extended safely.

## Definition of done

WhatsApp is not considered production-ready merely because Embedded Signup returns success.

The integrated system must demonstrate, with automated tests plus real Meta test/sandbox/account verification where required:

- reliable connect;
- safe reconnect and disconnect;
- correct health/action-required status;
- verified and deduplicated inbound webhook processing;
- inbound text into Inbox;
- outbound reply;
- provider message ID tracking;
- sent/delivered/read/failed reconciliation;
- template-window enforcement;
- agreed media support;
- token/revocation behavior;
- cross-company isolation;
- retries without duplicate visible messages or duplicate sends;
- observability sufficient to diagnose Meta failures;
- documented current Meta prerequisites and operational runbook.

QA/SEC determines findings; L1 owns remediation decomposition. Product Manager decides final product acceptance.
