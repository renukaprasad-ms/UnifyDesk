# Meta WhatsApp Round-3 Final QA / Security Feedback

Date: 2026-09-29  
QA branch: `review/whatsapp-security-qa-r3`  
Exact final integrated QA base: `8738d9f7daafb878552eef70f1730ed6d8332a6c`  
Round-3 B2 implementation: `3a7f3f094289d8adf9c68ada3d9be49905d6779f`

## Final disposition

**NOT RELEASE-READY**

No remaining implementation defect was found in the targeted Round-3 remediation. `WA-R1-003` is **PASS at integrated source/contract review** and the focused regression coverage now matches the frozen Round-3 requirements.

However, the required final executable acceptance matrix is still **EXECUTION BLOCKED** by the GitHub Actions repository-runner failure. Jobs terminate or remain unassigned with `runner_id=0`, `steps=[]`, and no command logs. Because Maven/npm/Flyway/Redis tests did not actually execute, this report does not call those suites PASS.

Real Meta provider verification is **NOT VERIFIED** in this QA environment.

This final disposition is therefore blocked by verification infrastructure, not by a known remaining WhatsApp implementation defect.

---

# Verification classification

## WA-R1-003

**Finding result: PASS — integrated source/contract retest.**

Execution classification:
- focused Maven regression execution: **EXECUTION BLOCKED**
- local/mock execution in this final QA environment: **NOT VERIFIED**
- real Redis execution in this final QA environment: **NOT VERIFIED**
- real Meta provider execution: **NOT VERIFIED**

The finding is marked PASS because the integrated production code now implements every required Round-3 state/order invariant and the non-weakened focused tests encode those invariants. This is not represented as an executed-test PASS.

## Previously passed findings

No Round-3 regression was found in:
- `WA-R1-001`
- `WA-R1-002`
- `WA-R1-004`
- `WA-R1-005`
- `WA-R1-006`
- `WA-R1-007`
- `WA-R1-008`

Their final executable regression runs are **EXECUTION BLOCKED**, not reclassified as failures.

---

# WA-R1-003 final retest

## Required provider operation ordering

### TEXT — PASS

Required:

`reserve -> requirePermit -> sendText`

Integrated production path:

1. durable logical reservation;
2. `requirePermitOrFail(...)`;
3. `graphClient.sendText(...)`.

No provider operation exists between permit acquisition and the text message submission.

Round-3 focused test:

`MetaWhatsAppMessageServiceRound3RateLimitTests.textAndTemplateKeepPermitImmediatelyBeforeSubmission`

also verifies the reservation/permit/send order.

### TEMPLATE — PASS

Required:

`reserve -> requirePermit -> sendTemplate`

Integrated production path:

1. durable logical reservation;
2. `requirePermitOrFail(...)`;
3. `graphClient.sendTemplate(...)`.

Template preparation occurs before reservation/provider submission. After the logical reservation, the distributed permit directly guards the actual message send.

The same Round-3 ordering regression covers TEMPLATE.

### MEDIA — PASS

Required:

`reserve -> read media -> uploadMedia -> persist media link -> requirePermit -> sendMedia`

Integrated production path is now exactly:

1. reserve logical outbound request/message;
2. `mediaService.readTrustedObject(...)`;
3. `graphClient.uploadMedia(...)`;
4. `linkOutboundMedia(...)`;
5. `requirePermitOrFail(...)`;
6. `graphClient.sendMedia(...)`.

The Round-2 defect is removed: no permit is acquired before the potentially slow local-media read/provider-media upload.

The existing non-weakened QA regression remains present:

`MetaWhatsAppRound2RateLimitQaTests.mediaPermitIsAcquiredAfterUploadImmediatelyBeforeMessageSubmission`

and now matches the production order.

Round-3 also adds:

`MetaWhatsAppMessageServiceRound3RateLimitTests.delayedMediaUploadAcquiresFreshPermitImmediatelyBeforeSend`

which introduces a synthetic upload delay longer than the synthetic limiter window and verifies:
- upload completes first;
- outbound media link is persisted;
- permit is acquired only afterwards;
- `sendMedia` follows the permit.

This prevents a slow upload from consuming an earlier/stale rate window.

---

# Post-upload limiter rejection/failure

## Local rate rejection after successful upload — PASS

Production `requirePermitOrFail(...)` catches `TooManyRequestsException` and calls `markLocalLimiterFailed(...)` before rethrowing.

Result:
- zero `sendMedia`;
- runtime row becomes `FAILED`;
- runtime rank becomes 25;
- `provider_message_id` remains null;
- `provider_status_at` remains null;
- local error marker is persisted;
- generic Inbox message becomes `FAILED`;
- realtime `inbox.whatsapp.status.changed` is published;
- no UNKNOWN transition is used.

Persisted local rate code maps back to:

`WHATSAPP_RATE_LIMITED`

for duplicate/readback responses.

Focused test:

`mediaRateRejectionAfterUploadPersistsFailedAndDuplicateDoesNotReplay`

verifies:
- upload occurs once;
- `sendMedia` occurs zero times;
- durable status is FAILED;
- generic Inbox status is FAILED;
- realtime status change occurs;
- duplicate same `clientRequestId` returns FAILED;
- duplicate safe error is `WHATSAPP_RATE_LIMITED`;
- duplicate performs zero second upload;
- duplicate performs zero second send.

## Redis / limiter infrastructure failure after successful upload — PASS

`ServiceUnavailableException("WHATSAPP_RATE_LIMIT_UNAVAILABLE")` follows the same definite pre-message-submission failure path.

Persisted local error marker maps duplicate/readback to:

`WHATSAPP_RATE_LIMIT_UNAVAILABLE`

Focused test:

`mediaRedisFailureAfterUploadPersistsFailedAndNeverSubmits`

verifies:
- provider media upload completes;
- zero `sendMedia`;
- durable FAILED;
- generic Inbox FAILED;
- duplicate returns FAILED with `WHATSAPP_RATE_LIMIT_UNAVAILABLE`;
- no second upload;
- no second send.

Because the failure occurs before the actual message endpoint, it does not become UNKNOWN.

---

# Provider 429 after MEDIA submission

**PASS at integrated source/contract review.**

If `sendMedia` itself returns provider HTTP 429:
- provider exception is caught after submission attempt;
- parsed `Retry-After` is passed to `outboundRateLimiter.imposeProviderCooldown(phone, retryAfter)`;
- cooldown remains phone-scoped and Redis-distributed;
- no immediate message replay occurs.

Focused regression:

`provider429AfterMediaSubmissionStillImposesSharedCooldown`

verifies:

`uploadMedia -> requirePermit -> sendMedia -> imposeProviderCooldown`.

Existing Retry-After parser tests cover numeric seconds and RFC-1123 HTTP-date forms plus invalid-header fallback behavior.

---

# Redis final verification

## Source/regression coverage present

Existing real-Redis integration:

`MetaWhatsAppOutboundRateLimiterRedisIntegrationTests`

covers:
- two limiter instances sharing one same-phone budget;
- provider cooldown imposed by one limiter instance being observed by another.

Final QA added review-only:

`MetaWhatsAppRound3RedisQaTests`

covering:
- independent rate budgets for two different phone IDs;
- cooldown remains phone-scoped while shared across limiter instances;
- actual Redis connection failure path fails closed with `WHATSAPP_RATE_LIMIT_UNAVAILABLE`.

Existing unit coverage also verifies:
- exhausted phone window -> `WHATSAPP_RATE_LIMITED`;
- Redis script/runtime failure -> fail closed;
- cooldown fallback duration.

## Execution result

**EXECUTION BLOCKED**

No Redis integration command started because the GitHub runner was never assigned. Therefore no real Redis PASS is claimed by final QA.

---

# Flyway final verification

No Round-3 migration was introduced.

Latest WhatsApp migration remains V79.

Existing review-only migration regression:

`MetaWhatsAppRound2MigrationQaTests`

covers:
- clean V1 -> V79;
- representative V75 -> V79;
- representative V78 -> V79.

## Execution result

**EXECUTION BLOCKED**

The PostgreSQL compose service was never started because Actions never obtained a runner.

No Flyway execution PASS is claimed.

---

# Previously passed finding regression review

## WA-R1-001 — no Round-3 regression found

Round-3 implementation changed only outbound `MetaWhatsAppMessageService` plus focused tests. Webhook normalizer/controller behavior is untouched.

Targeted backend regression intended for execution includes `MetaWhatsAppWebhookControllerTests`.

Execution: **BLOCKED**.

## WA-R1-002 — no Round-3 regression found

Durable `whatsappStatus`, Inbox projection and frontend realtime/reload behavior were not modified by Round 3.

Targeted backend regression includes `InboxQueryServiceWhatsAppStatusTests`; full frontend suite was also scheduled.

Execution: **BLOCKED**.

## WA-R1-004 — no Round-3 regression found

Inbound provider-media reservation/ingest path is unchanged.

Targeted regression includes:
- `MetaWhatsAppWebhookWorkerTests`
- `MetaWhatsAppProviderMediaReservationStoreTests`
- `MediaServiceTests`

Execution: **BLOCKED**.

## WA-R1-005 — no Round-3 regression found

Stale-PENDING recovery is unchanged. The new limiter-failure path intentionally exits PENDING immediately as definite FAILED, which is compatible with the stale-outcome contract.

Targeted regression includes:
- `MetaWhatsAppPendingOutcomeRecoveryTests`
- `MetaWhatsAppMessageServiceIdempotencyTests`

Execution: **BLOCKED**.

## WA-R1-006 — no Round-3 regression found

Control-plane WABA intersection authorization is untouched.

Targeted regression includes `MetaWhatsAppWabaScopeAuthorizationTests`.

Execution: **BLOCKED**.

## WA-R1-007 — no Round-3 regression found

Provider timestamp state machine is untouched.

Targeted regression includes `MetaWhatsAppMessageStateMachineTests`.

Execution: **BLOCKED**.

## WA-R1-008 — no Round-3 regression found

Frontend template URL-button derivation/payload code is untouched.

The complete frontend suite was scheduled.

Execution: **BLOCKED**.

---

# Automated check attempts

Final review workflow:

`.github/workflows/whatsapp-r3-final-qa.yml`

was added as a QA-only workflow pinned to the review-branch head.

It contains:

## Backend matrix

- `./mvnw spotless:check`
- `./mvnw test`
- `./mvnw clean test`
- `./mvnw package -DskipTests`

Result: **EXECUTION BLOCKED**

Final QA run `36550776200`, attempt 1:
- `backend-matrix`: `runner_id=0`, `steps=[]`
- `whatsapp-focused`: `runner_id=0`, `steps=[]`
- `frontend-matrix`: `runner_id=0`, `steps=[]`

Attempt 2 again failed to obtain runners for backend/focused jobs.

The repository's independent `Checks` workflow run `36550776002` also returned:
- backend: `runner_id=0`, `steps=[]`
- frontend: `runner_id=0`, `steps=[]`
- email-provider: `runner_id=0`, `steps=[]`
- website: `runner_id=0`, `steps=[]`

These are GitHub runner infrastructure failures and are neither application PASS nor application FAIL.

## Focused WhatsApp matrix

Scheduled:
- Round-2 permit-order regression;
- Round-3 failure/idempotency/rate-order tests;
- Redis two-instance integration;
- final independent-phone/outage Redis QA;
- Retry-After tests;
- V1/V75/V78 -> V79 Flyway tests;
- all targeted backend regressions for previously passed findings.

Result: **EXECUTION BLOCKED**

No Maven command started.

## Frontend matrix

Scheduled:
- `npm ci`
- `npm run format:check`
- `npm run lint`
- `npm test`
- `npm run build`

Result: **EXECUTION BLOCKED**

No npm command started in the independent repository Checks execution. The final QA workflow likewise did not obtain a usable runner during the verification window.

---

# Local runtime availability

The QA container was checked for an alternate execution path.

Available:
- Java runtime;
- Node/npm.

Unavailable:
- repository checkout;
- network route for direct private GitHub clone;
- Docker;
- PostgreSQL client/server;
- Redis server.

A direct repository clone attempt cannot resolve GitHub from the container.

Therefore reconstructing or claiming the complete Maven/npm/Flyway/Redis matrix locally would not be valid final QA evidence.

---

# Real Meta provider verification

**NOT VERIFIED**

No real Meta test app/WABA/phone credentials are available in this QA environment.

Final QA therefore does not claim real-provider verification for:
- Embedded Signup v4;
- real token/granular-scope responses;
- phone registration;
- subscribed_apps;
- real signed webhook payload variants;
- text send;
- template send;
- media upload/send;
- real provider 429/Retry-After behavior;
- delivered/read callbacks;
- permission revocation;
- current account-model identifiers;
- quality/throughput behavior;
- coexistence.

Mocks/source inspection are not treated as real Meta verification.

---

# New Round-3 findings

**None.**

No `WA-R3-NNN` implementation finding was identified.

---

# Final result matrix

| Area | Result | Verification status |
|---|---|---|
| WA-R1-003 | **PASS** | source/contract PASS; executable regression BLOCKED |
| TEXT permit order | PASS | source/contract verified |
| TEMPLATE permit order | PASS | source/contract verified |
| MEDIA permit after upload/link | PASS | source/contract verified |
| Slow upload stale-window protection | PASS | encoded in focused mock regression; execution BLOCKED |
| Local rate rejection after upload | PASS | source/contract verified; focused execution BLOCKED |
| Redis failure after upload | PASS | source/contract verified; focused execution BLOCKED |
| Durable FAILED, not PENDING/UNKNOWN | PASS | source/contract verified |
| Safe local limiter code retained | PASS | source/contract verified |
| Realtime status update | PASS | source/contract verified |
| Duplicate request no second upload/send | PASS | source/contract verified |
| Provider 429 shared cooldown | PASS | source/contract verified; Redis execution BLOCKED |
| WA-R1-001 regression | no regression found | execution BLOCKED |
| WA-R1-002 regression | no regression found | execution BLOCKED |
| WA-R1-004 regression | no regression found | execution BLOCKED |
| WA-R1-005 regression | no regression found | execution BLOCKED |
| WA-R1-006 regression | no regression found | execution BLOCKED |
| WA-R1-007 regression | no regression found | execution BLOCKED |
| WA-R1-008 regression | no regression found | execution BLOCKED |
| Backend full matrix | **EXECUTION BLOCKED** | GitHub runner_id=0 / steps=[] |
| Frontend full matrix | **EXECUTION BLOCKED** | GitHub runner unavailable |
| Flyway V1/V75/V78 -> V79 | **EXECUTION BLOCKED** | PostgreSQL runner never started |
| Redis integration | **EXECUTION BLOCKED** | Redis runner never started |
| Real Meta | **NOT VERIFIED** | credentials/provider environment unavailable |

---

# Release blockers

There is no known remaining WhatsApp implementation blocker from Round 3.

Verification blockers remain:

1. backend full/focused Maven matrix has not executed;
2. frontend npm matrix has not executed;
3. Flyway V1/V75/V78 -> V79 has not executed;
4. real Redis concurrency/cooldown/outage tests have not executed;
5. real Meta provider verification has not been performed.

Because the final executable acceptance gates are still unverified, the final release disposition remains:

**NOT RELEASE-READY**

Once an executable runner is available, rerun the already-committed final QA workflow. If backend, frontend, Flyway and Redis matrices all execute PASS and no new regression appears, the current source review has no known implementation defect preventing release.
