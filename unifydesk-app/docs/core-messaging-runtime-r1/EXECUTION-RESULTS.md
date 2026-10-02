# Execution Results

## Audit date

2026-10-02

## Frozen executable baseline

**PASS — baseline freeze**

Live GitHub `dev` was inspected at the start of this regenerated execution round.

Exact frozen production SHA:

`f2760fe0a11ad571f984636a65304a64b77459ac`

Commit:

`Merge PR #249: Core Messaging runtime/provider verification R1 audit`

Remote branch policy was also inspected. Only the intended long-lived branches existed before the temporary evidence branch was recreated:

- `dev`
- `UAT`
- `prod`

Historical PR #250 was confirmed closed/unmerged and its old branch absent. Its old branch head is not used as runtime truth.

## Previous remediation lineage

Independent final QA PR #248 is merged.

It reviewed production SHA:

`353069f922040be922ec353961386fb962d8d187`

and accepted the three original source remediations:

- CMJ-R1-QA-001 preview-to-freeze binding;
- CMJ-R1-QA-002 workspace epoch isolation;
- CMJ-R1-QA-003 zero-pending terminal Notification.

That QA explicitly did **not** claim executable/provider PASS.

## Post-QA delta source review

**PASS as source inspection, not runtime/provider execution**

Reviewed current source confirms:

- Managed Email permanent-bounce post-acceptance reconciliation passes the matched provider event to suppression feedback.
- canonical suppression bridge receives provider event identity/timestamp.
- Segment HTTP wire model uses Jackson 3 `tools.jackson.databind.JsonNode`.
- Segment mapper uses Jackson 3 ObjectMapper.
- Segment Preview sends normalized filter to the server Preview without replacing interactive editor state.
- Preview/freeze descriptor checks and immutable candidate capture remain present.
- send-time Contact eligibility/destination recheck remains present.
- zero-pending terminal Notification remains present.

## CI on exact frozen SHA

**BLOCKED — CI execution environment**

Exact frozen SHA:

`f2760fe0a11ad571f984636a65304a64b77459ac`

GitHub Actions run:

`37011044661`

Current run attempt:

`2`

Observed result:

- backend: `failure`, `steps = null`
- database: `failure`, `steps = null`
- frontend: `failure`, `steps = null`
- email-provider: `failure`, `steps = null`
- website: `failure`, `steps = null`

No Maven, npm, database migration or packaging step actually executed. This is not valid application PASS evidence and not valid application-code FAIL evidence.

## Phase 0 deployment proof

**BLOCKED — authenticated runtime access unavailable in this execution session**

Required proof is that the deployed k3s backend/container is running the exact frozen SHA `f2760fe0a11ad571f984636a65304a64b77459ac`.

Current evidence obtained:

- GitHub source SHA frozen: yes;
- live branch policy checked: yes;
- GitHub CI execution attempt inspected: yes;
- public `api.unifydesk.in` build/health proof from this tool environment: unavailable;
- authenticated `kubectl`/server shell: unavailable in this execution session;
- deployed image/build SHA: not yet proven.

Because Phase 0 is not proven, Contacts/consent/audience/provider mutations are not started and are not labeled PASS.

## Internal journey execution status

- CMR1-001 Workspace isolation: **NOT EXECUTED**
- CMR1-002 Contact import: **NOT EXECUTED**
- CMR1-003 Email consent: **NOT EXECUTED**
- CMR1-004 SMS consent: **NOT EXECUTED**
- CMR1-005 WhatsApp consent: **NOT EXECUTED**
- CMR1-006 duplicate-import semantics: **NOT EXECUTED**
- CMR1-007 List Preview: **NOT EXECUTED**
- CMR1-008 Segment Validate/Preview: **NOT EXECUTED**
- CMR1-009 stale Preview rejection: **NOT EXECUTED**
- CMR1-010 immutable freeze: **NOT EXECUTED**
- CMR1-011 send-time eligibility recheck: **NOT EXECUTED**
- CMR1-013 zero-pending/all-skipped finalization: **NOT EXECUTED**

These remain pending behind Phase 0 deployment proof.

## Provider execution

### Managed Email / AWS SES

**NOT EXECUTED**

No actual SES send/SNS event was executed through the repository audit.

### Managed SMS / MSG91

**NOT EXECUTED**

No actual MSG91 request/callback was executed through the repository audit.

### Meta WhatsApp test WABA

**NOT EXECUTED**

No actual test-WABA send/webhook/inbound message was executed through the repository audit.

### Meta customer Embedded Signup

**BLOCKED BY META**

The initiative handoff records current provider onboarding ineligibility.

### Provider-4 direct bulk Email

**SCOPE DECISION RECORDED / NOT EXECUTED**

MAIN resolved Provider-4 regression scope to the modern supported customer-managed bulk Email providers:

- Amazon SES
- SendGrid
- Azure Communication Services Email
- MSG91 Email

Gmail/Microsoft/SMTP mailbox transports are not Provider-4 targets for this round. No mailbox-provider source remediation is required by this decision.

## Environment / credential evidence

**BLOCKED — runtime access gate**

Repository source proves the backend consumes the Kubernetes `messaging-env` Secret. The current execution session does not have an authenticated shell/Kubernetes channel to inspect environment-variable names/presence in the deployed backend. No claim is made about which provider secrets or AWS runtime credentials are currently mounted. Secret values must never be written into this evidence.

## Stale PR housekeeping

**PASS**

Closed as superseded without merging:

- #197
- #218
- #244

## Current release-verification disposition

**HOLD FOR EXECUTABLE RUNTIME EVIDENCE**

Reasons:

1. deployed k3s/runtime SHA has not yet been proven equal to `f2760fe0a11ad571f984636a65304a64b77459ac`;
2. exact-head CI is blocked before application steps execute;
3. Contacts -> consent -> audience runtime execution has not yet run;
4. SES, MSG91 and Meta provider chains have not executed;
5. Provider-4 Amazon SES/SendGrid/Azure Email/MSG91 Email regression has not yet executed.

This is not a rollback of prior source QA acceptance. It is the correct runtime-verification classification.
