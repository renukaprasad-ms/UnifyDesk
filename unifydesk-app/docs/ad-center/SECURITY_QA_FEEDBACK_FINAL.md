# UnifyDesk Ad Center — Final Security / QA Feedback

Status: ROUND-2 RETEST IN PROGRESS

Frozen remediation base SHA:

`b81f68c9d162c5778042f338afbdfe561d164506`

Review branch:

`review/ad-center-security-qa-final-r2`

Remediation integration:

PR #195

## 1. Remediation retest

### ADS-FINAL-001 — concurrent launch step execution

Severity: **High**  
State: **SOURCE FIX VERIFIED / EXECUTION PENDING**

Evidence on the frozen base:

- launch steps are now loaded through a tenant/run-scoped `PESSIMISTIC_WRITE` repository query before execution claim;
- `RUNNING` steps now fail with conflict instead of being reacquired;
- `SUCCEEDED` and `SKIPPED` steps now fail with conflict when reached from a stale execution snapshot, preventing a second provider mutation;
- only `PENDING` and `FAILED` steps may transition to `RUNNING`;
- explicit retry from `FAILED` remains supported;
- `CampaignLaunchStateServiceTests` covers PENDING claim, RUNNING rejection, terminal rejection, and FAILED retry.

Required closure evidence still pending:
- backend test execution;
- preferably a database-backed two-thread execution test proving exactly one provider executor invocation.

### ADS-FINAL-002 — concurrent launch preparation idempotency

Severity: **Medium**  
State: **SOURCE FIX VERIFIED / EXECUTION PENDING**

Evidence on the frozen base:

- `CampaignLaunchPlanningService.prepare()` keeps the fast-path idempotency lookup;
- if no run is visible, it obtains a tenant-scoped `PESSIMISTIC_WRITE` lock on the Reachlyo campaign;
- it then performs a second idempotency lookup after the lock;
- the second request therefore observes and returns the first committed launch run instead of racing to a unique-key violation;
- `CampaignLaunchPlanningServiceTests.concurrentIdempotencyRetryReloadsExistingRunAfterCampaignLock` covers the post-lock reload contract.

Required closure evidence still pending:
- backend test execution;
- PostgreSQL two-transaction concurrency verification.

## 2. Frontend build regression reported after PR #193

State: **SOURCE FIX VERIFIED / BUILD PENDING**

Original errors:

- `brandGuidelinesEnabled` missing from `CreateGooglePerformanceMaxCampaignInput`;
- duplicate `merchantId` in `CreateGoogleAdvancedCampaignInput`.

Frozen-base source now shows:

- `CreateGooglePerformanceMaxCampaignInput` includes:
  - `brandGuidelinesEnabled`
  - `brandMainColor`
  - `brandAccentColor`
  - `brandFontFamily`
  - `merchantId`
  - `feedLabel`
- `CreateGoogleAdvancedCampaignInput` contains only one `merchantId`.

Required closure evidence:

```bash
cd unifydesk-app/frontend
npm ci
npm run build
npm test
npm run lint
npm run format:check
```

or the equivalent Docker build path.

## 3. Security source-review status

Initial source review remains positive for:

- tenant-scoped launch-run and campaign lookups;
- explicit ADS_READ / ADS_CREATE / ADS_MODIFY / ADS_CONNECT controller gates;
- Redis-backed one-time OAuth state for Google and Meta;
- restricted OAuth return paths;
- constant-time Meta webhook signature comparison;
- company-scoped Google/Meta first-party audience lookup;
- SHA-256 hashing before provider submission for supported email/phone identifiers;
- PAUSED-first launch architecture.

These are not substitutes for executable or real-provider verification.

## 4. Executable verification status

| Area | Status |
| --- | --- |
| Backend full suite | NOT EXECUTED — GitHub runner fails before steps |
| Frontend build/tests/lint/format | NOT EXECUTED — GitHub runner fails before steps |
| Flyway clean/upgrade | NOT EXECUTED |
| DB concurrency test | NOT EXECUTED |
| Real Google Ads | NOT ATTEMPTED |
| Real Meta Ads | NOT ATTEMPTED |

## 5. Current decisions

Application code: **NOT YET CERTIFIED**  
Google provider: **NOT ATTEMPTED**  
Meta provider: **NOT ATTEMPTED**  
Overall Ad Center: **NOT RELEASE-READY YET**

Reason: remediation source is in place, but executable gates and real-provider verification remain.
