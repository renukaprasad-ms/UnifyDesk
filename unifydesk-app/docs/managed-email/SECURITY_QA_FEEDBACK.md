# Managed Email Security & QA Feedback

**Owner during review:** Security & QA  
**Review branch:** review/managed-email-security-qa-r1  
**Frozen integrated base SHA:** 8708a51c53eb53a5cdca89a205deb7ba7a010bc7  
**Review date:** 2026-09-26  
**Current status:** ROUND_2_RETEST_COMPLETE_NOT_RELEASE_READY

## Executive summary

Round-1 is **not release-ready**.

The review branch was created from the exact frozen integrated SHA and was not rebased onto moving dev. Architecture, security, API-contract, data-model, SES research, QA plan, Broadcast architecture, implementation code, migrations, runtime guards, event processing, rate limiting, DNS integration and frontend Managed Email flows were inspected against the frozen invariants.

The integrated candidate has several good controls, including company-scoped Managed Email API lookups, provider-event signature validation, exact TopicArn/region checks, SSRF-resistant SNS certificate URL validation, managed/customer SES credential separation, atomic monthly quota reservation, database uniqueness for live customer-domain claims, hosted-domain permanent reservation, 2048-bit Easy DKIM configuration, and reuse of the existing Broadcast engine.

However, the candidate contains multiple release-blocking failures:

1. the backend source at the frozen SHA contains a Java syntax error and cannot compile as committed;
2. the runtime sender guard does not prove that the persisted From address still belongs to the bound Managed Email domain;
3. suppression is checked during recipient snapshot creation but is not re-checked immediately before provider submission;
4. a provider event is permanently deduplicated before all required side effects finish, so a crash/error can lose billing, suppression or delivery projection reconciliation;
5. SES account 24-hour quota synchronization is incomplete and the Redis account cap is calendar-day based instead of SES's rolling 24-hour model;
6. account SendingEnabled / sandbox state is not a complete local send gate.

A second review/retest is required after Architecture Lead-approved fixes.

---

## Build and execution status

### Branch/base verification

PASS.

The review branch exists at exactly:

    8708a51c53eb53a5cdca89a205deb7ba7a010bc7

No later dev commit was used as the review base.

### GitHub Actions independent check

A push of the frozen SHA to the review branch triggered Checks run:

    36236675480

The jobs backend, frontend, website and email-provider all concluded failure before any job steps were made available. The job payloads contained no step execution data and no job logs. Therefore this run cannot be treated as evidence that Maven/npm tests executed.

This is consistent with the previously reported GitHub Actions infrastructure failure mode.

### Independent source-level build blocker

Even without a functional runner, the committed source proves the backend cannot compile at this SHA.

File:

    unifydesk-app/backend/src/main/java/com/messaging/broadcast/transport/AmazonSesBroadcastEmailTransport.java

Committed expression:

    sender.getDisplayName().replace(""", "");

This is invalid Java syntax.

Therefore the requested commands cannot truthfully be reported as passing:

Backend:

    ./mvnw -B spotless:check test
    ./mvnw -B clean test

Frontend:

    npm ci
    npm test
    npm run build
    npm run lint
    npm run format:check

### Dynamic tests not conclusively executable in this review environment

Because the integrated backend does not compile and the available CI runner failed before executing steps, the following require retest after the build blocker is fixed:

- clean PostgreSQL Flyway migration to latest;
- upgrade of an existing dev schema through V56 -> V57 -> V58;
- Hibernate schema validation;
- real Redis multi-worker contention;
- real SES sandbox/production integration;
- Cloudflare failure injection;
- browser desktop/tablet/mobile interaction;
- large event burst/performance runs;
- end-to-end existing-provider regression.

This report does not convert those unexecuted checks into PASS claims.

---

# Findings

## ME-SEC-001 — Integrated backend does not compile at the frozen release candidate

- **Severity:** high
- **Area:** reliability
- **Affected flow/files:** AmazonSesBroadcastEmailTransport.java; entire backend build/release
- **Preconditions:** Build the frozen SHA.
- **Reproduction:**
  1. Checkout 8708a51c53eb53a5cdca89a205deb7ba7a010bc7.
  2. Open AmazonSesBroadcastEmailTransport.fromAddress().
  3. Observe the invalid replace expression.
  4. Run Maven compile/test once a runner is available.
- **Expected result:** Integrated candidate compiles before QA/security acceptance.
- **Actual result:** The committed Java source contains invalid syntax: sender.getDisplayName().replace(""", "").
- **Evidence:** Exact Git blob at the frozen SHA contains the invalid expression.
- **Security/business impact:** No backend artifact can be reliably built or tested from the claimed release candidate. All previous passing-test claims are invalid for this exact SHA until a clean build is demonstrated.
- **Required invariant:** QA-SEC-PLAN entry criterion requires backend compile/tests to pass.
- **Suggested remediation direction:** Architecture Lead should approve a minimal syntax correction, then rerun spotless/check/test and clean test from the corrected integration SHA.
- **Whether release-blocking:** yes
- **Regression test required:** CI compile plus both requested Maven command lines on the final candidate.
- **Lead disposition:** accepted
- **Round fixed:** not fixed in Round 2
- **Retest result:** STILL_OPEN — the exact frozen Round-2 SHA still contains invalid Java syntax in `AmazonSesBroadcastEmailTransport.fromAddress()` (`replace(""", "")`). GitHub Actions run 36242792959 failed before any workflow steps and exposed no logs, so Maven never executed. Source inspection independently proves this candidate cannot compile as committed.

## ME-SEC-002 — Runtime sender guard does not validate From address against the bound Managed Email domain

- **Severity:** high
- **Area:** sender spoofing
- **Affected flow/files:** JdbcManagedEmailRuntimeRepository, ManagedEmailDeliveryGuard, SesSendContextResolver, AmazonSesBroadcastEmailTransport
- **Preconditions:** A legitimate Managed Email sender/domain exists; sender row is later corrupted or modified through an administrative bug, migration, compromised database path or future code path.
- **Reproduction:**
  1. Create ACTIVE domain example.com and sender offers@example.com.
  2. Keep managed_email_sender_bindings pointing to example.com.
  3. Alter broadcast_senders.from_address to another domain/address while leaving sender/binding/domain statuses ACTIVE.
  4. Deliver an already prepared recipient.
  5. The runtime query validates company, sender, connection, binding, domain and tenant status but does not load/compare s.from_address with d.normalized_domain.
  6. SesSendContextResolver likewise does not perform this comparison.
  7. Transport sends using sender.getFromAddress().
- **Expected result:** Runtime fails closed unless the exact From address domain equals the ACTIVE bound ManagedEmailDomain.normalizedDomain.
- **Actual result:** Runtime eligibility can remain true while the transport consumes the tampered From address.
- **Evidence:** SENDER_SQL selects domain normalized_domain but not sender from_address; ManagedSenderRuntime contains no from-address field; isSendable() has no address/domain binding check.
- **Security/business impact:** Defense against sender spoofing depends on initial API creation correctness and provider behavior. The frozen security architecture explicitly requires the send-time guard to survive database relationship/address tampering and says not to rely on SES to enforce product ownership.
- **Required invariant:** SECURITY.md section 3: at send, sender binding/domain/tenant/company must match and fromAddress domain must equal the bound domain.
- **Suggested remediation direction:** Add normalized sender From address to the runtime projection and reject unless a strict parsed email-domain comparison equals the bound normalized domain. Keep this check in the provider-submission path, not only sender creation.
- **Whether release-blocking:** yes
- **Regression test required:** Tamper persisted sender.from_address after valid setup and prove no SES SendEmail call occurs.
- **Lead disposition:** accepted
- **Round fixed:** Round 2 partial remediation
- **Retest result:** PARTIALLY_FIXED — the runtime projection now loads persisted `sender_from_address`, joins binding/domain/tenant ownership, compares the normalized From domain to the bound Managed Email domain, and the SES context uses the persisted validated address instead of the stale caller object. However `ManagedEmailAddressPolicy` only validates/extracts the domain portion; malformed same-domain persisted addresses (for example an invalid local-part such as consecutive dots) can still pass the runtime domain check, and the required provider-invocation-zero attack matrix could not execute because the backend does not compile.

## ME-SEC-003 — Suppression can be bypassed after recipient snapshot creation

- **Severity:** high
- **Area:** suppression
- **Affected flow/files:** BroadcastPreparationChunkService, BroadcastDeliveryService, BroadcastSuppressionRepository
- **Preconditions:** A recipient is snapshotted as PENDING, then becomes suppressed before its provider submission.
- **Reproduction:**
  1. Launch/prepare a Broadcast containing recipient R while R is not suppressed.
  2. BroadcastPreparationChunkService snapshots R as PENDING after its one suppression query.
  3. Before the worker submits R, create an active HARD_BOUNCE, COMPLAINT, UNSUBSCRIBED, ADMIN_BLOCKED or INVALID application suppression for the same company/channel/destination.
  4. Allow the batch worker to continue.
  5. BroadcastDeliveryService performs Managed Email eligibility, rendering, rate limiting and quota reservation, but does not query current suppression before provider submission.
- **Expected result:** Application suppression is product truth and must be revalidated close to every provider submission.
- **Actual result:** A suppression created after snapshotting does not stop an already-PENDING recipient.
- **Evidence:** SuppressionRepository.findActiveDestinations() is used in BroadcastPreparationChunkService; no suppression dependency/check is present in BroadcastDeliveryService.deliverManaged().
- **Security/business impact:** A user can receive mail after unsubscribe/complaint/hard-bounce suppression if the run was already prepared. This creates compliance, deliverability and provider-reputation risk.
- **Required invariant:** SECURITY.md section 10 and the Round-1 task require suppression to prevent later sends regardless of sender, retry or new campaign.
- **Suggested remediation direction:** Re-check company + channel + normalized destination immediately before quota reservation/provider submission. Make the transition to SKIPPED idempotent and race-safe.
- **Whether release-blocking:** yes
- **Regression test required:** Snapshot recipient, add suppression, then run delivery and assert zero provider invocation and recipient becomes safely skipped/non-sendable.
- **Lead disposition:** accepted
- **Round fixed:** Round 2 source remediation present
- **Retest result:** PARTIALLY_FIXED — `ManagedEmailSuppressionGate` now rechecks active company-scoped EMAIL suppression after rendering and before Managed Email rate consumption, usage/provider-budget reservation, and provider submission; it transitions a PROCESSING recipient to SKIPPED and source tests cover every suppression enum plus company isolation. The required executable race test did not run because the frozen backend cannot compile and CI executed zero steps, so this cannot be certified CLOSED.

## ME-SEC-004 — Provider event is deduplicated before required side effects complete

- **Severity:** high
- **Area:** events
- **Affected flow/files:** ManagedEmailProviderEventProcessor, ManagedEmailEventLedger, delivery projection, suppression, usage and reputation services
- **Preconditions:** A valid signed SNS event passes correlation, then any downstream mutation fails after ledger insertion.
- **Reproduction:**
  1. Submit a valid matched SEND/BOUNCE/COMPLAINT/etc event.
  2. ManagedEmailProviderEventProcessor calls ledger.recordMatched() before projection/billing/suppression side effects.
  3. recordMatched() inserts managed_email_provider_events with processed_at=now().
  4. Inject a failure in projectionService.apply(), usageService.commitLatest(), suppressionService or a later state mutation.
  5. SNS retries the same MessageId.
  6. ledger.alreadyReceived(MessageId) returns true and the processor returns immediately.
- **Expected result:** A successfully authenticated event may be deduplicated, but processing completion must be atomic or resumable. A retry after partial failure must finish missing side effects exactly once.
- **Actual result:** Ledger receipt is treated as processing completion. A partial failure can permanently discard the event's remaining effects.
- **Evidence:** ManagedEmailProviderEventProcessor is not transactionally wrapping ledger + projection + local side effects; ManagedEmailEventLedger writes processed_at during the initial insert.
- **Security/business impact:** Lost hard-bounce/complaint suppression, missed billing reconciliation, stale delivery projection, incorrect reputation and inconsistent statistics are possible after ordinary transient failures or crashes.
- **Required invariant:** SECURITY.md section 9 requires idempotent state mutations; ADR-011 relies on SNS retry behavior; provisioning/event reliability requires eventual convergence.
- **Suggested remediation direction:** Separate RECEIVED from PROCESSED state and use an atomic transaction for local DB effects where possible, or persist resumable processing checkpoints/outbox work. Only mark processed after required durable local mutations complete.
- **Whether release-blocking:** yes
- **Regression test required:** Force failure after ledger insert, retry same SNS MessageId, and prove missing side effects complete once without double billing/suppression.
- **Lead disposition:** accepted
- **Round fixed:** Round 2 partial remediation
- **Retest result:** PARTIALLY_FIXED — matched event receipt is now durable with `processed_at = null`, local projection/usage/local suppression/reputation run transactionally under a row lock, and retries can resume failed local work. But `ManagedEmailEventLocalProcessor` marks `processed_at` before `ManagedEmailProviderEventProcessor` performs the required external SES tenant suppression call. Therefore `processed_at` still does not mean the complete event workflow finished, and there is no durable provider-suppression completion/checkpoint for a crash after local commit but before the external call. This remains release-blocking.

## ME-SEC-005 — SES 24-hour account quota is modeled as a UTC calendar-day counter instead of a rolling 24-hour quota

- **Severity:** high
- **Area:** AWS/SES
- **Affected flow/files:** AwsManagedEmailSesGateway, ManagedEmailRateLimiter, ManagedEmail tenant quota state
- **Preconditions:** Managed Email sending enabled with a finite SES Max24HourSend.
- **Reproduction:**
  1. Observe AwsManagedEmailSesGateway stores MaxSendRate and Max24HourSend but not SentLast24Hours from SES GetAccount SendQuota.
  2. Observe ManagedEmailRateLimiter creates account-day Redis key using yyyyMMdd UTC.
  3. Send near the allowed account cap shortly before UTC midnight.
  4. After midnight the limiter uses a new account-day key while SES still counts the previous sends in its rolling prior-24-hour window.
  5. A process/Redis restart also starts with no knowledge of sends already made during the previous 24 hours.
- **Expected result:** The Managed Email limiter must respect the provider's actual remaining rolling 24-hour capacity and fail closed when it cannot determine safe remaining capacity.
- **Actual result:** The local daily account cap resets by UTC date and ignores SES SentLast24Hours/current rolling usage.
- **Evidence:** ManagedEmailRateLimiter key is account-day:<region>:<yyyyMMdd>; AwsManagedEmailSesGateway TenantSnapshot contains maxSendRate and max24HourSend only. AWS SES documentation states the 24-hour sending quota is calculated on a rolling time period and exposes SentLast24Hours.
- **Security/business impact:** Aggregate Managed Email sends can exceed the provider's real rolling account quota, producing throttling/rejections and potentially impacting every tenant sharing the SES account.
- **Required invariant:** ADR-015 and the QA task require the account-region limiter to know real SES account limits and prevent accidental provider/account policy exceedance.
- **Suggested remediation direction:** Synchronize SentLast24Hours (or equivalent current rolling usage), derive a conservative remaining provider budget, and combine it atomically with UnifyDesk reservations. Do not model provider Max24HourSend as a midnight-reset quota.
- **Whether release-blocking:** yes
- **Regression test required:** Simulate sends across UTC midnight and process restart with non-zero provider SentLast24Hours; prove aggregate sends remain within provider remaining quota.
- **Lead disposition:** accepted
- **Round fixed:** Round 2 partial remediation
- **Retest result:** PARTIALLY_FIXED — Round 2 now captures MaxSendRate, Max24HourSend and SentLast24Hours, uses a durable DB provider budget with a region advisory lock, counts local commits/reservations after the provider snapshot, applies snapshot freshness, and no longer treats UTC midnight as the provider quota reset. Residual race: the provider observation time is not captured in `TenantSnapshot`; `connection.lastValidatedAt` is assigned later during persistence. A send accepted after `GetAccount` returns but before that later timestamp can be absent from both SES `SentLast24Hours` and `committedSince(lastValidatedAt)`, temporarily understating usage. The mandatory live midnight/restart/Redis/quota-decrease tests also could not execute. Release blocker remains.
- **External reference:** https://docs.aws.amazon.com/ses/latest/dg/manage-sending-quotas.html

## ME-SEC-006 — SES account SendingEnabled / sandbox production state is not a complete local eligibility gate

- **Severity:** high
- **Area:** AWS/SES
- **Affected flow/files:** AwsManagedEmailSesGateway, ManagedEmailProvisioningWorker.persistTenantProviderState, ManagedEmailRuntimeRepository
- **Preconditions:** SES account is sandboxed or account sending is disabled while SES tenant status remains ENABLED/REINSTATED.
- **Reproduction:**
  1. GetAccount reports ProductionAccessEnabled=false and/or SendingEnabled=false.
  2. AwsManagedEmailSesGateway combines both into one providerProductionAccessEnabled boolean.
  3. ManagedEmailProvisioningWorker sets tenant.sendingStatus from the SES tenant sending status only.
  4. connection.sendingEnabled is calculated from platform flags and tenant.sendingStatus, without requiring providerProductionAccessEnabled.
  5. ManagedSenderRuntime.isSendable() likewise does not require productionAccessEnabled/account SendingEnabled.
- **Expected result:** Account-level sending disablement fails closed locally; normal Managed Email campaigns do not launch/send from an SES sandbox account.
- **Actual result:** Local runtime can remain sendable even when account-level GetAccount state says normal production sending is unavailable.
- **Evidence:** persistTenantProviderState() copies providerProductionAccessEnabled but does not use it in connection.setSendingEnabled(); runtime record has no production-access/account-sending field.
- **Security/business impact:** Repeated provider rejections/throttling and sandbox-only behavior can leak into customer workloads, while an AWS account safety stop is not reflected promptly in local eligibility.
- **Required invariant:** QA task section 15; SECURITY.md account-health/containment requirements.
- **Suggested remediation direction:** Persist account SendingEnabled separately from ProductionAccessEnabled and require safe account state in launch and per-recipient runtime guards.
- **Whether release-blocking:** yes
- **Regression test required:** Mock sandbox, production, and account-sending-disabled GetAccount responses and prove only allowed state can launch/submit.
- **Lead disposition:** accepted
- **Round fixed:** Round 2 source remediation present
- **Retest result:** PARTIALLY_FIXED — SES `SendingEnabled` and `ProductionAccessEnabled` are now captured separately; managed connection enablement requires account sending enabled, production access, tenant/provider state and usable quota, and both launch/submission runtime guards require the derived safe connection state plus a fresh provider snapshot. Source tests cover sandbox/account-disabled cases. Dynamic launch/submission retest could not execute because Maven never ran, so this is not certified CLOSED.
- **External reference:** https://docs.aws.amazon.com/ses/latest/APIReference-V2/API_GetAccount.html

## ME-SEC-007 — Cross-module ownership is not fully enforced by database foreign keys

- **Severity:** medium
- **Area:** tenant isolation
- **Affected flow/files:** V56__managed_email_control_plane.sql, V57__managed_email_delivery_usage.sql, V58__managed_email_cross_module_constraints.sql
- **Preconditions:** Direct SQL, faulty migration, future bug or privileged code attempts an inconsistent ownership insert.
- **Reproduction:**
  1. Review V58 composite keys added for tenant/domain/sender event references.
  2. managed_email_domains still references tenant_id alone, not (tenant_id, company_id).
  3. managed_email_sender_bindings references sender_id, domain_id and tenant_id independently plus company_id, but no composite FK guarantees that all parents belong to that company.
  4. managed_email_dns_records has separate domain and company FKs but no (domain_id, company_id) FK.
  5. usage events likewise carry company/recipient/run IDs without a complete composite ownership FK graph.
- **Expected result:** Important ownership corruption should be rejected by the database, not only hidden by repository joins.
- **Actual result:** Several inconsistent cross-company combinations can exist at the relational layer even though current runtime joins often fail closed.
- **Evidence:** V58 adds composite uniqueness but only provider-event/reputation composite FKs; it does not retrofit the full managed ownership graph.
- **Security/business impact:** Defense-in-depth is weaker and future code that assumes relational integrity can become an IDOR/cross-tenant mutation path.
- **Required invariant:** SECURITY.md treats a validated relational ownership graph as trusted; QA task section 29 requires direct inconsistent inserts to be stopped for important ownership relationships.
- **Suggested remediation direction:** Architecture Lead should define the minimal composite unique keys/FKs for tenant-domain, company-domain DNS, sender-binding and usage relationships, then add a migration after V58.
- **Whether release-blocking:** no by itself, but should be fixed before production hardening; becomes blocking if any path bypasses current fail-closed joins.
- **Regression test required:** SQL integration tests attempting cross-company domain/tenant/binding/DNS/usage inserts.
- **Lead disposition:** accepted
- **Round fixed:** Round 2 source remediation present in V59
- **Retest result:** PARTIALLY_FIXED — V59 adds composite ownership constraints for domain→tenant/company, DNS→domain/company, sender binding→sender/domain/tenant company ownership, and usage→run/recipient/company. `NOT VALID` constraints still reject new violating rows, but they intentionally do not validate pre-existing V56–V58 rows. The included integration test models new-row rejection but the actual clean migration, V58→V59 upgrade with existing data, and Hibernate validation did not execute; the test also self-skips when PostgreSQL is unavailable. No duplicate migration version was found.

## ME-SEC-008 — Generic Broadcast sender API/UI exposes platform SES region and shared account quota data for Managed Email

- **Severity:** medium
- **Area:** privacy
- **Affected flow/files:** BroadcastSenderResponse, BroadcastWorkspace.tsx, generic /broadcast/senders compatibility flow
- **Preconditions:** Tenant can read its Broadcast senders and has a MESSAGING_MANAGED AMAZON_SES sender.
- **Reproduction:**
  1. ManagedEmailProvisioningWorker writes SES account maxSendRate/max24HourSend into the shared managed BroadcastSenderConnection.
  2. BroadcastSenderResponse.from() returns connection.region, productionAccessEnabled, max24HourSend, maxSendRate and sentLast24Hours for all sender modes.
  3. BroadcastWorkspace labels the provider as UnifyDesk Managed Email but still renders sender.region and maxSendRate when present.
- **Expected result:** Managed Email customer-facing API/UI does not expose AWS region or platform-account quota internals.
- **Actual result:** The generic Broadcast compatibility response can expose actual region and account-derived send limits; the workspace can render region/rate next to the Managed Email label.
- **Evidence:** BroadcastSenderResponse.from() directly copies connection region/quota fields; BroadcastWorkspace condition excludes MSG91 only, not MESSAGING_MANAGED.
- **Security/business impact:** Leaks internal platform capacity/topology and violates the Managed Email abstraction. It can also mislead a customer into treating an account-wide/provider limit as its personal allowed rate.
- **Required invariant:** API-CONTRACT.md: customer API must not expose AWS region or platform account quotas; provider branding should show UnifyDesk Managed Email.
- **Suggested remediation direction:** Sanitize generic BroadcastSenderResponse for MESSAGING_MANAGED or add a safe projection that returns MANAGED/no shared quota fields, and suppress those details in all frontend surfaces.
- **Whether release-blocking:** yes for Round-1 contract compliance
- **Regression test required:** Managed sender response/history/workspace tests proving no AWS region/account quota fields are displayed while CUSTOMER_MANAGED SES still shows its own provider details.
- **Lead disposition:** accepted
- **Round fixed:** Round 2 source remediation present
- **Retest result:** PARTIALLY_FIXED — backend `BroadcastSenderResponse` now returns `region=MANAGED` and null shared quota/usage/validation fields for MESSAGING_MANAGED while preserving CUSTOMER_MANAGED SES details. Frontend privacy helpers suppress stale managed region/rate fields, and the Architecture Lead sidebar fix uses the managed-safe label-only path. Stale fixture tests exist for composer/history/detail/viewport surfaces. Frontend npm test/build/lint/format commands never executed because the available Actions runner failed before steps, so the end-to-end privacy retest cannot be certified CLOSED.

## ME-SEC-009 — Managed Email provisioning/control endpoints lack the frozen abuse-rate controls and manual containment surface

- **Severity:** medium
- **Area:** abuse
- **Affected flow/files:** ManagedEmailController, ManagedEmailDomainService, ManagedEmailSenderService, Managed Email admin/ops surface
- **Preconditions:** Authenticated tenant with Managed Email entitlement and capacity.
- **Reproduction:**
  1. Repeatedly invoke customer-domain creation, hosted-domain creation, sender creation, refresh and delete/recreate within entitlement capacity.
  2. ManagedEmailController wires authorization but no request-rate limiter.
  3. Domain refresh has only a five-second provider-sync freshness check, not a per-user/company abuse limiter.
  4. No Round-1 admin endpoint/service was found for explicit policy suspension/unsuspension of a tenant/domain; current tenant suspension is automated reputation logic or direct state manipulation.
- **Expected result:** Security architecture requires rate limiting of provisioning operations and a fast administrative containment path for a compromised tenant.
- **Actual result:** Delivery has hierarchical Redis controls, but control-plane provisioning abuse and manual tenant kill-switch operations are not equivalently implemented.
- **Evidence:** ManagedEmailController dependencies contain no Redis/control-plane limiter; services enforce entitlement/idempotency but not request velocity; no dedicated administrative Managed Email suspension controller was found in the reviewed module.
- **Security/business impact:** A compromised tenant can create avoidable AWS/Cloudflare/provider load and operational noise; incident responders lack a first-class tenant containment control besides broader/global controls or direct DB intervention.
- **Required invariant:** SECURITY.md sections 11-13 require admin kill switch and rate limits for domain/sender/provisioning operations.
- **Suggested remediation direction:** Add company/user-scoped control-plane rate limiting and an audited privileged suspension/resume surface that immediately blocks launch and per-recipient submission.
- **Whether release-blocking:** no for basic functional testing, but recommended before production exposure
- **Regression test required:** Burst create/refresh/delete tests and admin suspension test proving in-flight workers stop subsequent provider submissions.
- **Lead disposition:** accepted
- **Round fixed:** Round 2 source remediation present
- **Retest result:** PARTIALLY_FIXED — a dedicated Redis Lua control-plane limiter now gates domain create/hosted create/refresh/delete and sender create/disable by company and user before provider service calls, returning 429 on budget exhaustion and failing closed on Redis errors. Privileged suspend/resume uses platform `COMPANY_SUSPEND` authorization plus company access, immediately disables tenant/managed connection, records actor/company/reason/time, and resume stays PROVISIONING/DISABLED until provider reconciliation. Multi-pod Redis behavior, endpoint-burst bypass checks, live 429 behavior, queued-worker containment and authorization could not be executed in this environment.

## ME-SEC-010 — Customer-domain public-suffix validation is an incomplete hard-coded approximation

- **Severity:** medium
- **Area:** DNS
- **Affected flow/files:** ManagedEmailDomainNameService
- **Preconditions:** User submits a domain whose registrability depends on a public-suffix rule not represented in the small hard-coded set/heuristic.
- **Reproduction:**
  1. Inspect normalizeCustomerDomain().
  2. Public suffix rejection uses COMMON_MULTI_LABEL_PUBLIC_SUFFIXES plus a small two-label country-code heuristic.
  3. It does not use a complete Public Suffix List/eTLD+1 implementation.
- **Expected result:** The API contract requires a registrable domain, not a public suffix.
- **Actual result:** Validation coverage depends on a manually curated subset and can drift from real public-suffix rules.
- **Evidence:** ManagedEmailDomainNameService contains a finite hand-written COMMON_MULTI_LABEL_PUBLIC_SUFFIXES set.
- **Security/business impact:** Incorrect domain-claim behavior can cause onboarding conflicts, shared-hosting suffix mistakes and avoidable domain-claim denial-of-service scenarios.
- **Required invariant:** API-CONTRACT.md section 3: not a public suffix; SECURITY.md domain claim safety.
- **Suggested remediation direction:** Use a maintained PSL/eTLD+1 implementation and add IDN/public-suffix regression vectors.
- **Whether release-blocking:** no
- **Regression test required:** Comprehensive PSL-based cases including IDN, wildcard/exception suffix rules and ordinary registrable domains.
- **Lead disposition:** accepted
- **Round fixed:** Round 2 source remediation present
- **Retest result:** PARTIALLY_FIXED — the hand-maintained suffix list is replaced with Guava `InternetDomainName` backed PSL handling after IDNA/NFC normalization; source tests cover `com`, `co.uk`, wildcard `foo.ck`, exception `www.ck`, registrable domains, Unicode/punycode equivalence, URL/email/wildcard rejection. The backend test suite did not execute, so the PSL vectors cannot be certified CLOSED on this frozen SHA.

---

# Verified controls / positive findings

These items passed source-level review at the frozen SHA. Dynamic retest is still required once the build is fixed.

## Existing Broadcast engine reuse

PASS by architecture/code inspection.

Managed Email routes through existing Broadcast launch/run/outbox/Kafka preparation/batches/recipients and BroadcastDeliveryService. Only one AMAZON_SES BroadcastEmailTransport remains. No second campaign engine, recipient engine, delivery worker or duplicate Broadcast state machine was found.

## Tenant-scoped customer APIs

PASS by source inspection for reviewed Managed Email endpoints.

ManagedEmailController performs current-user permission checks. Domain reads/mutations use findByIdAndCompanyId. Managed sender create/delete uses company-scoped binding/domain/tenant/sender lookups. Usage and deliverability controllers require BROADCAST_READ for the path company.

## Customer-domain claim concurrency

PASS structurally.

createCustomer() takes a PostgreSQL advisory transaction lock derived from normalized domain and V56 has a live global unique index over lower(normalized_domain) for CUSTOMER_DOMAIN excluding DELETED/RETIRED.

Database uniqueness remains final authority even if application pre-check races.

## Hosted-domain allocation

PASS structurally for core requirements.

Hosted names use:
- lower-case ASCII slug normalization;
- random six-character base32 suffix;
- DNS-label length limiting;
- reserved platform/vendor names;
- prohibited-token filter;
- global permanent unique index for UNIFYDESK_HOSTED domain names.

Company ID is not embedded directly in the hosted hostname. The stored hosted domain remains independent of later company rename.

## Sender creation validation

PASS at API creation layer, subject to ME-SEC-002 runtime-tamper finding.

Managed sender creation:
- accepts domainId + localPart, not arbitrary From address;
- company-scopes the domain lookup;
- requires ACTIVE domain and active tenant;
- rejects @, leading/trailing dot, consecutive dots, whitespace/control characters and >64 local part;
- validates using InternetAddress;
- constructs localPart@normalizedDomain server-side.

## Domain verification lifecycle

PASS structurally.

SES identity/DKIM status is provider-fetched. A domain becomes ACTIVE only when verification and DKIM both succeed. An ACTIVE domain that later fails verification/DKIM transitions to SUSPENDED_VERIFICATION and bound senders are disabled.

## SES identity architecture

PASS.

Implementation provisions SES domain identity, not one identity per local-part sender, associates it to the tenant/configuration set, and requests RSA_2048_BIT Easy DKIM.

No custom MAIL FROM provisioning was found.

## Hosted Cloudflare DNS baseline

PASS for reviewed SSRF/zone/write shape, subject to live failure-injection retest.

Cloudflare client:
- uses Bearer API token configuration;
- is pinned to one configured zone ID;
- uses the fixed Cloudflare API base URL;
- writes CNAME with proxied=false;
- exact-value deletion avoids deleting an unrelated record value;
- hosted DMARC is created as v=DMARC1; p=quarantine;
- unavailable DNS configuration fails hosted provisioning.

IAM/token scope itself is deployment configuration and cannot be proven from repository source alone.

## Managed/customer SES credential isolation

PASS.

CUSTOMER_MANAGED SES uses AmazonSesClientManager and customer configured credentials/assume-role behavior.

MESSAGING_MANAGED uses ManagedSesClientProvider with default workload credentials. Managed sender connection creation stores no encrypted AWS customer credentials and no role ARN.

## SNS webhook authentication and SSRF controls

PASS by source inspection for the reviewed attack classes.

The Managed Email SNS endpoint is permitAll and CSRF-exempt, then SnsMessageVerifier enforces:
- body size;
- bounded JSON depth;
- exact configured TopicArn;
- expected region;
- supported SNS partition;
- HTTPS certificate/subscription URL;
- exact sns.<region>.amazonaws.com / amazonaws.com.cn hostname form;
- no userinfo;
- no custom port;
- no fragment;
- signing-certificate path pattern;
- timestamp replay window;
- X.509 validity;
- SNS canonical signature verification;
- no HTTP redirects in certificate/subscription HttpClient.

This blocks localhost, metadata IP, attacker.com, amazonaws.com.attacker.com, HTTP downgrade, userinfo and redirect-chain SSRF attempts before fetch.

AWS currently recommends validating SNS signatures, trusted AWS certificate URLs and TopicArn. Reference:
https://docs.aws.amazon.com/sns/latest/dg/sns-verify-signature-of-message.html

## Provider event cross-tenant correlation

PASS structurally for the normal persisted graph.

Event processor requires companyId/runId/senderId/domainId/recipientId tags, loads recipient runtime through joined run/sender/connection/binding/domain/tenant company relationships, compares provider message ID when already known, and requires provider correlation ID. Mismatches are recorded as rejected and do not reach projection/suppression/usage.

Database defense-in-depth remains incomplete per ME-SEC-007.

## Monthly quota concurrency

PASS structurally.

ManagedEmailUsageService ensures the period row, locks it FOR UPDATE, checks accepted + reserved + positive adjustment under the lock, inserts idempotent reservation event and increments reserved_count inside the transaction. This prevents the simple multi-pod check-then-increment race.

## UNKNOWN semantics

PASS by source inspection and existing focused test.

Managed delivery reserves usage before send. BroadcastTransportOutcome.UNKNOWN does not release the reservation and BroadcastDeliveryStateService transitions the recipient to UNKNOWN with no automatic retry schedule. This preserves the existing no-blind-resend safety model.

## Rate limiter atomic hierarchy

PASS for token-bucket atomicity, subject to ME-SEC-005 provider-rolling-quota finding.

The account/company/plan/run buckets and account/company counters are evaluated in one Redis Lua script. Tokens/counters are only written after every bucket/cap passes, so a failed bucket does not consume earlier bucket tokens.

Null/zero provider quota fails closed.

## Reputation minimum sample

PASS structurally.

Reputation evaluation checks minimumReputationSampleSize before threshold enforcement. Division by zero returns zero rate. SUSPENDED tenant state is persisted; rateMultiplier returns zero for latest SUSPENDED snapshot.

## Managed Email branding

PARTIAL PASS.

Provider label helpers correctly map MESSAGING_MANAGED to "UnifyDesk Managed Email" in reviewed frontend surfaces. ME-SEC-008 remains because the same workspace can expose actual backend region/rate fields for the managed sender.

---

# Required retest matrix after blockers are fixed

The following must be executed against the corrected integrated candidate before Round-1 signoff:

1. Backend:
   - ./mvnw -B spotless:check test
   - ./mvnw -B clean test

2. Frontend:
   - npm ci
   - npm test
   - npm run build
   - npm run lint
   - npm run format:check

3. Database:
   - clean database -> latest;
   - existing pre-V56 dev database -> V56 -> V57 -> V58 -> any approved QA-fix migrations;
   - Hibernate validation;
   - deliberate cross-company inconsistent inserts.

4. Tenant isolation:
   - Company A substitutes every Company B domain/sender/connection/run/recipient ID;
   - prove 404/non-enumerating read and zero mutation/DNS leakage.

5. Sender spoofing:
   - all localPart malformed vectors;
   - direct DB sender/from-domain mismatch;
   - direct DB binding/domain/tenant mismatch.

6. SES quota/runtime:
   - sandbox;
   - production;
   - account SendingEnabled=false;
   - quota unavailable;
   - quota up/down change;
   - non-zero SentLast24Hours;
   - UTC-midnight rolling-window case;
   - Redis restart with provider usage already consumed.

7. Redis:
   - multi-replica stress;
   - account/company/run/plan buckets;
   - noisy Company A vs Company B;
   - failure atomicity;
   - outage behavior;
   - TTL/time-boundary behavior.

8. Suppression:
   - suppression before snapshot;
   - suppression after snapshot/before send;
   - different sender/new sender/new campaign/retry;
   - tenant isolation between Company A and B.

9. Provider events:
   - forged/modified signature;
   - wrong topic/region/partition;
   - SSRF URL variants;
   - duplicate and out-of-order SEND/DELIVERY/BOUNCE/COMPLAINT/OPEN/CLICK;
   - failure after event ledger insert then SNS retry;
   - mixed cross-tenant IDs.

10. Provisioning recovery:
    - AWS success -> DB failure;
    - DB commit -> AWS failure;
    - Cloudflare partial creation;
    - worker crash/stale claim;
    - duplicate provider success;
    - cleanup partial failure.

11. Frontend:
    - desktop/tablet/mobile;
    - own-domain and hosted flows;
    - verification loss;
    - usage/deliverability/suspension;
    - no AWS region/account quota leakage;
    - existing customer-managed Amazon SES, SendGrid, Azure Email, MSG91 Email and shared SMS regression.

12. Performance/privacy:
    - event bursts;
    - large Broadcast;
    - rate-limit contention;
    - N+1/lock/transaction inspection under load;
    - logs checked for credentials, tokens, bodies, recipient lists and raw webhook payloads.

---

# Round-1 disposition

**Disposition: NOT RELEASE-READY.**

Release blockers currently tracked:

- ME-SEC-001
- ME-SEC-002
- ME-SEC-003
- ME-SEC-004
- ME-SEC-005
- ME-SEC-006
- ME-SEC-008

ME-SEC-007, ME-SEC-009 and ME-SEC-010 are defense-in-depth/production-hardening findings that should be dispositioned explicitly by the Architecture Lead.

No broad production fix was applied by QA/SEC in this branch. The authoritative output of this round is this findings document. Architecture Lead should approve remediation ownership, integrate fixes on dedicated branches, then return a new frozen candidate SHA for QA/SEC retest.


---

# Round-2 Retest Summary

**Exact frozen SHA tested:** `0dd63e9f4951277144bcbe1b9c45a50d4b6fbdd6`  
**Review branch:** `review/managed-email-security-qa-r2`  
**Release decision:** **NOT_RELEASE_READY**

## Execution evidence

### Backend build

**NOT EXECUTED successfully.**

Required commands:

    cd unifydesk-app/backend
    ./mvnw -B spotless:check test
    ./mvnw -B clean test

GitHub Actions run `36242792959` was triggered from the exact frozen SHA, but backend/frontend/website/email-provider jobs failed before any steps were created. Job step lists were empty and job logs were unavailable. No Maven command can be claimed as run.

Independently, source inspection found the original ME-SEC-001 syntax error still present in `AmazonSesBroadcastEmailTransport.java`, so the backend is not buildable from this SHA as committed.

### Frontend build

**NOT EXECUTED successfully.**

Required commands:

    cd unifydesk-app/frontend
    npm ci
    npm test
    npm run build
    npm run lint
    npm run format:check

The same Actions run failed before frontend steps started. There is no local repository checkout available in the QA execution environment, so these commands could not be substituted locally.

### Migration / Hibernate validation

**NOT EXECUTED dynamically.**

Source-level checks:

- exactly one V56, V57, V58 and V59 migration exists;
- no duplicate migration number was found;
- V59 adds the intended composite ownership constraints and Round-2 admin/provider-state columns;
- V59 uses `NOT VALID` on several FKs: PostgreSQL enforces those constraints for new/updated rows, but existing pre-V59 rows are not retroactively validated until `VALIDATE CONSTRAINT` is run;
- the included ownership integration test is conditional and skips when its PostgreSQL service is unavailable.

The required clean-database migration, real V58→V59 upgrade with existing data, Flyway checksum validation and Hibernate validation remain unproven.

## Finding status

| Finding | Round-2 result | Release blocker after retest |
|---|---|---:|
| ME-SEC-001 | STILL_OPEN | YES |
| ME-SEC-002 | PARTIALLY_FIXED | YES |
| ME-SEC-003 | PARTIALLY_FIXED | YES — dynamic proof missing |
| ME-SEC-004 | PARTIALLY_FIXED | YES |
| ME-SEC-005 | PARTIALLY_FIXED | YES |
| ME-SEC-006 | PARTIALLY_FIXED | YES — dynamic proof missing |
| ME-SEC-007 | PARTIALLY_FIXED | NO, but migration execution required |
| ME-SEC-008 | PARTIALLY_FIXED | YES — frontend/backend execution missing |
| ME-SEC-009 | PARTIALLY_FIXED | NO, production hardening unverified |
| ME-SEC-010 | PARTIALLY_FIXED | NO, executable PSL tests missing |

Totals:

- **CLOSED:** 0
- **PARTIALLY_FIXED:** 9
- **STILL_OPEN:** 1
- **REGRESSION_FOUND:** 0

## Residual technical blockers

### R2-A — build candidate is still syntactically invalid

ME-SEC-001 is unchanged at the frozen SHA. This alone prevents release-candidate status.

### R2-B — persisted From-address validation is domain-only

The new runtime guard correctly rejects a foreign bound domain, but `ManagedEmailAddressPolicy` does not perform strict mailbox/local-part validation. A malformed persisted same-domain address can pass the domain equality check. Runtime should fail closed on a syntactically invalid persisted mailbox before constructing an SES request.

### R2-C — event `processed_at` precedes external provider suppression completion

Local event work is now resumable and transactional, but hard-bounce/complaint provider suppression happens after `processed_at` is committed. A durable event marked processed therefore can still have unfinished external required work. Persist a separate external-effect completion/checkpoint/outbox, or do not define `processed_at` as full completion until provider-suppression work is durably scheduled/completed.

### R2-D — SES rolling-budget snapshot timestamp has a concurrency gap

Round 2 correctly replaces the UTC-day provider model with provider `SentLast24Hours` + durable local activity. However the persisted `lastValidatedAt` is generated after the SES account read. Concurrent sends between the provider observation and that later persistence timestamp can be excluded from both terms. Capture the provider observation timestamp at/before the GetAccount read and use that exact instant as the local-ledger lower bound, or otherwise close the refresh/send race under the same region budget lock.

## Source-level regression review

No new critical/high regression unrelated to the original findings was identified in the reviewed Round-2 diffs.

Source inspection indicates:

- CUSTOMER_MANAGED Amazon SES still uses the existing customer credential/client path;
- SendGrid, Azure Email and MSG91 Email remain on the existing delivery path;
- Managed Email-specific delivery guards are bypassed for CUSTOMER_MANAGED connections;
- SNS signature/TopicArn/region/URL validation remains present;
- UNKNOWN outcomes retain reservations and do not schedule blind resend;
- managed frontend branding now uses centralized privacy helpers including the Broadcast sidebar.

These are **not dynamic PASS results** because the candidate could not execute its test suites.

## Performance / logging areas not dynamically verified

Not executed:

- provider-budget advisory-lock contention under concurrent tenants;
- event row-lock/retry burst load;
- multi-pod Redis control-plane limiting;
- stale-provider refresh contention;
- N+1/deadlock/runtime transaction profiling;
- real log inspection under AWS/Cloudflare/SNS failures.

Source inspection did not show a new per-recipient `GetAccount` call: provider account state is refreshed by control-plane provisioning/refresh scheduling and consumed from persisted connection/runtime state during send.

## Round-2 release recommendation

**NOT_RELEASE_READY**

Reasons:

1. ME-SEC-001 is still open and prevents backend compilation.
2. ME-SEC-002, ME-SEC-004 and ME-SEC-005 retain substantive correctness gaps.
3. Required backend/frontend/Flyway/Hibernate test commands did not actually execute.
4. Several otherwise-promising Round-2 fixes therefore remain only source-reviewed, not release-verified.

A new frozen SHA is required after fixing the residual blockers. QA/SEC must then rerun the exact Maven/npm/migration commands in an environment where workflow steps actually execute before any `READY_FOR_RELEASE_CANDIDATE` decision.
