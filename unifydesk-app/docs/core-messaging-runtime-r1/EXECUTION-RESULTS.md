# Execution Results

## Audit date

2026-10-02

## Frozen SHA resolution

**PASS**

Live GitHub `dev` resolved to:

`850ce7ad2874d20070067d46d8604ddc2bdd2c99`

Commit:

`test(contacts): keep segment conditions after preview`

This exact SHA is the source baseline for the audit. `dev` later moved during the audit to `3a12d84e4aecd31f3c067b6af6f998e173992d24` for unrelated Cloudflare frontend-hosting configuration; the frozen runtime audit baseline was intentionally not moved.

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

**NOT EXECUTED**

GitHub returned no workflow run for:

`850ce7ad2874d20070067d46d8604ddc2bdd2c99`

The repository contains an executable `Checks` workflow using GitHub-hosted `ubuntu-latest`, but presence of the workflow is not execution evidence.

## Audit-PR CI evidence

PR #249 triggered `Checks` workflow run:

`37008691423`

Result reported by GitHub:

**FAILURE — runner/step execution evidence absent**

All five jobs were reported `failure`, but their job payloads contained `steps = null`:

- database
- email-provider
- frontend
- backend
- website

This is not evidence that application tests/build steps ran and failed. It is classified as an **environment/CI execution blocker**, consistent with the previous no-runner/no-step evidence, until GitHub Actions actually allocates a runner and exposes executed steps/logs.

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

### Gmail / Microsoft / SMTP canonical Broadcast

**FAIL — current source/scope mismatch**

Modern canonical Broadcast rejects these mailbox providers through `BroadcastSenderProvider.supports()` and `BroadcastLaunchService`.

This failure is distinct from provider credential readiness.

## Environment / credential evidence

**NOT EXECUTED**

Repository source proves the backend consumes the Kubernetes `messaging-env` Secret. It does not expose live values. No claim is made here about which provider secrets are currently mounted.

## Stale PR housekeeping

**PASS**

Closed as superseded without merging:

- #197
- #218
- #244

## Current release-verification disposition

**HOLD FOR EXECUTABLE RUNTIME EVIDENCE**

Reasons:

1. exact frozen SHA has no executed CI evidence;
2. audit PR CI terminated without step execution evidence;
3. SES, MSG91 and Meta test-provider chains have not executed;
4. Provider-4 Gmail/Microsoft/SMTP requirement conflicts with current canonical Broadcast source.

This is not a rollback of prior source QA acceptance. It is the correct runtime-verification classification.
