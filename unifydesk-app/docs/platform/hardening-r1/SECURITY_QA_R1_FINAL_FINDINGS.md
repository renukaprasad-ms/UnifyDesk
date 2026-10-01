# UnifyDesk Platform Hardening Round-1 — FINAL Independent SEC/QA Re-verification

## Review identity

- **Review branch:** `review/platform-hardening-security-qa-r1-final`
- **Exact frozen final production-code SHA:** `5012fd426b4d2da50d5c8fe5c44cf3f15b5aebc3`
- **Production-code changes by SEC/QA:** none
- **Runner policy for this review:** Main explicitly deferred GitHub Actions runner allocation. SEC/QA did not rerun known zero-step jobs and does not classify any zero-step job as PASS.

## Final outcome

**FINAL SOURCE RE-VERIFICATION IS NOT FULLY CLOSED.**

B1 backend semantic remediation is source-accepted. B2 contains only the five evidence-backed email-provider formatter corrections and remains partial/deferred as intended. Flyway/V83/Snowflake/canonical-workflow protections remain intact.

However, final source still contains one residual Managed SMS mutation-generation defect: the setup mutation guard is company-ID-only. A mutation that becomes stale during `A -> B` can become accepted again after `B -> A` because the old async closure sees the same company ID again. This violates the frozen requirement that a multi-step mutation stop once its initiating tenant becomes stale.

Therefore:

- PHR1-QA-001: **BLOCKED EXTERNAL / DEFERRED**
- PHR1-QA-002: **DEFERRED VERIFICATION / FORMATTING DEBT**
- PHR1-QA-003: **SOURCE REMEDIATION PARTIAL / DEFERRED VERIFICATION / FORMATTING DEBT**
- PHR1-QA-004: **SOURCE REMEDIATION COMPLETE / EXECUTABLE VERIFICATION DEFERRED**
- PHR1-QA-005: **SOURCE REMEDIATION PARTIAL / STILL NEEDS CODE CHANGES; EXECUTABLE VERIFICATION DEFERRED**
- New source finding PHR1-QA-006: **OPEN — stale mutation can reactivate after A -> B -> A**

No Platform Hardening Round 2 work should start from this review result.

## Evidence availability

No new canonical runner reruns were requested in this final review.

Existing remediation-head canonical runs were inspected only:

| Scope | Head | Run | Result |
|---|---|---:|---|
| B1 PR #231 | `8c4f6bfd37e457a44f7d120dfff330730083e387` | `36853998345` | all five jobs completed with no runner steps |
| F1 PR #230 | `09beabd0b7170b27c8b8b4fae8352e687705fd10` | `36853242144` | all five jobs completed with no runner steps |
| L1/F1 completion PR #233 | `cd6e50aa127189251677278f9c5202508ebfaa32` | `36867556072` | all five jobs completed with no runner steps |
| B2 PR #232 | `11d16ccbd2ecaf30032860f87807ba59b1085f97` | `36864358199` | all five jobs completed with no runner steps |

SEC/QA had source access but no materialized executable repository checkout. Accordingly, no command is represented below as executed on the final SHA.

---

## PHR1-QA-001 — GitHub Actions runner allocation

- **Original severity:** Critical
- **Exact final SHA:** `5012fd426b4d2da50d5c8fe5c44cf3f15b5aebc3`
- **Source-remediation status:** **N/A — external condition**
- **Executable-verification status:** **BLOCKED EXTERNAL / DEFERRED**
- **Final disposition:** **DEFERRED — NOT PASS**
- **Evidence classification:** BLOCKED EXTERNAL / EXECUTION BLOCKED
- **Exact evidence:** existing remediation PR runs `36853998345`, `36853242144`, `36867556072`, and `36864358199` each produced canonical jobs with no executed runner steps.
- **Owner:** External / Main

The canonical workflow source remains fail-closed. `.github/workflows/checks.yml` is unchanged from the frozen remediation base and still contains the five required jobs with separate required commands and no `continue-on-error`.

Per Main's explicit decision, SEC/QA did not repeatedly rerun the known zero-step jobs.

---

## PHR1-QA-002 — Backend Spotless baseline

- **Original severity:** High
- **Exact final SHA:** `5012fd426b4d2da50d5c8fe5c44cf3f15b5aebc3`
- **Source-remediation status:** **DEFERRED FORMATTING DEBT — repository-wide formatter remediation was not performed**
- **Executable-verification status:** **DEFERRED VERIFICATION / EXECUTION BLOCKED**
- **Final disposition:** **DEFERRED — NOT CLOSED**
- **Evidence classification:** SOURCE REVIEWED + historical executable evidence
- **Owner:** B1 / Main deferred-verification decision

Evidence:

1. Historical executable evidence remains the real Spotless failure: B1/B2-era execution reported roughly 809–810 backend Java files requiring formatting.
2. PR #231 changed 17 backend files for targeted semantic/test remediation; it did not fabricate an ~810-file formatter rewrite.
3. Backend `pom.xml` blob is identical between remediation base `677dea286bfe45b2172872442125f2faf4ce7953` and final SHA. Spotless rules/exclusions were not weakened.
4. Canonical `Checks` workflow is likewise unchanged.
5. PR #231 explicitly did not claim repository-wide Spotless green.

Required eventual executable proof:

`cd unifydesk-app/backend && ./mvnw -B spotless:check`

Until that command actually executes and the formatting debt is remediated, PHR1-QA-002 remains deferred.

---

## PHR1-QA-003 — Email-provider Spotless baseline

- **Original severity:** High
- **Exact final SHA:** `5012fd426b4d2da50d5c8fe5c44cf3f15b5aebc3`
- **Source-remediation status:** **PARTIAL**
- **Executable-verification status:** **DEFERRED VERIFICATION / EXECUTION BLOCKED**
- **Final disposition:** **DEFERRED — NOT CLOSED**
- **Evidence classification:** SOURCE REVIEWED + historical executable evidence
- **Owner:** B2 / Main deferred-verification decision

PR #232 modifies exactly five Java files:

- `EmailDeliveryAttemptRepository.java`
- `MimeMessageBuilder.java`
- `EmailProviderApplicationTests.java`
- `PostfixQueueIdExtractorTests.java`
- `PostfixSmtpClientTests.java`

Independent patch review confirms all five changes are mechanical whitespace/line-wrapping formatter corrections matching the earlier real Spotless diagnostic. No product logic, assertions, control flow, provider behavior, formatter configuration, or CI behavior changed.

Additional source evidence:

- the five final-SHA files are byte-identical to PR #232 head `11d16ccbd2ecaf30032860f87807ba59b1085f97`;
- `email-provider/pom.xml` is unchanged from the remediation base, so no Spotless rule/exclusion weakening was introduced;
- `email-provider/.mvn/wrapper/maven-wrapper.properties` is unchanged and remains newline-corrected.

Historical real execution found 40 email-provider Spotless violations. The five observable fixes do not prove the complete baseline green.

Required eventual executable proof:

`./mvnw -B spotless:check`, `./mvnw -B clean test`, and `./mvnw -B package -DskipTests` from `email-provider`.

---

## PHR1-QA-004 — Backend regression remediation

- **Original severity:** High
- **Exact final SHA:** `5012fd426b4d2da50d5c8fe5c44cf3f15b5aebc3`
- **Source-remediation status:** **COMPLETE**
- **Executable-verification status:** **DEFERRED VERIFICATION / EXECUTION BLOCKED**
- **Final disposition:** **SOURCE CLOSED; OVERALL VERIFICATION DEFERRED**
- **Evidence classification:** SOURCE REVIEWED + historical worker execution
- **Owner:** B1 for source; External/Main for deferred execution

### GoogleProviderCampaignCompiler production fixes

PR #231 changed only one backend production file: `GoogleProviderCampaignCompiler.java`.

**Display provider-backed audience fix — accepted.**

The Display branch now avoids dereferencing `context.audienceDefinition()` when only provider-backed audience lists are present. The nullable `audienceDefinitionId` is passed through `ProviderCampaignPlanSupport.config(...)`, whose implementation deliberately omits null-valued pairs before `Map.copyOf`. Therefore the null dereference is not merely moved elsewhere.

Final source still contains the regression test `compilesDisplayTargetingWhenOnlyProviderAudienceListsAreSelected`, which builds a Display campaign with provider audience IDs and a null canonical `AudienceDefinition` and expects:

- `create-display-paused`
- `configure-display-targeting`
- `create-responsive-display-ad`

**Performance Max lifecycle placement — accepted.**

`configure-pmax-lifecycle` was removed from the Display branch and is emitted inside the `GOOGLE_PERFORMANCE_MAX` branch only when `pmaxLifecycleGoalType` is present.

Final test source contains a PMax lifecycle case with `pmaxLifecycleGoalType=NEW_CUSTOMER_ACQUISITION` expecting `configure-pmax-lifecycle` in the PMax operation sequence.

### Test/fixture remediation review

All 16 changed test files were independently patch-reviewed.

No new `@Disabled`, `@Ignore`, assumptions, skipped-test flags, `continue-on-error`, or equivalent test weakening was introduced.

The only removed assertions in PR #231 are replaced expectations:

- Reachlyo conversions `30 -> 100` and `3 -> 8`;
- Contacts custom-field type `SELECT -> SINGLE_SELECT`.

The Reachlyo corrections align with the test fixtures and production aggregator: exact-range rows contain conversions `60 + 40 = 100`, and daily fallback rows contain `3 + 5 = 8`. Production aggregation sums `AdInsightDaily.getConversions()`.

Other changed tests are fixture/current-contract repairs rather than assertion suppression, including current JDBC overloads, current provider resource-name calls, current Meta recommendation key, current Microsoft URI encoding, Java Time ObjectMapper support, database-derived retry timing, and removal of unreachable Mockito stubs.

### Flyway / Snowflake preservation

PR #231 did not modify the relevant hardening files. Their blobs are identical between remediation base and final SHA:

- `application.yaml`
- `DatabaseMigrationConfig.java`
- `PlatformMigrationHardeningTests.java`
- `SnowflakeIdGenerator.java`
- `.github/workflows/checks.yml`

Final source confirms:

- `FLYWAY_BASELINE_ON_MIGRATE` defaults to `false`;
- `DatabaseMigrationConfig.baselineOnMigrate(...)` defaults false;
- explicit baseline version/opt-in remains supported;
- the migration directory contains 80 migrations and the highest is `V83__contacts_audience_round2_security.sql`;
- `PlatformMigrationHardeningTests` still asserts clean -> V83, V79 -> V83, unmanaged non-empty fail-closed behavior, explicit known-V1 adoption, and Hibernate validation;
- production `SnowflakeIdGenerator.resolveNodeId()` still throws when `SNOWFLAKE_NODE_ID` is absent/blank;
- `SNOWFLAKE_NODE_ID=0` remains limited to backend/database canonical CI job environment configuration.

Historical B1 worker execution remains valid supporting evidence for the unchanged migration tests: `DatabaseMigrationConfigTests` 3/3 and `PlatformMigrationHardeningTests` 4/4 were observed. This is not final-SHA executable proof.

Required final-SHA backend execution remains deferred:

- `SNOWFLAKE_NODE_ID=0 ./mvnw -B test`
- `SNOWFLAKE_NODE_ID=0 ./mvnw -B clean test`
- `SNOWFLAKE_NODE_ID=0 ./mvnw -B package -DskipTests`
- `SNOWFLAKE_NODE_ID=0 ./mvnw -B -Dtest=DatabaseMigrationConfigTests,PlatformMigrationHardeningTests test`

---

## PHR1-QA-005 — Managed SMS tenant isolation

- **Original severity:** High
- **Exact final SHA:** `5012fd426b4d2da50d5c8fe5c44cf3f15b5aebc3`
- **Source-remediation status:** **PARTIAL — direct A -> B isolation is fixed, but mutation generation is incomplete**
- **Executable-verification status:** **DEFERRED VERIFICATION / EXECUTION BLOCKED**
- **Final disposition:** **STILL NEEDS CODE CHANGES**
- **Evidence classification:** SOURCE REVIEWED
- **Owner:** F1 / L1

### Source behavior that is correctly remediated

`ManagedSmsPanel.tsx` and `ManagedSmsSetupModal.tsx` now use `useLayoutEffect` to invalidate a prior company before passive effects run.

Load paths capture a monotonically changing `requestGeneration` and require both:

1. company identity is still current; and
2. captured request generation still equals the active generation.

That guard is applied to load success, error, and finally paths.

Source tests use delayed promises with same-mounted-component `company-a -> company-b` rerenders and cover:

- Panel stale success;
- Panel stale rejection;
- Setup stale load success;
- Setup stale load failure;
- Setup stale mutation success;
- Setup stale mutation failure.

PR #233 additionally prevents direct `A -> B` stale mutation completion from emitting success/error UI, calling `onChanged`, altering B's busy state, or issuing the second step of a create/submit flow.

### Residual defect

Setup mutations do **not** capture a tenant generation.

`withBusy(...)` currently captures only:

`requestCompanyId = companyId`

and defines currentness only as:

`requestCompanyId === currentCompanyId.current`

The load `requestGeneration` is not part of mutation validity.

Consequently an old mutation can be reactivated by an ABA navigation sequence:

1. company A starts `createDltProfile`;
2. switch to company B — the old A operation correctly becomes stale;
3. switch back to company A before the old create promise settles;
4. the old A promise resolves;
5. `isCurrentTenant()` becomes true again solely because the current company ID is A;
6. the stale flow can call `submitDltProfile`, emit a success snackbar, invoke `onChanged`, and run its follow-up load.

The same pattern applies to header/template/sender mutation completion paths.

This violates the frozen acceptance criterion: **a multi-step mutation must stop after the initiating tenant becomes stale**.

Existing `ManagedSmsTenantRace.test.tsx` covers `A -> B`, but has no `A -> B -> A` delayed-mutation regression.

PHR1-QA-005 therefore cannot be source-closed.

---

## PHR1-QA-006 — NEW: Managed SMS stale mutation can reactivate after A -> B -> A

- **Severity:** Medium
- **Affected scope:** `ManagedSmsSetupModal.tsx`; multi-step DLT/header/template/sender mutation lifecycle
- **Exact SHA:** `5012fd426b4d2da50d5c8fe5c44cf3f15b5aebc3`
- **Evidence classification:** SOURCE REVIEWED
- **Source-remediation status:** **NOT REMEDIATED**
- **Executable-verification status:** **NOT EXECUTED**
- **Final disposition:** **OPEN — CODE CHANGE REQUIRED**
- **Owner:** F1 / L1

Recommended targeted correction:

- maintain a dedicated tenant epoch/generation that increments on every committed company switch;
- capture both company ID and tenant epoch when a mutation begins;
- require both values to remain current before every post-await success/error/finally effect and before every later API call in a multi-step mutation;
- keep this tenant epoch separate from the load request generation if normal same-tenant reloads should not invalidate an in-flight mutation;
- add same-mounted-component delayed-promise tests for `A -> B -> A` covering at minimum:
  - old A success must not call the second API step;
  - old A success must not emit success/onChanged/follow-up load;
  - old A rejection must not emit an error;
  - current new-A operations remain usable.

No production code was modified by SEC/QA.

---

## PLAT-002 disposition

**OPEN / BLOCKED EXTERNAL / DEFERRED.**

The canonical workflow source remains fail-closed, but executable release evidence remains unavailable. Backend Spotless is deferred debt; email-provider Spotless is partial/deferred; zero-step jobs are not PASS.

Main may retain its decision to unblock normal product development from the external runner condition, but this report does not mark production release gates green.

## PLAT-003 disposition

**OPEN — SOURCE PROTECTIONS INTACT / FINAL INTEGRATED EXECUTION DEFERRED.**

Flyway safe defaults, explicit controlled adoption, V83 head, migration tests, Hibernate validation, and strict Snowflake production validation remain intact in final source.

Historical focused migration execution is supporting worker-level evidence only. Final SHA has no independent executable migration proof.

## Handoff to Main / L1

Targeted next action is limited to the residual Managed SMS mutation-generation gap:

- F1/L1: remediate PHR1-QA-005 / PHR1-QA-006 with a permanent tenant epoch check and add `A -> B -> A` regression coverage.
- External/Main: PHR1-QA-001 remains deferred.
- B1/Main: PHR1-QA-002 remains deferred formatting debt.
- B2/Main: PHR1-QA-003 remains partial/deferred formatting debt.
- PHR1-QA-004 requires no further source change from this review; only deferred executable proof remains.

After the Managed SMS residual fix is integrated, freeze a new exact production-code SHA and perform a narrow final SEC/QA source re-verification of that fix. Do not start Platform Hardening Round 2 from this report.
