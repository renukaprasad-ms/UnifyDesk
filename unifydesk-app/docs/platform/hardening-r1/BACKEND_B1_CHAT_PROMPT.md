# B1 Chat Prompt — Platform Hardening Round 1

You are B1 for UnifyDesk Platform Hardening Round 1.

Repository:

`renukaprasad-ms/UnifyDesk`

Your exact frozen worker base SHA is:

`c541899f238dbe6f70f9b91f5c513fa1b10f5641`

Create:

`fix/platform-backend-db-hardening-r1`

from exactly that SHA.

Do not branch from moving `dev`. Do not rebase unless L1 explicitly instructs you.

After the Lead planning PR is merged, read these documents from current `dev` before creating your worker branch:

- `unifydesk-app/docs/platform/README.md`
- `unifydesk-app/docs/platform/PRODUCTION-READINESS.md`
- `unifydesk-app/docs/platform/RISK-REGISTER.md`
- `unifydesk-app/docs/platform/hardening-r1/README.md`
- `unifydesk-app/docs/platform/hardening-r1/CI-INVESTIGATION.md`
- `unifydesk-app/docs/platform/hardening-r1/CHECK-MATRIX.md`
- `unifydesk-app/docs/platform/hardening-r1/ROUND1_TASKS.md`

## Scope

You own backend/database verification only.

### 1. Flyway fail-closed default

Current source has two unsafe defaults:

- `application.yaml`: `FLYWAY_BASELINE_ON_MIGRATE:true`;
- `DatabaseMigrationConfig`: Environment fallback `true`.

Make the smallest production-safe correction:

- normal default = false in both places;
- explicit true still supported;
- do not silently baseline a non-empty unmanaged database;
- no migration is skipped accidentally.

Add focused tests that prove:

- default false;
- explicit true;
- unmanaged/non-empty schema fails closed by default.

### 2. Safe intentional legacy adoption

Document and test an intentional adoption path.

Use a real schema state, not a hand-written fake.

Recommended test technique:

1. create isolated PostgreSQL database/schema;
2. run real Flyway only through V1;
3. remove Flyway history to simulate a legacy V1-equivalent unmanaged schema;
4. prove default baseline=false refuses silent adoption;
5. separately prove explicit baseline=true with an explicitly chosen baseline version can adopt that known-compatible schema and migrate forward.

Do not claim arbitrary legacy schemas are safe to baseline.

Add/update a runbook under:

`unifydesk-app/docs/platform/hardening-r1/`

### 3. Current-head migration verification

Current migration head is V83.

Create a dedicated platform migration test owned by this round. Do not reuse historical tests that assert V66 or V79 is the final version.

Required:

- clean database -> V83;
- representative V79 -> V83 upgrade using real migrations;
- Hibernate schema validation after migration.

Use the existing PostgreSQL test service at `127.0.0.1:55432`.

### 4. Backend canonical environment

For test execution use:

`SNOWFLAKE_NODE_ID=0`

The current canonical workflow omits it and many tests cascade through `IdGenerator` static initialization.

Do not weaken `SnowflakeIdGenerator` production requirement merely to make tests pass.

### 5. Backend formatting and regression baseline

Execute:

```bash
docker compose -f unifydesk-app/compose.test.yml up -d --wait
cd unifydesk-app/backend

SNOWFLAKE_NODE_ID=0 ./mvnw -B spotless:check
SNOWFLAKE_NODE_ID=0 ./mvnw -B test
SNOWFLAKE_NODE_ID=0 ./mvnw -B clean test
SNOWFLAKE_NODE_ID=0 ./mvnw -B package -DskipTests
```

Current evidence says Spotless reports 809 Java files needing formatting.

Apply the repository formatter rather than disabling/excluding the rule. Keep formatting in a separate commit where practical.

After the Snowflake environment is correct, classify remaining failures:

- stale/broken test;
- real implementation regression;
- test-environment problem.

Make only minimal backend corrections needed for current intended contracts. Do not redesign Managed Email, SMS, WhatsApp, Ads, Notifications, Contacts, Inbox or other features in this round.

### 6. Restrictions

Do not edit:

- `.github/workflows/**`
- `unifydesk-app/frontend/**`
- `email-provider/**`
- `unifydesk-website/**`

Do not use `continue-on-error`.
Do not skip tests.
Do not change assertions merely to obtain green output without establishing the intended contract.

## Deliverable

Open a PR to `dev`.

Report:

- branch;
- exact starting SHA;
- final SHA;
- files changed;
- Flyway default changes;
- legacy adoption procedure;
- clean migration evidence;
- representative upgrade evidence;
- Hibernate validation evidence;
- Spotless/test/clean-test/package results;
- every remaining EXECUTION BLOCKED item.

Do not merge your own PR.
