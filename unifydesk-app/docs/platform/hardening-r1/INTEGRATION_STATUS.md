# Platform Hardening R1 Integration Status

> Current authoritative Round-1 state is the **final targeted-remediation integration** section at the end of this file. Earlier sections are preserved as historical integration evidence.

## Frozen lineage

Starting `dev` / frozen worker base:

`c541899f238dbe6f70f9b91f5c513fa1b10f5641`

Lead planning PR: #222

Frozen integrated **production-code** SHA:

`7fe25fe73eca72b57172d025d944b6ed54ce196f`

This SHA is the SEC/QA production-code base even if later documentation-only commits move `dev`.

## Worker review and integration

| Worker | PR | Reviewed worker head | L1 decision | Merge commit |
|---|---:|---|---|---|
| B1 | #225 | `3e9f56f1c0f13c8116f97e6ede10a1f2770999fd` | APPROVED / integrated first | `9381ebe49e30e46b914310a01b6bbe645b5e2cb0` |
| F1 | #223 | `3a0a5f3b350216cea3bde682adefd8a94c628c17` | APPROVED / integrated second | `8442d585b25ae439f1a33f250fdcd64c89a75f4e` |
| B2 | #224 | `db197237182a61617e19a2a17c034da06d7b404e` | APPROVED / integrated last | `7fe25fe73eca72b57172d025d944b6ed54ce196f` |

All three worker heads are second parents in the recorded merge lineage; L1 did not rebase a worker onto moving `dev`.

### B1 review

Scope compliant: backend/Flyway/migration tests plus the legacy-adoption runbook only.

Accepted behavior:

- `application.yaml` default changed to `FLYWAY_BASELINE_ON_MIGRATE=false`;
- custom `DatabaseMigrationConfig` fallback changed from true to false;
- explicit true remains supported;
- current-head test covers clean -> V83;
- representative V79 -> V83;
- unmanaged real V1-equivalent schema fails closed by default;
- explicit baseline version 1 can adopt the known-compatible V1-equivalent schema;
- Hibernate validates after current migration;
- historical Managed SMS V66 and WhatsApp V79 migration tests are explicitly pinned to their historical heads instead of accidentally treating V83 as their final version.

B1 exact-head executable evidence includes `PlatformMigrationHardeningTests` 4/4 and `DatabaseMigrationConfigTests` 3/3 inside GitHub-hosted execution. Historical `WhatsApp Round 2 QA` uses `continue-on-error`, so its overall green job is **not** accepted as a whole-suite PASS; only the actually observed individual test results are evidence.

### F1 review

Scope compliant: frontend only.

The large 216-file diff was decomposed during Lead review:

- 39 files contained substantive test/lint/refactor corrections before formatting;
- one separate commit applied the canonical Prettier baseline across 184 frontend files;
- the temporary formatting extractor was removed before final head.

Reviewed substantive changes include explicit RTL cleanup between tests, stale grouped-notification assertion updates, async waits, reducer request-scope setup, hook/ref timing fixes, Fast Refresh component/helper separation, safe route control-character validation, and behavior-preserving helper extraction.

No UI/product redesign or backend/workflow changes were found.

Final exact-head canonical run `36843368908` produced jobs without runner steps. F1 final-head acceptance remains **EXECUTION BLOCKED**, not PASS.

### B2 review

Scope compliant: five files only.

Accepted behavior:

- canonical `Checks` jobs are exactly `backend`, `database`, `frontend`, `email-provider`, `website`;
- Java 25 / Node 24;
- `SNOWFLAKE_NODE_ID=0` is set only inside backend/database CI test jobs;
- required gates are fail-closed with no `continue-on-error`;
- PostgreSQL/Redis lifecycle is deterministic;
- historical WhatsApp R2/R3 workflows no longer run as general PR-to-`dev` release checks;
- email-provider Maven wrapper properties is newline-terminated;
- website has a committed npm lockfile and uses `npm ci`.

B2 exact-head run `36834015519` proves the five canonical jobs can receive real runners. Website job `110276953115` executed `npm ci` and build successfully.

## Frozen integrated canonical evidence

Canonical workflow run:

`36846527232`

Exact head:

`7fe25fe73eca72b57172d025d944b6ed54ce196f`

### Attempt 1

All five jobs were created but received no runner steps:

| Job | Job ID | Evidence |
|---|---:|---|
| database | `110317868006` | EXECUTION BLOCKED — `steps=null` |
| email-provider | `110317868227` | EXECUTION BLOCKED — `steps=null` |
| website | `110317868232` | EXECUTION BLOCKED — `steps=null` |
| backend | `110317868431` | EXECUTION BLOCKED — `steps=null` |
| frontend | `110317868660` | EXECUTION BLOCKED — `steps=null` |

### Attempt 2

L1 explicitly reran failed jobs. GitHub again completed without allocating runners:

| Job | Job ID | Evidence |
|---|---:|---|
| backend | `110318001966` | EXECUTION BLOCKED — `steps=null` |
| frontend | `110318002110` | EXECUTION BLOCKED — `steps=null` |
| database | `110318002151` | EXECUTION BLOCKED — `steps=null` |
| email-provider | `110318002167` | EXECUTION BLOCKED — `steps=null` |
| website | `110318002239` | EXECUTION BLOCKED — `steps=null` |

### Attempt 3

L1 issued a fresh failed-job rerun during final handoff review. GitHub again completed every canonical job without allocating a runner:

| Job | Job ID | Evidence |
|---|---:|---|
| email-provider | `110321916569` | EXECUTION BLOCKED — `runner_id=0`, `steps=[]` |
| database | `110321916839` | EXECUTION BLOCKED — `runner_id=0`, `steps=[]` |
| website | `110321916863` | EXECUTION BLOCKED — `runner_id=0`, `steps=[]` |
| backend | `110321916920` | EXECUTION BLOCKED — `runner_id=0`, `steps=[]` |
| frontend | `110321916934` | EXECUTION BLOCKED — `runner_id=0`, `steps=[]` |

No result from any attempt of run `36846527232` is a PASS.

The recurring zero-step behavior remains an external GitHub/account/runner-allocation issue. Main still needs to confirm Actions usage/budget/payment status in GitHub Billing & licensing.

## Known required-gate findings from executable worker evidence

These are not hidden by the integrated runner outage.

### Backend formatting

B2 run `36834015519`, backend job `110276953129` executed `./mvnw -B spotless:check`:

- 1869 Java files inspected;
- 809 require formatting;
- BUILD FAILURE.

B1 later reported 810 formatting violations on its own head after adding hardening source/tests.

The integrated source contains the B1 changes and no repository-wide backend formatting repair. Therefore backend Spotless remains a known Round-1 finding until executable remediation proves otherwise.

### Email-provider formatting

B2 run `36834015519`, email-provider job `110276953077` executed Spotless:

- 190 Java files inspected;
- 40 require formatting;
- BUILD FAILURE.

The Maven wrapper bootstrap defect itself is fixed, but the format gate remains a known finding.

### Frontend

F1 corrected the known test/lint/format issues in source, but the exact F1 head and integrated SHA both hit the external zero-step runner condition. Final acceptance is **EXECUTION BLOCKED**.

### Database / Flyway

Source review and B1 exact-head executed focused tests support the safe Flyway behavior, but the canonical `database` job on the exact integrated SHA is **EXECUTION BLOCKED**.

PLAT-003 therefore remains open pending independent integrated-SHA execution/SEC-QA evidence.

## Required backend explicit matrix

For the exact frozen SHA, SEC/QA must account for all four commands with `SNOWFLAKE_NODE_ID=0` and deterministic PostgreSQL/Redis:

- `./mvnw -B spotless:check`
- `./mvnw -B test`
- `./mvnw -B clean test`
- `./mvnw -B package -DskipTests`

At this integration freeze none of those commands has executable evidence on the exact frozen SHA because run `36846527232` never received a runner.

## Prepared targeted remediation

L1 prepared, but did not start:

- [B1 integration remediation](BACKEND_B1_R1_INTEGRATION_REMEDIATION_CHAT_PROMPT.md) — backend Spotless baseline plus environment-correct full matrix and classification;
- [B2 integration remediation](BACKEND_B2_R1_INTEGRATION_REMEDIATION_CHAT_PROMPT.md) — email-provider Spotless baseline plus its complete verification matrix.

No F1 remediation branch is justified from final-head evidence yet because its remaining state is external EXECUTION BLOCKED rather than a reproduced source failure.

## SEC/QA handoff

SEC/QA prompt:

`unifydesk-app/docs/platform/hardening-r1/SECURITY_QA_R1_CHAT_PROMPT.md`

SEC/QA must branch from exactly:

`7fe25fe73eca72b57172d025d944b6ed54ce196f`

not from later moving `dev`.

## Risk-state rules

- PLAT-002 remains OPEN: canonical workflow source is integrated, but the exact integrated run is zero-step and known format gates remain red in executable worker evidence.
- PLAT-003 remains OPEN: safe defaults are implemented and focused migration tests executed on B1, but exact integrated-SHA database execution is blocked.


## Independent SEC/QA Round-1 result

QA PR:

`#228`

QA branch/head:

`review/platform-hardening-security-qa-r1@0bf0965b2fade910bea9d206e8277c041d81f0d6`

Reviewed production-code SHA:

`7fe25fe73eca72b57172d025d944b6ed54ce196f`

QA result:

**NOT PASS — targeted remediation required**

Frozen findings:

| Finding | Owner | Status |
|---|---|---|
| PHR1-QA-001 | External | BLOCKED EXTERNAL / EXECUTION BLOCKED |
| PHR1-QA-002 | B1 | remediation required |
| PHR1-QA-003 | B2 | remediation required |
| PHR1-QA-004 | B1 | remediation required |
| PHR1-QA-005 | F1 | remediation required |

QA PR #228 merged into `dev` as:

`677dea286bfe45b2172872442125f2faf4ce7953`

That exact commit is frozen as the common targeted-remediation worker base.

### Targeted remediation workers

| Worker | Findings | Branch | Frozen base | State |
|---|---|---|---|---|
| B1 | PHR1-QA-002, PHR1-QA-004 | `fix/platform-backend-remediation-r1` | `677dea286bfe45b2172872442125f2faf4ce7953` | READY |
| B2 | PHR1-QA-003 | `fix/platform-email-provider-remediation-r1` | `677dea286bfe45b2172872442125f2faf4ce7953` | READY |
| F1 | PHR1-QA-005 | `fix/platform-managed-sms-tenant-race-r1` | `677dea286bfe45b2172872442125f2faf4ce7953` | READY |

All three workers are independent and must not rebase onto later moving `dev`.

L1 planned integration order:

1. B1;
2. F1;
3. B2.

After all three are reviewed and integrated, L1 must freeze a NEW exact integrated production-code SHA. SEC/QA must then independently reverify that exact SHA.

PHR1-QA-001 remains external. Do not close it unless actual runner allocation is restored and exact-SHA jobs execute.


## Final targeted-remediation integration — authoritative current state

Main explicitly deferred the external GitHub Actions runner-allocation problem for this round. No additional runner reruns are required for this integration decision.

### Final remediation lineage

Common remediation worker base:

`677dea286bfe45b2172872442125f2faf4ce7953`

| Order | Scope | PR | Reviewed head | Integration decision | Merge commit |
|---:|---|---:|---|---|---|
| 1 | B1 — PHR1-QA-002 / PHR1-QA-004 | #231 | `8c4f6bfd37e457a44f7d120dfff330730083e387` | APPROVED for semantic/test remediation; formatting proof deferred | `b833aa739138f423463c8015dfdecc335874b922` |
| 2 | F1 — PHR1-QA-005 | #230 | `09beabd0b7170b27c8b8b4fae8352e687705fd10` | APPROVED core load-generation remediation | `a56fbb0eb2fde1a599f287f50f72be220beafdcb` |
| 2a | L1 integration-only completion for PHR1-QA-005 | #233 | `cd6e50aa127189251677278f9c5202508ebfaa32` | APPROVED; closes stale setup-mutation completion gap | `e5856cfb61c7613427713f73dc8fd6014768538d` |
| 3 | B2 — PHR1-QA-003 | #232 | `11d16ccbd2ecaf30032860f87807ba59b1085f97` | APPROVED five exact evidence-backed formatter corrections; full baseline proof deferred | `5012fd426b4d2da50d5c8fe5c44cf3f15b5aebc3` |

### New frozen production-code SHA

Final Round-1 remediated production-code SHA:

`5012fd426b4d2da50d5c8fe5c44cf3f15b5aebc3`

This is the exact base for final independent SEC/QA re-verification.

Later documentation-only commits do not change this frozen production-code base.

### B1 review disposition

PR #231 changed one production file plus targeted tests/fixtures only.

Accepted production fixes in `GoogleProviderCampaignCompiler`:

1. Display provider-audience-list targeting no longer dereferences a null canonical `AudienceDefinition`.
2. `configure-pmax-lifecycle` is emitted from the Performance Max branch rather than the Display branch.

The test corrections were reviewed against current production contracts. They update stale enum/key/URI/metric expectations, repair missing mocks/current JDBC overload fixtures, and remove historical fixture defects rather than suppressing assertions.

No Flyway migration, `DatabaseMigrationConfig`, `application.yaml`, canonical CI workflow, or `SnowflakeIdGenerator` production validation was changed by #231.

PHR1-QA-004 source remediation is therefore accepted.

The full environment-correct backend matrix did not execute because the available GitHub runner path remained externally blocked. That verification debt remains explicit.

PHR1-QA-002 repository-wide Spotless is NOT claimed green. The broad ~810-file mechanical formatting operation was intentionally not fabricated without an executable formatter environment.

### F1 / L1 review disposition

PR #230 correctly moves company-switch invalidation into layout phase and adds request-generation checks for Managed SMS loads.

L1 review identified one narrow remaining integration gap in `ManagedSmsSetupModal`: a company-A mutation already in flight could still reach unguarded post-mutation `onChanged`, snackbar, or `finally setBusy(false)` paths after company B committed.

PR #233 completed that same finding by:

- synchronously clearing stale busy state on tenant switch;
- scoping setup mutation completion to the initiating company;
- preventing stale success/error/finally UI effects;
- stopping later create/submit calls when the initiating tenant becomes stale;
- adding delayed-promise component tests for stale mutation success and rejection.

No Managed SMS UI redesign was introduced.

PHR1-QA-005 source remediation is accepted. Frontend executable verification remains deferred if no execution environment is available.

### B2 review disposition

PR #232 was reviewed while draft, then marked ready by L1.

Net diff is five email-provider Java files and is mechanical formatting only. The exact changes match prior real Spotless diagnostic output.

No formatter rules, exclusions, Maven Wrapper behavior, CI workflow, product behavior, backend/frontend/website code, or migrations changed.

PHR1-QA-003 is NOT claimed fully green. Only the recoverable five formatter corrections are integrated; the complete email-provider Spotless/test/package matrix remains deferred verification debt.

## Final finding status before final SEC/QA

| Finding | Current L1 status | Meaning |
|---|---|---|
| PHR1-QA-001 | **BLOCKED EXTERNAL / DEFERRED** | Main explicitly deferred GitHub runner allocation. Zero-step jobs are not PASS. |
| PHR1-QA-002 | **DEFERRED VERIFICATION / FORMATTING DEBT** | Backend Spotless baseline not proven green; does not block normal product development under Main's decision. |
| PHR1-QA-003 | **SOURCE REMEDIATION PARTIAL / DEFERRED VERIFICATION / FORMATTING DEBT** | Five exact formatter fixes integrated; full email-provider formatter/test/package proof unavailable. |
| PHR1-QA-004 | **SOURCE REMEDIATION COMPLETE / EXECUTABLE VERIFICATION DEFERRED** | All historical failures classified; 19 stale/fixture issues + 2 production compiler regressions corrected; canonical backend rerun unavailable. |
| PHR1-QA-005 | **SOURCE REMEDIATION COMPLETE / EXECUTABLE VERIFICATION DEFERRED** | Tenant-switch load and mutation completion paths are source-remediated with component race tests; frontend matrix not claimed executed. |

## Final SEC/QA handoff

Prompt:

`unifydesk-app/docs/platform/hardening-r1/SECURITY_QA_R1_FINAL_CHAT_PROMPT.md`

SEC/QA must branch from exactly:

`5012fd426b4d2da50d5c8fe5c44cf3f15b5aebc3`

Expected review emphasis:

- independent SOURCE REVIEWED validation of B1/F1/B2 remediation;
- use executable evidence only where a command actually runs;
- retain PHR1-QA-001 as BLOCKED EXTERNAL / DEFERRED;
- retain backend/email-provider Spotless debt unless actually proven;
- do not falsely close PLAT-002;
- preserve PLAT-003 integrated-execution limitation unless the final frozen SHA is actually executed.

Main's deferral unblocks normal UnifyDesk product development after final SEC/QA source re-verification; it does not convert deferred production-release evidence into PASS.


## Narrow tenant-epoch remediation after final SEC/QA

Final SEC/QA PR #235 reviewed:

`5012fd426b4d2da50d5c8fe5c44cf3f15b5aebc3`

and found one residual source issue:

- PHR1-QA-005 remained partially open;
- new PHR1-QA-006 identified A -> B -> A mutation reactivation because setup mutation currentness was company-ID-only.

QA PR #235 merged into `dev` as:

`20db8a07f28a6611d2b448d8273575dd736a0d7a`

That exact commit was frozen as the sole base for the final narrow remediation.

### Final narrow remediation

Branch:

`fix/platform-managed-sms-tenant-epoch-r1`

PR:

`#236`

Worker/L1 head:

`b0f58a29e3822f47d18e079eeaeb97e02efc3922`

Integration merge / NEW frozen production-code SHA:

`d592da3b7b09f91a80b8029db5dc7500e1d6ba52`

Scope was limited to:

- `ManagedSmsSetupModal.tsx`
- `ManagedSmsTenantRace.test.tsx`

Accepted design:

- dedicated `tenantEpoch` separate from ordinary `requestGeneration`;
- epoch increments on every committed company transition;
- setup mutation captures initiating company ID + epoch;
- mutation currentness requires BOTH company ID and epoch;
- multi-step profile/header/template flows retain checks before later API operations;
- shared `withBusy` protects stale success/error/`onChanged`/follow-up load/finally;
- sender and refresh flows inherit the same epoch-scoped completion protection;
- no UI/backend/provider/CI changes.

Regression coverage now includes same-mounted-component A1 -> B -> A2 delayed mutation success/rejection and proves a NEW A2 mutation remains usable.

### Current status before narrow final SEC/QA

- PHR1-QA-001: BLOCKED EXTERNAL / DEFERRED
- PHR1-QA-002: DEFERRED VERIFICATION / FORMATTING DEBT
- PHR1-QA-003: SOURCE REMEDIATION PARTIAL / DEFERRED VERIFICATION / FORMATTING DEBT
- PHR1-QA-004: SOURCE REMEDIATION COMPLETE / EXECUTABLE VERIFICATION DEFERRED
- PHR1-QA-005: SOURCE REMEDIATED BY #236 / PENDING NARROW INDEPENDENT QA
- PHR1-QA-006: SOURCE REMEDIATED BY #236 / PENDING NARROW INDEPENDENT QA

Narrow final SEC/QA prompt:

`unifydesk-app/docs/platform/hardening-r1/SECURITY_QA_R1_TENANT_EPOCH_FINAL_CHAT_PROMPT.md`

SEC/QA must branch from exactly:

`d592da3b7b09f91a80b8029db5dc7500e1d6ba52`

If independent source review finds no further issue, PHR1-QA-005 and PHR1-QA-006 may be marked SOURCE REMEDIATION COMPLETE and Round 1 may be classified:

**SOURCE COMPLETE WITH DEFERRED EXECUTABLE/FORMATTING VERIFICATION**
