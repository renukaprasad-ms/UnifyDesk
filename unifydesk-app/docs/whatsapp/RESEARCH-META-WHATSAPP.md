# Meta WhatsApp 2026 Research

Status: FROZEN FOR ROUND 1  
Research date: 2026-09-28  
Owner: L1 / Architecture Lead

## Purpose

This file records the external provider facts used by the Round-0 architecture. It is not a substitute for Meta's live documentation. Provider behavior that could not be proved without a real account is called out as an explicit validation item instead of being guessed.

## Sources used

Primary/authoritative sources consulted on 2026-09-28:

- Meta WhatsApp Business Platform documentation: https://developers.facebook.com/docs/whatsapp/
- Meta Embedded Signup documentation: https://developers.facebook.com/documentation/business-messaging/whatsapp/embedded-signup/
- Meta Embedded Signup v4: https://developers.facebook.com/documentation/business-messaging/whatsapp/embedded-signup/version-4
- Meta Cloud API overview: https://developers.facebook.com/docs/whatsapp/cloud-api/overview
- Meta-maintained WhatsApp Business Platform Postman collection: https://www.postman.com/meta/whatsapp-business-platform/
- Meta Developers — Unified Onboarding on WhatsApp, published 2026-06-16: https://developers.meta.com/resources/videos/unified-onboarding-whatsapp/
- Meta Developers — WhatsApp Account Model Evolution, published 2026-06-16: https://developers.meta.com/resources/videos/whatsapp-account-model-evolution/
- Meta Developers — WhatsApp Usernames, published 2026-06-16: https://developers.meta.com/resources/videos/whatsapp-usernames/
- Twilio current ESU v4 migration notice, used as an independent Tech Provider implementation reference: https://help.twilio.com/articles/54890520784027
- Twilio Tech Provider integration guide: https://www.twilio.com/docs/whatsapp/isv/tech-provider-program/integration-guide

## 2026 changes that affect this repository

### Embedded Signup v4 is required for the architecture

Meta introduced a unified Embedded Signup v4 flow in 2026. Meta's current developer material describes v4 as the migration path from legacy v2/v3. Current Tech Provider guidance states that v2/v3 new-customer onboarding is retired on 2026-10-15.

The current frontend sends:

`extras.setup = {}` and `sessionInfoVersion: '3'`.

That must not be the Round-1 target. Round 1 freezes v4 semantics: a v4 Facebook Login for Business configuration ID, no sessionInfoVersion 3, and products/assets/permissions configured in Meta App Dashboard.

The configuration must include only the WhatsApp products/assets/scopes UnifyDesk actually needs. Current Tech Provider guidance identifies:
- Facebook Login for Business;
- WhatsApp Embedded Signup login variation;
- WhatsApp Cloud API;
- WhatsApp accounts asset access;
- `whatsapp_business_management`;
- `whatsapp_business_messaging`.

Allowed JavaScript SDK domains and OAuth redirect URIs must be HTTPS and explicitly configured.

### WhatsApp account model is evolving

Meta announced the WhatsApp Account Model Evolution in June 2026. The legacy WhatsApp Business Account concept is being separated into:
- a WhatsApp Account for phone-number ownership; and
- a Messaging Account for templates/billing/integration.

Meta describes this as enabling the same phone number to participate in multiple partner integrations while billing/integration ownership remains separable.

Round 1 therefore MUST NOT make new business logic depend on the assumption that one legacy WABA ID is the permanent universal key. The database keeps legacy WABA compatibility while allowing WhatsApp Account and Messaging Account identifiers to be stored when returned by the current APIs.

### Usernames / BSUID

Meta announced WhatsApp Usernames and a Business Scoped User ID (BSUID) model in June 2026. Phone numbers can no longer be assumed to be the only durable customer identity forever.

Round 1 does not attempt full username rollout, but webhook parsing and conversation identity are versioned so an opaque provider user identifier can be added without re-keying all inbox data. We continue to accept the current `wa_id`/phone-oriented payloads during Round 1.

## Embedded Signup callback

The browser receives two related pieces of information:
1. Facebook Login returns an authorization code.
2. the Embedded Signup window posts `WA_EMBEDDED_SIGNUP` events such as FINISH, CANCEL and ERROR. FINISH currently supplies account/WABA and phone-number identifiers.

The frontend-provided IDs are hints only. The backend must:
- consume a one-time state bound to company + user;
- exchange the code server-side;
- validate the token and scopes/assets against Meta;
- resolve the account and phone number from Meta;
- reject a phone/account pair not granted to that token;
- prevent another UnifyDesk company from claiming an already-owned local provider asset.

The code and access token must never be logged or returned after exchange.

## Token lifecycle

Embedded Signup is an OAuth/business-login flow. Access tokens are server secrets. Exact token type/expiry can depend on the configured v4 access-token mode and Meta program setup.

Architecture rule:
- persist provider token only through existing encrypted ProviderCredential storage;
- persist provider-reported expiry when available;
- validate/debug the token during onboarding and reconciliation;
- distinguish expired/revoked/missing-permission from transient provider outage;
- require reauthorization instead of silently continuing with invalid credentials.

A real Meta account test is required to confirm the token type and expiration delivered by UnifyDesk's own v4 configuration before production release.

## Registration and subscription

Current Cloud API/Embedded Signup integration still requires the selected business phone number to be registered for Cloud API operation. Registration uses a six-digit two-step-verification PIN. The server must generate it, persist it encrypted, and reuse the same PIN during idempotent retry instead of generating a different secret after every partial failure.

The Meta app must also be subscribed to the relevant WhatsApp account so the app receives message/status webhooks. Registration/subscription are external side effects; they must be treated as idempotent/reconcilable provisioning steps, not as one all-or-nothing DB transaction.

Disconnect must revoke UnifyDesk's local ability to use the credential. It must not delete customer-owned Meta business assets. Provider-side unsubscription is only performed when it is safe for the whole UnifyDesk connection.

## Cloud API messages

The Meta-maintained Cloud API collection documents `/{phone-number-id}/messages` for text, replies/context, templates and media. Successful send returns a provider message ID. Final delivery is asynchronous.

Round-1 send state therefore distinguishes:
- local pending;
- provider accepted with provider message ID;
- sent;
- delivered;
- read;
- failed;
- unknown/ambiguous.

A request timeout after bytes may have reached Meta is UNKNOWN, not a safe automatic retry.

## Customer-service/template rule

Current platform behavior retains the customer service window: free-form business replies are allowed while the customer service window opened by the customer's inbound activity is active; outside that window, an approved message template is required.

Pricing changes are separate from send eligibility and can change independently. Round 1 stores the last trusted inbound-customer timestamp and uses a configurable policy boundary (default 24 hours), but it also treats provider policy errors as authoritative. The UI is guidance only; the backend blocks an out-of-window free-form send.

Templates remain approved provider resources. Meta's current template APIs support list/get/create/edit/delete and categories including MARKETING, UTILITY and AUTHENTICATION. Round 1 implements list/sync + sending approved templates. Create/edit/delete is deferred until the runtime connection is stable and QA has passed, to keep the first production slice bounded.

## Webhooks

Current Meta webhook semantics require:
- GET verification using the configured verify token and returning `hub.challenge`;
- POST authenticity validation using `X-Hub-Signature-256` with the Meta App Secret and exact request bytes;
- message and status events under the WhatsApp messages subscription.

Meta documentation/examples show statuses including sent, delivered, read and failed. Status notifications may arrive out of apparent order; provider timestamps must be used and state transitions must be monotonic.

Architecture rules:
- verify signature before parsing trusted content;
- reject oversized bodies before durable payload storage;
- after signature verification, create a bounded durable event and acknowledge quickly;
- dedupe both webhook delivery and provider message/status effects;
- never derive tenant/company from an untrusted webhook company ID alone.

## Media

Meta's Cloud API supports upload, retrieve URL, download and delete media. A retrieved media URL is short-lived (documented as five minutes) and still requires authentication.

Round 1:
- outbound: image and PDF/document through the existing private Media subsystem;
- inbound: image/document metadata and safe download into the existing Media subsystem;
- audio/video are normalized as unsupported placeholders in the first production slice unless QA proves current local Media MIME/size validation supports them safely.

No permanent Meta media URL is stored as application storage.

## Provider errors and observability

Graph errors are structured. Round 1 retains only safe diagnostics:
- HTTP status;
- Meta error `type`;
- numeric `code`;
- numeric `error_subcode` when present;
- `fbtrace_id`;
- a bounded/redacted provider message category.

Never persist/log:
- access token;
- app secret;
- authorization code;
- registration PIN;
- Authorization header;
- raw request containing credentials;
- complete customer message bodies just for diagnostics.

## Coexistence

Meta supports coexistence/migration scenarios for numbers associated with the WhatsApp Business App, but the exact eligibility and routing semantics are account/program dependent and are changing alongside the 2026 account model.

Round-1 architecture contains an explicit coexistence capability/state field but the customer-facing coexistence path is FEATURE-GATED OFF by default. Production enablement requires a controlled real-account test proving:
- eligible number onboarding;
- app/Cloud API message visibility;
- template/account ownership;
- webhook routing;
- disconnect behavior.

## Audit of Product Manager observations

| Observation | Code evidence | Disposition | Severity | Owner |
|---|---|---|---|---|
| signup state minted too early | CompanyConnections calls prepareMeta on page load; signupConfig creates Redis state immediately | CONFIRMED | High | F1+B1 |
| sessionInfoVersion 3 | metaWhatsAppSdk.ts hard-codes `sessionInfoVersion: '3'` | CONFIRMED / obsolete for target | Critical for new onboarding after 2026-10-15 | F1 |
| Graph errors lose diagnostics | MetaGraphClient maps RestClientResponseException to HTTP-only BadRequestException | CONFIRMED | Medium | B1/B2 |
| provider side effects before persistence | register + subscribe occur before persistenceService.persist | CONFIRMED | High | B1 |
| DB-only health | MetaWhatsAppProvider checks only ChannelConnectionStatus.ACTIVE | CONFIRMED | High | B1 |
| webhook audit but no inbox ingestion | controller verifies/parses/audits and returns; comment says ingestion remains provider-specific | CONFIRMED | Critical product gap | B2 |
| outbound provider incomplete | MetaWhatsAppProvider only implements base health capability | CONFIRMED | Critical product gap | B2 |
| token/reconnect/disconnect lifecycle incomplete | no Meta-specific reconciliation/reauth state; generic disconnect only | CONFIRMED | High | B1 |
| coexistence unclear | signup response always returns coexistenceEnabled=false; no implementation | CONFIRMED | Medium; feature-gate in R1 | B1/F1 |
| tests too small | only connection-service and webhook-controller tests; no runtime messaging/inbox/status/template tests | CONFIRMED | High | all + QA |

## Real-account validation still required

Before production acceptance QA must verify with UnifyDesk's actual Meta app:
- v4 Configuration ID and enabled assets/products;
- actual token type/expiry and debug-token fields;
- registration behavior for a fresh number and already-registered idempotent retry;
- WABA/WAAC/PMA identifiers returned by the account-model state available to UnifyDesk;
- webhook subscription and delivery retry behavior;
- coexistence eligibility/routing if product chooses to enable it;
- provider message/status behavior for text, reply, template, image and document;
- revoked permission and expired token behavior;
- current throughput/quality fields and any account-specific messaging limits.
