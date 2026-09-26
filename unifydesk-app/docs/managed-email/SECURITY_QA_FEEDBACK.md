# Managed Email Security & QA Feedback

**Owner during review:** Security & QA  
**Review branch:** review/managed-email-security-qa-r1  
**Frozen integrated base SHA:** 8708a51c53eb53a5cdca89a205deb7ba7a010bc7  
**Review date:** 2026-09-26  
**Current status:** ROUND_3_CODE_REVIEW_COMPLETE_DYNAMIC_VALIDATION_PENDING

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
- **Round fixed:** Round 3
- **Retest result:** CLOSED — the malformed Java expression is gone. `AmazonSesBroadcastEmailTransport` now sanitizes display-name quotes and all ISO control characters, falls back to a plain mailbox when the sanitized display name is blank/control-only, and formats the From header with `InternetAddress`. Focused tests cover quoted names, CR, LF, CRLF, blank names and valid mailbox parsing. GitHub Actions still executed zero steps, so Maven compilation is not independently proven by a runner; this infrastructure limitation is tracked separately and does not reopen the source defect.

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
- **Round fixed:** Round 3
- **Retest result:** CLOSED — `ManagedEmailAddressPolicy` now validates the complete persisted plain mailbox, rejects outer whitespace, mailbox wrapper/display-name syntax, controls/whitespace, multiple @, invalid dot placement, repeated dots, invalid IDN/domain labels and quoted local-part syntax, then requires exact normalized bound-domain equality. `SesSendContextResolver` uses that persisted runtime value and focused tests verify all required malformed same-domain/foreign-domain vectors fail before the managed SES client is resolved; no fallback to caller-side `BroadcastSender.fromAddress` remains.

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
- **Round fixed:** Round 2; Round 3 regression retest
- **Retest result:** CLOSED — no Round-3 regression found. The company-scoped suppression gate remains immediately before Managed Email rate/provider-budget usage and provider submission. Active suppression transitions PROCESSING recipients to SKIPPED, and focused tests cover all suppression reasons plus company isolation and prove no Managed Email rate/budget/provider/usage interaction when the late suppression check matches.

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
- **Round fixed:** Round 3
- **Retest result:** CLOSED — hard-bounce/complaint local processing now inserts exactly one durable `TENANT_SUPPRESSION` provider-effect row inside the same local-processing transaction before `processed_at` is marked. V60 gives the queue PK/FKs, unique `(event_id,effect_type)`, status/reason checks and claim/stale indexes. The worker uses transactional `FOR UPDATE SKIP LOCKED` claiming, stale PROCESSING recovery, bounded attempts/backoff, durable RETRY/FAILED/SUCCEEDED states and generic stored failure messages. Before PUT, tenant-scoped `GetSuppressedDestination` detects an already-completed suppression, closing the SES-success/DB-update-loss window. `processed_at` now correctly means local effects completed and required external work durably scheduled.

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
- **Round fixed:** Round 3
- **Retest result:** CLOSED — `TenantSnapshot` carries `providerObservedAt`; `AwsManagedEmailSesGateway` captures it immediately before `GetAccount`, and the provisioning worker persists that exact instant unchanged to tenant `lastProviderSyncAt` and connection `lastValidatedAt`. Retrying persistence of the same snapshot cannot refresh the timestamp. The provider-budget query still uses `usage.created_at >= snapshotTime`, so T2 sends at/after T1 are conservatively included even if persistence occurs later at T3. Existing rolling-quota, midnight, restart/Redis, stale/missing and quota-reduction protections remain intact by source review.
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
- **Round fixed:** Round 2; Round 3 regression retest
- **Retest result:** CLOSED — no Round-3 regression found. Managed sendability still requires production access plus derived connection sending-enabled state, and both launch and delivery guards fail closed for sandbox/account-disabled/tenant-disabled state and fresh provider quota requirements.
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
- **Round fixed:** Round 2; Round 3 regression retest
- **Retest result:** CLOSED — V59 ownership constraints remain intact and V60 adds provider-effect event/company/recipient and `(tenant_id,company_id)` ownership FKs without weakening the earlier graph. Source inspection finds no duplicate migration numbers through V60. Clean V1→V60 and real V59→V60 execution/Hibernate validation are still dynamic-validation items because the runner did not start tests.

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
- **Round fixed:** Round 2; Round 3 regression retest
- **Retest result:** CLOSED — no Round-3 frontend changes touched the privacy remediation. Backend managed sender responses still replace the platform region with `MANAGED` and null shared quota/usage/validation fields, while customer-managed SES preserves customer-owned details. Frontend managed privacy helpers/sidebar still render `UnifyDesk Managed Email` and suppress stale managed region/rate details.

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
- **Round fixed:** Round 2; Round 3 regression retest
- **Retest result:** CLOSED — no Round-3 regression found. The dedicated Redis Lua control-plane limiter remains company+user scoped and fails closed on Redis errors; privileged containment still requires platform authorization/company access, disables tenant/connection immediately, audits actor/company/reason/time, and resume remains PROVISIONING/DISABLED until provider reconciliation.

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
- **Round fixed:** Round 2; Round 3 regression retest
- **Retest result:** CLOSED — no Round-3 regression found. Customer-domain validation remains Guava `InternetDomainName` PSL-backed after NFC/IDNA normalization, including wildcard/exception behavior, with public suffixes rejected and registrable domains accepted.

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

# Round-3 Retest Summary

**Exact frozen SHA reviewed:** `4ea705b0349ee82b888c83699b4d55ea1fa174b1`  
**Review branch:** `review/managed-email-security-qa-r3`  
**Code-level finding disposition:** all ME-SEC-001 through ME-SEC-010 CLOSED  
**Final release state:** **CODE_REVIEW_READY_BUT_DYNAMIC_VALIDATION_PENDING**

## Branch/base verification

The review branch was verified to point exactly to the frozen integrated Round-3 SHA above. No later moving `dev` commit was used as the review base.

## Build / CI execution

Required backend commands:

    cd unifydesk-app/backend
    ./mvnw -B spotless:check test
    ./mvnw -B clean test

Required frontend commands:

    cd unifydesk-app/frontend
    npm ci
    npm test
    npm run build
    npm run lint
    npm run format:check

These commands were **not executed by GitHub Actions**.

Actions run:

    36250522073

was created for the exact review SHA, but backend, frontend, website and email-provider jobs all concluded failure with empty step lists. No job command step ran, and therefore this run is an infrastructure failure rather than product-code test evidence.

The QA environment did not have an authenticated local checkout of the private repository, so the commands could not be substituted locally. No unexecuted command is reported as PASS.

## ME-SEC-001..010 final table

| Finding | Final code result | Dynamic execution state |
|---|---|---|
| ME-SEC-001 | CLOSED | Maven compile/test pending due runner zero-step failure |
| ME-SEC-002 | CLOSED | Focused source/test review complete; runtime execution pending |
| ME-SEC-003 | CLOSED | Regression source/test review complete; runtime execution pending |
| ME-SEC-004 | CLOSED | Durable-effect design/source review complete; Postgres/worker execution pending |
| ME-SEC-005 | CLOSED | Exact timestamp + rolling-budget source/test review complete; Postgres execution pending |
| ME-SEC-006 | CLOSED | Regression source/test review complete; runtime execution pending |
| ME-SEC-007 | CLOSED | V59/V60 schema review complete; actual Flyway/Hibernate execution pending |
| ME-SEC-008 | CLOSED | Backend/frontend privacy regression source review complete; npm/browser execution pending |
| ME-SEC-009 | CLOSED | Abuse/admin regression source review complete; Redis/auth execution pending |
| ME-SEC-010 | CLOSED | PSL regression source/test review complete; Maven execution pending |

Totals:

- **CLOSED:** 10
- **PARTIALLY_FIXED:** 0
- **STILL_OPEN:** 0
- **REGRESSION_FOUND:** 0

## Round-3 targeted fixes

### ME-SEC-001 — source compile blocker and display-name safety

The exact invalid `replace(""", "")` expression is gone. Display-name handling now removes quotes and ISO controls and uses Jakarta Mail `InternetAddress` to build a valid encoded From header. Blank/control-only names fall back to the plain persisted mailbox.

### ME-SEC-002 — strict persisted mailbox validation

The full persisted mailbox is now checked, not just its domain. Required malformed same-domain vectors are rejected before the managed client/provider invocation point. Exact normalized bound-domain equality remains required and IDN domains are normalized before comparison.

### ME-SEC-004 — durable provider suppression

V60 adds `managed_email_provider_effects` with:

- primary key;
- event/company/recipient FKs;
- tenant+company composite FK;
- unique `(event_id,effect_type)`;
- effect type/reason/status/attempt checks;
- claim index;
- stale PROCESSING index;
- company/status operational index.

Local event processing schedules the provider effect transactionally before marking the event locally processed. The webhook no longer invokes SES suppression directly.

Provider-effect worker behavior:

- claim uses `FOR UPDATE SKIP LOCKED`;
- stale PROCESSING claims are recovered to RETRY;
- attempts increment under the claim transaction;
- 429/5xx/SDK-style transient failures retry with bounded exponential backoff;
- exhausted/permanent failures become durable FAILED rows with generic operator-safe messages;
- success becomes SUCCEEDED;
- prior provider completion is reconciled by tenant-scoped `GetSuppressedDestination` before PUT.

The resulting `processed_at` invariant is acceptable: all required local processing is complete and external effects are durably scheduled; provider-effect status separately represents external completion.

### ME-SEC-005 — exact provider observation timestamp

`providerObservedAt` is taken from the injected clock immediately before SES `GetAccount`. The same timestamp travels through `TenantSnapshot` and is persisted unchanged to:

- `ManagedEmailTenant.lastProviderSyncAt`;
- `BroadcastSenderConnection.lastValidatedAt`.

Persistence does not replace it with a later `Instant.now()`.

The provider-budget query remains inclusive:

    usage.created_at >= snapshotTime

Therefore a local acceptance at T1 or T2 where T1 < T2 < T3 is counted even if provider state is persisted later at T3.

## V60 / migration source verification

Source-level migration review: PASS.

- exactly one V56, V57, V58, V59 and V60 exists;
- no duplicate migration version was found;
- V60 references the V59/V56 ownership keys it requires;
- provider-effect status/reason/type constraints are fail-closed;
- provider-effect tenant/company cross-ownership is DB constrained;
- V60 does not remove or weaken V59 ownership constraints.

Dynamic migration verification remains pending:

- clean V1 → V60;
- real existing V1 → V59 → V60;
- Flyway checksum/history verification;
- Hibernate validation;
- deliberate cross-company V59/V60 invalid inserts against a running PostgreSQL instance.

## Round-2 regression areas

Round 3 changed no frontend files and did not modify the existing provider routing, suppression gate, account/sandbox guard, V59 controls, privacy helpers, control-plane limiter, admin containment or PSL service.

Source review confirms the prior remediations remain present:

- late company-scoped suppression before provider usage/submission;
- account/sandbox/tenant fail-closed launch and delivery guards;
- V59 ownership constraints;
- Managed Email backend/frontend provider privacy;
- Redis provisioning abuse limits and audited suspend/resume;
- Guava PSL validation;
- CUSTOMER_MANAGED SES customer-credential path;
- SendGrid/Azure/MSG91 existing delivery path;
- SNS signature/TopicArn/region/URL/replay/correlation checks;
- UNKNOWN outcome retains reservation and prevents blind resend.

No critical/high Round-3 regression was found.

## Runtime configuration

Integrated defaults are bounded and non-zero:

- provider snapshot max age: 300 seconds;
- provider-effect batch size: 25;
- stale claim timeout: 120 seconds;
- max attempts: 8;
- initial retry: 2 seconds;
- max retry: 900 seconds.

The scheduled provider-effect poll has a 1000 ms default delay.

## Logging/privacy review

Provider-effect worker logs only a safe exception class for interrupted polling. Durable retry/failure rows store a sanitized error code plus generic fixed messages, not exception text or provider payloads.

No new path was found that logs AWS credentials, Cloudflare tokens, raw webhook bodies, complete message bodies or recipient lists.

## Dynamic validation still required before production release

The code defects are closed, but the following have not actually executed in this QA environment:

- Maven Spotless/tests/clean test;
- npm install/tests/build/lint/format;
- Flyway clean and V59→V60 upgrade;
- Hibernate validation;
- PostgreSQL provider-effect crash/concurrency tests;
- multi-worker `SKIP LOCKED` stress;
- Redis multi-pod/outage behavior;
- live/mock SES 429/5xx/network injection through the running application;
- browser E2E privacy checks;
- runtime performance and log inspection.

## Final recommendation

**CODE_REVIEW_READY_BUT_DYNAMIC_VALIDATION_PENDING**

No substantive Managed Email implementation/security/reliability blocker remains in the reviewed Round-3 source.

This is intentionally **not** recorded as `READY_FOR_RELEASE_CANDIDATE` because the required build, frontend, migration and integration commands did not actually run. The only observed blocker to that final release-candidate label is the external GitHub Actions zero-step infrastructure failure / lack of another executable private-repository runner, not a newly identified product-code defect.
