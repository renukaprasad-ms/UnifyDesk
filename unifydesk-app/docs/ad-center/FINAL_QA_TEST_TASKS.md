# UnifyDesk Ad Center — Final QA / Security / Real-Provider Test Tasks

Status: FINAL QA STARTED  
Frozen application base SHA: `88af16fe1cd6966730c26447119416104039e6bb`  
Ad Center completion PR: #193  
Review branch: `review/ad-center-security-qa-final`

## 1. Final QA objective

This round certifies the completed Ad Center as a whole. It is not another feature-development round.

QA must independently determine:

1. whether the integrated application code is safe and regression-free;
2. whether the unified campaign orchestration behaves safely under partial failure/retry;
3. whether Google Ads and Meta Ads tenant/RBAC/OAuth boundaries hold;
4. whether first-party audience/customer-list handling avoids raw identifier persistence/logging;
5. whether provider-specific eligibility/capability restrictions are enforced rather than fabricated;
6. whether the customer-facing UI matches the backend contract;
7. whether real Google and Meta provider flows have actually been verified.

Do not mark a provider-dependent scenario PASS from mocks or source review alone.

## 2. Frozen review base

Test exactly:

`88af16fe1cd6966730c26447119416104039e6bb`

Do not silently move the QA base to later `dev`.

If implementation fixes are required, record a finding and hand the fix to a separate implementation branch. Do not repair production implementation inside this QA branch.

## 3. Required executable gate

### Backend

From `unifydesk-app/backend`:

```bash
./mvnw -B spotless:check test
./mvnw -B clean test
./mvnw -B package -DskipTests
```

Record each command independently:
- exit code;
- test count;
- failures/errors/skips;
- failing test names;
- relevant output.

At minimum, the full suite must include all existing Ads tests. The repository currently contains 77 Ads-related backend test classes.

Focused classes that MUST be explicitly checked include:

- `GoogleProviderCampaignCompilerTests`
- `MetaProviderCampaignCompilerTests`
- `CampaignLaunchPlanningServiceTests`
- `ProviderDeploymentServiceTests`
- `ReachlyoCampaignServiceTests`
- `ReachlyoCampaignAnalyticsServiceTests`
- `ReachlyoCampaignRecommendationServiceTests`
- `ReachlyoBudgetRecommendationServiceTests`
- `ReachlyoFunnelAttributionServiceTests`
- `AdFeatureCapabilityServiceTests`
- `GoogleAdsApiClientTests`
- `GoogleAdsPerformanceMaxServiceTests`
- `GoogleAdsPerformanceMaxLifecycleServiceTests`
- `GoogleAdsPerformanceMaxListingGroupServiceTests`
- `GoogleAdsPortfolioBiddingServiceTests`
- `GoogleAdsExperimentServiceTests`
- `GoogleDataManagerServiceTests`
- `MetaAdsCustomerListServiceTests`
- `MetaAdsWebhookControllerTests`
- `MetaAdsExperimentServiceTests` if present in the current test tree
- `AdOptimizationPolicyServiceTests`
- `FirstPartyAudienceBuilderServiceTests`

### Frontend

From `unifydesk-app/frontend`:

```bash
npm ci
npm test
npm run build
npm run lint
npm run format:check
```

Record each command independently.

The existing Ads frontend automated coverage is thin compared with the backend. Do not treat a successful TypeScript build as equivalent to UI behavioral coverage.

Required targeted frontend verification:
- unified campaign wizard;
- provider plan/review;
- launch/retry status;
- campaign details;
- Google PMax lifecycle;
- Google PMax retail/listing groups;
- Google portfolio bidding/shared budgets;
- Meta targeting/creative/customer-list surfaces;
- provider/account switch behavior;
- permission-disabled controls;
- responsive layouts.

### CI caveat

Recent GitHub Actions jobs for PR #193 failed before executing any step (`steps=[]`).

If that runner condition persists:
- record CI as `NOT EXECUTED — RUNNER/INFRASTRUCTURE`;
- do not convert it to PASS;
- do not treat it as an application-code failure either.

## 4. Tenant isolation / authorization matrix — P0

Create or extend automated tests proving that IDs from company B cannot be used while authenticated/authorized for company A.

Cover at minimum:
- ad account;
- provider connection;
- provider deployment;
- Reachlyo campaign;
- campaign channel;
- Google campaign/ad group/asset group;
- Meta campaign/ad set/ad/creative;
- audience;
- conversion;
- Merchant account/data source/product;
- Google portfolio strategy/shared budget assignment;
- launch run/retry;
- recommendations/analytics.

Required behavior:
- no cross-company read;
- no cross-company mutation;
- no provider request issued after tenant mismatch;
- no existence leak through different error shapes where practical.

RBAC matrix:
- ADS_READ: read-only surfaces;
- ADS_CREATE: creation only where contract allows;
- ADS_MODIFY: mutation/status/sync where required;
- ADS_CONNECT: OAuth/provider connection;
- unauthorized user denied before provider call.

Record any controller/service route that bypasses the shared authorization helpers as a finding.

## 5. OAuth / credential security — P0

### Google Ads

Verify:
- OAuth state is random;
- state is stored server-side in Redis;
- state is single-use through atomic consume/get-and-delete semantics;
- expired/missing/replayed state rejects;
- state is bound to company + initiating user;
- callback re-checks ADS_CONNECT permission;
- return path cannot become an open redirect;
- access/refresh tokens never reach browser responses;
- refresh tokens/secrets are encrypted at rest through the existing credential store;
- access tokens/refresh tokens are never logged.

### Meta Ads

Perform the same state/replay/company/user/permission checks.

Also verify:
- long-lived/provider token material is not exposed to frontend payloads;
- disconnect/reconnect cannot attach one tenant's provider assets to another tenant.

## 6. Meta Ads webhook security — P0

Verify:
- `X-Hub-Signature-256` required when app secret is configured;
- HMAC comparison is constant-time;
- malformed hex/signature rejects safely;
- invalid JSON rejects;
- verification token cannot be bypassed;
- webhook body/signature are not logged;
- forged provider events cannot mutate another tenant;
- duplicate provider events remain idempotent where the event pipeline performs mutations.

Real provider verification must confirm Meta's actual signed callback reaches the production/staging endpoint successfully.

## 7. Unified launch Saga / financial safety — P0

The defining safety contract is:

`compile/preflight -> create provider objects PAUSED -> verify -> persist mappings -> activate only when valid`

Test:
- compiler is side-effect free;
- unsupported capability blocks before mutation;
- invalid audience/conversion/creative blocks before mutation;
- launch uses an idempotency key;
- retry does not create duplicate provider campaigns;
- backend restart/resume does not duplicate provider resources;
- partial Google success + Meta failure remains visible;
- partial Meta success + Google failure remains visible;
- failed provider objects stay PAUSED;
- retry operates only on failed/retryable work;
- provider IDs/request metadata are persisted;
- activation happens only after required provider deployments are valid;
- no automatic cross-provider budget movement occurs from advisory recommendations.

## 8. Google Ads final matrix

Use the Google Ads API version configured by the application (`v25` at this frozen base) and verify the active provider contract before interpreting failures.

### Connection / account
- direct advertiser account;
- manager/MCC -> child account;
- stored login-customer-id used for child mutations;
- reconnect preserves tenant ownership;
- revoked refresh token produces safe reconnect state.

### Search
- goal-first unified campaign -> Search deployment;
- geo/language/network/daypart;
- age/gender/detailed demographic where supported;
- observation vs targeting;
- keyword + negatives;
- shared negative lists;
- RSA;
- conversion mapping;
- AI Max enable/disable constraints;
- search-term reporting and promote/exclude actions.

### Performance Max
- standard PMax;
- required asset minimum validation;
- optional image/video/logo assets;
- URL automation/final URL expansion constraints;
- brand guidelines;
- lifecycle/customer-acquisition/retention settings;
- customer-list classifications;
- retail Merchant Center mode;
- listing groups/product groups;
- asset diagnostics/performance.

### Demand Gen / Display / Shopping / App
Verify create/read/update/status/reporting only for the capabilities intentionally supported by the API.

Standalone Video must remain sync/report-only.

### Portfolio bidding / shared budgets
Verify:
- create shared budget;
- create supported portfolio strategies;
- attach strategy only;
- attach budget only;
- attach aligned pair;
- Search/Display/Shopping compatibility;
- incompatible PMax/Demand Gen/App/Video blocks before mutation;
- alignment fails if strategy and shared budget are not attached to the exact same campaign set;
- 30-day metrics read back correctly.

Google's current documentation confirms explicitly shared campaign budgets and aligned bidding strategy IDs are first-class API concepts. Campaigns with experiments have shared-budget restrictions; test that provider errors are surfaced safely rather than hidden.

### Experiments
- supported Search/custom experiment creation;
- schedule/end/promote/graduate;
- Campaign Mix only if the real account is allowlisted;
- non-supported/specialized experiment setup remains provider-hosted/read-only.

### First-party data
Google Customer Match/Data Manager:
- require explicit consent flags;
- normalize + hash email/phone before provider submission;
- raw identifiers never persist in DB;
- raw identifiers never enter logs/audit metadata/errors;
- cross-company audience IDs reject before hashing/provider call.

## 9. Meta Ads final matrix

Verify against the active Meta Marketing API version configured by the frozen application (repo target: Graph API v26.0).

### Connection / account
- OAuth connect/reconnect;
- correct business/ad-account discovery;
- required scopes/permissions;
- revoked permission handling;
- account-disabled/restricted state.

### Campaign creation
- campaign objective;
- budget;
- schedule;
- ad set;
- ad;
- creative;
- conversion/pixel;
- PAUSED-first behavior.

### Audience / targeting
- provider-backed interests;
- geo targeting;
- detailed targeting fields only where currently exposed;
- customer-list Custom Audience;
- lookalike behavior currently supported by the implementation;
- Advantage+ audience;
- Advantage+ placements;
- manual placement tree;
- placement-specific creative variants.

### Creative
- standard creative;
- existing post;
- Advantage+ enhancements;
- generative text eligibility;
- image expansion/uncrop eligibility;
- background generation eligibility;
- catalog/product-set ads.

### Provider-hosted boundaries
Do not fail the product merely because Meta Ads Manager has an interface that the public API does not stably expose.

Verify the UI clearly treats these as provider-hosted/read-only when applicable:
- unsupported experiment creation;
- creator/partnership discovery;
- WhatsApp Status placement without a verified public API contract.

### Customer-list privacy
- explicit authorization/terms confirmation;
- browser/backend hashing contract as designed;
- no raw email/phone persistence;
- no raw values in logs;
- invalid/foreign audience cannot receive member batches.

## 10. Capability integrity — P0

For every fine-grained capability:
- SUPPORTED only when implementation exists;
- ACCOUNT_ELIGIBILITY_REQUIRED when provider/account eligibility is needed;
- PROVIDER_HOSTED/READ_ONLY when mutation is not safely supported;
- reason exposed to the UI;
- frontend does not render a mutation flow solely because a high-level provider exists.

Specifically regression-check:
- Google AI asset generation closed-beta/eligibility behavior;
- Campaign Mix experiment eligibility;
- Meta generative creative eligibility;
- Meta provider-hosted experiment/WhatsApp Status boundaries.

## 11. Analytics / recommendation correctness

Cross-provider analytics:
- same date range applied to both providers;
- currency mismatch prevents invalid summed spend;
- cross-provider reach is not naively added/de-duplicated;
- partial provider sync failure preserves valid provider data and exposes partial state;
- CPA/ROAS divide-by-zero handling;
- provider-specific drill-down remains accessible.

Recommendations:
- evidence fields present;
- minimum sample thresholds applied;
- recommendations remain advisory unless an explicitly safe reversible automation exists;
- budget recommendations do not move money automatically.

## 12. Automation / experiments safety

Verify:
- scheduled optimization rules are tenant-scoped;
- cooldown and sample-size guardrails;
- automatic actions limited to reversible status actions;
- budget changes remain recommendation/approval based;
- provider failure creates a durable error/audit event;
- repeated scheduler execution is idempotent.

## 13. Audit / observability / privacy

Verify activity log coverage for:
- connect/disconnect;
- campaign create/update/status;
- audience/conversion/creative mutations;
- first-party audience operations;
- portfolio/shared-budget operations;
- launch/retry;
- recommendation actions;
- experiment actions;
- automated-rule execution.

Secrets/PII that must not appear in logs:
- OAuth access token;
- refresh token;
- app secret;
- customer raw email;
- customer raw phone;
- Meta customer-list raw member data.

Provider error messages shown to customers must not reveal credentials or raw payload secrets.

## 14. Database / migration verification

Run the full current Flyway chain on a clean PostgreSQL database.

Also run a representative upgrade from the last pre-Ad-Center schema that exists in repository history to the frozen head.

Verify:
- unique/idempotency constraints;
- tenant/company indexes;
- foreign keys;
- no destructive migration needed to upgrade;
- launch/provider-deployment uniqueness supports retry guarantees.

## 15. Real Google provider verification

Status must be one of:
- REAL_GOOGLE_VERIFIED
- PARTIALLY_VERIFIED
- BLOCKED_EXTERNAL
- NOT_ATTEMPTED

Capture evidence without storing secrets:
- account ID masked;
- operation;
- provider resource type/ID masked if needed;
- timestamp;
- expected vs actual;
- provider request ID/error code where safe.

Minimum launch proof:
1. OAuth;
2. account discovery;
3. create one Search deployment PAUSED;
4. verify provider object;
5. activate/pause;
6. sync reporting;
7. create one shared budget + portfolio strategy if account supports it;
8. verify PMax create/preflight if account is eligible;
9. revoke token/permission and confirm safe failure/reconnect.

Never spend beyond a deliberately tiny QA budget.

## 16. Real Meta provider verification

Status must be one of:
- REAL_META_VERIFIED
- PARTIALLY_VERIFIED
- BLOCKED_EXTERNAL
- NOT_ATTEMPTED

Minimum launch proof:
1. OAuth;
2. ad-account discovery;
3. create campaign/ad set/ad PAUSED;
4. verify targeting/creative;
5. activate/pause only when safe;
6. sync insights;
7. signed webhook verification;
8. customer-list flow using synthetic/test identifiers only;
9. revoke permission and confirm safe failure/reconnect.

Never use scraped or non-consented personal data for QA.

## 17. Required finding format

New findings use:

`ADS-FINAL-NNN`

Severity:
- Critical
- High
- Medium
- Low

State:
- OPEN
- CLOSED
- BLOCKED_EXTERNAL
- NOT_EXECUTED
- ACCEPTED_PROVIDER_LIMITATION

Each finding must include:
- affected component;
- exact evidence;
- exploit/regression scenario;
- expected behavior;
- recommended owner: B1/B2/F1/Infra;
- test needed to close.

## 18. Final decisions

Give three separate decisions.

### A. Application-code readiness

`GO` only if:
- executable backend/frontend gate passes;
- no open Critical/High finding;
- no launch/idempotency/tenant-isolation regression;
- no secret/PII leak.

### B. Google provider readiness

`GO`, `PARTIAL`, or `BLOCKED_EXTERNAL`.

Do not grant GO from mocks.

### C. Meta provider readiness

`GO`, `PARTIAL`, or `BLOCKED_EXTERNAL`.

Do not grant GO from mocks.

Overall Ad Center production readiness is GO only when application-code readiness is GO and the provider capabilities intended for launch have real-provider evidence.

## 19. Required QA report

Update:

`unifydesk-app/docs/ad-center/SECURITY_QA_FEEDBACK_FINAL.md`

Report:
- exact frozen base SHA;
- QA branch;
- source/security audit result;
- backend commands/results;
- frontend commands/results;
- Flyway result;
- tenant/RBAC matrix;
- OAuth/security matrix;
- launch Saga/idempotency matrix;
- Google functional matrix;
- Meta functional matrix;
- privacy/PII result;
- real Google status;
- real Meta status;
- all ADS-FINAL findings;
- application-code readiness;
- Google readiness;
- Meta readiness;
- overall production readiness.

Do not merge the QA branch.
