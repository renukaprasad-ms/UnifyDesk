# UnifyDesk Managed SMS — Security / QA Feedback

Review round: **Round 1**  
Review branch: `review/managed-sms-security-qa-r1`  
Exact tested/reviewed base SHA: `b3a0155570d67e30be11889a60c5c40e9c2544b8`  
Review date: **2026-09-27**

## Release recommendation

**NO-GO / NOT READY FOR RELEASE.**

Round 1 has useful fail-closed and tenant-safety foundations, but the integrated candidate has multiple HIGH-severity open findings in compliance freshness, CTA validation, webhook replay protection, provider safety bootstrap, and B2 ownership consistency. The backend test suite also contains a deterministic stale migration-resource assertion after the V61 -> V62 integration rename.

In addition, this QA session has repository-content/branch access through the GitHub connector but does not have an executable repository checkout. The local execution container cannot resolve GitHub, and no CI workflow run is associated with the exact base SHA. Therefore Maven, npm, Docker/Postgres, Redis and Flyway execution results **must not be represented as passed**. They remain release-blocking verification work.

Production GA also remains blocked by the external MSG91/DLT items already frozen in `RESEARCH-DLT-MSG91.md` and `INTEGRATION_STATUS.md`.

## Scope reviewed

The review read the frozen Managed SMS documents and traced the integrated B1/B2/F1 code at the exact base SHA, including:

- V62 control-plane migration
- V63 delivery/usage migration
- V64 cross-module constraints
- customer and platform-admin APIs
- tenant/profile/Header/template/provider-binding ownership paths
- Broadcast compose, launch and delivery integration
- managed MSG91 gateway
- correlation, webhook inbox, event projection and reconciliation
- usage reservation/commit/release
- Redis rate limiting and provider-wallet safety
- existing CUSTOMER_MANAGED MSG91 regression test
- frontend Managed SMS setup/status/composer privacy and company switching
- focused B1/B2/F1 Managed SMS tests

## Required executable checks — status

| Check | Required command / path | Round-1 QA result |
|---|---|---|
| Backend compile | `cd unifydesk-app/backend && ./mvnw -DskipTests compile` | **NOT EXECUTED — environment blocker** |
| Backend Spotless | `cd unifydesk-app/backend && ./mvnw spotless:check` | **NOT EXECUTED — environment blocker** |
| Backend full tests | `cd unifydesk-app/backend && ./mvnw test` | **NOT EXECUTED — environment blocker; deterministic stale-resource failure identified statically (MS-SMS-R1-001)** |
| Frontend install/tests | `cd unifydesk-app/frontend && npm ci && npm test` | **NOT EXECUTED — environment blocker** |
| Frontend build | `npm run build` | **NOT EXECUTED — environment blocker** |
| Frontend lint | `npm run lint` | **NOT EXECUTED — environment blocker** |
| Frontend format | `npm run format:check` | **NOT EXECUTED — environment blocker** |
| Clean Flyway path through V64 | Postgres test service from `compose.test.yml`, clean schema, start/migrate through V64 | **NOT EXECUTED — environment blocker** |
| Upgrade Flyway path through V64 | representative pre-V62/current-dev schema -> V62/V63/V64 | **NOT EXECUTED — environment blocker** |
| Redis concurrency/failure integration | Redis test service from `compose.test.yml` | **NOT EXECUTED — environment blocker; fail-closed behavior is present in unit-level source/test** |
| Real MSG91 / DLT | production-contract provider account | **NOT AVAILABLE / UNVERIFIED** |

The repository test compose contract expects PostgreSQL at `127.0.0.1:55432/messaging_test` and Redis at `127.0.0.1:56379`.

## Positive controls observed statically

These are **not execution passes**, but should be preserved during remediation:

- Customer-scoped Managed SMS controllers call `CompanyAuthorizationService.requirePermission(..., companyId, ...)`.
- B1 repositories/services commonly use `findBy...AndCompanyId` for profile/Header/template/sender ownership.
- Platform review paths require platform permission plus company access; decision requests require a reason and optimistic-lock version; audit rows are written after authorized decisions.
- Managed Broadcast compose does not trust customer scratch SMS text. `BroadcastMessageService` renders the managed template server-side and persists the rendered approved body.
- The existing CUSTOMER_MANAGED MSG91 path has a focused regression test proving it remains on the legacy transport path.
- Redis exceptions in `ManagedSmsRateLimiter` return false rather than falling back to local permissive limiting.
- Ambiguous provider outcomes are held as UNKNOWN and the focused coordinator test verifies no retry is scheduled.
- Usage reservations have unique idempotency keys; acceptance billing uses a stable acceptance key and `UsageBillingService` is idempotent at the usage-event layer.
- V62 installs an immutability trigger for activated/superseded/stale/archived template content.
- F1 sanitizers whitelist customer-safe Managed SMS fields and drop stale/mock provider account, client, AuthKey, Flow, route, wallet and global-capacity fields.
- F1 status/setup views clear old company state and discard late responses after company switching.
- F1 renders a managed sender as **UnifyDesk Managed SMS** and hides connection/provider details in the customer UI.
- B1 provisioning deliberately returns ACTION_REQUIRED rather than manufacturing MSG91 provisioning success while the real provider contract remains unverified.

---

## Findings

### MS-SMS-R1-001 — Integrated backend test still references pre-integration V61 migration filename

- **Severity:** HIGH
- **Status:** OPEN
- **Violated invariant:** Integrated Round-1 backend compile/Spotless/full tests must be green against the exact integrated migration set.
- **Affected component / worker:** Integration / B1 test suite
- **Exact evidence / reproduction:**
  - Integration status states B1's migration was renumbered to `V62__managed_sms_control_plane.sql` because V61 is already occupied by the Ads migration.
  - The actual file at the base SHA is `backend/src/main/resources/db/migration/V62__managed_sms_control_plane.sql`.
  - `backend/src/test/java/com/messaging/managedsms/control/ManagedSmsControlContractTests.java` still calls `getResourceAsStream("/db/migration/V61__managed_sms_control_plane.sql")` and asserts the stream is non-null.
  - Reproduction: execute the focused test or full Maven test suite. The requested Managed SMS V61 resource is absent, so the non-null assertion must fail.
- **Recommended remediation:** Update the contract test to the integrated V62 filename (or make it resolve the intended migration by a stable test helper), then execute the full backend suite from the frozen integration base.
- **Required retest:** `./mvnw spotless:check`, `./mvnw test`, and explicit execution of `ManagedSmsControlContractTests`.

### MS-SMS-R1-002 — Provider-wallet safety has no positive snapshot bootstrap/refresh path

- **Severity:** HIGH
- **Status:** OPEN
- **Violated invariant:** Known provider capacity/wallet state must fail closed without making the product permanently unsendable; provider-wallet protection must use a real, fresh provider observation.
- **Affected component / worker:** B2 safety/runtime
- **Exact evidence / reproduction:**
  - `ManagedSmsProviderSafetyService.isAvailable()` requires a non-expired `managed_sms_provider_snapshots` row with `sending_enabled=true` and `balance > providerBalanceReserve` whenever the reserve is positive.
  - `application.yaml` defaults `provider-balance-reserve` to 100.
  - The B2 integrated diff contains only one insert into `managed_sms_provider_snapshots`: `tripInsufficientCredit()`, which writes balance 0 and `sending_enabled=false`.
  - No B2 positive balance/snapshot observer is present in the integrated change set.
  - Both launch and delivery call `isAvailable()`.
  - Reproduction on a clean V63/V64 database with Managed SMS enabled and default reserve: there is no snapshot row, `isAvailable()` returns false, and launch fails with `MANAGED_SMS_PROVIDER_CAPACITY_UNAVAILABLE`.
- **Recommended remediation:** Add a provider-snapshot acquisition/refresh contract that is backed by verified MSG91 balance/rate semantics, or explicitly keep production sending disabled until that observer is implemented. A manually seeded DB row must not be the production mechanism. Always honor a valid `sending_enabled=false` snapshot even if reserve is configured to zero.
- **Required retest:** clean DB with no snapshot; fresh healthy snapshot; expired snapshot; low balance; insufficient-credit trip; recovery after fresh healthy observation; multi-child account isolation.

### MS-SMS-R1-003 — CTA URL/callback whitelist policy is not enforced at compose/launch/runtime

- **Severity:** HIGH
- **Status:** OPEN
- **Violated invariant:** Runtime CTA values must match the approved DLT CTA policy; a different/non-whitelisted domain or callback must produce no provider request.
- **Affected component / worker:** B1 template validation + B2 runtime rendering
- **Exact evidence / reproduction:**
  - B1 `ManagedSmsTemplateService.validateCtaList()` checks only that a CTA key exists and has URL/CALLBACK type.
  - B1 `validUrl()` checks only `http/https` and that a host exists.
  - B2 `managedsms.runtime.ManagedSmsTemplateService.validateCtaPolicy()` again checks only that listed keys use the expected type.
  - B2 `validUrl()` checks scheme, host and no user-info, but does not compare host/domain/path against an approved whitelist.
  - There is no callback-number allowlist comparison.
  - Reproduction: an approved URL variable can be supplied as `https://evil.example/...`; it is syntactically valid and passes the current local checks. Likewise a syntactically valid callback number is not matched against an approved callback set.
- **Recommended remediation:** Persist the exact approved CTA constraints (allowed full URL/domain/subdomain/path rules and callback values/patterns as applicable) in each immutable template version and enforce them in preview, compose, launch and delivery rendering. Do not infer approval from the variable type alone.
- **Required retest:** different domain, different subdomain, IP URL, user-info URL, HTTP-vs-HTTPS policy, path variation, encoded redirect, callback mismatch, extra CTA variable, missing CTA variable, and approved values.

### MS-SMS-R1-004 — Delivery does not re-evaluate compliance freshness timestamps

- **Severity:** HIGH
- **Status:** OPEN
- **Violated invariant:** PE/TM chain, Header, template and provider binding must be ACTIVE **and fresh at the actual provider send**, not only when the run is launched.
- **Affected component / worker:** B2 delivery/runtime
- **Exact evidence / reproduction:**
  - `ManagedSmsLaunchGuard` checks profile/Header/template `stale_after`, provider-binding sync age and tenant provider-sync age.
  - `JdbcManagedSmsRuntimeRepository.findDeliveryRuntime()` does not select those freshness timestamps.
  - `DeliveryRuntime.currentlySendable()` checks only status strings/flags.
  - `ManagedSmsComplianceService.refreshStaleState()` converts expired resources to STALE on a scheduler with a default 5-minute interval.
  - Reproduction: launch while compliance is fresh, let `stale_after` pass, then deliver before the scheduler flips status (or while that scheduler is unhealthy). ACTIVE status rows still satisfy `currentlySendable()`, so the provider-send path remains eligible.
- **Recommended remediation:** Carry current compliance timestamps into the delivery runtime and fail closed at every external submission when any release-blocking fact is expired. Scheduler state transitions should be operational convenience, not the enforcement boundary.
- **Required retest:** expired profile/Header/template/binding/tenant timestamps with statuses still ACTIVE; scheduler stopped; long-running broadcast crossing expiry; resume only after a verified refresh.

### MS-SMS-R1-005 — Configured webhook replay window is unused

- **Severity:** HIGH
- **Status:** OPEN
- **Violated invariant:** Trusted callbacks must enforce replay/time bounds where provider timestamps exist, in addition to deduplication.
- **Affected component / worker:** B2 events/webhook
- **Exact evidence / reproduction:**
  - `ManagedSmsRuntimeProperties` has `eventReplayWindow`; `application.yaml` defaults it to 10 minutes.
  - `ManagedSmsEventInboxService.receive()` verifies size, static secret, JSON shape, correlation and request ID consistency, then inserts/deduplicates an event.
  - It never reads `eventReplayWindow` and never rejects an old/future provider event timestamp.
  - `eventAt()` falls back to `Instant.now()` for absent or unparsable timestamps, which prevents a bad timestamp from failing the trust gate.
  - Correlations live for 30 days.
  - Reproduction: replay a previously valid event outside the configured replay window. An exact duplicate may be deduplicated, but an old callback with a different canonical event identity/status/timestamp can still be accepted and queued.
- **Recommended remediation:** Parse the provider timestamp in the trust gate, enforce past/future skew against the configured replay window, and define a fail-closed policy for malformed or unexpectedly absent timestamps based on the verified MSG91 webhook contract. Keep deduplication as a separate control.
- **Required retest:** exact duplicate, modified duplicate, >10-minute old event, future event, malformed timestamp, missing timestamp, late legitimate DLR per documented provider semantics.

### MS-SMS-R1-006 — B2 ownership constraints are incomplete; billing/state helpers do not bind company+run+recipient atomically

- **Severity:** HIGH
- **Status:** OPEN
- **Violated invariant:** Every company-scoped resource lookup/mutation must include company ownership; run/recipient/correlation/usage tuples must not be cross-company or cross-run.
- **Affected component / worker:** B2 runtime/metering/events + V64 integration constraints
- **Exact evidence / reproduction:**
  - V64 adds same-company composite FKs from `managed_sms_run_bindings` to B1 control-plane records, which is good.
  - V63/V64 do not add equivalent composite company/run/recipient ownership constraints for `managed_sms_correlations`, `managed_sms_usage_reservations`, or `managed_sms_recipient_delivery`; V63 uses independent FKs.
  - `ManagedSmsCorrelationService.create(companyId, runId, recipientId, attemptNo)` reuses/creates by recipient+attempt without verifying that the recipient belongs to the supplied company/run.
  - `ManagedSmsUsageService.commit()` calls `lockReservation(recipientId, attemptNo)`; that SQL does not include company or run. It then calls `UsageBillingService.record(companyId, ... reservation.segments())` **before** the company-scoped reservation update.
  - `ManagedSmsRecipientStateService.confirmAccepted()` selects/updates `broadcast_recipients` by recipient ID only and transitions stats for a separately supplied run ID.
  - Normal code paths usually pass matched tuples, but the security invariant explicitly requires the lookup itself to enforce ownership. A corrupted/mismatched correlation or internal call can turn into cross-company billing or state/stat mutation.
- **Recommended remediation:** Bind all B2 hot-path queries to `company_id + run_id + recipient_id (+ attempt_no)`, validate the exact tuple before mutation/billing, and add composite DB constraints/indexes where the existing Broadcast schema supports them. Never bill before ownership is verified.
- **Required retest:** crafted cross-company correlation, same-company wrong run, foreign recipient, foreign usage reservation, mismatched run binding, duplicate attempt, and concurrent commit.

### MS-SMS-R1-007 — CHILD_PER_COMPANY topology is not enforced by the runtime credential path

- **Severity:** HIGH
- **Status:** BLOCKED / PROVIDER CONTRACT UNVERIFIED
- **Violated invariant:** Preferred production topology isolates each company in a provider child/client account, and the provider credential/account actually used for submission must match that binding.
- **Affected component / worker:** B1/B2 provider topology
- **Exact evidence / reproduction:**
  - B1 defaults `providerMode` to `CHILD_PER_COMPANY`; eligibility requires a child/client reference for non-shared-master mode.
  - B2 snapshots the provider account/client key for rate/event correlation.
  - `ManagedMsg91Gateway` does not use provider mode or provider account/client key to select credentials. Every request uses `properties.getMasterAuthKey()`.
  - No child credential service/resolver is present in the Round-1 runtime.
  - The architecture permits a shared-master model only after MSG91/access-provider confirmation of the commercial/account and PE-TM-chain arrangement.
- **Recommended remediation:** Either implement a server-side provider credential resolver that submits through the verified child/account associated with the run, or formally select the shared-master model only after written/provider-account validation proves it is the correct supported path for every customer PE/Header/template mapping. Do not treat child reference metadata alone as isolation.
- **Required retest:** real MSG91 account: two companies/children, credential/account isolation, wrong-child Flow rejection, PE/Header/template mapping, DLR account correlation, wallet behavior.

### MS-SMS-R1-008 — Raw MSG91 429 message can reach customer-facing recipient API data

- **Severity:** MEDIUM
- **Status:** OPEN
- **Violated invariant:** Customer responses/errors must use stable internal codes/messages and must not expose raw provider account/client/Flow/topology details.
- **Affected component / worker:** B2 gateway + existing Broadcast recipient API; F1 provides UI defense-in-depth only
- **Exact evidence / reproduction:**
  - `ManagedMsg91Gateway` reads provider `message/description/error` and, for HTTP 429, throws a retryable exception using that raw provider message as the exception text.
  - `ManagedSmsDeliveryCoordinator` passes the exception message to `BroadcastDeliveryStateService.retry()`.
  - `BroadcastDeliveryStateService` persists the reason in recipient/attempt failure fields.
  - `BroadcastRecipientResponse` returns `failureReason` and `failureCode`.
  - F1 deliberately hides raw failure reasons for managed SMS in the rendered table, but direct API clients can still receive the backend field.
  - Reproduction: simulate MSG91 429 with a message containing an account/client/Flow diagnostic; fetch the broadcast recipients API.
- **Recommended remediation:** Map every provider response, including 429, to customer-safe fixed text before it reaches recipient state. Store detailed provider diagnostics only in restricted ops telemetry with explicit redaction.
- **Required retest:** 429/4xx/5xx/credit/provider-suspension responses containing fake AuthKey/account/client/Flow IDs; verify customer API, logs and UI contain none of them.

### MS-SMS-R1-009 — Distributed scopes share one global token rate and there is no campaign absolute safety cap

- **Severity:** MEDIUM
- **Status:** OPEN
- **Violated invariant:** Distributed platform/account/company/campaign/new-tenant controls must prevent a noisy tenant/run from consuming shared provider capacity.
- **Affected component / worker:** B2 rate/safety
- **Exact evidence / reproduction:**
  - `ManagedSmsRateLimiter` creates distinct Redis keys for global, account, company and run scopes.
  - The Lua script receives one `rate`, sourced only from `globalMaxPerSecond`, and applies that identical capacity/refill rate to all four scopes.
  - New-tenant hour/day counters exist, but established-company/run-specific throughput limits are not independently configurable.
  - No absolute campaign/run size safety cap is enforced by this limiter; launch only checks plan segments and a new-tenant daily cap.
  - Reproduction: two established tenants/runs can each be configured only against the same global threshold; a single established tenant/run can consume the full configured global per-second budget.
- **Recommended remediation:** Add explicit provider/account, company, run/campaign and new-tenant rates plus an absolute campaign safety cap, enforced in one atomic Redis operation. Keep Redis failure fail-closed.
- **Required retest:** multi-pod/two-tenant contention, noisy-tenant starvation, account cap lower than global, company/run caps lower than account, campaign-size cap, Redis outage and recovery.

### MS-SMS-R1-010 — Approved-body fingerprint parameter is never verified

- **Severity:** MEDIUM
- **Status:** OPEN
- **Violated invariant:** Runtime send content must be bound to the frozen approved body fingerprint/snapshot.
- **Affected component / worker:** B2 runtime template renderer
- **Exact evidence / reproduction:**
  - `ManagedSmsTemplateService.render(approvedBody, expectedFingerprint, ...)` only checks that `expectedFingerprint` is nonblank.
  - It never hashes `approvedBody` and compares it to `expectedFingerprint`.
  - Existing tests intentionally pass the literal string `opaque-fingerprint` and still expect successful rendering, confirming that the value is not validated.
  - Reproduction: call `render()` with a modified approved body and an unrelated nonblank fingerprint; rendering proceeds.
- **Recommended remediation:** Canonicalize exactly as B1 does, recompute SHA-256 (or the frozen fingerprint algorithm) and constant-time compare before variable substitution. Fail closed on mismatch.
- **Required retest:** correct hash, wrong hash, changed fixed text, changed whitespace according to canonicalization rules, run-snapshot tampering.

### MS-SMS-R1-011 — ACTIVE DLT profile can mutate reviewed metadata/evidence without returning to review

- **Severity:** MEDIUM
- **Status:** OPEN
- **Violated invariant:** Release-blocking compliance facts/evidence must remain tied to the reviewed state and audit lifecycle.
- **Affected component / worker:** B1 control/compliance
- **Exact evidence / reproduction:**
  - `ManagedSmsProfileService.patch()` prevents changing PE ID when profile status is ACTIVE.
  - It permits changing `legalName`, `dltPlatform` and `evidence` while leaving ACTIVE status intact.
  - Changing `tmChainReference` correctly moves chain state back to review, but the other reviewed fields do not.
  - Reproduction: approve a profile, PATCH new evidence or DLT platform without changing PE/TM reference, then read status; profile remains ACTIVE until another independent staleness mechanism intervenes.
- **Recommended remediation:** Define which compliance fields are immutable after approval. Any review-relevant change should create a new version/evidence revision or invalidate ACTIVE state and require re-review with audit history.
- **Required retest:** ACTIVE profile changes for legal name, DLT platform, PE ID, TM chain reference and evidence; verify correct review/audit transitions and send blocking.

### MS-SMS-R1-012 — Admin template approval can mark a binding ACTIVE with an unusable provider reference set

- **Severity:** MEDIUM
- **Status:** OPEN
- **Violated invariant:** READY/ACTIVE means the exact provider binding required by runtime delivery is complete and usable.
- **Affected component / worker:** B1 admin/provisioning + B2 gateway
- **Exact evidence / reproduction:**
  - `ManagedSmsAdminService.decideTemplate()` considers provider refs supplied when **either** `providerTemplateRef` **or** `providerFlowRef` is nonblank.
  - In that branch it marks the provider binding ACTIVE and can mark the template version ACTIVE.
  - `ManagedMsg91Gateway.send()` requires `providerFlowRefSnapshot`; missing Flow ID is a permanent `MANAGED_SMS_TEMPLATE_NOT_READY` failure.
  - Reproduction: admin approves with providerTemplateRef only. Control plane can show ACTIVE/READY, while delivery later fails because Flow ID is null.
- **Recommended remediation:** Centralize provider-binding readiness in the provider adapter and require every runtime-required reference/state before activation. Do not encode readiness as an OR between unrelated identifiers.
- **Required retest:** template-ref only, flow-ref only, mapping-ref only, complete valid set, invalid/stale mapping and provider refresh.

### MS-SMS-R1-013 — Webhook authentication is global, not bound to the provider account/child endpoint

- **Severity:** MEDIUM
- **Status:** OPEN / PROVIDER AUTH CAPABILITY UNVERIFIED
- **Violated invariant:** A callback trust decision must include exact endpoint/account association; one tenant/account compromise must not authenticate another tenant's callbacks.
- **Affected component / worker:** B2 webhook trust
- **Exact evidence / reproduction:**
  - `ManagedSmsWebhookController` exposes one MSG91 path and one header: `X-UnifyDesk-Managed-Sms-Secret`.
  - `ManagedSmsEventInboxService.trustedSecret()` compares it against one global `app.managed-sms.webhook-secret`.
  - Provider account key is inferred after correlation lookup; the incoming request does not cryptographically authenticate the child/account identity.
  - The research document correctly marks signed/HMAC capability as an external item that must be confirmed with MSG91.
- **Recommended remediation:** After provider verification, prefer MSG91 signed/HMAC payload validation if available. Otherwise use a high-entropy per-account secret/dedicated unguessable endpoint mapped to exactly one provider account, then verify that mapping against correlation before mutation.
- **Required retest:** wrong global/account secret, Company-A child callback using Company-B correlation, endpoint/account mismatch, rotated secret, old secret, unsigned callback when signatures are available.

### MS-SMS-R1-014 — Required executable QA/migration verification is not available in this review runtime

- **Severity:** HIGH
- **Status:** BLOCKED
- **Violated invariant:** Round-1 exit criteria require clean/upgrade Flyway, backend compile/Spotless/full tests and frontend test/build/lint/format results from the exact integrated SHA.
- **Affected component / worker:** QA environment / integration verification
- **Exact evidence / reproduction:**
  - GitHub connector access allowed exact-SHA source review and branch creation, but no executable checkout is mounted in the session.
  - The local execution container cannot resolve `github.com`, so it cannot clone the private repository.
  - GitHub workflow lookup for the exact base and the three worker merge SHAs returned no associated PR workflow runs.
  - `INTEGRATION_STATUS.md` independently states earlier branch workflows failed before job steps were populated and are not test passes.
- **Recommended remediation:** Run the command matrix above in an authorized checkout/CI runner at exactly `b3a0155570d67e30be11889a60c5c40e9c2544b8` (or the remediation integration SHA for Round 2), including Postgres/Redis services and both clean + upgrade migration paths. Attach command output/CI run URLs to the retest.
- **Required retest:** all command-matrix entries; no release decision should convert NOT EXECUTED to PASS without actual output.

---

## Requested threat/test matrix — Round-1 disposition

| Area | Disposition |
|---|---|
| Clean/upgrade Flyway V62-V64 | **BLOCKED — executable migration run unavailable; static V64 ownership gap recorded** |
| Backend compile/Spotless/full tests | **BLOCKED; MS-SMS-R1-001 is a deterministic test failure** |
| Frontend test/build/lint/format | **BLOCKED — executable checkout unavailable** |
| CUSTOMER_MANAGED MSG91 regression | **Static path/test present; execution pending** |
| Company/tenant/profile/Header/template IDOR | **B1 customer API scoping looks correct statically; B2 tuple ownership gap in MS-SMS-R1-006** |
| Sender/run/recipient ownership | **MS-SMS-R1-006 OPEN** |
| PE-TM chain enforcement | **Launch/control checks present; real DLT chain unverified externally** |
| Stale/suspended compliance | **Launch/suspension checks present; delivery-time freshness defect MS-SMS-R1-004** |
| Arbitrary/raw managed body bypass | **Static control present; compose replaces scratch body with server-rendered approved template** |
| Header/template mismatch | **Static control present in compose/runtime joins; execution pending** |
| Fixed-template modification | **V62 DB trigger + service checks present; fingerprint check missing MS-SMS-R1-010** |
| Invalid/extra variable injection | **Static checks present; CTA whitelist gap MS-SMS-R1-003** |
| CTA/URL/callback bypass | **MS-SMS-R1-003 OPEN** |
| Provider credential/topology leakage | **F1 sanitization good; raw 429 API leakage MS-SMS-R1-008; topology runtime issue MS-SMS-R1-007** |
| Webhook forgery | **Static secret gate present; account-bound auth weakness MS-SMS-R1-013** |
| Webhook replay/duplicates | **DB idempotency present; replay-window defect MS-SMS-R1-005** |
| Cross-company webhook correlation | **Application checks exist, but systemic tuple ownership gap MS-SMS-R1-006 and account-auth issue MS-SMS-R1-013 remain** |
| Duplicate sends / UNKNOWN retry | **Focused source/test indicates UNKNOWN is not retried; execution pending** |
| Exactly-once usage/billing | **Idempotency controls present; cross-company commit ownership defect MS-SMS-R1-006** |
| Concurrent quota | **Postgres advisory-lock test exists; execution pending** |
| Redis/rate-limit failure | **Fail-closed implementation/test exists; per-scope policy defect MS-SMS-R1-009** |
| Provider wallet exhaustion | **MS-SMS-R1-002 OPEN; real wallet semantics external** |
| Admin/manual review | **Permission/company/reason/version/audit controls present statically; active profile mutation issue MS-SMS-R1-011** |
| Duplicate/out-of-order events | **Idempotent inbox and delivered-wins projection logic present; replay execution pending** |
| Historical identifiers/template versions | **Snapshot/archive/immutability design present; fingerprint verification defect MS-SMS-R1-010** |
| Frontend privacy | **No F1 privacy defect found in static review; execution pending** |
| Company switching/stale data | **F1 clears state and drops old-company late responses; execution pending** |
| Stale/mock provider fields in UI | **F1 sanitizers explicitly drop them; execution pending** |

## Findings summary

| Severity | OPEN | BLOCKED / EXTERNAL | Total |
|---|---:|---:|---:|
| CRITICAL | 0 | 0 | 0 |
| HIGH | 6 | 2 | 8 |
| MEDIUM | 6 | 0 | 6 |
| LOW | 0 | 0 | 0 |
| **Total** | **12** | **2** | **14** |

## External / untested GA blockers

The following remain unverified and must not be marked passed without a real production-contract MSG91/DLT environment:

1. UnifyDesk's exact telemarketer registration/classification and the actual PE-TM chain used for each customer.
2. Contracted master/subaccount/child behavior, including which AuthKey is authoritative for child traffic.
3. Customer PE/Header mapping and DLT Template ID / Flow provisioning behavior.
4. Whether MSG91 Webhook (New) supports signed/HMAC payload authentication; otherwise the exact supported custom-header/per-account secret model.
5. CRQID and request ID behavior across real sends/DLRs, including duplicate/late callbacks.
6. Provider 429/rate semantics and per-child isolation.
7. Provider balance/credit API, authoritative wallet level, acceptance-time charging and downstream refund/credit behavior.
8. CTA whitelisting behavior against the actual DLT/operator scrubber.
9. 2026 Third Amendment staged effective-date changes as each provision becomes effective.

## Round-2 retest gate

Do not move Managed SMS to release-ready until:

- all HIGH OPEN findings are remediated and retested;
- MEDIUM findings are either remediated or explicitly accepted by the Architecture/Security owner with evidence;
- the exact integrated remediation SHA passes backend compile + Spotless + full tests;
- frontend tests + build + lint + format check pass;
- both clean and representative upgrade Flyway paths succeed through the final migration;
- real-provider blockers required for production sending are closed with recorded evidence;
- CUSTOMER_MANAGED MSG91 regression remains green.
