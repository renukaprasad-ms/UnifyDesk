# F1 Integration Remediation Prompt — Platform Hardening R1

You are F1 for the targeted UnifyDesk Platform Hardening Round-1 remediation.

Repository:

`renukaprasad-ms/UnifyDesk`

Independent QA finding owned by you:

`PHR1-QA-005` — High — Managed SMS company-switch stale-response tenant race.

## Exact frozen remediation base

Create:

`fix/platform-managed-sms-tenant-race-r1`

from exactly:

`677dea286bfe45b2172872442125f2faf4ce7953`

Do NOT branch from later moving `dev`.
Do NOT rebase.

After the remediation-plan documentation is merged, read from current `dev`:

- `unifydesk-app/docs/platform/hardening-r1/SECURITY_QA_R1_FINDINGS.md`
- `unifydesk-app/docs/platform/hardening-r1/REMEDIATION_R1.md`
- `unifydesk-app/docs/platform/hardening-r1/CHECK-MATRIX.md`
- this prompt.

## Finding

Affected:

- `unifydesk-app/frontend/src/components/managed-sms/ManagedSmsPanel.tsx`
- `unifydesk-app/frontend/src/components/managed-sms/ManagedSmsSetupModal.tsx`

Current source uses:

`useEffect(() => { currentCompanyId.current = companyId }, [companyId])`

while async requests accept results when the request company equals `currentCompanyId.current`.

That passive-effect update is too late for tenant isolation. After the component commits company B, a company-A request may resolve before the passive effect updates the ref, see A as still current, and mutate the B screen with A state.

This is not a cosmetic issue. Treat it as tenant-isolation/correctness hardening.

## Required remediation

Implement the smallest robust design that makes stale tenant requests impossible to commit after a company switch.

Acceptable families include:

- synchronous layout-phase invalidation plus request-generation/token checking;
- per-load generation IDs invalidated during the committed tenant transition;
- abort/cancellation plus generation/tenant validation;
- another design with equivalent ordering guarantees.

Do not merely replace one passive effect with another passive effect.
Do not rely only on API cancellation; a resolved/callback path must still be safely ignored.
Do not weaken or remove stale-response checks.

Every async state mutation belonging to the affected load paths must be protected, including error/finally handling.

### ManagedSmsPanel

Protect at minimum:

- status;
- usage;
- delivery health;
- loading/error transitions.

### ManagedSmsSetupModal

Protect at minimum:

- status;
- DLT profile;
- headers;
- templates;
- loading/error transitions.

Also inspect mutation/refresh flows that call `load()` and ensure the fix does not allow a prior tenant's post-action refresh to write into the newly selected tenant.

## Required component-level regression coverage

Add tests at component level, not only `tenantScope.ts` helper tests.

At minimum prove for BOTH components:

1. render company A;
2. hold one or more company-A API promises unresolved;
3. rerender the same mounted component with company B;
4. resolve the old A promise after the B commit/tenant switch;
5. prove A status/profile/header/template/usage/health content is not rendered into B;
6. resolve B requests and prove B state is accepted normally;
7. prove stale A rejection does not surface an A-scoped error/snackbar on B.

Use deterministic deferred promises/fake API mocks. Do not use arbitrary sleeps.

## Scope restrictions

Do not redesign Managed SMS UI.
Do not change backend.
Do not edit GitHub workflows.
Do not broaden into Managed SMS provider/control-plane behavior.
Do not weaken existing assertions to make tests pass.

A small shared frontend tenant-request helper is allowed only if it genuinely simplifies both affected components and remains tightly scoped.

## Verification

From `unifydesk-app/frontend` execute independently:

```bash
npm ci
npm test
npm run build
npm run lint
npm run format:check
```

All five must execute successfully for acceptance. If runner allocation is unavailable, report EXECUTION BLOCKED rather than PASS, but execute locally wherever the available environment supports it and provide exact evidence.

## Deliverable

Open a PR to `dev`.

Report:

- branch;
- exact base SHA;
- final SHA;
- files changed;
- root-cause explanation;
- invalidation/generation design used;
- new component race tests and what ordering they prove;
- npm ci/test/build/lint/format results;
- every remaining EXECUTION BLOCKED item.

Do not merge your own PR.
