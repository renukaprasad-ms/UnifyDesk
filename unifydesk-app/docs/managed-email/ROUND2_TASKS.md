# Managed Email Round 2 Tasks

**Architecture owner:** Lead / Architect  
**Source review:** SECURITY_QA_FEEDBACK.md, Round 1 QA/SEC  
**QA base reviewed:** `8708a51c53eb53a5cdca89a205deb7ba7a010bc7`  
**QA findings merged to dev:** `c20b088b6dd9ddd365c0c61f18f3f5144abd0327`

Round 2 exists to close every Round-1 release blocker and the three production-hardening findings without re-opening the frozen product architecture.

## Parallel-work rule

B1, B2 and F1 start from the same Lead-published Round-2 base SHA after this plan is merged.

They must not rebase onto moving `dev`, cherry-pick one another, or edit another worker's owned production files.

If a worker believes a forbidden file must change, the PR must contain an **Architecture Lead Integration Request** instead of editing it.

QA/SEC does not implement these fixes. It retests the Lead-integrated Round-2 candidate.

---

# Finding disposition

| Finding | Severity | Release blocker | Round-2 owner |
|---|---|---:|---|
| ME-SEC-001 backend syntax/build blocker | HIGH | YES | B2 |
| ME-SEC-002 runtime From/domain binding check | HIGH | YES | B2 |
| ME-SEC-003 suppression recheck before provider submission | HIGH | YES | B2 |
| ME-SEC-004 resumable/idempotent provider-event processing | HIGH | YES | B2 |
| ME-SEC-005 rolling SES 24h quota correctness | HIGH | YES | B1 provider sync + B2 limiter |
| ME-SEC-006 account SendingEnabled/sandbox gate | HIGH | YES | B1 provider sync + B2 runtime gate |
| ME-SEC-007 DB ownership defense-in-depth | MEDIUM | NO | B1 |
| ME-SEC-008 hide platform SES internals | MEDIUM | YES | B2 API + F1 UI |
| ME-SEC-009 provisioning abuse + admin containment | MEDIUM | NO | B1 |
| ME-SEC-010 full Public Suffix validation | MEDIUM | NO | B1 |

No finding is intentionally deferred beyond Round 2.

---

# B1 — Control Plane Security / Provider-State Hardening

**Branch:** `fix/managed-email-control-plane-r2`

## Scope

B1 owns the provider/control-plane half of ME-SEC-005 and ME-SEC-006 and all of ME-SEC-007, ME-SEC-009 and ME-SEC-010.

### A. SES account-state synchronization — ME-SEC-005 / 006

Extend the existing SES account snapshot to capture, on every relevant provider refresh:

- `MaxSendRate`;
- `Max24HourSend`;
- `SentLast24Hours`;
- account `SendingEnabled`;
- `ProductionAccessEnabled`.

Persist/synchronize these onto the existing Managed Email `BroadcastSenderConnection` fields where available:

- `maxSendRate`;
- `max24HourSend`;
- `sentLast24Hours`;
- `productionAccessEnabled`;
- `sendingEnabled`.

`sendingEnabled` for MESSAGING_MANAGED must be true only when ALL relevant state is safe:

- platform Managed Email enabled;
- platform sending enabled;
- SES tenant active/sending enabled;
- SES account SendingEnabled=true;
- SES production access=true;
- required provider quota state available.

Sandbox or account-disabled state must fail closed for normal Managed Email Broadcast.

Do not invent a new provider quota table.

Do not change B2 limiter implementation.

### B. Database ownership hardening — ME-SEC-007

Own one new migration:

`V59__managed_email_round2_control_security.sql`

Add the minimal composite unique keys/FKs needed to reject important cross-company corruption for:

- managed domain -> tenant/company;
- DNS record -> domain/company;
- managed sender binding -> sender/company;
- managed sender binding -> domain/company;
- managed sender binding -> tenant/company;
- usage event company/run/recipient relationships where safely expressible against existing schema.

Do not create constraints that break legitimate existing Broadcast relationships.

Before adding each FK, inspect the existing referenced table's keys and current V56-V58 data model.

Migration must be safe on an existing database that already has V56-V58.

### C. Control-plane abuse rate limiting — ME-SEC-009

Add a dedicated Redis-backed Managed Email control-plane limiter.

At minimum rate-limit by company and, where current auth context supports it, user for:

- customer-domain creation;
- hosted-domain creation;
- domain refresh/recheck;
- domain delete/recreate;
- sender creation;
- sender disable/delete.

Use atomic Redis operations/Lua where a multi-counter decision is needed.

Rate-limit denial must return the application's normal safe 429 response and must not call AWS or Cloudflare.

Do not reuse the delivery throughput limiter.

### D. Manual administrative containment — ME-SEC-009

Add an audited platform-admin/Super Admin Managed Email containment surface following the repository's existing platform-admin authorization patterns.

Required operations:

- suspend Managed Email for one company/tenant;
- resume only through an explicit privileged action after remediation.

Suspension must immediately make the tenant and managed Broadcast connection non-sendable. B2 will independently re-check this state at submission time.

Every operation must record actor, target company, reason and timestamp using an existing audit pattern if available. Do not expose AWS tenant identifiers to customers.

### E. Public Suffix correctness — ME-SEC-010

Replace the hand-maintained public-suffix heuristic with a maintained PSL/eTLD+1 implementation.

Requirements:

- IDNA-normalized input;
- wildcard PSL rules;
- exception rules;
- multi-label public suffixes;
- reject a public suffix itself;
- allow a real registrable domain;
- preserve current URL/email/wildcard rejection.

If introducing a dependency is necessary, B1 owns the `pom.xml` change for Round 2. B2 must not touch `pom.xml`.

## Existing files B1 MAY modify

- `com/messaging/managedemail/control/**`
- `BroadcastSenderConnectionRepository.java` only if genuinely required
- `application.yaml`
- `.env.example`
- `backend/pom.xml` only for the approved PSL implementation
- tests under `managedemail/control/**`
- existing platform-admin files only when required for the new privileged containment endpoint
- new `V59__managed_email_round2_control_security.sql`

## B1 explicitly FORBIDDEN

- `AmazonSesBroadcastEmailTransport.java`
- `BroadcastDeliveryService.java`
- `BroadcastLaunchService.java`
- `BroadcastEmailCommand.java`
- `BroadcastSenderResponse.java`
- anything under `managedemail/delivery/**`
- anything under `managedemail/events/**`
- anything under `managedemail/metering/**`
- anything under `managedemail/reputation/**`
- anything under `managedemail/runtime/**`
- frontend production files
- V60 or later migration numbers
- architecture documents

## B1 acceptance

- live SES SentLast24Hours is synchronized;
- sandbox/account-disabled state makes managed connection non-sendable;
- provider quota unavailable fails closed;
- direct cross-company SQL corruption attempts covered by V59 fail;
- provisioning bursts are rate-limited before provider calls;
- privileged company suspension immediately blocks managed sending;
- PSL tests cover wildcard/exception/IDN cases;
- existing CUSTOMER_MANAGED SES remains untouched.

---

# B2 — Delivery / Event / Suppression Security Fixes

**Branch:** `fix/managed-email-delivery-security-r2`

B2 owns ME-SEC-001, 002, 003, 004, the limiter/runtime half of 005/006, and backend half of 008.

## A. Restore build — ME-SEC-001

Fix the invalid Java syntax in:

`AmazonSesBroadcastEmailTransport.java`

Do not stop after the syntax fix. Round-2 B2 is complete only after the full assigned security work and backend test suite pass.

## B. Runtime From/domain invariant — ME-SEC-002

At provider-submission time:

- load the persisted sender From address into the managed runtime projection;
- parse/normalize it as an email address;
- extract the domain;
- require exact equality to `ManagedEmailDomain.normalizedDomain`;
- require sender/binding/domain/tenant/company graph to remain consistent.

A tampered sender row must fail closed before SES `SendEmail`.

Do not rely on initial sender creation or SES rejection.

## C. Suppression recheck immediately before submission — ME-SEC-003

After recipient snapshot but immediately before Managed Email provider submission:

- check active application suppression for company + EMAIL + normalized destination;
- cover HARD_BOUNCE, COMPLAINT, UNSUBSCRIBED, ADMIN_BLOCKED, INVALID and any existing active generic reason;
- if suppressed, do not reserve/send;
- transition the recipient safely/idempotently to the appropriate non-send state;
- do not mutate another company.

Use the existing suppression source of truth. Do not create a second suppression table.

## D. Resumable provider-event processing — ME-SEC-004

Do not treat "ledger row exists" as "all side effects completed".

Redesign the local event-processing transaction/checkpoint semantics so:

- authenticated event receipt is durable;
- duplicate SNS deliveries are harmless;
- a crash after receipt but before projection/suppression/billing can be retried;
- required local DB side effects are atomic where practical;
- external/provider suppression signaling is retryable and cannot prevent local state convergence;
- `processed_at` means processing actually completed;
- SEND cannot double bill;
- BOUNCE/COMPLAINT cannot double suppress;
- out-of-order rules remain intact.

A forced failure after event receipt followed by the same SNS MessageId must complete missing side effects exactly once.

Do not weaken SNS signature/SSRF validation.

## E. Rolling SES 24-hour account budget — ME-SEC-005

Consume the provider state synchronized by B1 through existing `BroadcastSenderConnection` runtime fields.

Runtime projection must include:

- max send rate;
- max 24h quota;
- provider sent-last-24h;
- last provider validation/sync timestamp;
- production access;
- connection sending-enabled state.

Do NOT model SES's provider quota as a UTC-midnight reset.

The limiter must conservatively account for:

`providerRemaining ~= max24h - providerSentLast24h - UnifyDesk sends/reservations since the provider snapshot`

or an equally safe architecture.

Required properties:

- safe across UTC midnight;
- safe across process restart;
- safe across Redis restart;
- safe with non-zero SentLast24Hours;
- fail closed if provider snapshot is absent/stale beyond the chosen safe threshold;
- local platform safety caps may remain stricter than provider quota.

You may retain calendar/day counters only as UnifyDesk policy limits, not as a substitute for SES rolling quota.

## F. Account/sandbox gate — ME-SEC-006

Managed runtime/launch/delivery eligibility must require:

- connection productionAccessEnabled=true;
- connection sendingEnabled=true;
- tenant/domain/sender active;
- platform sending flag true.

B1 will make connection.sendingEnabled reflect SES account SendingEnabled + production access + tenant/platform state.

B2 must still independently enforce the persisted safe state at launch and immediately before provider submission.

## G. Sanitize generic Broadcast sender API — backend half of ME-SEC-008

B2 owns:

`unifydesk-app/backend/src/main/java/com/messaging/broadcast/dto/BroadcastSenderResponse.java`

For `MESSAGING_MANAGED` return only customer-safe values:

- region must be `MANAGED` or null according to existing frontend contract;
- platform account max24h/maxSendRate/sentLast24h must be null;
- provider/platform account internals must not leak.

CUSTOMER_MANAGED SES must continue returning its own existing fields.

## Existing files B2 MAY modify

- `AmazonSesBroadcastEmailTransport.java`
- `BroadcastDeliveryService.java`
- `BroadcastLaunchService.java`
- `BroadcastEmailCommand.java` if needed
- `BroadcastSenderResponse.java`
- `BroadcastGroupMemberRepository.java` only if needed
- existing suppression repository/service as a consumer; prefer no modification unless unavoidable
- `managedemail/delivery/**`
- `managedemail/events/**`
- `managedemail/metering/**`
- `managedemail/reputation/**`
- `managedemail/runtime/**`
- related backend tests

## B2 explicitly FORBIDDEN

- `pom.xml`
- `application.yaml`
- `.env.example`
- `managedemail/control/**`
- B1 V59
- frontend production files
- architecture documents

## B2 acceptance

- backend compiles and full test suite executes;
- tampered From address produces zero SES call;
- suppression created after snapshot prevents send;
- event failure-after-receipt converges on retry;
- provider rolling quota survives midnight/restart scenarios;
- sandbox/account disabled state cannot launch or submit;
- generic managed sender response leaks no AWS region/account quotas;
- customer-managed providers regressions pass.

---

# F1 — Managed Email Privacy / UX Round 2

**Branch:** `fix/managed-email-ui-privacy-r2`

F1 owns frontend half of ME-SEC-008 plus regression verification of Managed Email branding/privacy.

## Scope

Audit every customer-visible Managed Email surface for provider/account internals.

For a sender where:

`managementMode === MESSAGING_MANAGED`

the UI must:

- display `UnifyDesk Managed Email`;
- never display actual AWS region;
- never display platform SES max send rate;
- never display platform Max24HourSend;
- never display SentLast24Hours;
- never call these values the customer's quota;
- use Managed Email `/usage` for customer plan consumption;
- use Managed Email `/deliverability` for safe health state.

Audit at minimum:

- Company Connections;
- Managed Email modal/panel;
- Broadcast sender picker;
- Broadcast workspace;
- Broadcast history cards;
- Broadcast detail;
- composer sender details.

CUSTOMER_MANAGED Amazon SES must still display Amazon SES and its existing customer-owned connection details.

Do not attempt to "security fix" this only with CSS. B2 is separately sanitizing the API; F1 must also render safely when legacy/stale API data still contains those fields.

## Files F1 MAY modify

- `frontend/src/features/managedEmail/**`
- `frontend/src/components/managed-email/**`
- `frontend/src/pages/company/CompanyConnections.tsx`
- `frontend/src/components/broadcast/BroadcastEmailProviderPickerModal.tsx`
- `frontend/src/pages/broadcast/BroadcastWorkspace.tsx`
- related frontend tests

## F1 explicitly FORBIDDEN

- backend files;
- migrations;
- backend config;
- architecture docs;
- B1/B2 code.

## F1 acceptance

- no Managed Email UI exposes region/provider-account quota values even if fixture/API provides them;
- all managed surfaces say UnifyDesk Managed Email;
- CUSTOMER_MANAGED SES still says Amazon SES and retains current useful details;
- usage/deliverability still come only from Managed Email safe endpoints;
- responsive/mobile behavior remains intact;
- npm test/build/lint/format checks execute and pass.

---

# Lead-owned Round-2 integration

After B1, B2 and F1 PRs are ready, the Architecture Lead will:

1. inspect ownership;
2. merge/reconcile conflicts centrally;
3. run migration/build checks;
4. verify B1's provider-state contract matches B2 runtime assumptions;
5. add only unavoidable cross-module glue;
6. update SECURITY_QA_FEEDBACK.md dispositions with the resulting candidate;
7. freeze one exact Round-2 QA retest SHA.

Workers must not merge their own PRs.

---

# QA/SEC Round-2 retest

QA/SEC will retest all ME-SEC-001 through ME-SEC-010 against the one Lead-integrated SHA.

Round 2 is not complete until all seven release blockers are CLOSED and the three hardening findings are either CLOSED or explicitly re-dispositioned by the Architecture Lead with evidence.
