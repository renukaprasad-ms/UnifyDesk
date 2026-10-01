# B2 Chat Prompt — Platform Hardening Round 1

You are B2 for UnifyDesk Platform Hardening Round 1.

Repository:

`renukaprasad-ms/UnifyDesk`

Your exact frozen worker base SHA is:

`c541899f238dbe6f70f9b91f5c513fa1b10f5641`

Create:

`fix/platform-ci-infrastructure-r1`

from exactly that SHA.

Do not branch from moving `dev`. Do not rebase unless L1 explicitly instructs you.

After the Lead planning PR is merged, read these documents from current `dev` before creating your worker branch:

- `unifydesk-app/docs/platform/hardening-r1/README.md`
- `unifydesk-app/docs/platform/hardening-r1/CI-INVESTIGATION.md`
- `unifydesk-app/docs/platform/hardening-r1/CHECK-MATRIX.md`
- `unifydesk-app/docs/platform/hardening-r1/ROUND1_TASKS.md`

## Scope

You own CI/repository verification infrastructure, email-provider verification bootstrap and website deterministic install.

### 1. Canonical Checks workflow

Refactor `.github/workflows/checks.yml` into the canonical current-head workflow.

Exact job names:

- `backend`
- `database`
- `frontend`
- `email-provider`
- `website`

Trigger on PRs to `dev` and useful pushes to `dev`. Add `workflow_dispatch` if useful.

Use:

- Temurin Java 25;
- Node 24;
- safe dependency caching;
- `unifydesk-app/compose.test.yml` for PostgreSQL/Redis;
- backend env `SNOWFLAKE_NODE_ID=0`.

Do not add Kafka unless an actually executed normal-matrix test requires it.

Required gates must fail on failure. No `continue-on-error`.

### 2. Backend job contract

Canonical CI may avoid redundant Maven execution:

- `./mvnw -B spotless:check`
- `./mvnw -B clean test`
- `./mvnw -B package -DskipTests`

B1 owns backend fixes. Your job is to make CI invoke the contract correctly and expose failures truthfully.

### 3. Database job

B1 will add the current-head platform migration/schema-validation test.

Wire a separate `database` job that executes that focused test with deterministic PostgreSQL dependencies.

Do not duplicate historical V66/V79-as-final assertions.

Because workers are independent, your branch may reference the agreed test class/path contract in the workflow even before B1 is integrated; L1 will validate final integration.

### 4. Frontend job

Run separate visible steps:

- `npm ci`
- `npm test`
- `npm run build`
- `npm run lint`
- `npm run format:check`

F1 owns frontend fixes.

### 5. Email provider

Current failure occurs before Maven:

`cannot read distributionUrl property in ./.mvn/wrapper/maven-wrapper.properties`

The last `distributionUrl` line has no final newline and the POSIX wrapper does not process an unterminated final read.

Apply the smallest safe repository fix: make the properties file newline-terminated or regenerate the Maven wrapper consistently.

Then run:

- `./mvnw -B spotless:check`
- `./mvnw -B clean test`
- `./mvnw -B package -DskipTests`

Fix only build/verification infrastructure defects exposed in `email-provider`; do not redesign its product behavior.

### 6. Website

Current workflow references a missing `unifydesk-website/package-lock.json`.

Standardize this project on npm for this round:

- generate/commit a valid `package-lock.json` from current `package.json`;
- use `npm ci`;
- run `npm run build`;
- cache using the committed lockfile.

Do not change website content/design unless build correctness strictly requires a tiny compatibility fix.

### 7. Historical QA workflow scoping

Inspect all workflows.

In particular, historical WhatsApp R2/R3 QA workflows currently trigger on general PRs to `dev`.

They are not canonical current-head checks:

- R2 uses `continue-on-error` around required commands;
- R3 contains historical feature/migration expectations that can be stale at V83.

Remove the general PR-to-`dev` trigger from historical feature QA workflows. Preserve their historical review-branch/manual usability where appropriate.

Do not delete historical QA evidence.

### 8. Historical zero-step root cause

Do not create a fake repository fix for the September 29 zero-step condition.

Record that current hosted runners execute and that the exact account billing/quota subtype requires Main to confirm in GitHub Billing & licensing.

### Restrictions

Do not edit backend product/business logic.
Do not edit frontend product code.
Do not weaken tests.
Do not add fake-success jobs.

## Deliverable

Open a PR to `dev`.

Report:

- branch/base/final SHA;
- workflow changes;
- exact check names;
- email-provider result;
- website result;
- current Actions run IDs;
- runner IDs/actual step execution;
- failures honestly classified.

Do not merge your own PR.
