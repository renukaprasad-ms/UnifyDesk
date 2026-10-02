# UnifyDesk Platform Hardening Round-1 — Managed SMS Tenant Epoch FINAL SEC/QA Findings

## Review identity

- **Review branch:** `review/platform-hardening-managed-sms-tenant-epoch-r1-final`
- **Exact frozen production-code SHA:** `d592da3b7b09f91a80b8029db5dc7500e1d6ba52`
- **Remediation PR:** #236
- **Remediation branch:** `fix/platform-managed-sms-tenant-epoch-r1`
- **Remediation head:** `b0f58a29e3822f47d18e079eeaeb97e02efc3922`
- **Scope:** `PHR1-QA-005` and `PHR1-QA-006` only
- **Production-code changes by SEC/QA:** none
- **Evidence classification:** SOURCE REVIEWED
- **Executable verification:** DEFERRED

## Final result

**PASS FOR NARROW SOURCE RE-VERIFICATION.**

No further Managed SMS tenant-isolation source defect was found in the narrow remediation.

Final dispositions:

- **PHR1-QA-005 = SOURCE REMEDIATION COMPLETE**
- **PHR1-QA-006 = SOURCE REMEDIATION COMPLETE**

Platform Hardening Round 1 may therefore be classified:

**Platform Hardening Round 1 = SOURCE COMPLETE WITH DEFERRED EXECUTABLE/FORMATTING VERIFICATION**

This result does not reopen or change any previously deferred CI, backend Spotless, email-provider Spotless, B1/B2, PLAT-002, or PLAT-003 disposition.

---

## 1. Scope and lineage integrity

PR #236 changes exactly two files:

- `unifydesk-app/frontend/src/components/managed-sms/ManagedSmsSetupModal.tsx`
- `unifydesk-app/frontend/src/components/managed-sms/ManagedSmsTenantRace.test.tsx`

The final frozen SHA contains the exact PR #236 versions of both files; their blobs are identical to remediation head `b0f58a29e3822f47d18e079eeaeb97e02efc3922`.

No Managed SMS UI redesign, backend/provider contract change, B1/B2 change, workflow change, or runner-related change was introduced by the narrow remediation.

`ManagedSmsPanel.tsx` is unchanged from the prior final reviewed SHA, preserving the existing load-generation remediation.

---

## 2. Dedicated mutation tenant epoch

**SOURCE REVIEWED: PASS**

`ManagedSmsSetupModal.tsx` now maintains two separate refs:

- `tenantEpoch`
- `requestGeneration`

They have distinct responsibilities.

### Tenant epoch

`tenantEpoch.current` increments only inside the committed-company-transition `useLayoutEffect`:

`currentCompanyId.current = companyId`
`tenantEpoch.current += 1`
`requestGeneration.current += 1`

The early return:

`if (currentCompanyId.current === companyId) return`

prevents same-company renders/reloads from advancing the tenant epoch.

Therefore, within the same mounted component:

- initial A1 uses epoch 0;
- transition A -> B advances epoch to 1;
- transition B -> A2 advances epoch to 2.

A1 and A2 cannot share mutation validity merely because both use the same company ID.

### Load request generation remains separate

Ordinary `load()` execution performs:

`const generation = ++requestGeneration.current`

but does not modify `tenantEpoch.current`.

Therefore same-tenant reloads can invalidate older loads without invalidating a current mutation solely because a load was refreshed.

This satisfies the required separation between tenant-session mutation identity and ordinary request generation.

---

## 3. Mutation capture and irreversible currentness

**SOURCE REVIEWED: PASS**

At `withBusy(...)` entry, every setup mutation captures:

- `mutationCompanyId = companyId`
- `mutationTenantEpoch = tenantEpoch.current`

Currentness requires both:

- initiating company ID equals `currentCompanyId.current`; and
- captured epoch equals `tenantEpoch.current`.

The predicate is:

`isManagedSmsTenantCurrent(mutationCompanyId, currentCompanyId.current) && mutationTenantEpoch === tenantEpoch.current`

This fixes the prior ABA defect.

Once an A1 operation observes a later company transition, its captured epoch can never equal the monotonically advanced current epoch again during that mounted component's lifetime. Returning to company A creates A2 under a newer epoch rather than reviving A1.

Therefore a mutation that becomes stale cannot become valid again.

---

## 4. Async completion and mutation-path review

**SOURCE REVIEWED: PASS**

The common `withBusy` boundary protects all setup mutation completion effects:

1. mutation is rejected before starting if company + epoch are not current;
2. after `await action(...)`, currentness is checked again before:
   - `onChanged()`;
   - follow-up `load()`;
3. catch suppresses stale error UI;
4. finally changes `busy` only if company + epoch are still current.

The company-transition layout effect also immediately executes `setBusy(false)`, so a previous tenant's busy state does not remain attached to the newly committed tenant.

### DLT profile create -> submit

PASS.

After `createDltProfile` resolves, `isCurrentTenant()` is checked before `submitDltProfile`.

After submit resolves, currentness is checked again before success UI.

A stale A1 therefore cannot execute the second API step after a tenant transition.

### Header create -> submit

PASS.

After `createHeader`, currentness is checked before `submitHeader`.

After submit, currentness is checked before field reset/success UI.

### Template create -> submit

PASS.

After `createTemplate`, currentness is checked before evaluating/using the returned active version for the next mutation.

After `submitTemplateVersion`, currentness is checked before form resets/success UI.

### Sender creation

PASS.

`createSender` runs under `withBusy`; currentness is checked immediately after its await before success UI, while the outer guard protects `onChanged`, follow-up load, error, and finally.

### DLT profile refresh

PASS.

The refresh API is a single-step operation under `withBusy`. After its await returns, the outer company+epoch check executes before `onChanged` or follow-up `load()`. Stale errors and stale finally state are suppressed by the wrapper.

### Header refresh

PASS.

Same single-step `withBusy` protection applies.

### Template refresh

PASS.

Same single-step `withBusy` protection applies.

No setup mutation path found in the component bypasses the epoch-aware `withBusy` boundary.

---

## 5. Stale success/error/finally guarantees

**SOURCE REVIEWED: PASS**

For an operation that becomes stale:

- later API steps in multi-step mutations are blocked by explicit inner `isCurrentTenant()` checks;
- success snackbar/state updates are blocked;
- `onChanged` is blocked by the outer post-action check;
- follow-up `load()` is blocked by the same outer check;
- catch returns without showing a stale error;
- finally returns without changing the current tenant's busy state.

Because validity includes the captured epoch, these protections continue to hold after `A1 -> B -> A2`; company-ID equality alone can no longer revive A1.

---

## 6. A -> B -> A deterministic component regression coverage

**SOURCE REVIEWED: PASS**

`ManagedSmsTenantRace.test.tsx` adds deterministic deferred-promise coverage using the same mounted `ManagedSmsSetupModal`. No sleeps or timing guesses are used.

### Old A1 success test

The test:

1. mounts company A1;
2. starts a deferred DLT create mutation;
3. rerenders the same component for B;
4. rerenders the same component for A2 before A1 settles;
5. resolves old A1;
6. proves:
   - old A1 does not call `submitDltProfile`;
   - old A1 emits no success;
   - old A1 emits no error;
   - old A1 does not call `onChanged`;
   - old A1 does not trigger an extra follow-up status/load request;
   - current A2 is not left busy/disabled;
7. starts a new A2 mutation;
8. proves the new A2 mutation:
   - calls create;
   - calls submit;
   - calls `onChanged`;
   - emits success;
   - triggers the expected follow-up load.

This directly proves both permanent A1 invalidation and continued A2 usability.

### Old A1 failure test

The second test follows the same A1 -> B -> A2 transition with a deferred rejected A1 promise and proves:

- no submit occurs;
- no stale success appears;
- no stale error appears;
- `onChanged` is not called;
- no stale follow-up load occurs;
- A2 remains usable/not disabled.

Together with the successful fresh-A2 operation in the preceding test, the suite covers both required stale success and stale failure behavior plus new-A2 operability.

Existing direct A -> B load/mutation regression tests remain present.

---

## 7. PHR1-QA-005 final disposition

### PHR1-QA-005 — Managed SMS tenant isolation

- **Exact SHA:** `d592da3b7b09f91a80b8029db5dc7500e1d6ba52`
- **Source-remediation status:** **SOURCE REMEDIATION COMPLETE**
- **Executable-verification status:** **EXECUTABLE VERIFICATION DEFERRED**
- **Final disposition:** **SOURCE CLOSED**
- **Evidence:** SOURCE REVIEWED
- **New source finding:** none

The prior direct A -> B protections remain intact, and mutation completion now has durable epoch identity across repeated company transitions.

---

## 8. PHR1-QA-006 final disposition

### PHR1-QA-006 — Managed SMS stale mutation ABA reactivation

- **Exact SHA:** `d592da3b7b09f91a80b8029db5dc7500e1d6ba52`
- **Source-remediation status:** **SOURCE REMEDIATION COMPLETE**
- **Executable-verification status:** **EXECUTABLE VERIFICATION DEFERRED**
- **Final disposition:** **SOURCE CLOSED**
- **Evidence:** SOURCE REVIEWED
- **New source finding:** none

Dedicated tenant epochs make A1 and A2 distinct mutation identities even when their company IDs are identical.

The original ABA reproduction is no longer valid against the remediated source.

---

## 9. Executable verification

**EXECUTABLE VERIFICATION DEFERRED.**

This narrow QA did not obtain a materialized frontend execution environment and did not reopen or rerun the previously deferred GitHub runner condition.

No zero-step workflow result is represented as PASS.

No executable test/build/lint/format claim is made by this report.

---

## Final Round-1 classification

**Platform Hardening Round 1 = SOURCE COMPLETE WITH DEFERRED EXECUTABLE/FORMATTING VERIFICATION**

This source-complete classification is limited to the completed source-remediation obligations. It does not convert previously documented deferred executable or formatting evidence into green release evidence.

No new source defect was found in this final narrow review.

Do not start Platform Hardening Round 2 as part of this SEC/QA task.
