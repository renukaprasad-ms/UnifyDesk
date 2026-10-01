# Platform Hardening Round-1 — Targeted Remediation

Status: **L1 REMEDIATION CONTRACT FROZEN — B1/B2/F1 MAY START AFTER THIS DOCUMENTATION IS MERGED**

## Frozen lineage

Original Round-1 worker base:

`c541899f238dbe6f70f9b91f5c513fa1b10f5641`

First integrated production-code / SEC-QA base:

`7fe25fe73eca72b57172d025d944b6ed54ce196f`

Independent SEC/QA:

- PR #228
- QA branch: `review/platform-hardening-security-qa-r1`
- QA head: `0bf0965b2fade910bea9d206e8277c041d81f0d6`
- findings: [SECURITY_QA_R1_FINDINGS.md](SECURITY_QA_R1_FINDINGS.md)

QA PR #228 merge / exact remediation worker base:

`677dea286bfe45b2172872442125f2faf4ce7953`

All B1/B2/F1 remediation workers MUST branch from exactly that SHA.

Do not branch from later moving `dev`.
Do not rebase remediation workers onto later `dev`.

The remediation prompt documents may be merged after the frozen SHA above. Workers read the prompts from current `dev`, but create their code branches from exactly `677dea286bfe45b2172872442125f2faf4ce7953`.

## Frozen QA findings

| Finding | Severity | Owner | Remediation state |
|---|---|---|---|
| PHR1-QA-001 | Critical | External | BLOCKED EXTERNAL / EXECUTION BLOCKED |
| PHR1-QA-002 | High | B1 | ASSIGNED |
| PHR1-QA-003 | High | B2 | ASSIGNED |
| PHR1-QA-004 | High | B1 | ASSIGNED |
| PHR1-QA-005 | High | F1 | ASSIGNED |

No worker may broaden this remediation round beyond the findings assigned below.

## External — PHR1-QA-001

GitHub Actions exact-SHA execution continues to fail before runner allocation with `runner_id=0` / zero steps.

There is no repository code remediation assigned for this finding.

Classification remains:

**BLOCKED EXTERNAL / EXECUTION BLOCKED**

Main must continue to verify GitHub account Actions usage, budget/spending controls and payment/account status. When runners become available, L1/SEC-QA must rerun the canonical workflow against the final remediated integrated SHA.

Do not create fake CI changes to make this external condition appear resolved.

## B1 — PHR1-QA-002 + PHR1-QA-004

Branch:

`fix/platform-backend-remediation-r1`

Prompt:

[BACKEND_B1_R1_INTEGRATION_REMEDIATION_CHAT_PROMPT.md](BACKEND_B1_R1_INTEGRATION_REMEDIATION_CHAT_PROMPT.md)

Scope:

1. restore the backend Spotless baseline without changing/excluding formatter rules;
2. keep the ~810-file mechanical formatter change separate from semantic remediation where practical;
3. review the resulting formatter diff for accidental source modifications;
4. with deterministic PostgreSQL/Redis and `SNOWFLAKE_NODE_ID=0`, execute independently:
   - `./mvnw -B spotless:check`
   - `./mvnw -B test`
   - `./mvnw -B clean test`
   - `./mvnw -B package -DskipTests`
5. classify every remaining failure:
   - production regression;
   - stale/broken test;
   - environment/configuration defect;
   - cascade from another failure;
6. make only evidence-backed targeted corrections;
7. preserve Flyway fail-closed behavior and all current V83 migration hardening coverage.

Acceptance:

- backend Spotless executes green;
- full environment-correct backend test matrix executes;
- package executes;
- no test is skipped/weakened to obtain green;
- any unresolved failure is explicitly reported with test name and classification;
- Flyway safe defaults and migration tests remain intact.

## B2 — PHR1-QA-003

Branch:

`fix/platform-email-provider-remediation-r1`

Prompt:

[BACKEND_B2_R1_INTEGRATION_REMEDIATION_CHAT_PROMPT.md](BACKEND_B2_R1_INTEGRATION_REMEDIATION_CHAT_PROMPT.md)

Scope:

1. restore email-provider Spotless baseline without formatter exclusions/rule changes;
2. keep formatter-only changes mechanical;
3. execute independently:
   - `./mvnw -B spotless:check`
   - `./mvnw -B clean test`
   - `./mvnw -B package -DskipTests`
4. classify any post-format failure before editing behavior;
5. preserve the Maven Wrapper newline fix and canonical CI contracts.

Acceptance:

- Spotless executes green;
- clean tests execute;
- package executes;
- no email-provider product redesign;
- no canonical CI weakening.

## F1 — PHR1-QA-005

Branch:

`fix/platform-managed-sms-tenant-race-r1`

Prompt:

[FRONTEND_F1_R1_INTEGRATION_REMEDIATION_CHAT_PROMPT.md](FRONTEND_F1_R1_INTEGRATION_REMEDIATION_CHAT_PROMPT.md)

Affected components:

- `ManagedSmsPanel.tsx`
- `ManagedSmsSetupModal.tsx`

The current passive `useEffect` that updates `currentCompanyId.current` leaves a commit-to-passive-effect window where a company-A request can still pass the stale-response guard after the UI has switched to company B.

Required behavior:

- invalidate the old tenant/request synchronously with the committed company switch;
- preserve stale-response protection for every relevant status/profile/header/template/usage/health path;
- use a robust layout-phase, request-generation, abort/cancellation, or equivalently safe design;
- add component-level regression tests using delayed company-A requests and the same mounted component rerendered for company B;
- prove resolving A after the switch cannot write A state into B UI;
- do not redesign the Managed SMS UI.

Full frontend acceptance matrix:

- `npm ci`
- `npm test`
- `npm run build`
- `npm run lint`
- `npm run format:check`

## Integration order

All three workers are independent and start from the same frozen base.

L1 integration order:

1. B1 — backend verification/semantic remediation;
2. F1 — Managed SMS tenant-race remediation;
3. B2 — email-provider formatting/verification remediation.

Workers open PRs to `dev` but do not merge independently.

L1 reviews every diff before integration. In particular:

- B1 formatter output must be separated from semantic fixes enough to review the ~810-file change;
- F1 must contain actual component-level race coverage, not only helper tests;
- B2 must remain email-provider only.

After integration, L1 freezes ONE NEW exact integrated **production-code SHA**. Later documentation commits do not change that frozen base.

## Final SEC/QA re-verification

Round 1 is not complete after worker remediation.

SEC/QA must independently branch from the NEW exact integrated production-code SHA and reverify:

1. PHR1-QA-002 backend Spotless;
2. PHR1-QA-004 backend `test`, `clean test`, and package with `SNOWFLAKE_NODE_ID=0`;
3. existing Flyway fail-closed and V83 migration tests remain valid;
4. PHR1-QA-003 email-provider Spotless/test/package;
5. PHR1-QA-005 Managed SMS company-switch isolation regression;
6. complete frontend canonical matrix;
7. canonical workflow source remains fail-closed;
8. if GitHub runners allocate, all five canonical jobs execute against the NEW exact integrated SHA;
9. if runners still do not allocate, PHR1-QA-001 remains **BLOCKED EXTERNAL / EXECUTION BLOCKED**, never PASS.

Only evidence from actually executed commands can close executable findings.

Do not begin Platform Hardening Round 2 or another product feature until this targeted Round-1 remediation is integrated and independently reverified.
