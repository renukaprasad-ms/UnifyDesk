# Meta WhatsApp Round-1 Integrated QA / Security Feedback

Date: 2026-09-29  
QA branch: `review/whatsapp-security-qa-r1`  
Frozen integrated base: `cd41f5b4dc3389e2b6213ee176558d70010827d7`

Integrated inputs reviewed:

- B1: `d8b98df2a6b1ea277081b7bde179dbe8f8b60781`
- B2: `5ab5f71a06f6d605a02b9211c8d47b31b51aeefe`
- F1: `35c954504bf9dea3c981b9f5d34308ac0321db26`
- L1 integration: V78 cross-module constraints + runtime configuration wiring

## Final disposition

**NOT RELEASE-READY**

The integrated candidate has release-blocking contract gaps in both control-plane asset authorization and runtime webhook/delivery-state handling. Additional medium reliability, ordering, performance and frontend-template defects are also present.

Real Meta provider behavior was **NOT VERIFIED** in this QA environment.

## Verification classification

### Source / local-static verification completed

Reviewed the frozen QA prompt and contracts, then traced implementation and existing regression coverage for:

- Embedded Signup v4 frontend flow and listener cleanup;
- Redis one-time signup state, TTL, company/user binding and parallel consume behavior;
- token scope/asset validation and cross-company phone ownership;
- encrypted token/PIN persistence seams;
- connect/reconnect/disconnect and purge cancellation;
- cached health/reconciliation behavior;
- exact-byte webhook signature verification and durable event storage;
- webhook dedupe/worker leases/retry/dead handling;
- tenant mapping through sender + B1 projection;
- Inbox permission separation;
- inbound mapping and postprocessor exactly-once intent;
- outbound idempotency reservation, policy enforcement, template/media ownership;
- provider failure classification and UNKNOWN handling;
- message state machine ordering;
- template cache/send validation;
- private media flow and Meta URL allow-listing;
- V76/V77/V78 DDL and cross-module FK intent;
- WhatsApp frontend connect, Inbox, template, media, UNKNOWN warning and disconnect UX.

### Automated suites

Required backend commands:

- `./mvnw spotless:check`
- `./mvnw test`
- `./mvnw clean test`
- `./mvnw package -DskipTests`

Required frontend commands:

- `npm ci`
- `npm run format:check`
- `npm run lint`
- `npm test`
- `npm run build`

**Result: NOT EXECUTED in this QA session.**

Reason: the connected GitHub repository is readable/writable, but the QA runtime does not contain a checkout of the private repository and does not have GitHub credentials exposed for a direct clone. The frozen merge SHA has no usable workflow execution attached to it. Per the assignment, runner-empty/skipped GitHub Actions evidence was not counted as PASS or FAIL.

A QA-only regression test was added for WA-R1-001:

`backend/src/test/java/com/messaging/channel/meta/MetaWhatsAppRound1QaRegressionTests.java`

It expresses the frozen contract expectation that an unsupported signed webhook envelope must be rejected.

### Migration execution

- V76 source reviewed.
- V77 source reviewed.
- V78 source reviewed.
- Fresh V1 -> V78 Flyway execution: **NOT EXECUTED**.
- Representative pre-WhatsApp -> V78 upgrade: **NOT EXECUTED**.
- Real PostgreSQL duplicate/constraint race checks: **NOT EXECUTED**.

Therefore migration correctness is **source-reviewed but not runtime-verified**.

### Real Meta provider verification

**NOT VERIFIED**

No real Meta app/test WABA/phone credentials were available in this QA environment, so the following remain provider-unverified:

- real Embedded Signup v4;
- exchanged token fields/expiry;
- phone registration;
- subscribed_apps behavior;
- real inbound webhook signatures/payload variants;
- text/template/media sends;
- delivered/read callbacks;
- revocation behavior;
- current account-model identifiers;
- coexistence;
- throughput/quality behavior.

Mocked/local tests must not be interpreted as provider verification.

---

# Findings

## High

### WA-R1-001 — Signed unsupported webhook envelopes are accepted as 200

- **Severity:** High
- **Affected invariant:** WA-SEC-06, WA-SEC-07; WEBHOOK-CONTRACT request acceptance step 7.
- **Owner:** B2
- **Status:** OPEN

#### Reproduction

Submit a correctly signed body such as:

```json
{
  "object": "instagram",
  "entry": []
}
```

to `POST /api/v1/webhooks/meta/whatsapp`.

#### Expected

After signature verification, the controller must require the supported WhatsApp webhook object/change envelope. A signed but structurally unsupported request must return 400 and must not be treated as a successfully accepted WhatsApp callback.

#### Actual

`MetaWhatsAppWebhookNormalizer.normalize()` only requires the root to be a JSON object. Missing/non-array `entry` returns an empty event list. The controller then calls `eventStore.persist(emptyList)` and returns `200 EVENT_RECEIVED`.

There is no validation that `object == "whatsapp_business_account"` or that the payload contains a supported WhatsApp change envelope before durable acceptance.

#### Evidence

- `MetaWhatsAppWebhookController.receiveExactBytes()` returns 200 after `persist(normalizer.normalize(...))`.
- `MetaWhatsAppWebhookNormalizer.normalize()` returns an empty event list when `entry` is absent/non-array.
- Frozen `WEBHOOK-CONTRACT.md` explicitly requires supported WhatsApp object/change envelope validation.
- QA regression test added: `MetaWhatsAppRound1QaRegressionTests.rejectsUnsupportedSignedWebhookEnvelopeBeforeDurableAcceptance`.

#### Required regression test

Controller-level test using a valid HMAC for:
1. wrong `object`;
2. missing `entry`;
3. malformed entry/change shape.

Assert 400 and zero durable inserts.

---

### WA-R1-002 — Detailed WhatsApp delivery state is not recoverable from the Inbox API/UI

- **Severity:** High
- **Affected invariant:** MESSAGE-STATE detailed runtime authority; API-CONTRACT frontend behavior; Round-1 delivered/read UX.
- **Owner:** B2 + F1 (L1 should split remediation by ownership)
- **Status:** OPEN

#### Reproduction

1. Send an outbound WhatsApp message.
2. Receive `sent`, `delivered`, then `read` provider status webhooks.
3. Reload the browser or navigate away/back to the WhatsApp Inbox.
4. Fetch the normal conversation detail/messages API.

#### Expected

The user-visible message status must recover the durable WhatsApp runtime state and display the actual detailed status (`SENT/DELIVERED/READ/FAILED/UNKNOWN`).

The frozen contract explicitly says F1 must use WhatsApp runtime status, not infer detailed delivery from generic `InboxMessageStatus.SENT`.

#### Actual

`MetaWhatsAppStatusService` persists detailed status into `meta_whatsapp_message_runtime`, but generic `InboxMessageResponse` contains no WhatsApp runtime status field.

`InboxQueryService.messages()` returns only generic Inbox state. For successful statuses, `MetaWhatsAppStatusService` maps all SENT/DELIVERED/READ states to generic `InboxMessageStatus.SENT`.

`WhatsAppInbox.messageStatus()` therefore falls back to:
- FAILED -> FAILED
- generic SENT -> SENT

after a reload. DELIVERED/READ are lost from the user-visible projection.

The frontend also does not subscribe to `inbox.whatsapp.status.changed`, so status webhooks do not update the WhatsApp view in real time.

#### Evidence

- `MetaWhatsAppStatusService.apply()`: successful statuses set generic Inbox status to `SENT`.
- `InboxMessageResponse`: no detailed WhatsApp runtime status.
- `InboxQueryService.messages()`: does not join `meta_whatsapp_message_runtime`.
- `WhatsAppInbox.messageStatus()`: relies on ephemeral local send results or generic Inbox status.
- `MetaWhatsAppRealtimePublisher.statusChanged()` emits `inbox.whatsapp.status.changed`, but `WhatsAppInbox` has no corresponding subscription.

#### Required regression test

Integrated backend/frontend regression:
1. persist an outbound runtime row;
2. apply DELIVERED then READ;
3. reload/fetch conversation detail;
4. assert API returns durable detailed status and UI renders READ;
5. emit realtime status event and assert the open conversation updates without manual refresh.

---


### WA-R1-006 — WABA authorization uses the union of granular-scope targets instead of requiring the selected asset in every required scope

- **Severity:** High
- **Affected invariant:** WA-SEC-01, WA-SEC-02; API-CONTRACT asset validation; control-plane ownership validation.
- **Owner:** B1
- **Status:** OPEN

#### Reproduction

Use a valid inspected token whose top-level scopes include both required permissions, but whose granular targets are split:

- `whatsapp_business_management` -> `waba-management-only`
- `whatsapp_business_messaging` -> `waba-messaging-only`

Then complete signup selecting `waba-management-only`.

#### Expected

The selected WABA must be authorized for every required granular permission relevant to the operation. An asset granted only to one required scope must be rejected before any WABA/phone provider lookup or persistence.

#### Actual

`MetaWhatsAppConnectionService.grantedTargetIds()` unions target IDs from both required scopes into one set. `resolveWabaId()` then checks only whether the selected WABA exists in that union.

As a result, a WABA present in the management target list but absent from the messaging target list can pass the local asset-authorization check.

#### Evidence

QA contract test already present on this branch:

`MetaWhatsAppRound1ControlPlaneQaTests.selectedWabaMustBeGrantedByEveryRequiredGranularScope`

The test expects `WHATSAPP_ASSET_NOT_GRANTED` before `getWaba()`, but the current union-based implementation does not enforce that condition.

#### Required regression test

Keep the QA test above and add production-facing coverage for:
1. WABA present in both required granular target lists -> allowed;
2. present only in management -> rejected;
3. present only in messaging -> rejected;
4. multiple targets with one true intersection -> only the intersection is selectable.

---

## Medium

### WA-R1-003 — No distributed per-phone outbound limiter / Retry-After control

- **Severity:** Medium
- **Affected invariant:** PERFORMANCE “Rate / provider limits”; provider-protection fault behavior.
- **Owner:** B2, with L1 config wiring if needed
- **Status:** OPEN

#### Reproduction

Issue concurrent sends for the same WhatsApp phone from multiple backend instances or workers.

#### Expected

The frozen performance contract requires a conservative configurable per-phone distributed Redis limiter, with safe behavior when Redis is unavailable and provider throttling/429 is observed. Retry-After should be honored where available.

#### Actual

The outbound send path goes directly from authorization/policy/idempotency to `MetaWhatsAppRuntimeGraphClient`.

No WhatsApp Redis limiter is present in the runtime package or configuration. `429` is classified as a retryable provider failure, but no Retry-After value is parsed or used and there is no adaptive per-phone throttling.

#### Evidence

- `MetaWhatsAppMessageService.send()` has no limiter dependency.
- No WhatsApp-specific Redis limiter implementation is present.
- `MetaWhatsAppRuntimeGraphClient` classifies 429 but does not parse Retry-After.
- Frozen `PERFORMANCE.md` requires a per-phone distributed Redis limiter.

#### Required regression test

Two-instance Redis integration test:
- configure N sends/sec per phone;
- concurrently submit from both instances;
- prove aggregate accepted rate is bounded;
- prove Redis outage fails closed or follows the explicitly approved conservative fallback;
- provider 429 + Retry-After prevents immediate retry/burst.

---

### WA-R1-004 — Inbound media can be duplicated after a crash between media ingest and message linking

- **Severity:** Medium
- **Affected invariant:** WA-SEC-08 replay/idempotency; WEBHOOK-CONTRACT image/document retry rule.
- **Owner:** B2
- **Status:** OPEN

#### Reproduction

For an inbound image/document event:

1. canonical Inbox message already exists;
2. `hasMediaLink(...)` is false;
3. authenticated media is downloaded;
4. `mediaService.ingestTrustedCompanyMedia(...)` successfully creates/stores Media;
5. process crashes before `inboxPersistence.linkMedia(...)`;
6. webhook event lease expires and worker retries.

#### Expected

Retry must enrich the same message without creating a second stored copy of the same provider media.

#### Actual

On retry, `hasMediaLink(...)` remains false because the crash occurred before the link. The worker downloads and ingests the provider media again, creating another Media object, then attempts to link that second copy.

The unique key on `meta_whatsapp_message_media(message_id, provider_media_id)` protects the link row only; it does not make the preceding Media/storage ingest idempotent.

#### Evidence

`MetaWhatsAppWebhookWorker.processMessage()` sequence:

1. `persistInbound`
2. `hasMediaLink`
3. provider metadata/download
4. `ingestTrustedCompanyMedia`
5. `linkMedia`

There is no provider-media reservation/idempotency key before step 4.

#### Required regression test

Fault-injection integration test:
- force an exception/crash after Media ingest but before link;
- retry same durable event;
- assert exactly one stored media object and one attachment/link exist.

---

### WA-R1-005 — Provider-accepted send can remain permanently PENDING after process loss

- **Severity:** Medium
- **Affected invariant:** outbound reliability/idempotency; MESSAGE-STATE PENDING/UNKNOWN semantics.
- **Owner:** B2
- **Status:** OPEN

#### Reproduction

1. Reserve `clientRequestId` and commit local PENDING/runtime + Inbox draft.
2. Meta successfully accepts the send and returns a provider message ID.
3. JVM/process terminates after the provider response but before `markAccepted()` commits locally.
4. Client retries the same logical request with the same `clientRequestId`.

#### Expected

The logical request must converge to a recoverable truth. If acceptance cannot be proven locally, the durable state must become/resurface as UNKNOWN with an explicit reconciliation/manual-resolution path; it must not remain indefinitely PENDING.

#### Actual

The durable row remains PENDING with no provider message ID. On the next request, `existingResponse()` returns the existing PENDING result before any provider call. There is no outbound reconciliation worker or stale-PENDING recovery path for this crash window.

This does avoid resubmitting the same `clientRequestId`, but the message can remain permanently unresolved even if Meta delivered it.

#### Evidence

- `MetaWhatsAppOutboundReservationStore.tryReserve()` persists PENDING before network send.
- `MetaWhatsAppMessageService.markAccepted()` is the only path that attaches the returned provider message ID after success.
- no stale outbound PENDING reconciliation/recovery worker is present.

#### Required regression test

Fault-injection integration test that terminates/fails immediately after a mocked provider success but before local acceptance persistence. After restart, assert the request is surfaced as UNKNOWN/recoverable rather than permanently PENDING, without a second provider submission.

---


### WA-R1-007 — Older provider FAILED event can overwrite a newer ACCEPTED state

- **Severity:** Medium
- **Affected invariant:** MESSAGE-STATE provider timestamp monotonicity; out-of-order status safety.
- **Owner:** B2
- **Status:** OPEN

#### Reproduction

Current runtime state:
- status = ACCEPTED
- rank = 10
- provider_status_at = 10:00:00

Then process a FAILED provider event timestamped 09:59:59.

#### Expected

The older provider event must be ignored. Provider timestamps are part of the ordering contract and an older failure must not replace newer known truth.

#### Actual

`MetaWhatsAppMessageStateMachine.shouldApply()` only applies the “incoming timestamp is older” rejection when `currentRank >= 20`. Because ACCEPTED rank is 10 and FAILED rank is 25, the older FAILED state is accepted.

#### Evidence

QA contract test already present:

`MetaWhatsAppRound1QaContractTests.olderFailureCannotOverwriteNewerAcceptedState`

#### Required regression test

Cover:
- ACCEPTED newer than FAILED -> keep ACCEPTED;
- SENT newer than FAILED -> keep SENT;
- DELIVERED/READ newer than FAILED -> keep successful state;
- genuinely newer FAILED after ACCEPTED -> apply according to frozen failure semantics.

---

### WA-R1-008 — Frontend cannot derive URL-button template variables from Meta BUTTONS components

- **Severity:** Medium
- **Affected invariant:** TEMPLATE-POLICY variable rendering/validation; frontend template-required flow.
- **Owner:** F1
- **Status:** OPEN

#### Reproduction

Use an approved template with:
- BODY text containing `{{1}}`;
- BUTTONS component containing a URL button such as `https://example.test/orders/{{1}}`.

Open the WhatsApp template composer.

#### Expected

The frontend must render one body variable input and one URL-button variable input, matching the backend/provider component structure.

#### Actual

`templateVariableCounts()` reads only `component.text`. Meta BUTTONS components store URL variable placeholders inside `component.buttons[*].url`, so the frontend derives zero button variables.

The backend `MetaWhatsAppTemplateService` does inspect the serialized buttons and expects a button parameter, so the frontend-generated request cannot satisfy backend variable-count validation for these templates.

#### Evidence

QA contract test already present:

`WhatsAppRound1QaContract.test.ts` -> `derives URL button variables from the provider BUTTONS structure`.

#### Required regression test

Keep the QA contract test and add an integration test that selects an approved URL-button template, fills body + button variables, sends, and asserts the exact `parameters.buttons` payload expected by the backend.

---

# Areas reviewed with no release-blocking source finding

The following controls looked consistent with the frozen contracts in source review:

- 256-bit signup state generation;
- TTL <= 10 minutes by default;
- Redis GETDEL one-time consume;
- state company/user binding;
- parallel consume allows one winner;
- top-level token scope presence checks;
- browser WABA/phone hints revalidated with Meta token;
- reauthorization bound to existing phone/account;
- partial unique active-phone constraint;
- DB race converted to safe ownership conflict;
- ProviderCredentialService used for token/PIN;
- safe Graph error diagnostics;
- local-only disconnect preserving customer Meta assets;
- reconnect cancels pending purge;
- exact-byte HMAC validation with constant-time digest compare;
- unsigned/oversized payload not stored by webhook path;
- durable event unique key and worker claim lease;
- unknown phone does not create a tenant;
- control-plane/company mismatch fails closed;
- WhatsApp vs Email Inbox permission separation;
- duplicate inbound message avoids repeated post-processing;
- free-form service-window enforcement is server-side;
- foreign conversation/template/media send scope checks;
- template approval/freshness/variable count enforcement;
- arbitrary inbound media URLs are not accepted from the browser/webhook;
- Meta media URL policy requires HTTPS and Meta-owned host suffixes;
- outbound media is company-scoped and ACTIVE;
- successful high-rank status ordering prevents READ/DELIVERED regression from late SENT events;
- frontend Embedded Signup uses v4 options and no `sessionInfoVersion: 3`;
- untrusted Embedded Signup message origins are ignored;
- frontend UNKNOWN result warns against blind resend;
- disconnect UI correctly states that Meta-owned assets are not deleted.

These observations are source/static verification only unless backed by an actually executed test suite.

# Migration notes

Source inspection of V76/V77/V78 shows:

- V76 partial unique index on active `selected_phone_number_id`;
- V77 webhook event dedupe, runtime client-request uniqueness, template/session/media tables;
- V78 FKs tie WhatsApp control/runtime rows into company/channel/Inbox/Media lifecycle with cascade semantics.

No migration PASS is claimed because PostgreSQL/Flyway execution was unavailable in this QA session.

# Release blockers

1. WA-R1-001 — webhook structural acceptance.
2. WA-R1-002 — detailed durable delivery state is not recoverable/rendered after reload.
3. WA-R1-006 — granular-scope asset authorization does not require the selected WABA in every required permission target set.
4. Required backend/frontend/migration suites have not been executed in an executable checkout.
5. Real Meta provider verification has not been performed.

# Required next step

L1 should create targeted remediation only for affected owners, then publish a new frozen integrated SHA for Round-2 QA. QA should rerun the full executable backend/frontend/migration matrix plus the real Meta verification checklist before release.
