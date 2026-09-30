# UnifyDesk Meta WhatsApp Production Architecture

Status: FROZEN FOR ROUND 1  
Date: 2026-09-28

## Goal

Make META_WHATSAPP a production-grade channel inside the existing Spring Boot monolith and unified Inbox. Do not build a parallel inbox, media service, queue platform or broadcast engine.

## Existing reusable platform

We keep and extend:
- ChannelConnection / SenderIdentity / ProviderCredential;
- ProviderCredentialService encrypted-at-rest credentials;
- CompanyAuthorizationService and WhatsApp permission keys;
- existing InboxConversation, InboxMessage, InboxParticipant and attachment model;
- InboxMessagePostProcessor so existing notification behavior can react to WhatsApp inbound;
- existing Redis and Kafka infrastructure;
- existing RealtimeService;
- existing private Media subsystem;
- existing channel disconnect/retention lifecycle.

## Logical architecture

```
Company Connections UI
   |
   | POST signup-session (fresh one-time state)
   v
Meta Embedded Signup v4
   |
   | code + provider IDs
   v
B1 Control Plane
  state consume -> code exchange -> token/asset validation
  -> persist PENDING local connection + encrypted token/PIN
  -> register phone -> subscribe app -> reconcile provider health
  -> ACTIVE / ACTION_REQUIRED / REAUTH_REQUIRED / DEGRADED
   |
   +---- encrypted credential + frozen SQL projection ----+
                                                        |
Public Meta Webhook                                     | B2 runtime
  -> body cap                                           |
  -> exact-byte HMAC                                   |
  -> minimal durable event ----------------------------+
  -> 200 after durable handoff
       |
       v
B2 WhatsApp Event Worker
  -> dedupe
  -> resolve trusted local connection by phone/account mapping
  -> normalize inbound/status
  -> Inbox + status ledger + media
  -> post processors + realtime

Inbox send API
  -> auth + company/conversation ownership
  -> customer-service/template policy
  -> idempotency reservation
  -> Meta messages endpoint
  -> accepted/unknown/failure projection
  -> webhook reconciliation
```

## Round-1 production slice

Included:
- Embedded Signup v4 connect;
- reconnect/reauthorize;
- safe disconnect;
- live/cached health reconciliation;
- text inbound;
- image/document inbound with safe private storage;
- unsupported placeholder for other inbound types;
- text and reply outbound;
- approved template list/sync and template send;
- image and PDF/document outbound through existing Media;
- provider message ID persistence;
- sent/delivered/read/failed status reconciliation;
- customer-service-window enforcement;
- durable webhook handling, dedupe/retry/DLQ;
- notification/realtime hooks through existing platform.

Deferred:
- customer template create/edit/delete UI;
- interactive/Flows/catalog;
- contacts/location as rich first-class Inbox cards;
- audio/video outbound until Media validation coverage is expanded;
- broadcast/campaign WhatsApp transport;
- automatic coexistence enablement;
- full BSUID/usernames product UI.

## Control plane invariants

1. Signup state is created only when the user clicks Connect/Reconnect.
2. State is 256-bit random, Redis TTL <=10 minutes, company-bound, user-bound, and atomically GETDEL consumed.
3. The frontend cannot authorize WABA/account/phone ownership. Provider IDs are revalidated server-side from the exchanged token.
4. Local PENDING connection + encrypted token/PIN are persisted before provider registration/subscription side effects.
5. No provider network call is made while a long DB transaction is open.
6. Every provisioning step is retryable/reconcilable. Repeating a successful external step converges rather than duplicating ownership.
7. ACTIVE requires local credential + phone mapping + successful registration/subscription + recent provider validation.
8. Reconnect cannot transfer a provider phone/account to a different company.
9. Disconnect revokes local credentials and disables senders. It does not delete customer-owned Meta business assets.

## Connection state

Generic ChannelConnectionStatus remains unchanged for compatibility:
- PENDING
- ACTIVE
- ERROR
- DISCONNECTED

Detailed Meta product state lives in the B1-owned control-plane table and API:

- CONNECTING
- VALIDATING
- ACTIVE
- DEGRADED
- ACTION_REQUIRED
- REAUTH_REQUIRED
- DISCONNECTED
- ERROR

Separate dimensions:
- authState: VALID / EXPIRING / EXPIRED / REVOKED / MISSING_PERMISSION / UNKNOWN
- registrationState: PENDING / REGISTERED / ACTION_REQUIRED / ERROR
- subscriptionState: PENDING / SUBSCRIBED / ERROR
- coexistenceState: DISABLED / ELIGIBLE_UNVERIFIED / ENABLED / ERROR

Generic mapping:
- ACTIVE -> generic ACTIVE
- CONNECTING/VALIDATING -> generic PENDING
- DEGRADED stays generic ACTIVE while sends may fail closed depending on cause
- ACTION_REQUIRED/REAUTH_REQUIRED/ERROR -> generic ERROR
- DISCONNECTED -> generic DISCONNECTED

## Health

Health is cached, not computed by provider calls on every page render.

A B1 reconciliation operation fetches the minimum provider metadata required to validate:
- token/scopes;
- account/phone still accessible;
- phone quality/registration fields when available;
- app subscription.

Store `lastValidatedAt`, safe diagnostic code/subcode/fbtrace, and next recommended user action.

UI read is O(1) local DB. Manual Refresh queues/performs a bounded reconciliation and returns the new cached health.

## Webhook acknowledgement boundary

The current WebhookCallbackAuditService stores large sanitized raw payloads before signature validation. Meta WhatsApp Round 1 must not use that as the durable ingress boundary.

New B2 flow:
1. reject body over configured hard limit before JSON parsing/storage;
2. validate X-Hub-Signature-256 over exact bytes;
3. parse only enough structure to identify supported events;
4. insert bounded durable `meta_whatsapp_webhook_events` rows containing a payload JSONB limited to accepted signed events;
5. commit;
6. return 200;
7. worker claims/processes asynchronously.

The audit service may receive bounded metadata/result after signature verification; do not store unauthenticated raw message bodies.

## Event processing

Durable DB inbox first, Kafka optional fanout second.

Why: returning 200 requires a durable boundary even when Kafka is temporarily unavailable. The B2 table is an inbox/outbox-style durable work queue. Workers claim rows using SKIP LOCKED/leases, process idempotently and can publish internal Kafka/realtime events after DB commit.

Dedupe:
- webhook event unique key derived from provider message/status identity + kind + timestamp where needed;
- InboxMessage already uniquely keys connection + external_message_id;
- outbound attempt has unique client idempotency key;
- status ledger uniquely keys provider_message_id + status + provider timestamp/event identity.

## Tenant resolution

Trusted tenant mapping is local:
- primary hot-path lookup by `metadata.phone_number_id` -> SenderIdentity.externalId -> ChannelConnection;
- validate provider/account context from B1 SQL projection where supplied;
- require META_WHATSAPP provider and non-disconnected local state.

Never trust a companyId from a webhook payload or frontend.

## Inbound conversation mapping

Round 1 maps one WhatsApp customer identity per business phone to one InboxConversation:

`external_conversation_id = "wa:" + opaqueProviderUserId`

Today opaqueProviderUserId is normally wa_id/phone-oriented. It is intentionally not named phoneNumber in the contract so BSUID can replace/augment it later.

Inbox mapping:
- providerType META_WHATSAPP;
- participant type PHONE_NUMBER for current IDs;
- providerFolderKey = "WHATSAPP";
- subject = null;
- lastMessagePreview = bounded text/type summary;
- inbound read=false;
- post-processors run only when a new message is committed.

## Outbound

B2 owns a WhatsApp send endpoint under Inbox/WhatsApp runtime rather than changing email send providers.

Before send:
- permission whatsapp.send;
- company owns conversation;
- conversation belongs to active META_WHATSAPP connection;
- sender phone is active;
- B1 runtime SQL projection says usable;
- policy says free-form allowed OR request is an approved template;
- referenced media belongs to company and is ACTIVE.

Idempotency:
- every UI send generates clientRequestId UUID/opaque key;
- DB row reserves unique (company, connection, clientRequestId) before provider call;
- a duplicate clientRequestId returns existing result;
- definite pre-submit failures can be retried;
- timeout/connection-loss after request submission becomes UNKNOWN and is not automatically re-sent.

## Status monotonicity

Provider statuses can arrive out of order. Store provider timestamp and apply monotonic rank:
PENDING < ACCEPTED < SENT < DELIVERED < READ

FAILED is terminal only when the provider event belongs to that message and is not older than a later successful state. A late SENT must never regress READ to SENT.

Generic InboxMessageStatus remains:
- outbound accepted/sent/delivered/read are represented as SENT for current generic APIs;
- definite provider failure maps FAILED;
- detailed WhatsApp status is in B2 runtime table and WhatsApp-specific API fields.

## Templates

B2 syncs approved/current templates to a local cache. The cache is not authorization by itself: send-time checks require cached APPROVED state and bounded staleness; stale cache triggers sync/fail-closed.

Round 1 APIs expose list + sync + send. Template mutation is deferred.

## Media

Outbound:
- existing Media upload/finalization;
- restrict Round 1 to JPEG/PNG/WebP and PDF already supported by local validator;
- B2 reads trusted private object, uploads to Meta or uses the provider-supported media upload path, and sends by Meta media ID;
- no public permanent URL.

Inbound:
- webhook supplies Meta media ID;
- worker retrieves short-lived authenticated media URL;
- downloads with strict byte/time limits;
- validates MIME and size;
- persists into existing storage through a small lead-approved/B2 adapter, not a new storage system.

If the existing MediaService cannot safely ingest server-fetched media without a small adapter, B2 owns that integration adapter and L1 reviews it.

## Realtime + notification

After a new inbound InboxMessage transaction commits:
- existing InboxMessagePostProcessor chain is invoked;
- notification platform can materialize inbox activity;
- B2 emits the same existing Inbox realtime-change shape used by the Inbox, or extends it in a backward-compatible way owned by B2.

No webhook thread enumerates company members.

## Reliability

- provider calls have bounded connect/read timeouts;
- retry only transient, definitely-not-submitted operations;
- exponential backoff + jitter for provisioning/reconciliation/event processing;
- dead-letter state after bounded attempts;
- admin/ops replay operates by durable event ID;
- no infinite raw webhook retention.

## Ownership boundary

B1 owns connection/control-plane code and V76.
B2 owns webhook/runtime/inbox adaptation and V77.
F1 owns all WhatsApp frontend UX.
L1 owns post-integration V78 cross-module constraints/adapters if needed.

B2 may query B1 tables only through frozen SQL columns documented in DATA-MODEL.md and a B2-owned JdbcTemplate projection. It must not import B1 implementation classes.
