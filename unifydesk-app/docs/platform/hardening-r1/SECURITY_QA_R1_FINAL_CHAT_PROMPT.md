# SEC/QA Final Re-verification Prompt — Platform Hardening Round 1

You are SEC/QA for the FINAL independent re-verification of UnifyDesk Platform Hardening Round 1 after targeted remediation.

Repository:

`renukaprasad-ms/UnifyDesk`

## Exact frozen production-code base

Create:

`review/platform-hardening-security-qa-r1-final`

from exactly:

`5012fd426b4d2da50d5c8fe5c44cf3f15b5aebc3`

Do NOT branch from later moving `dev`.
Do NOT rebase.
Do NOT modify production code during the review.

Main has explicitly deferred the external GitHub Actions runner-allocation problem for now.

Do NOT repeatedly rerun zero-step GitHub Actions.
Do NOT attempt to solve GitHub billing/quota/runner allocation in this review.
Do NOT call any zero-step job PASS.

Classify the runner issue as:

`PHR1-QA-001 = BLOCKED EXTERNAL / DEFERRED`

## Required documents

Read from current `dev` documentation:

- `unifydesk-app/docs/platform/hardening-r1/SECURITY_QA_R1_FINDINGS.md`
- `unifydesk-app/docs/platform/hardening-r1/REMEDIATION_R1.md`
- `unifydesk-app/docs/platform/hardening-r1/INTEGRATION_STATUS.md`
- `unifydesk-app/docs/platform/hardening-r1/CHECK-MATRIX.md`
- `unifydesk-app/docs/platform/RISK-REGISTER.md`

## Remediation lineage

Frozen remediation worker base:

`677dea286bfe45b2172872442125f2faf4ce7953`

B1:
- finding ownership: `PHR1-QA-002`, `PHR1-QA-004`
- PR #231
- worker head: `8c4f6bfd37e457a44f7d120dfff330730083e387`
- L1 merge commit: `b833aa739138f423463c8015dfdecc335874b922`

F1:
- finding ownership: `PHR1-QA-005`
- PR #230
- worker head: `09beabd0b7170b27c8b8b4fae8352e687705fd10`
- L1 merge commit: `a56fbb0eb2fde1a599f287f50f72be220beafdcb`

L1 integration-only F1 completion:
- PR #233
- head: `cd6e50aa127189251677278f9c5202508ebfaa32`
- merge commit: `e5856cfb61c7613427713f73dc8fd6014768538d`
- scope: protect stale setup-mutation success/error/finally follow-up paths after a tenant switch.

B2:
- finding ownership: `PHR1-QA-003`
- PR #232
- worker head: `11d16ccbd2ecaf30032860f87807ba59b1085f97`
- L1 merge / final production-code commit:
  `5012fd426b4d2da50d5c8fe5c44cf3f15b5aebc3`

## Required finding dispositions to verify

### PHR1-QA-001 — External runner allocation

Expected status:

**BLOCKED EXTERNAL / DEFERRED**

Source review may confirm the canonical workflow remains fail-closed, but do not spend this review repeatedly rerunning zero-step jobs.

If an execution environment independently becomes available during the review, record only commands that actually execute. Lack of runner execution is not a code failure and is not PASS.

### PHR1-QA-002 — Backend Spotless

Expected current status:

**DEFERRED VERIFICATION / FORMATTING DEBT**

B1 did not fabricate an ~810-file formatter change without an executable formatter environment.

Verify:

- Spotless configuration/rules were not weakened;
- exclusions were not introduced to hide the debt;
- remediation PR #231 did not claim the repository-wide baseline is green;
- backend source/test semantic changes are independently reviewable from the deferred formatter debt.

If you can actually execute:

`./mvnw -B spotless:check`

then record the real result. Otherwise keep deferred verification.

Do not close this finding solely from source review.

### PHR1-QA-003 — Email-provider Spotless

Expected current status:

**SOURCE REMEDIATION PARTIAL / DEFERRED VERIFICATION / FORMATTING DEBT**

PR #232 applies only five exact mechanical formatter corrections recoverable from previous real Spotless output.

Verify those five files are formatting-only:

- `EmailDeliveryAttemptRepository.java`
- `MimeMessageBuilder.java`
- `EmailProviderApplicationTests.java`
- `PostfixQueueIdExtractorTests.java`
- `PostfixSmtpClientTests.java`

Confirm:

- no formatter rules/exclusions changed;
- Maven Wrapper fix remains intact;
- product behavior was not redesigned.

If an executable Java 25 environment is available, run:

```bash
cd email-provider
./mvnw -B spotless:check
./mvnw -B clean test
./mvnw -B package -DskipTests
```

Otherwise retain deferred verification. Do not call the full email-provider Spotless baseline green from the five source fixes alone.

### PHR1-QA-004 — Backend regression remediation

Expected current status:

**SOURCE REMEDIATION COMPLETE / EXECUTABLE VERIFICATION DEFERRED**

Review all PR #231 semantic/test changes.

Pay particular attention to the two production fixes in:

`GoogleProviderCampaignCompiler.java`

Verify:

1. Display provider-backed audience lists no longer dereference a null canonical `AudienceDefinition`.
2. `configure-pmax-lifecycle` is emitted from the Performance Max branch when the PMax lifecycle override is present, and is no longer incorrectly emitted from the Display branch.

Review test corrections to confirm they align with current production contracts rather than weakening assertions.

Verify:

- no Flyway/V83 hardening source/tests were removed or weakened;
- `application.yaml` still defaults `FLYWAY_BASELINE_ON_MIGRATE=false`;
- `DatabaseMigrationConfig` still defaults false;
- production `SnowflakeIdGenerator.resolveNodeId()` still requires an explicit node ID;
- `SNOWFLAKE_NODE_ID=0` remains a test-environment convention only.

If an executable backend environment is available, use deterministic PostgreSQL/Redis and run separately:

```bash
cd unifydesk-app/backend
SNOWFLAKE_NODE_ID=0 ./mvnw -B test
SNOWFLAKE_NODE_ID=0 ./mvnw -B clean test
SNOWFLAKE_NODE_ID=0 ./mvnw -B package -DskipTests
SNOWFLAKE_NODE_ID=0 ./mvnw -B -Dtest=DatabaseMigrationConfigTests,PlatformMigrationHardeningTests test
```

If execution is unavailable, do not invent PASS. Record source remediation separately from deferred executable proof.

### PHR1-QA-005 — Managed SMS tenant isolation

Expected current status:

**SOURCE REMEDIATION COMPLETE / EXECUTABLE VERIFICATION DEFERRED**

Review:

- `ManagedSmsPanel.tsx`
- `ManagedSmsSetupModal.tsx`
- `ManagedSmsTenantRace.test.tsx`

Verify:

1. company switch invalidation occurs in layout phase rather than a passive effect;
2. load results use both tenant identity and request-generation validation;
3. load success, catch and finally paths cannot write stale company-A state after B commits;
4. panel status/usage/health are protected;
5. setup status/profile/headers/templates are protected;
6. setup mutation follow-up paths are tenant-scoped:
   - stale success does not emit A success UI into B;
   - stale failure does not emit A error UI into B;
   - stale finally cannot alter B busy state;
   - multi-step create/submit operations stop before later calls once the initiating tenant is stale;
7. component tests use delayed promises and same-mounted-component A -> B rerenders;
8. no unrelated Managed SMS UI redesign occurred.

If frontend execution is available, run:

```bash
cd unifydesk-app/frontend
npm ci
npm test
npm run build
npm run lint
npm run format:check
```

Otherwise record executable verification as deferred.

## Flyway / PLAT-003

Current source expectation remains:

- normal baseline-on-migrate default false;
- explicit baseline opt-in supported only deliberately;
- current migration head V83;
- clean -> V83;
- representative V79 -> V83;
- unmanaged/non-empty schema fails closed by default;
- controlled known-compatible baseline adoption;
- Hibernate schema validation.

Historical B1 worker-level execution previously demonstrated the focused migration tests, but exact final-SHA execution may remain unavailable.

Do not close the integrated-execution limitation unless you actually execute it on the final frozen SHA.

## PLAT-002

PLAT-002 remains OPEN while executable canonical CI proof is unavailable.

Main's decision to defer the external runner issue unblocks normal product development; it does NOT convert missing release evidence into PASS and does NOT make public-production release gates green.

## Evidence language

Use:

- SOURCE REVIEWED
- UNIT TEST EXECUTED
- INTEGRATION TEST EXECUTED
- MIGRATION TEST EXECUTED
- EXECUTION BLOCKED
- BLOCKED EXTERNAL
- DEFERRED VERIFICATION

Keep source remediation and executable verification as separate dimensions.

## Final QA output

Create/update:

`unifydesk-app/docs/platform/hardening-r1/SECURITY_QA_R1_FINAL_FINDINGS.md`

The final report must state, for each PHR1 finding:

- exact final SHA;
- source-remediation status;
- executable-verification status;
- whether it is closed, deferred, or still requires code remediation;
- evidence used;
- any new source finding.

A valid final outcome may explicitly distinguish:

- meaningful source remediation complete;
- executable CI verification deferred;
- backend Spotless verification deferred;
- email-provider Spotless verification deferred;
- GitHub runner allocation BLOCKED EXTERNAL / DEFERRED.

Do not start Platform Hardening Round 2.
