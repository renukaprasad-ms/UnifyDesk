# Platform Hardening Round 1 Tasks

All workers use exactly:

`c541899f238dbe6f70f9b91f5c513fa1b10f5641`

No worker rebases onto moving `dev`.

## B1 — Backend / database verification

Branch:

`fix/platform-backend-db-hardening-r1`

Owns:

- Flyway fail-closed default in application configuration and custom migration configuration;
- tests proving default false and explicit opt-in true;
- controlled legacy-adoption test/runbook;
- clean V1 -> V83 migration test;
- representative V79 -> V83 upgrade test;
- Hibernate schema validation after current migration;
- backend formatting baseline;
- backend compile/unit/integration/package failures exposed by the canonical commands;
- classification of test defects versus implementation defects.

B1 may make minimal backend implementation corrections required to make the existing intended behavior pass its current contracts. B1 must not redesign product features.

B1 does not edit frontend, website, email-provider or GitHub workflow files.

## B2 — CI / repository verification infrastructure

Branch:

`fix/platform-ci-infrastructure-r1`

Owns:

- canonical `.github/workflows/checks.yml`;
- exact job names `backend`, `database`, `frontend`, `email-provider`, `website`;
- deterministic Java/Node/caching and backend test environment;
- PostgreSQL/Redis Compose lifecycle;
- no required-gate `continue-on-error`;
- historical WhatsApp QA workflow trigger cleanup so stale feature QA does not run as a general PR gate;
- email-provider Maven wrapper newline/CI bootstrap issue;
- email-provider canonical verification job;
- website npm lockfile + `npm ci` verification job;
- CI evidence/reporting.

B2 does not change backend business logic or frontend product code.

## F1 — Frontend verification hygiene

Branch:

`fix/platform-frontend-verification-r1`

Owns only failures exposed by:

- `npm ci`
- `npm test`
- `npm run build`
- `npm run lint`
- `npm run format:check`

F1 must:

- fix test isolation/stale assertions when current product behavior is correct;
- fix frontend implementation only where a real current-contract defect is exposed;
- preserve the current UI/product design;
- keep the production TypeScript build green;
- avoid unrelated redesign/refactoring.

F1 does not edit backend, GitHub workflow, website or email-provider files.

## Shared contract

Workers may read each other's assigned contract but do not depend on unmerged code from another worker.

Known shared environment contract:

`SNOWFLAKE_NODE_ID=0` for backend test execution.

B1 can use that environment locally/on its branch without waiting for B2 to add it to Actions.

## L1 integration order

Preferred integration order after all PRs are ready:

1. B1 backend/database corrections;
2. F1 frontend verification corrections;
3. B2 CI/infrastructure last, rebased/integrated deliberately by L1 so its canonical workflow evaluates the final combined source.

This ordering is an L1 integration decision, not a dependency between workers.

L1 then freezes one exact integrated production-code SHA and obtains real canonical workflow evidence before SEC/QA starts.
