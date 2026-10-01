# UnifyDesk Platform Hardening Round-1 — Independent SEC/QA Findings

## Review identity

- **Review branch:** `review/platform-hardening-security-qa-r1`
- **Frozen production-code SHA:** `7fe25fe73eca72b57172d025d944b6ed54ce196f`
- **Canonical workflow:** `Checks`
- **Canonical run:** `36846527232`
- **Review rule:** production code was not modified during QA; this branch contains review evidence only.
- **Release evidence rule:** historical `continue-on-error` green jobs are not treated as release PASS evidence.

## Overall result

**NOT PASS — targeted remediation is required and exact-SHA canonical execution remains EXECUTION BLOCKED.**

The exact integrated production SHA was re-run during this independent review. Run `36846527232` reached **attempt 4** with head SHA `7fe25fe73eca72b57172d025d944b6ed54ce196f`. All five canonical jobs were created, all completed as failure, and all returned an empty step list. No checkout, setup, test, build, lint, format, migration, or package command started.

Fresh attempt-4 job IDs:

| Job | Job ID | Executed steps | QA classification |
| --- | ---: | ---: | --- |
| frontend | `110327987636` | 0 | EXECUTION BLOCKED |
| database | `110327987831` | 0 | EXECUTION BLOCKED |
| email-provider | `110327987948` | 0 | EXECUTION BLOCKED |
| backend | `110327987955` | 0 | EXECUTION BLOCKED |
| website | `110327988042` | 0 | EXECUTION BLOCKED |

This is fresh exact-SHA evidence. It is not a code-test failure and must not be represented as PASS.

## Findings

### PHR1-QA-001 — Exact-SHA canonical verification cannot obtain runner execution

- **Severity:** Critical
- **Affected scope:** canonical CI; backend, database, frontend, email-provider, website
- **Exact SHA:** `7fe25fe73eca72b57172d025d944b6ed54ce196f`
- **Evidence classification:** **EXECUTION BLOCKED / BLOCKED EXTERNAL**
- **Evidence:** canonical run `36846527232`, attempt 4, exact head SHA; five jobs above have zero steps.
- **Reproduction:** re-run failed jobs for workflow run `36846527232`, then inspect the five job step lists.
- **Remediation owner:** **External**
- **Required remediation:** restore GitHub Actions runner allocation/account execution, then rerun the canonical `Checks` workflow on the exact remediated integrated SHA. Billing/quota/payment-state subtype is not visible from job evidence and must be checked in GitHub account billing/usage controls.

### PHR1-QA-002 — Backend Spotless gate is red on the backend tree used by the frozen SHA

- **Severity:** High
- **Affected scope:** `unifydesk-app/backend`
- **Exact SHA:** `7fe25fe73eca72b57172d025d944b6ed54ce196f`
- **Evidence classification:** **SOURCE REVIEWED + executable worker evidence; exact-SHA canonical execution blocked**
- **Evidence:** B1 backend source at `3e9f56f1c0f13c8116f97e6ede10a1f2770999fd` is unchanged in `unifydesk-app/backend/**` through the frozen integrated SHA. Executed job `110278369419` reported `1871` Java files with `810` requiring Spotless changes and failed `spotless:check`. B2 job `110276953129` independently reported `809` violations before B1's additional backend test files.
- **Reproduction command:** `cd unifydesk-app/backend && ./mvnw -B spotless:check`
- **Reproduction run/job:** historical executable job `110278369419` in run `36834450479`; corroborating job `110276953129` in run `36834015519`.
- **Remediation owner:** **B1**
- **Required remediation:** restore the backend formatting baseline without weakening Spotless. Rerun Spotless independently before the full backend matrix.

### PHR1-QA-003 — Email-provider Spotless gate is red on the exact email-provider tree

- **Severity:** High
- **Affected scope:** `email-provider`
- **Exact SHA:** `7fe25fe73eca72b57172d025d944b6ed54ce196f`
- **Evidence classification:** **SOURCE REVIEWED + executable worker evidence; exact-SHA canonical execution blocked**
- **Evidence:** the email-provider tree at B2 head `db197237182a61617e19a2a17c034da06d7b404e` is unchanged through the frozen integrated SHA. Executed job `110276953077` inspected `190` Java files, reported `40` requiring changes, and failed `spotless:check`.
- **Reproduction command:** `cd email-provider && ./mvnw -B spotless:check`
- **Reproduction run/job:** run `36834015519`, job `110276953077`.
- **Remediation owner:** **B2**
- **Required remediation:** fix the email-provider formatting baseline and then execute clean tests and package as separate fail-closed commands.

### PHR1-QA-004 — Full backend regression matrix remains unresolved after non-canonical executable failures

- **Severity:** High
- **Affected scope:** backend regression suite across Contacts, Ads, Microsoft, WhatsApp, Notifications, Managed Email/SMS, and related services
- **Exact SHA:** `7fe25fe73eca72b57172d025d944b6ed54ce196f`
- **Evidence classification:** **UNIT TEST EXECUTED in a non-canonical environment + SOURCE REVIEWED; exact-SHA environment-correct execution blocked**
- **Evidence:** historical B1 backend-full job `110278369419` executed the same backend source tree later present in the frozen SHA. Both `./mvnw -B test` and `./mvnw -B clean test` reported `873` tests, `19` failures, `30` errors, and `2` skipped. A substantial portion of errors were caused by the historical workflow omitting `SNOWFLAKE_NODE_ID`, producing `IllegalStateException: SNOWFLAKE_NODE_ID environment variable is required`. However, the same run also contains assertion/Mockito failures not explained by that missing environment variable, including representative failures in Contacts API contract, Ads services/compiler/reporting, Microsoft API client, and other suites.
- **Important classification:** the historical workflow uses `continue-on-error`; its green job conclusion is **not** release evidence. These failures are not being promoted to exact-SHA production-regression findings until rerun with the canonical `SNOWFLAKE_NODE_ID=0` environment.
- **Reproduction command:** `cd unifydesk-app/backend && SNOWFLAKE_NODE_ID=0 ./mvnw -B test`, followed independently by `SNOWFLAKE_NODE_ID=0 ./mvnw -B clean test`.
- **Reproduction run/job:** historical run `36834450479`, job `110278369419`; exact-SHA canonical backend job `110327987955` could not start.
- **Remediation owner:** **B1**
- **Required remediation:** after formatting is green, run the environment-correct matrix and classify every remaining failure as production regression, stale/broken test, environment/configuration issue, or cascade. Do not suppress or weaken failing tests.

### PHR1-QA-005 — Managed SMS tenant guard can accept a stale prior-company response after a company switch

- **Severity:** High
- **Affected scope:** frontend multi-tenant isolation; `ManagedSmsPanel.tsx`, `ManagedSmsSetupModal.tsx`
- **Exact SHA:** `7fe25fe73eca72b57172d025d944b6ed54ce196f`
- **Evidence classification:** **SOURCE REVIEWED**
- **Evidence:** F1 changed `currentCompanyId.current = companyId` during render to a passive `useEffect(() => { currentCompanyId.current = companyId }, [companyId])`. Both components accept async results when `requestCompanyId === currentCompanyId.current`. After a new company render commits, an old request can resolve before the passive effect updates the ref; during that interval the equality guard still contains the previous company ID and can commit stale prior-company status/profile/header/template/usage/health data into the newly selected company's UI. The helper `tenantScope.ts` is only an equality check, and existing frontend Managed SMS tests cover the helper/policies rather than this component-level in-flight switch race.
- **Reproduction:** start a delayed Managed SMS load for company A, switch the same mounted component to company B, then resolve A's request before the passive company-ref effect flushes; observe that the A response passes `isManagedSmsTenantCurrent(A, currentCompanyId.current)`.
- **Remediation owner:** **F1**
- **Required remediation:** make tenant-generation invalidation synchronous with the committed company switch (for example, a layout-phase/request-generation or abort-based design), retain the stale-response guard, and add a component test that proves an in-flight A response cannot update the UI after switching to B.

## Verified controls / non-findings

These controls were independently source-reviewed and should remain intact during remediation:

1. **Canonical workflow is fail-closed.** `.github/workflows/checks.yml` contains exactly the five required jobs, separate commands, no `continue-on-error`, PR-to-`dev`, push-to-`dev`, and `workflow_dispatch` triggers.
2. **Deterministic test dependencies are present.** Canonical jobs use Java 25 / Node 24; `compose.test.yml` pins PostgreSQL `18.6-alpine` and Redis `8.10.1-alpine`; website uses `npm ci` with committed `package-lock.json`.
3. **Snowflake production behavior remains strict.** `SnowflakeIdGenerator.resolveNodeId()` requires `SNOWFLAKE_NODE_ID`; canonical backend/database jobs inject `0` only as a test-job environment value.
4. **Flyway is fail-closed by default.** `application.yaml` defaults `FLYWAY_BASELINE_ON_MIGRATE` to false; `DatabaseMigrationConfig` independently falls back to false and preserves explicit baseline version configuration.
5. **Migration tests cover the required paths.** B1 executable evidence shows `DatabaseMigrationConfigTests` 3/3 and `PlatformMigrationHardeningTests` 4/4 passing on the worker head. The latter covers clean DB to V83, representative V79 to V83, unmanaged non-empty fail-closed behavior, deliberate V1 baseline adoption, and Hibernate schema validation. Because exact-SHA database execution is blocked, this is worker-level migration evidence, not integrated PASS.
6. **Legacy adoption documentation is conservative.** It requires a known compatible V1-equivalent schema and deliberate temporary baselining; it does not recommend arbitrary production baselining.
7. **Website deterministic build has executable worker evidence.** B2 run `36834015519`, job `110276953115`, successfully executed `npm ci` and `npm run build` on a website tree unchanged in the frozen SHA. Exact-SHA website status remains EXECUTION BLOCKED.
8. **Historical WhatsApp QA workflows no longer act as general PR-to-`dev` gates.** Their current triggers are limited to their review branch plus manual dispatch. Their historical `continue-on-error` behavior is not used as release evidence.
9. **Sensitive-data review found no new secret-bearing additions.** Added lines across the B1/F1/B2 merge set were checked for common credential/private-key/token patterns and new logging calls. No hard-coded credential/private-key/token additions or new sensitive provider-payload logging were identified in the Round-1 changes reviewed.

## Required remediation handoff to L1

- **B1:** PHR1-QA-002 and PHR1-QA-004.
- **B2:** PHR1-QA-003.
- **F1:** PHR1-QA-005.
- **External:** PHR1-QA-001.
- **L1:** integrate only targeted remediation from the frozen finding set, then produce a new frozen integrated SHA for independent re-verification.

Do **not** start Platform Hardening Round 2 from this result. Round 1 requires targeted remediation and a fresh independent exact-SHA verification first.
