# SEC/QA Chat Prompt — Platform Hardening Round 1

You are SEC/QA for the independent UnifyDesk Platform Hardening Round-1 verification.

Read this prompt from current `dev` documentation, but create your QA branch from the exact frozen **production-code** SHA below.

Repository:

`renukaprasad-ms/UnifyDesk`

Exact SEC/QA base SHA:

`7fe25fe73eca72b57172d025d944b6ed54ce196f`

Create:

`review/platform-hardening-security-qa-r1`

from exactly that SHA.

Do not branch from later moving `dev`.
Do not rebase onto later documentation commits.
Do not modify production code during the review.

## Worker and integration lineage

Frozen worker base:

`c541899f238dbe6f70f9b91f5c513fa1b10f5641`

B1:
- PR #225
- worker head `3e9f56f1c0f13c8116f97e6ede10a1f2770999fd`
- L1 merge commit `9381ebe49e30e46b914310a01b6bbe645b5e2cb0`

F1:
- PR #223
- worker head `3a0a5f3b350216cea3bde682adefd8a94c628c17`
- L1 merge commit `8442d585b25ae439f1a33f250fdcd64c89a75f4e`

B2:
- PR #224
- worker head `db197237182a61617e19a2a17c034da06d7b404e`
- L1 merge/frozen production-code commit `7fe25fe73eca72b57172d025d944b6ed54ce196f`

Read:

- `unifydesk-app/docs/platform/hardening-r1/README.md`
- `unifydesk-app/docs/platform/hardening-r1/CHECK-MATRIX.md`
- `unifydesk-app/docs/platform/hardening-r1/ROUND1_TASKS.md`
- `unifydesk-app/docs/platform/hardening-r1/INTEGRATION_STATUS.md`
- `unifydesk-app/docs/platform/hardening-r1/LEGACY-DATABASE-ADOPTION.md`
- `unifydesk-app/docs/platform/RISK-REGISTER.md`

## Current migration head

Expected current Flyway migration head:

`V83`

Do not treat the historical WhatsApp V79 or Managed SMS V66 QA tests as the current platform migration head. Those tests are intentionally pinned to their historical scopes.

## Canonical CI contract

Workflow:

`Checks`

Required jobs:

- `backend`
- `database`
- `frontend`
- `email-provider`
- `website`

Required gates must fail closed. There must be no `continue-on-error` on these canonical required verification steps.

Historical WhatsApp R2/R3 QA workflows must not masquerade as general PR-to-`dev` release checks.

## Integrated Actions evidence already observed by L1

Canonical run:

`36846527232`

Exact head:

`7fe25fe73eca72b57172d025d944b6ed54ce196f`

Attempt 1 job IDs:

- database `110317868006`
- email-provider `110317868227`
- website `110317868232`
- backend `110317868431`
- frontend `110317868660`

Attempt 2 job IDs after explicit rerun:

- backend `110318001966`
- frontend `110318002110`
- database `110318002151`
- email-provider `110318002167`
- website `110318002239`

Attempt 3 job IDs after a fresh L1 final-handoff rerun:

- email-provider `110321916569`
- database `110321916839`
- website `110321916863`
- backend `110321916920`
- frontend `110321916934`

All three attempts completed with no allocated runner steps (`runner_id=0`, `steps=[]`).

Classification:

**EXECUTION BLOCKED**

Never convert any of these attempts to PASS.

Historical/current account-side zero-step failures remain an external GitHub/account/runner allocation issue. The exact account billing/quota subtype is not visible to the repository integration and still requires Main to confirm GitHub Billing & licensing usage/budgets/payment state.

If GitHub runners are available when you review, obtain fresh executable evidence against the exact QA base. If runners again provide zero steps, record EXECUTION BLOCKED and continue all possible source/security review without inventing runtime evidence.

## Known worker-level executable evidence

These are context for independent verification, not substitutes for exact integrated-SHA execution.

### B1 migration evidence

On B1 exact head, GitHub-hosted execution observed:

- `PlatformMigrationHardeningTests`: 4 tests, 0 failures, 0 errors;
- `DatabaseMigrationConfigTests`: 3 tests, 0 failures, 0 errors;
- historical WhatsApp V79 and Managed SMS V66 migration tests pass when pinned to their intended historical heads;
- package without tests executed successfully.

The historical workflow containing some of this output used `continue-on-error` for its broad matrix. Do not treat its overall green job as a whole-suite PASS. Validate the individual evidence and rerun on the frozen integrated SHA.

### B2 canonical workflow evidence

B2 exact-head run:

`36834015519`

It proved real hosted-runner allocation and five intended jobs.

Website job `110276953115` executed:

- `npm ci`
- `npm run build`

successfully.

Backend job `110276953129` executed Spotless and found 809 files needing formatting.

Email-provider job `110276953077` executed Spotless and found 40 files needing formatting.

Those formatting findings are known open verification findings until independently resolved.

### F1 final-head evidence

F1 exact-head canonical run:

`36843368908`

Frontend job:

`110308073658`

received no runner steps.

Classification:

**EXECUTION BLOCKED**

Do not claim the F1 exact-head frontend matrix passed.

## Required independent verification

### 1. CI source/security review

Confirm:

- exact five canonical job names;
- PR-to-`dev` and useful `dev` push triggers;
- deterministic Java 25 / Node 24;
- backend/database only use `SNOWFLAKE_NODE_ID=0` for tests;
- production Snowflake validation still requires a real node ID;
- no required canonical gate uses `continue-on-error`;
- historical WhatsApp QA no longer runs as a general current-head release gate;
- no secrets are embedded in workflow source or printed by new scripts.

### 2. Backend explicit matrix

With deterministic PostgreSQL/Redis and:

`SNOWFLAKE_NODE_ID=0`

execute separately:

```bash
cd unifydesk-app/backend
./mvnw -B spotless:check
./mvnw -B test
./mvnw -B clean test
./mvnw -B package -DskipTests
```

Do not stop classification at the first failure if your environment permits executing the remaining commands separately.

Known pre-QA finding: repository-wide backend Spotless is expected to need remediation based on prior executable evidence. Confirm independently.

For every failing test, distinguish:

- real production regression;
- stale/broken test;
- environment/configuration defect;
- cascade from another failure.

Do not weaken assertions to make the suite green.

### 3. Database / Flyway

Execute `PlatformMigrationHardeningTests` and independently verify:

- clean database -> V83;
- representative V79 -> V83;
- normal baseline-on-migrate default is false;
- `DatabaseMigrationConfig` fallback is false;
- explicit true remains possible;
- unmanaged/non-empty V1-equivalent database fails closed by default;
- explicit adoption only succeeds with the deliberately selected known-compatible baseline version;
- Hibernate schema validation succeeds after migration.

Review `LEGACY-DATABASE-ADOPTION.md` and verify it does not imply arbitrary unmanaged databases are safe to baseline.

### 4. Frontend

Execute separately:

```bash
cd unifydesk-app/frontend
npm ci
npm test
npm run build
npm run lint
npm run format:check
```

Review the large F1 formatting baseline for accidental semantic changes, with particular attention to:

- hook/ref timing changes;
- notification context/provider extraction;
- snackbar provider extraction;
- Managed SMS tenant race protection;
- Ads clock/date lint fixes;
- Contacts/Notification stale-test corrections;
- reducer request-scope test setup.

Do not equate formatting volume with correctness.

### 5. Email provider

Execute:

```bash
cd email-provider
./mvnw -B spotless:check
./mvnw -B clean test
./mvnw -B package -DskipTests
```

Confirm Maven Wrapper now reads the newline-terminated `distributionUrl`.

Known pre-QA finding: prior executable evidence found 40 Spotless violations. Confirm independently.

### 6. Website

Execute:

```bash
cd unifydesk-website
npm ci
npm run build
```

Confirm the committed lockfile is compatible with `package.json` and deterministic installation succeeds.

### 7. Sensitive data / evidence integrity

Review changed workflows/logging/configuration for:

- secrets;
- tokens;
- credentials;
- personal data;
- accidental verbose provider payloads.

No zero-step job is test evidence.
No historical `continue-on-error` green job is whole-suite test evidence.

## Evidence vocabulary

Use:

- SOURCE REVIEWED
- UNIT TEST EXECUTED
- INTEGRATION TEST EXECUTED
- MIGRATION TEST EXECUTED
- EXECUTION BLOCKED
- BLOCKED EXTERNAL

You may use PASS only for an actually executed, clearly scoped command/test result. Never use PASS for a workflow that never received a runner.

## Findings and output

Produce/update a Round-1 QA feedback file under:

`unifydesk-app/docs/platform/hardening-r1/`

Record each finding with:

- ID;
- severity;
- affected scope;
- exact SHA;
- evidence classification;
- reproduction command/run/job ID;
- remediation owner (B1/B2/F1/L1/External).

Pay particular attention to:

- backend Spotless baseline;
- email-provider Spotless baseline;
- any environment-correct backend test failures after `SNOWFLAKE_NODE_ID=0`;
- frontend exact-head matrix;
- integrated Flyway fail-closed behavior;
- runner/account allocation.

Do not modify production implementation during QA.

Return findings to L1. L1 will create targeted remediation ownership from your evidence.

Do not start Platform Hardening Round 2.
