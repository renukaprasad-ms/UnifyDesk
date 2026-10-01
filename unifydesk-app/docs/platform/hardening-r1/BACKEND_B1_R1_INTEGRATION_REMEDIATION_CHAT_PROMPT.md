# B1 Integration Remediation Prompt — Platform Hardening R1

You are B1 for the targeted UnifyDesk Platform Hardening Round-1 remediation.

Repository:

`renukaprasad-ms/UnifyDesk`

Independent QA findings owned by you:

- `PHR1-QA-002` — High — backend Spotless baseline is red.
- `PHR1-QA-004` — High — backend regression matrix must be rerun with the canonical test environment and every remaining failure classified.

## Exact frozen remediation base

Create:

`fix/platform-backend-remediation-r1`

from exactly:

`677dea286bfe45b2172872442125f2faf4ce7953`

Do NOT branch from later moving `dev`.
Do NOT rebase.

After the remediation-plan documentation is merged, read from current `dev`:

- `unifydesk-app/docs/platform/hardening-r1/SECURITY_QA_R1_FINDINGS.md`
- `unifydesk-app/docs/platform/hardening-r1/REMEDIATION_R1.md`
- `unifydesk-app/docs/platform/hardening-r1/CHECK-MATRIX.md`
- `unifydesk-app/docs/platform/hardening-r1/LEGACY-DATABASE-ADOPTION.md`
- this prompt.

## Scope

Own ONLY `PHR1-QA-002` and `PHR1-QA-004`.

### 1. Restore backend Spotless baseline

Current evidence indicates roughly 810 Java files require the repository formatter.

Use the existing Spotless configuration.

Do NOT:

- disable Spotless;
- add exclusions to avoid existing files;
- change formatter rules merely to reduce the diff;
- manually bypass the formatter gate.

Apply the formatter mechanically.

Because this is a large diff:

- put the broad mechanical formatting change in its own commit where practical;
- put semantic/test corrections in later separate commits;
- inspect the formatter diff for accidental non-format source modifications;
- use `git diff --check` and review suspicious large rewrites before proceeding.

Formatting volume itself is not permission to refactor product code.

### 2. Canonical backend test environment

Use deterministic PostgreSQL/Redis from:

`unifydesk-app/compose.test.yml`

and set:

`SNOWFLAKE_NODE_ID=0`

for backend test execution.

Do NOT weaken production `SnowflakeIdGenerator.resolveNodeId()`. Production must still require an explicit valid node ID.

### 3. Execute the backend matrix independently

From the repository root:

```bash
docker compose -f unifydesk-app/compose.test.yml up -d --wait
cd unifydesk-app/backend

SNOWFLAKE_NODE_ID=0 ./mvnw -B spotless:check
SNOWFLAKE_NODE_ID=0 ./mvnw -B test
SNOWFLAKE_NODE_ID=0 ./mvnw -B clean test
SNOWFLAKE_NODE_ID=0 ./mvnw -B package -DskipTests
```

Do not stop at the first command if your environment permits running the remaining commands independently.

### 4. Classify every remaining failure before fixing it

Use exactly these categories:

- production regression;
- stale/broken test;
- environment/configuration defect;
- cascade from another failure.

Record the test class/method and reason for the classification.

Fix only what is justified by current implementation/contracts.

Do not:

- weaken assertions;
- broadly mock away behavior;
- skip failing tests;
- convert failures to warnings;
- redesign unrelated Contacts, Ads, Microsoft, WhatsApp, Notifications, Managed Email/SMS, Inbox, Broadcast or other product modules.

### 5. Preserve Round-1 Flyway hardening

The following must remain intact:

- normal `baseline-on-migrate=false`;
- `DatabaseMigrationConfig` fallback false;
- explicit controlled baseline opt-in;
- unmanaged/non-empty database fail-closed behavior;
- clean database -> V83;
- representative V79 -> V83;
- Hibernate schema validation;
- conservative legacy-adoption runbook.

After semantic fixes, explicitly run:

```bash
SNOWFLAKE_NODE_ID=0 ./mvnw -B -Dtest=DatabaseMigrationConfigTests,PlatformMigrationHardeningTests test
```

with the deterministic PostgreSQL test service available.

## Restrictions

Do not edit:

- `unifydesk-app/frontend/**`
- `email-provider/**`
- `unifydesk-website/**`
- `.github/workflows/**`

unless L1 explicitly changes ownership.

Do not merge your own PR.

## Acceptance

Required evidence:

- Spotless executed successfully;
- `test` executed;
- `clean test` executed;
- `package -DskipTests` executed;
- Flyway hardening tests executed successfully;
- all remaining failures, if any, listed and classified honestly;
- no formatter/test rule weakening;
- no production Snowflake validation weakening.

## Deliverable

Open a PR to `dev`.

Report:

- branch;
- exact base SHA;
- final SHA;
- formatter-only commit SHA;
- semantic-fix commit SHA(s), if any;
- total files changed by formatting;
- every backend failing test originally reproduced after `SNOWFLAKE_NODE_ID=0`;
- classification and correction for each;
- Spotless/test/clean-test/package results;
- Flyway focused verification result;
- any remaining EXECUTION BLOCKED / BLOCKED EXTERNAL item.

Do not merge independently.
