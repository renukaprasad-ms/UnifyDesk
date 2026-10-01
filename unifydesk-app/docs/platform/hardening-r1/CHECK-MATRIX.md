# Canonical Verification Matrix

Frozen worker base:

`c541899f238dbe6f70f9b91f5c513fa1b10f5641`

## Canonical GitHub workflow

Workflow name: **Checks**

Recommended eventual required job/check names:

- `backend`
- `database`
- `frontend`
- `email-provider`
- `website`

Do not configure them as required branch checks until an integrated SHA demonstrates that each job receives a runner and executes successfully.

## Deterministic toolchain

- runner: GitHub-hosted `ubuntu-latest` for this round;
- Java: Temurin 25;
- Node: 24;
- npm: version supplied with the pinned Node 24 runner unless the repository later pins npm explicitly;
- backend test dependencies: `unifydesk-app/compose.test.yml` PostgreSQL 18.6 and Redis 8.10.1;
- backend test environment: `SNOWFLAKE_NODE_ID=0`.

Do not start Kafka merely because production uses Kafka. Add a Kafka test service only if an executed normal-matrix test proves it is required.

## Backend job

Working directory:

`unifydesk-app/backend`

Canonical CI proof can avoid redundant Maven work while preserving guarantees:

1. `./mvnw -B spotless:check`
2. `./mvnw -B clean test`
3. `./mvnw -B package -DskipTests`

`clean test` proves clean compilation plus unit/integration tests in the normal Surefire matrix. Packaging remains separate evidence.

For final L1/SEC-QA evidence, also execute the explicit requested matrix once:

- `./mvnw -B spotless:check`
- `./mvnw -B test`
- `./mvnw -B clean test`
- `./mvnw -B package -DskipTests`

All backend test/package commands run with `SNOWFLAKE_NODE_ID=0` and deterministic PostgreSQL/Redis dependencies.

## Database job

Working directory:

`unifydesk-app/backend`

B1 must create a dedicated current-head platform migration test rather than reusing historical tests that assert V66/V79 as the final migration.

Required executable cases:

1. clean PostgreSQL database -> current migration head **V83**;
2. representative **V79 -> V83** upgrade, using real migrations rather than fabricated schema;
3. Hibernate `ddl-auto=validate` against the migrated current schema;
4. fail-closed unmanaged/non-empty database when baseline is not explicitly enabled;
5. explicit controlled legacy adoption remains possible when baseline is deliberately enabled with an explicitly chosen baseline version.

Normal CI must not silently baseline a non-empty unmanaged schema.

## Frontend job

Working directory:

`unifydesk-app/frontend`

Run as separate named steps so failures are visible:

1. `npm ci`
2. `npm test`
3. `npm run build`
4. `npm run lint`
5. `npm run format:check`

No `continue-on-error`.

## Email-provider job

Working directory:

`email-provider`

Run:

1. `./mvnw -B spotless:check`
2. `./mvnw -B clean test`
3. `./mvnw -B package -DskipTests`

Fix the Maven Wrapper EOF/newline issue first. Do not replace the wrapper with a system Maven dependency merely to bypass the repository wrapper.

## Website job

Working directory:

`unifydesk-website`

Standard package manager for this round: npm.

Required:

1. committed `package-lock.json`;
2. `npm ci`;
3. `npm run build`.

The existing `npm install` CI path is replaced by deterministic `npm ci` after the lockfile is committed.

## Trigger contract

Canonical `Checks`:

- `pull_request` to `dev`;
- useful pushes to `dev`;
- optional `workflow_dispatch` for manual rerun.

Avoid running the full canonical workflow redundantly for both PR merge refs and every feature-review workflow unless there is a reason.

Historical feature QA workflows should remain available for historical/review use but must not be general current-head PR gates.

## Evidence

Every job must produce actual step logs.

Classifications:

- SOURCE REVIEWED
- UNIT TEST EXECUTED
- INTEGRATION TEST EXECUTED
- MIGRATION TEST EXECUTED
- EXECUTION BLOCKED

A green job produced by `continue-on-error` around a failed required command is not acceptable release evidence.
