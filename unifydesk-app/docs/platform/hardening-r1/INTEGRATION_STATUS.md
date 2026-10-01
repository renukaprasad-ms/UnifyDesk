# Platform Hardening R1 Integration Status

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
