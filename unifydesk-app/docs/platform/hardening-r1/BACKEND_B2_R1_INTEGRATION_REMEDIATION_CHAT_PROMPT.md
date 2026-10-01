# B2 Integration Remediation Prompt — Platform Hardening R1

You are B2 for the targeted UnifyDesk Platform Hardening Round-1 remediation.

Repository:

`renukaprasad-ms/UnifyDesk`

Independent QA finding owned by you:

`PHR1-QA-003` — High — email-provider Spotless baseline is red.

## Exact frozen remediation base

Create:

`fix/platform-email-provider-remediation-r1`

from exactly:

`677dea286bfe45b2172872442125f2faf4ce7953`

Do NOT branch from later moving `dev`.
Do NOT rebase.

After the remediation-plan documentation is merged, read from current `dev`:

- `unifydesk-app/docs/platform/hardening-r1/SECURITY_QA_R1_FINDINGS.md`
- `unifydesk-app/docs/platform/hardening-r1/REMEDIATION_R1.md`
- `unifydesk-app/docs/platform/hardening-r1/CHECK-MATRIX.md`
- this prompt.

## Scope

Own ONLY `PHR1-QA-003`.

Current executable evidence:

- Maven Wrapper bootstrap/newline defect is already fixed;
- prior job `110276953077` reached Spotless;
- Spotless inspected 190 Java files and found 40 requiring formatting.

### 1. Restore email-provider Spotless baseline

Use the existing formatter configuration.

Do NOT:

- disable Spotless;
- exclude existing source files;
- change formatter rules merely to obtain green;
- hand-bypass the gate.

Keep the formatter-only change mechanical and separate from semantic fixes where practical.

### 2. Execute the complete email-provider matrix

From `email-provider`:

```bash
./mvnw -B spotless:check
./mvnw -B clean test
./mvnw -B package -DskipTests
```

Run the commands independently.

If tests/package expose a failure after formatting, classify the failure before editing behavior.

Make only minimal corrections required by existing behavior/contracts.

### 3. Preserve existing hardening

Do not regress:

- newline-terminated Maven Wrapper properties;
- Maven Wrapper-based execution;
- canonical `Checks` workflow;
- fail-closed CI gates;
- website/npm work from B2 Round 1.

No email-provider feature redesign belongs in this remediation.

## Restrictions

Do not edit:

- `unifydesk-app/backend/**`
- `unifydesk-app/frontend/**`
- `unifydesk-website/**`
- `.github/workflows/**`

unless a directly exposed B2-owned verification infrastructure defect makes a tiny correction necessary and you document it for L1 review.

Do not merge your own PR.

## Acceptance

Required evidence:

- Spotless executes successfully;
- clean tests execute successfully or every remaining failure is explicitly classified;
- package executes successfully;
- wrapper fix remains intact;
- no formatter/test rule weakening;
- no unrelated product redesign.

## Deliverable

Open a PR to `dev`.

Report:

- branch;
- exact base SHA;
- final SHA;
- files changed;
- formatter-only commit SHA;
- any semantic correction and justification;
- Spotless result;
- clean-test result;
- package result;
- any remaining EXECUTION BLOCKED / BLOCKED EXTERNAL item.

Do not merge independently.
