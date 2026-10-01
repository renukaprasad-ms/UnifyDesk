# SEC/QA Narrow Final Re-verification — Managed SMS Tenant Epoch

You are SEC/QA for the **narrow final source re-verification** of UnifyDesk Platform Hardening Round 1.

This review covers only:

- `PHR1-QA-005`
- `PHR1-QA-006`

Do not reopen B1/B2 remediation.
Do not revisit deferred backend Spotless or email-provider Spotless debt.
Do not attempt to solve or repeatedly rerun the deferred GitHub Actions runner-allocation issue.
Do not start Platform Hardening Round 2.

Repository:

`renukaprasad-ms/UnifyDesk`

## Exact frozen production-code SHA

Create:

`review/platform-hardening-managed-sms-tenant-epoch-r1-final`

from exactly:

`d592da3b7b09f91a80b8029db5dc7500e1d6ba52`

Do NOT branch from later moving `dev`.
Do NOT rebase.
Do NOT modify production code during QA.

## Lineage

Previous final SEC/QA:

- PR #235
- branch: `review/platform-hardening-security-qa-r1-final`
- QA head: `1f350a614e34c7562ca9119725c71d77336a5ad7`
- reviewed SHA: `5012fd426b4d2da50d5c8fe5c44cf3f15b5aebc3`
- report: `unifydesk-app/docs/platform/hardening-r1/SECURITY_QA_R1_FINAL_FINDINGS.md`

That review found residual ABA tenant reactivation and created `PHR1-QA-006`.

Final narrow remediation:

- remediation base: `20db8a07f28a6611d2b448d8273575dd736a0d7a`
- branch: `fix/platform-managed-sms-tenant-epoch-r1`
- PR #236
- remediation head: `b0f58a29e3822f47d18e079eeaeb97e02efc3922`
- integrated production SHA: `d592da3b7b09f91a80b8029db5dc7500e1d6ba52`

## Files in scope

Review only the narrow Managed SMS remediation and directly related regression coverage:

- `unifydesk-app/frontend/src/components/managed-sms/ManagedSmsSetupModal.tsx`
- `unifydesk-app/frontend/src/components/managed-sms/ManagedSmsTenantRace.test.tsx`

You may inspect surrounding Managed SMS code only as needed to validate the flow.

## Required source verification

### 1. Dedicated mutation tenant epoch

Confirm setup mutations now use a dedicated tenant epoch/generation that is separate from ordinary load request generation.

Verify:

- the epoch monotonically increments on every committed company transition;
- `A1 -> B -> A2` produces distinct epochs for A1 and A2 despite equal company IDs;
- same-tenant ordinary reloads do not change the mutation tenant epoch merely because `requestGeneration` changes.

### 2. Mutation capture/currentness

At mutation start, confirm the implementation captures:

- initiating company ID;
- initiating tenant epoch.

Mutation currentness must require BOTH:

- company ID still matches; and
- captured epoch still equals the current tenant epoch.

Company ID alone is insufficient.

### 3. Every async mutation completion path

Confirm the captured tenant identity is checked after awaits and before later effects.

Review all Managed SMS setup mutation flows, including:

- DLT profile create -> submit;
- Header create -> submit;
- Template create -> submit;
- Sender creation;
- DLT profile refresh;
- Header refresh;
- Template refresh.

For multi-step operations, verify stale epoch is checked before the next API operation.

For all mutations, verify stale A1 cannot:

- emit success UI;
- emit error UI;
- call `onChanged`;
- trigger the follow-up `load()`;
- alter current A2/B `busy` state from stale `finally`.

A mutation that becomes stale once must never become valid again.

### 4. A -> B -> A component regression tests

Review `ManagedSmsTenantRace.test.tsx`.

Required same-mounted-component coverage:

1. A1 starts mutation.
2. Switch A -> B.
3. Switch B -> A2 before A1 settles.
4. Resolve old A1 success.
5. Prove old A1 cannot invoke the second API step.
6. Prove old A1 cannot emit success.
7. Prove old A1 cannot call `onChanged`.
8. Prove old A1 cannot trigger stale follow-up load.
9. Reject old A1 and prove no stale error UI appears.
10. Start a NEW operation in A2 and prove it remains usable and completes normally.

Tests must use deterministic delayed/deferred promises, not sleeps.

### 5. Scope integrity

Confirm:

- no Managed SMS UI redesign;
- no backend/provider contract changes;
- no B1/B2 changes;
- no workflow/runner changes;
- ordinary load request-generation behavior remains intact.

## Executable evidence

This final narrow QA is primarily **SOURCE REVIEWED**.

If a real frontend execution environment is available, you may execute focused tests and/or the frontend matrix and record only commands that actually ran.

If execution is unavailable, use:

`EXECUTABLE VERIFICATION DEFERRED`

Do not convert zero-step jobs into PASS and do not spend this review attempting to repair the deferred runner condition.

## Expected disposition

If source review finds no further tenant-isolation defect:

- `PHR1-QA-005 = SOURCE REMEDIATION COMPLETE`
- `PHR1-QA-006 = SOURCE REMEDIATION COMPLETE`

Executable frontend verification may remain deferred.

Then Platform Hardening Round 1 may be closed as:

**SOURCE COMPLETE WITH DEFERRED EXECUTABLE/FORMATTING VERIFICATION**

The following existing deferred items remain unchanged and are NOT failures of this narrow review:

- PHR1-QA-001 — BLOCKED EXTERNAL / DEFERRED
- PHR1-QA-002 — DEFERRED VERIFICATION / FORMATTING DEBT
- PHR1-QA-003 — SOURCE REMEDIATION PARTIAL / DEFERRED VERIFICATION / FORMATTING DEBT
- PHR1-QA-004 — SOURCE REMEDIATION COMPLETE / EXECUTABLE VERIFICATION DEFERRED
- PLAT-002 remains open/deferred
- PLAT-003 retains its final-integrated execution limitation

## Final report

Create:

`unifydesk-app/docs/platform/hardening-r1/SECURITY_QA_R1_TENANT_EPOCH_FINAL_FINDINGS.md`

The report must state:

- exact reviewed SHA;
- SOURCE REVIEWED result for the epoch implementation;
- SOURCE REVIEWED result for A -> B -> A tests;
- PHR1-QA-005 disposition;
- PHR1-QA-006 disposition;
- executable evidence actually obtained, if any;
- whether Round 1 can be classified **SOURCE COMPLETE WITH DEFERRED EXECUTABLE/FORMATTING VERIFICATION**;
- any new source defect if found.

Do not start Platform Hardening Round 2.
