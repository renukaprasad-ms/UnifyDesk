# UnifyDesk Managed SMS — Round-2 Tasks

Status: FROZEN remediation plan
Planning parent dev SHA: 6321027d72a9db1c3cf3903fc13cb5afcc3fec7b
Round-1 QA source: SECURITY_QA_FEEDBACK.md
Round-1 findings: 14 total — 8 HIGH (6 OPEN + 2 BLOCKED), 6 MEDIUM

## 0. Round-2 objective

Round 2 is a focused security/remediation round. Do not redesign Managed SMS and do not reopen passing Round-1 areas.

Only two implementation workers are required:

- B1 — control/compliance fixes
- B2 — runtime/events/metering/safety fixes

There is NO F1 implementation branch in Round 2 because QA found no standalone frontend defect. F1 privacy/company-switch behavior is a required QA regression target only.

After B1+B2 integration, QA/SEC performs Round-2 retest from one exact frozen SHA.

## 1. Branches

B1: fix/managed-sms-control-security-r2
B2: fix/managed-sms-runtime-security-r2
QA retest: review/managed-sms-security-qa-r2

All workers branch from the exact Round-2 worker base SHA published in the Round-2 prompts.
Do not branch from later moving dev.
Do not cherry-pick the other worker.

## 2. Finding triage

### B1 owns

- MS-SMS-R1-001 — stale V61 migration test reference
- MS-SMS-R1-003 — control-plane half: authoritative CTA approval policy + preview validation
- MS-SMS-R1-011 — ACTIVE DLT profile mutation/re-review/audit
- MS-SMS-R1-012 — control-plane half: provider binding readiness must require runtime-usable refs

### B2 owns

- MS-SMS-R1-002 — provider observation/snapshot bootstrap abstraction and fail-closed production readiness
- MS-SMS-R1-003 — runtime half: enforce authoritative CTA policy
- MS-SMS-R1-004 — delivery-time freshness revalidation
- MS-SMS-R1-005 — webhook replay-window enforcement
- MS-SMS-R1-006 — company/run/recipient ownership + billing/state atomicity
- MS-SMS-R1-007 — runtime provider-mode/credential isolation; remove unsafe master-key fallback
- MS-SMS-R1-008 — raw provider diagnostic leakage
- MS-SMS-R1-009 — distinct distributed rate scopes + campaign absolute cap
- MS-SMS-R1-010 — approved-body fingerprint verification
- MS-SMS-R1-012 — runtime half: Flow/reference readiness
- MS-SMS-R1-013 — provider-account-bound webhook authentication

### Lead / QA owns

- MS-SMS-R1-014 — executable integrated verification
- final migration numbering after unrelated dev drift
- cross-worker contract review
- frozen Round-2 integrated SHA
- real-provider external blocker status

## 3. External provider facts verified for Round 2

Current official MSG91 docs establish:

1. Webhook (New) SMS payload can include:
   - CRQID
   - requestId
   - status/eventName
   - deliveryTime
   - requestedAt
   - ts
   - DLT_TE_ID
   - credit
2. For SMS Webhook (New), ts is documented as a status-update timestamp in the current sample payload.
3. Webhook (New) supports custom key/value headers.
4. MSG91 explicitly warns webhook retries can produce duplicate callbacks.
5. MSG91 reseller/subaccount docs continue to expose child/client operations.
6. MSG91 route-balance API remains documented and auth keys are described as unique per user/account.

Sources:
- https://msg91.com/help/webhook-new/how-to-receive-sms-delivery-reports-via-webhook-new
- https://msg91.com/help/webhook-new
- https://msg91.com/help/all-about-subaccount-in-msg91
- https://api.msg91.com/apidoc/basic/route-balance.php
- https://api.msg91.com/apidoc/reseller/add-clients.php
- https://api.msg91.com/apidoc/reseller/view-client-profile.php

Still NOT verified:
- whether each current MSG91 child exposes/uses its own Send SMS AuthKey for the chosen Flow API;
- whether a production-contract HTTPS balance endpoint is enabled for our account/product;
- signed/HMAC Webhook (New) support;
- exact PE–TM chain classification for UnifyDesk;
- production credit/refund semantics.

Code must remain fail-closed for those unknowns.

## 4. Frozen CTA approval policy contract

Round 1 stored cta_policy_json but only validated variable type. Round 2 freezes an authoritative policy format.

Activated template versions containing URL/CALLBACK variables MUST have cta_policy_json with policyVersion=2.

Shape:

{
  "policyVersion": 2,
  "urlRules": [
    {
      "key": "tracking_url",
      "mode": "EXACT_VALUES",
      "allowedValues": ["https://example.com/a"]
    },
    {
      "key": "portal_url",
      "mode": "HOST_ALLOWLIST",
      "allowedHosts": ["example.com"],
      "allowSubdomains": false,
      "requireHttps": true,
      "allowedPathPrefixes": ["/orders/"]
    }
  ],
  "callbackRules": [
    {
      "key": "support_phone",
      "allowedValues": ["919876543210"]
    }
  ]
}

Rules:
- one rule maximum per variable key/type;
- IP-literal URL hosts forbidden;
- URL user-info forbidden;
- HTTPS required when requireHttps=true;
- host comparison is normalized lower-case IDNA/ASCII;
- allowSubdomains=false means exact host only;
- allowSubdomains=true still requires label-boundary suffix matching;
- path prefix comparison occurs on normalized URI path, not decoded redirect targets;
- EXACT_VALUES compares canonical URI values;
- callback values normalize to digits/E.164-like form before exact comparison;
- variables not present in the approved rule cannot be used as URL/CALLBACK variables;
- no legacy policy can become ACTIVE when CTA variables exist.

Customer-facing F1 request format does not need to change in Round 2. The customer declaration remains input/evidence; platform admin/provider review finalizes the authoritative policy before activation.

## 5. B1 — control/compliance remediation

Branch:
fix/managed-sms-control-security-r2

### B1 migration

Own:
V65__managed_sms_round2_control_security.sql

Create managed_sms_compliance_actions:
- id PK
- company_id
- actor_user_id
- resource_type
- resource_id
- action
- before_state_json
- after_state_json
- reason nullable
- created_at

This records review-relevant company-user mutations separately from platform-admin decision audit.

If V65 is occupied before integration, Lead renumbers; B1 does not independently chase moving dev.

### MS-SMS-R1-001

Update ManagedSmsControlContractTests to the actual integrated control migration filename.
Prefer a stable helper/assertion that identifies the Managed SMS control migration by suffix rather than hardcoding a historical obsolete version when practical.

### MS-SMS-R1-003 control half

- validate CTA policy v2 structurally before approval;
- admin template approval must supply/finalize authoritative CTA rules for every URL/CALLBACK variable;
- B1 preview must enforce the same allowlist semantics;
- ACTIVE template version persists the authoritative policy;
- policy becomes immutable under the existing activated-template DB trigger;
- invalid or incomplete CTA approval => template cannot become ACTIVE.

Existing customer API may keep accepting the current declaration shape while pending review. Do not trust that declaration as final approval.

### MS-SMS-R1-011

For ACTIVE DLT profile:
- PE ID cannot be changed in-place;
- changing legalName, dltPlatform, tmChainReference, or evidence is review-relevant;
- accepted edit must record managed_sms_compliance_actions before/after;
- invalidate current approval immediately;
- state returns to WAITING_UNIFYDESK_REVIEW / equivalent internal pending state;
- verifiedAt cleared;
- checkedAt/staleAfter reset appropriately;
- tmChainReference change also invalidates TM-chain approval;
- Managed SMS eligibility recalculates in the same transaction and disables sending until re-approved.

No ACTIVE state may survive a review-relevant metadata/evidence edit.

### MS-SMS-R1-012 control half

Centralize provider binding readiness in ManagedSmsProviderAdapter.

For current Flow API runtime, ACTIVE binding requires at minimum:
- provider = MSG91_SMS
- nonblank providerFlowRef
- provider mapping state confirmed
- template version/header/tenant/company match
- no rejected/stale state.

providerTemplateRef alone MUST NOT make the binding ACTIVE.

Admin decision service delegates readiness to the adapter; it must not encode OR logic itself.

### B1 tests

Add/repair tests for:
- actual migration contract file;
- CTA policy exact URL;
- host allowlist exact host;
- subdomain allow/deny;
- IP URL reject;
- user-info URL reject;
- path-prefix allow/deny;
- callback exact allowlist;
- missing CTA rule blocks activation;
- ACTIVE legal name/platform/evidence/TM reference edit invalidates approval;
- PE ID in-place change denied;
- audit row written;
- template-ref-only approval rejected;
- flow-ref-complete approval accepted;
- company IDOR regression.

## 6. B2 — runtime/security remediation

Branch:
fix/managed-sms-runtime-security-r2

### B2 migration

Own:
V66__managed_sms_round2_runtime_security.sql

Add:
- provider_event_at timestamptz nullable to managed_sms_provider_events;
- unique index on broadcast_recipients(company_id, run_id, id);
- unique index on broadcast_recipients(company_id, id);
- composite FK from managed_sms_correlations(company_id, run_id, recipient_id)
  -> broadcast_recipients(company_id, run_id, id);
- composite FK from managed_sms_usage_reservations(company_id, run_id, recipient_id)
  -> broadcast_recipients(company_id, run_id, id);
- composite FK from managed_sms_recipient_delivery(company_id, recipient_id)
  -> broadcast_recipients(company_id, id).

Before adding constraints, migration must either prove existing Managed SMS rows are consistent or use a validation-safe strategy approved by Lead. Do not silently delete inconsistent data.

### MS-SMS-R1-002

Introduce ManagedSmsProviderObservationAdapter + service/scheduler.

Contract returns:
- providerAccountKey
- balance nullable
- maxSendRate nullable
- sendingEnabled
- observedAt
- validUntil
- observation source.

Requirements:
- a real positive snapshot must come through the adapter/service, never manual SQL as production mechanism;
- clean DB/no observation => fail closed;
- expired observation => fail closed;
- sendingEnabled=false always wins even when reserve=0;
- insufficient-credit provider response trips false snapshot;
- recovery requires a new fresh healthy observation;
- adapter unavailable => explicit NOT_READY/provider-capacity state, not fake healthy.

A production MSG91 observer may only use an HTTPS provider endpoint validated for the contracted account. Do not ship plain-HTTP balance polling just because legacy docs show an HTTP sample URL.

Unit/integration tests must support a fake observation adapter so positive/recovery paths are executable even while the real account contract remains external.

### MS-SMS-R1-003 runtime half

ManagedSmsTemplateService must consume CTA policyVersion=2 and validate actual runtime values, not only types.

Enforce:
- EXACT_VALUES
- HOST_ALLOWLIST
- requireHttps
- allowSubdomains
- allowedPathPrefixes
- IP/user-info rejection
- callback exact allowlist.

Enforce in:
- draft compose rendering;
- launch snapshot validation;
- delivery render before provider request.

Invalid CTA => MANAGED_SMS_CTA_NOT_APPROVED and zero provider calls.

### MS-SMS-R1-004

Delivery runtime query must carry current:
- tenant lastProviderSyncAt/freshness
- DLT profile checkedAt/staleAfter
- Header checkedAt/staleAfter
- template checkedAt/staleAfter
- provider binding lastSyncAt/staleness policy.

Every external provider submission rechecks freshness against Instant.now().
Do not depend on the scheduler flipping status to STALE.
A long-running run crossing expiry stops until fresh verification exists.

### MS-SMS-R1-005

Webhook replay contract:
- use MSG91 Webhook (New) ts as provider status-update/event timestamp;
- deliveryTime/requestedAt remain business/event metadata and are not interchangeable replay clocks;
- malformed/missing required ts fails closed for trusted DLRs;
- parse current ISO-offset form and explicitly supported legacy MSG91 time form;
- compute stable event key including providerAccountKey + correlation + requestId + status + ts;
- if exact event key already exists, return idempotent success without reprocessing even if now outside the replay window;
- for a NEW event identity, reject if ts is outside configured past/future skew window;
- never fall back malformed/missing timestamp to Instant.now().

Keep event deduplication separate from replay-window enforcement.

### MS-SMS-R1-006

Every hot-path mutation/lock includes exact tuple:
companyId + runId + recipientId (+ attemptNo where applicable).

Specifically:
- correlation create/reuse;
- usage reservation lock/commit/release/unknown;
- billing commit;
- recipient state mutation;
- stats transition;
- event correlation reconciliation.

Verify ownership BEFORE UsageBillingService.record().
No billing may occur before tuple verification.

Add DB composite constraints from V66 as defense in depth.

### MS-SMS-R1-007

Remove unconditional master AuthKey fallback.

Runtime rules:
- providerMode=SHARED_MASTER may use master AuthKey only when APP_MANAGED_SMS_ALLOW_SHARED_MASTER=true;
- providerMode=CHILD_PER_COMPANY must resolve an account-specific credential through ManagedSmsProviderCredentialResolver;
- default/no child resolver => fail closed MANAGED_SMS_PROVIDER_CAPACITY_UNAVAILABLE;
- child mode must never silently send using master key;
- providerAccountKey in the run must match the credential scope returned by resolver.

The real child-credential source remains an external provider-contract blocker until verified.

### MS-SMS-R1-008

No raw provider response text can enter Broadcast recipient customer fields.

Map:
- 429 -> stable MANAGED_SMS_PROVIDER_RATE_LIMIT + safe fixed message
- credit -> stable safe message
- permanent reject -> stable safe message
- 5xx/IO -> unknown safe message.

Raw provider body may be retained only in restricted/redacted ops telemetry if needed.

### MS-SMS-R1-009

Replace one-rate-for-all-scopes limiter with distinct configured limits:
- globalMaxPerSecond
- providerAccountMaxPerSecond
- companyMaxPerSecond
- runMaxPerSecond
- newTenantHourlyCap
- newTenantDailyCap
- campaignMaxRecipients absolute launch cap.

One Redis Lua execution must atomically test/decrement all applicable scopes.
Redis failure remains fail closed.

B2 is sole Round-2 writer of application.yaml/.env.example for these new runtime keys.

### MS-SMS-R1-010

Before any substitution:
- compute SHA-256 over approvedBody exact UTF-8 bytes, matching B1;
- constant-time compare against expectedFingerprint;
- mismatch => MANAGED_SMS_TEMPLATE_NOT_READY / tamper-safe internal code;
- zero provider request.

Do not normalize whitespace differently from B1.

### MS-SMS-R1-012 runtime half

ManagedMsg91Gateway continues to require providerFlowRefSnapshot.
Launch and delivery guard must treat absent Flow ref as not ready before provider call.

### MS-SMS-R1-013

Replace one global webhook trust secret with provider-account-bound authentication.

Round-2 contract:
- derive a high-entropy webhook token using HMAC-SHA256(serverWebhookTokenKey, providerAccountKey);
- webhook URL/header token is bound to exactly one providerAccountKey;
- after CRQID lookup, recompute expected token for runtime.providerAccountKey and constant-time compare;
- Company-A/account-A token must never authenticate Company-B/account-B event;
- token never logged;
- support rotation with current + previous server key for a bounded overlap period if implemented.

MSG91 documents custom webhook headers, so the deployment may configure the token as a custom header. If account-level custom header configuration is unavailable in the contracted topology, a dedicated unguessable account-bound endpoint token is acceptable.

A future provider-native HMAC/signature supersedes this once verified.

### B2 config ownership

B2 may modify:
- backend/src/main/resources/application.yaml
- unifydesk-app/.env.example

Add at least:
APP_MANAGED_SMS_ALLOW_SHARED_MASTER
APP_MANAGED_SMS_ACCOUNT_MAX_PER_SECOND
APP_MANAGED_SMS_COMPANY_MAX_PER_SECOND
APP_MANAGED_SMS_RUN_MAX_PER_SECOND
APP_MANAGED_SMS_CAMPAIGN_MAX_RECIPIENTS
APP_MANAGED_SMS_WEBHOOK_TOKEN_KEY
APP_MANAGED_SMS_WEBHOOK_PREVIOUS_TOKEN_KEY optional
APP_MANAGED_SMS_EVENT_REPLAY_WINDOW

Existing master AuthKey remains secret and is only valid for explicitly enabled shared-master mode.

### B2 tests

Required:
- clean snapshot fail closed;
- fresh healthy observation;
- expired/low/disabled snapshot;
- credit trip and recovery;
- CTA exact/host/subdomain/path/callback cases;
- delivery-time stale profile/header/template/binding with ACTIVE statuses;
- scheduler stopped;
- exact webhook duplicate outside window remains idempotent;
- new old/future/missing/malformed-ts event rejected;
- account-A webhook token cannot authenticate account-B;
- cross-company/wrong-run/foreign-recipient correlation and billing attempts;
- billing occurs only after tuple verified;
- master key blocked in child mode;
- shared master blocked unless explicit opt-in;
- raw provider diagnostic never persisted customer-facing;
- distinct rate scopes/noisy tenant/campaign cap;
- fingerprint mismatch;
- existing CUSTOMER_MANAGED MSG91 regression.

## 7. Files and conflict boundaries

B1 owns only:
- managedsms/control/**
- B1 tests
- V65
- ManagedSmsControlContractTests
- B1-owned DTO/admin/control files required for CTA/admin readiness.

B1 must NOT edit:
- managedsms/runtime/**
- managedsms/delivery/**
- managedsms/events/**
- managedsms/metering/**
- managedsms/safety/**
- Broadcast delivery/runtime hot files
- application.yaml/.env.example in Round 2
- frontend.

B2 owns only:
- managedsms/runtime/**
- managedsms/delivery/**
- managedsms/events/**
- managedsms/metering/**
- managedsms/safety/**
- B2 tests
- V66
- exact Broadcast runtime hot files needed for tuple ownership/campaign cap
- application.yaml/.env.example.

B2 must NOT edit:
- managedsms/control/**
- V65
- frontend.

No F1 code changes are planned.

## 8. Lead integration

Integration order:
1. review B1;
2. review B2;
3. merge B1;
4. rebase/apply B2 onto B1-integrated current dev;
5. resolve only Lead-owned glue;
6. renumber V65/V66 only if unrelated dev consumed them;
7. run migration/static ownership review;
8. freeze one Round-2 integrated SHA;
9. QA/SEC branches review/managed-sms-security-qa-r2 from exactly that SHA.

Lead does NOT mark MS-SMS-R1-002, R1-007 or R1-013 external provider semantics verified merely because fail-closed code exists.

## 9. Round-2 executable gate

Before Round-2 QA can recommend release:
- backend ./mvnw -B spotless:check test
- backend ./mvnw -B clean test
- frontend npm ci
- frontend npm test
- frontend npm run build
- frontend npm run lint
- frontend npm run format:check
- clean Flyway through final migration
- representative upgrade Flyway through final migration
- Redis concurrency/failure tests.

If repository CI again fails before steps start, that is environment failure, not PASS.

## 10. Expected finding disposition after Round 2

Target CLOSED by code/retest:
R1-001, R1-003, R1-004, R1-005, R1-006, R1-008, R1-009, R1-010, R1-011, R1-012.

Target CODE-SAFE / EXTERNAL VERIFICATION REMAINS:
R1-002, R1-007, R1-013.

Target VERIFIED BY EXECUTION:
R1-014.

Production GA remains blocked until the external provider/DLT contract items in RESEARCH-DLT-MSG91.md are evidenced.
