# Release Process

Baseline: `dev@d9717f93b3e10a308da9636750a4df6926bc6016`

This document defines the target release process. It records gaps in the current repository rather than claiming they are already enforced.

## Branch semantics

### `dev`

Integration branch for reviewed feature and hardening work.

Required target state:

- protected;
- pull requests required;
- required status checks enabled;
- no direct feature development;
- release candidates frozen from an exact commit SHA.

### `main`

Production/release branch.

Required target state:

- protected;
- changes arrive only through an explicit release PR from a frozen, verified `dev` SHA;
- required release checks and approvals must pass;
- production deployments reference immutable tags/digests derived from the merge commit.

At this baseline both `dev` and `main` are reported unprotected and required status checks are disabled. That is a P0 release-engineering blocker.

## Standard verification commands

### Backend

From `unifydesk-app/backend`:

```bash
./mvnw -B spotless:check test
./mvnw -B clean test
./mvnw -B package -DskipTests
```

Database-backed integration requires test PostgreSQL/Redis where expected.

### Frontend

From `unifydesk-app/frontend`:

```bash
npm ci
npm test
npm run build
npm run lint
npm run format:check
```

### Email provider service

From `email-provider`:

```bash
./mvnw -B spotless:check test
```

### Website

From `unifydesk-website`:

```bash
npm ci
npm run build
```

A command is only evidence after it actually executes against the named SHA.

## Feature lifecycle

1. Main/product control room selects and scopes the initiative.
2. L1 audits current implementation and external/provider requirements.
3. L1 freezes one exact worker base SHA.
4. L1 freezes architecture/API/data/security/performance/task contracts.
5. B1/B2/F1 work independently from the same base/contract.
6. Workers open PRs; L1 decides integration order and resolves cross-stream issues.
7. L1 freezes the exact integrated production-code SHA.
8. SEC/QA branches exactly from that SHA.
9. SEC/QA records evidence using the platform evidence vocabulary.
10. L1 creates targeted remediation rounds for real findings.
11. Repeat until release criteria are satisfied.

## Release candidate creation

A candidate must identify:

- exact source SHA;
- migration head;
- dependency/image versions;
- environment configuration version;
- included features;
- intentionally disabled features;
- test evidence links;
- provider evidence links;
- unresolved accepted risks;
- rollback target.

Do not call a moving branch a release candidate.

## Migration gate

For every candidate:

- MIGRATION TEST EXECUTED from an empty database through current head;
- MIGRATION TEST EXECUTED from representative supported upgrade snapshots;
- Flyway validation executed;
- backup/restore procedure verified before applying destructive/large migrations;
- `FLYWAY_BASELINE_ON_MIGRATE=false` explicitly enforced for normal production startup.

Baselining an existing database is a separate approved operation.

## Artifact and deployment gate

Target production flow:

1. CI checks candidate SHA.
2. CI builds immutable backend/frontend images tagged by release + commit SHA.
3. Registry digest is recorded.
4. Staging deploys those exact digests.
5. Smoke/provider/load checks run.
6. Release PR promotes the frozen candidate to `main`.
7. Git annotated release/tag is created.
8. Production deploy uses the already-tested digests, not a rebuild.
9. Post-deploy health/provider checks run.

Current local `k8s.sh` builds `latest` locally and imports into k3s; it is not the production flow above.

## Rollback

Rollback must be two-dimensional:

### Application rollback

Redeploy the last known-good immutable image digests.

### Database compatibility

Before release, classify migrations as:

- backward compatible;
- requires application ordering;
- irreversible / restore-required.

Never assume rolling application rollback is safe after a schema change. For irreversible migrations, rollback is restore/forward-fix according to the approved runbook.

## Release evidence record

Each release should retain:

- source SHA and tag;
- CI run IDs;
- executed test summaries;
- migration rehearsal evidence;
- provider smoke evidence;
- image digests/SBOM where available;
- deploy time/operator;
- database backup identifier;
- post-deploy checks;
- rollback target;
- incidents/deviations.

## Emergency changes

Emergency production fixes still require:

- a branch from the production SHA;
- minimum focused executable verification;
- explicit review;
- immutable artifact;
- merge back to `dev`;
- after-action note.

Do not bypass source traceability by editing a running server/container.
