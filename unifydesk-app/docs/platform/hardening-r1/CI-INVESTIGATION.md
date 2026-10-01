# CI Investigation — Hardening R1

Frozen source base:

`c541899f238dbe6f70f9b91f5c513fa1b10f5641`

## Historical zero-step evidence

### Checks run 36550776002

All four jobs were created on 2026-09-29 and failed in about two seconds.

Each job had:

- label `ubuntu-latest`;
- `runner_id=0`;
- empty runner name;
- `steps=[]`.

Affected jobs:

- backend;
- frontend;
- email-provider;
- website.

No job log exists because no runner executed a step.

### WhatsApp Round 3 Final QA run 36550776200

Attempt 2 showed the same pattern for:

- backend-matrix;
- whatsapp-focused;
- frontend-matrix.

Again: `runner_id=0`, no runner name, zero steps.

## Source issue versus GitHub/account issue

### Ruled out as the cause of historical zero-step execution

**Workflow syntax:** ruled out for the zero-step incident. GitHub accepted the workflows, generated jobs, and the same workflow definitions execute now.

**Action versions:** ruled out for zero-step. Current jobs successfully download and execute `actions/checkout@v5`, `actions/setup-java@v5` and `actions/setup-node@v5`.

**Job conditions:** ruled out. Historical jobs were not skipped; they were created and failed before runner allocation.

**Workflow concurrency:** no canonical `Checks` concurrency rule exists that explains the observed immediate runner-less failures.

**Environment approvals/restrictions:** the affected jobs do not declare protected environments.

**Repository Actions token permissions:** current jobs receive read permissions and successfully checkout. The historical symptom occurred before any step where token permissions could fail.

### External classification

The historical condition is a **GitHub/account/runner allocation issue**, not a repository-source execution failure.

Evidence supporting an Actions usage/billing allocation cause:

- repository is private;
- the failure affected every Ubuntu job in unrelated workflows;
- all jobs had runner_id 0 and no steps;
- no GitHub Actions service incident was reported for 2026-09-29;
- on 2026-10-01 the same repository receives hosted runners and executes real steps;
- GitHub-hosted minutes for private repositories are quota/billing controlled and reset by billing cycle.

The connected GitHub App cannot read the repository owner's billing/usage page. Therefore the exact subtype (included minutes exhausted, spending/budget stop, payment restriction, or another account allocation control) is not independently visible here.

### Required human check

Open GitHub account settings and verify:

1. Billing & licensing -> Usage;
2. GitHub Actions current/previous billing-cycle minutes;
3. Actions budget/spending limit and whether “stop usage” was enabled;
4. payment method/account restriction status.

Record the result in this document during integration.

## Current executable failures are separate

The recovery of runner execution does **not** make CI green.

### Canonical Checks run from Platform Baseline PR

Current jobs actually executed and failed for source/config reasons.

#### Backend

PostgreSQL/Redis Compose startup succeeded.

`./mvnw -B spotless:check test` failed at Spotless:

- 1869 Java files inspected;
- 809 require formatting.

A broader executable backend matrix also reached tests and reported real failures. Many errors cascade from `SNOWFLAKE_NODE_ID` not being defined in the test environment because `IdGenerator` initializes from that required environment variable. Other non-Snowflake test failures also remain and must be classified/fixed, not hidden.

#### Frontend

`npm ci` succeeds.

TypeScript/Vite production build succeeds in the full matrix.

Current failures include:

- stale/test-isolation assertions in Contacts components/pages;
- Segment editor test failures;
- notification tests still expecting old “New inbox activity” rendering after the newer grouped-email subject UI;
- Contacts route-guard expectation mismatch;
- Broadcast/Inbox Redux slice test failures;
- Managed Email/other component tests reporting duplicate rendered elements;
- format and lint checks are non-zero.

F1 must decide test defect versus product defect from current contracts; it must not merely weaken assertions.

#### Email provider

Failure occurs before Maven starts:

`cannot read distributionUrl property in ./.mvn/wrapper/maven-wrapper.properties`

The file's final line is `distributionUrl=...` with no trailing newline. The wrapper loops using `while IFS="=" read -r key value; do ... done` without an EOF fallback, so the last unterminated line is not processed.

Minimal fix: make the wrapper properties a valid newline-terminated text file (or regenerate Maven Wrapper consistently). Then execute Spotless/tests/package and fix only newly exposed build issues.

#### Website

`actions/setup-node@v5` fails before npm install because caching is configured with:

`unifydesk-website/package-lock.json`

No `package-lock.json`, `npm-shrinkwrap.json`, Yarn lock or pnpm lock exists.

Hardening decision: standardize the website on npm for this repository, generate and commit `package-lock.json`, use `npm ci`, then build.

## Historical feature-specific workflow issue

`.github/workflows/whatsapp-r2-qa.yml` uses `continue-on-error: true` for required verification steps. A successful job conclusion from that workflow cannot be treated as PASS.

The WhatsApp R2/R3 workflows also have PR-to-`dev` triggers even though they are historical feature QA workflows. Their frozen tests can conflict with later platform migration head V83.

B2 must scope historical QA workflows to their intended review branches/manual execution and leave the canonical current-head `Checks` workflow as the branch-protection candidate.
