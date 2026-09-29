# Meta WhatsApp Round-2 Integrated QA / Security Feedback

Date: 2026-09-29  
QA branch: `review/whatsapp-security-qa-r2`  
Frozen integrated Round-2 base: `87e43bbf598a5ea897cabc2a7e96a9acbb6cdf89`

## Final disposition

**NOT RELEASE-READY**

Seven Round-1 findings are remediated at the integrated contract/source level. `WA-R1-003` remains incomplete for MEDIA sends because the distributed phone permit is acquired before media upload rather than immediately before the actual Meta message submission.

Independent executable verification was attempted through a dedicated QA workflow and the repository's normal Checks workflow. GitHub Actions did not start runners: affected jobs returned `runner_id=0`, `steps=[]`, and no logs. This infrastructure condition is reported as **EXECUTION BLOCKED**, not PASS and not application FAIL.

Real Meta provider verification was **NOT VERIFIED** in this QA environment.

---

## QA execution environment

A dedicated QA workflow was added on the review branch:

`.github/workflows/whatsapp-r2-qa.yml`

It is pinned to the pull-request **head SHA** so it does not test a later moving `dev` merge result.

A draft, do-not-merge QA PR was opened only to trigger `pull_request` execution.

Observed workflow evidence:

- repository Checks run `36546239248`: all jobs `runner_id=0`, `steps=[]`
- dedicated WhatsApp QA run `36546239331`, attempt 1: all jobs `runner_id=0`, `steps=[]`
- dedicated WhatsApp QA run `36546239331`, attempt 2: all jobs again `runner_id=0`, `steps=[]`
- refreshed head runs also reproduced the same runner failure for backend/frontend jobs

No command output exists because no runner actually executed a step.

Therefore the following required commands are **EXECUTION BLOCKED BY GITHUB RUNNER INFRASTRUCTURE**:

Backend:
- `./mvnw spotless:check`
- `./mvnw test`
- `./mvnw clean test`
- `./mvnw package -DskipTests`

Frontend:
- `npm ci`
- `npm run format:check`
- `npm run lint`
- `npm test`
- `npm run build`

Also blocked:
- clean Flyway V1 -> V79
- representative V75 -> V79 upgrade
- representative V78 -> V79 upgrade
- real Redis multi-instance execution
- executable media crash/retry fault injection
- executable stale-PENDING restart/recovery

The source/container environment available to this QA session does not contain a checkout of the private repository, Docker, PostgreSQL or Redis, so the failed GitHub runner could not be replaced with a local full-stack execution.

---

# Round-1 finding retest

## WA-R1-001 — PASS

**Original issue:** correctly signed unsupported webhook envelopes could return 200.

**Result:** **PASS — integrated contract/source retest. Executable confirmation blocked by runner.**

### Evidence

`MetaWhatsAppWebhookNormalizer` now requires:

- root object;
- `object == "whatsapp_business_account"`;
- non-empty `entry` array;
- object entries;
- non-empty `changes`;
- object changes;
- non-blank `field`;
- object `value`;
- valid message/status effect shape.

`MetaWhatsAppWebhookControllerTests.rejectsSignedUnsupportedEnvelopesWithoutStoring` signs multiple unsupported envelopes and asserts HTTP 400 while verifying `eventStore.persist(...)` is never called.

Structurally valid unknown WhatsApp change fields remain accepted as bounded UNKNOWN events, matching the Round-2 contract.

### Expected vs actual

Expected: unsupported signed envelopes return 400 with zero durable inserts.  
Actual: implementation and regression coverage now enforce that behavior.

---

## WA-R1-002 — PASS

**Original issue:** durable DELIVERED/READ/FAILED/UNKNOWN state was lost after reload and not updated through WhatsApp realtime.

**Result:** **PASS — integrated contract/source retest. Executable confirmation blocked by runner.**

### Backend evidence

`InboxMessageResponse` now contains nullable `whatsappStatus`.

`InboxQueryService.messages(...)`:

- identifies WhatsApp messages;
- performs one batched query against `meta_whatsapp_message_runtime`;
- maps `detailed_status` by `inbox_message_id`;
- uses the same projection for conversation detail, message list and realtime snapshot;
- leaves non-WhatsApp status null.

`InboxQueryServiceWhatsAppStatusTests` covers durable READ through:

- conversation detail;
- message list;
- realtime snapshot;
- non-WhatsApp null compatibility.

### Frontend evidence

`WhatsAppInbox.messageStatus(...)` now prioritizes:

1. durable `message.whatsappStatus`;
2. temporary local send result;
3. generic Inbox fallback.

The component subscribes to `inbox.whatsapp.status.changed`, scopes by selected connection/conversation, requests the affected realtime snapshot, and replaces the message with durable state.

It also refreshes on browser focus and realtime reconnect.

`WhatsAppInbox.round2.test.tsx` covers:

- READ after normal HTTP reload;
- durable state replacing optimistic send memory;
- DELIVERED then READ through realtime;
- foreign connection/conversation event rejection;
- focus/reconnect recovery;
- durable UNKNOWN warning semantics.

### Expected vs actual

Expected: detailed WhatsApp state survives navigation/reload and changes live.  
Actual: integrated API and frontend now use durable runtime state consistently.

---

## WA-R1-003 — FAIL

**Original issue:** missing distributed per-phone limiter and provider Retry-After handling.

**Result:** **FAIL — partial remediation.**

### What now passes at source/contract level

`MetaWhatsAppOutboundRateLimiter`:

- uses Redis;
- keys by `unifydesk:whatsapp:rate:phone:<phoneNumberId>`;
- has configurable max/window;
- fails closed on Redis/runtime errors;
- shares one phone budget across limiter instances;
- supports a distributed provider cooldown.

`MetaWhatsAppRuntimeGraphClient` parses both numeric and RFC-1123 `Retry-After`.

A provider 429 invokes `imposeProviderCooldown(...)` and no automatic message replay occurs.

Existing Round-2 tests cover:

- two limiter instances sharing one Redis budget;
- cooldown propagation between instances;
- local limiter rejection preventing Meta submission;
- provider 429 cooldown;
- Retry-After parsing.

### Remaining failure — MEDIA send permit timing

The Round-2 contract requires the phone permit to be acquired **immediately before Meta message submission**.

Current `MetaWhatsAppMessageService.reserve(...)` acquires the permit before returning from the local reservation transaction.

For TEXT and TEMPLATE, the next provider message operation is the message send.

For MEDIA, however, the sequence is:

1. acquire phone permit;
2. read the private media object;
3. call Meta media upload;
4. persist the outbound media link;
5. call Meta `sendMedia`.

A slow media read/upload can exceed the configured one-second rate window before step 5. Concurrent MEDIA calls can therefore consume permits in an earlier window and later submit their actual messages together after uploads complete.

This does not satisfy the frozen Round-2 requirement that TEXT, TEMPLATE **and MEDIA message submissions** respect the per-phone send limiter.

### QA regression added

`MetaWhatsAppRound2RateLimitQaTests` verifies:

- TEXT: permit before `sendText`;
- TEMPLATE: permit before `sendTemplate`;
- MEDIA: expected `uploadMedia -> requirePermit -> sendMedia`.

The current MEDIA implementation has `requirePermit -> uploadMedia -> sendMedia`, so the MEDIA ordering assertion is expected to fail when an executable runner is available.

### Owner

B2.

### Required regression/fix condition

For MEDIA:
- upload/prepare the provider media first;
- acquire the distributed phone permit immediately before `sendMedia`;
- limiter rejection must not submit the message;
- no automatic message replay;
- provider 429 cooldown remains shared.

Retest with a delayed media-upload stub longer than `outboundRateWindow` and concurrent sends; actual `sendMedia` submissions must remain within the configured aggregate phone budget.

---

## WA-R1-004 — PASS

**Original issue:** crash after media ingest but before link could create duplicate Media rows/objects.

**Result:** **PASS — integrated contract/source retest. Executable confirmation blocked by runner.**

### Evidence

V79 adds `meta_whatsapp_provider_media_reservations` with unique:

`(connection_id, provider_media_id)`

and a durable fixed `reserved_media_id`.

The worker now:

1. reserves/finds provider media;
2. reuses the same reserved Media ID;
3. reuses an already ACTIVE Media row;
4. ingests with deterministic Media ID/object key;
5. marks INGESTED;
6. links;
7. marks LINKED.

`MediaService.ingestTrustedCompanyMediaWithId(...)`:

- returns an existing matching ACTIVE row;
- reuses the deterministic object key;
- reuses a valid object left by a prior crash;
- repairs an incompatible stale object at the same key;
- never generates another Media ID for the same reservation.

Regression coverage includes:

- crash after ingest before link followed by retry;
- reserved ID reuse;
- deterministic object key;
- pre-existing crash object reuse.

### Expected vs actual

Expected: one Media identity/object and one link after convergence.  
Actual: Round-2 implementation now has one durable provider-media reservation and idempotent reserved-ID ingest path.

---

## WA-R1-005 — PASS

**Original issue:** provider-accepted/local-response-loss window could leave PENDING forever.

**Result:** **PASS — integrated contract/source retest. Executable confirmation blocked by runner.**

### Evidence

`MetaWhatsAppPendingOutcomeRecovery`:

- finds outbound PENDING rows older than `pendingOutcomeTimeout`;
- requires `provider_message_id is null`;
- atomically changes status to UNKNOWN;
- runs on a bounded scheduled batch;
- also supports lazy recovery by company/connection/clientRequestId;
- emits status realtime after commit;
- does not call Meta.

`MetaWhatsAppMessageService.send(...)` calls lazy recovery before returning an existing duplicate result.

V79 adds a partial stale-PENDING index.

Tests cover:
- stale PENDING -> UNKNOWN;
- duplicate clientRequestId returns UNKNOWN;
- no Graph/provider interaction on lazy recovery.

### Expected vs actual

Expected: stale PENDING becomes durable UNKNOWN without second provider submission.  
Actual: implementation matches that contract.

---

## WA-R1-006 — PASS

**Original issue:** WABA target IDs were unioned across required granular scopes.

**Result:** **PASS — integrated contract/source retest. Executable confirmation blocked by runner.**

### Evidence

`MetaWhatsAppConnectionService.grantedTargetIds(...)` now:

1. unions duplicate target lists within each required scope;
2. requires both required scopes;
3. intersects required-scope target sets;
4. returns only WABAs present in every required scope.

The same intersection is checked during provider reconciliation.

`MetaWhatsAppWabaScopeAuthorizationTests` covers:

- WABA in both scopes;
- management-only rejection;
- messaging-only rejection;
- duplicate entries within one scope unioned first;
- only common WABA surviving;
- requested WABA outside the intersection rejected before provider lookup.

### Expected vs actual

Expected: selected WABA must be present in every required granular permission target set.  
Actual: integrated implementation now uses the required intersection.

---

## WA-R1-007 — PASS

**Original issue:** an older FAILED event could overwrite a newer ACCEPTED state.

**Result:** **PASS — integrated contract/source retest. Executable confirmation blocked by runner.**

### Evidence

`MetaWhatsAppMessageStateMachine.shouldApply(...)` now rejects an incoming event whenever both timestamps are known and the incoming provider timestamp is older, before rank comparison.

Regression coverage includes:

- newer ACCEPTED + older FAILED;
- newer SENT + older FAILED;
- READ not regressing to failure;
- duplicate same timestamp status;
- normal SENT -> DELIVERED -> READ advancement.

### Expected vs actual

Expected: older provider state never overwrites newer known truth regardless of current rank.  
Actual: timestamp ordering is now enforced before rank ordering.

---

## WA-R1-008 — PASS

**Original issue:** frontend did not derive variables from provider URL buttons.

**Result:** **PASS — integrated contract/source retest. Executable confirmation blocked by runner.**

### Evidence

The frontend template component type now includes provider `buttons` with type/text/url.

`templateVariableCounts(...)`:

- inspects `BUTTONS`;
- counts only variable-bearing URL buttons;
- ignores static URL and quick-reply buttons;
- preserves provider button order through parameter slot ordering.

`WhatsAppInbox.round2.test.tsx` verifies an exact request:

```
parameters: {
  header: [],
  body: ['ORDER-42'],
  buttons: ['TRACK-42', 'INVOICE-42']
}
```

for provider buttons appearing in that order.

### Expected vs actual

Expected: variable URL buttons render inputs and submit exactly in backend/provider order.  
Actual: integrated frontend now matches the backend Round-1 template policy.

---

# Backend test results

**EXECUTION BLOCKED — not PASS, not application FAIL.**

Attempted through both the repository Checks workflow and a dedicated frozen-head QA workflow.

Observed:
- no runner assigned;
- `runner_id=0`;
- `steps=[]`;
- no job logs.

Therefore Maven Spotless/test/clean-test/package results cannot be truthfully reported as executed.

Source/regression review did identify the concrete WA-R1-003 MEDIA ordering failure above.

---

# Frontend test results

**EXECUTION BLOCKED — not PASS, not application FAIL.**

The dedicated frontend job also terminated before any step with `runner_id=0` and `steps=[]`.

Round-2 frontend regression suites are present and source-reviewed, including durable status/realtime and URL-button payload tests.

---

# Flyway / migration results

**EXECUTION BLOCKED.**

Source review of V79:

- creates durable provider-media reservation table;
- unique `(connection_id, provider_media_id)`;
- unique reserved Media ID;
- reservation state check;
- channel/message cascade FKs;
- stale-PENDING partial index.

QA added:

`MetaWhatsAppRound2MigrationQaTests`

covering:
- clean V1 -> V79;
- V75 -> V79;
- V78 -> V79.

These tests target the PostgreSQL service from `compose.test.yml`, but GitHub never assigned a runner, so no migration PASS is claimed.

---

# Redis results

**EXECUTION BLOCKED.**

The integrated implementation/test suite includes a real Redis integration test against port 56379 from `compose.test.yml` with two independent limiter instances sharing one phone budget and cross-instance cooldown.

The test could not actually run because the Actions runner never started.

No Redis PASS is claimed.

---

# Fault-injection results

## Media crash/retry

**PASS at implementation/regression level; executable run blocked.**

The dedicated worker regression models a crash after media ingest and before link, then verifies retry reuses the reserved ACTIVE media rather than ingesting another media ID.

## Stale-PENDING restart/recovery

**PASS at implementation/regression level; executable run blocked.**

Recovery is durable SQL state transition only and has no provider replay path. Lazy duplicate-read coverage verifies Graph is not invoked.

---

# New Round-2 findings

No separate `WA-R2-NNN` finding is opened.

The remaining issue is directly the incomplete remediation of existing finding **WA-R1-003**, so it remains tracked under that ID rather than being duplicated as a new Round-2 finding.

---

# Real Meta provider verification

**NOT VERIFIED**

No real Meta test app/WABA/phone credential set was available to this QA environment.

The following remain provider-unverified:

- Embedded Signup v4 against real Meta;
- actual debug-token/granular-scope shapes;
- phone registration;
- subscribed_apps;
- real webhook payload/signature variants;
- text send;
- template send;
- media upload/send;
- provider Retry-After behavior;
- delivered/read webhooks;
- permission revocation;
- current account model identifiers;
- quality/throughput behavior;
- coexistence.

Local mocks and source regression tests are not reported as provider verification.

---

# Final Round-1 finding matrix

| Finding | Result | Summary |
|---|---|---|
| WA-R1-001 | PASS | strict signed webhook envelope validation + zero-store regression |
| WA-R1-002 | PASS | durable `whatsappStatus` through HTTP/realtime + frontend rendering |
| WA-R1-003 | **FAIL** | Redis/cooldown implemented, but MEDIA permit is acquired before upload rather than immediately before message submission |
| WA-R1-004 | PASS | durable provider-media reservation + deterministic reserved Media ID |
| WA-R1-005 | PASS | stale PENDING -> UNKNOWN, scheduler/lazy recovery, no provider replay |
| WA-R1-006 | PASS | per-scope union then required-scope intersection |
| WA-R1-007 | PASS | older provider timestamp rejected regardless of rank |
| WA-R1-008 | PASS | variable URL buttons mapped in provider order |

All PASS entries above are integrated contract/source + regression-coverage results; independent executable confirmation remains blocked by the repository runner outage.

---

# Release blockers

1. **WA-R1-003 remains FAIL for MEDIA message submission limiter timing.**
2. Required backend/frontend/Flyway/Redis executable QA could not run because GitHub jobs never obtained a runner.
3. Real Meta provider verification has not been performed.

## Release disposition

**NOT RELEASE-READY**

L1 should assign a targeted B2 remediation for the remaining WA-R1-003 MEDIA limiter-ordering gap. After integration, QA should execute the dedicated rate-order regression plus the full backend/frontend/Flyway/Redis matrix when the runner is operational, then complete the real Meta provider checklist where credentials permit.
