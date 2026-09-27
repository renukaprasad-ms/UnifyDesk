# UnifyDesk Managed SMS — Round-3 Final QA / Test Tasks

Status: FINAL RETEST
Integrated application code SHA before QA handoff: 5d3519f54b96a505c9b7977e5388dbd0dc51e1b4
Round-3 implementation PR: #156
Round-2 QA source: SECURITY_QA_FEEDBACK.md

## 1. Final QA objective

Round 3 contains only the residual CTA host/port parity fix for MS-SMS-R1-003.

QA must:

1. prove MS-SMS-R1-003 is fully CLOSED;
2. regression-check all findings that were CLOSED in Round 2;
3. execute the previously blocked integrated verification gate MS-SMS-R1-014 where the environment permits;
4. keep provider-dependent findings MS-SMS-R1-002, MS-SMS-R1-007 and MS-SMS-R1-013 explicitly BLOCKED_EXTERNAL unless real MSG91/DLT evidence is available;
5. produce the final Managed SMS release recommendation.

Do not treat runner/infrastructure failure as an application PASS or FAIL.

## 2. Review branch

Use:

review/managed-sms-security-qa-r3

Create it from the exact frozen QA base SHA supplied in SECURITY_QA_R3_FINAL_CHAT_PROMPT.md.

Do not branch from later moving dev.

## 3. Primary residual finding — MS-SMS-R1-003

Round-2 residual:
B1 preview rejected non-default ports in HOST_ALLOWLIST while B2 runtime accepted them.

Round-3 B2 fix:
ManagedSmsTemplateService now applies default-port-only semantics for HOST_ALLOWLIST while preserving exact custom-port semantics for EXACT_VALUES.

### Required HOST_ALLOWLIST matrix

Verify BOTH control-plane preview semantics and runtime semantics agree.

HTTPS policy:
- https://example.com/orders/123 => ALLOW
- https://example.com:443/orders/123 => ALLOW
- https://example.com:8443/orders/123 => REJECT
- https://sub.example.com/orders/123 => ALLOW only when allowSubdomains=true
- https://sub.example.com:443/orders/123 => ALLOW only when allowSubdomains=true
- https://sub.example.com:8443/orders/123 => REJECT
- https://evil-example.com/orders/123 => REJECT
- https://example.com/admin/123 when only /orders/ is approved => REJECT
- https://user@example.com/orders/123 => REJECT
- IP literal URL => REJECT

HTTP policy with requireHttps=false:
- http://example.com/path => ALLOW
- http://example.com:80/path => ALLOW
- http://example.com:8080/path => REJECT

### Required EXACT_VALUES matrix

Exact-value behavior must remain unchanged:
- exact approved standard-port URL => ALLOW
- exact approved non-default-port URL such as https://example.com:8443/callback => ALLOW
- same URL with different port => REJECT
- same host/path but different scheme/query/fragment when not exactly approved => REJECT according to canonical equality.

### Required pipeline points

Test the same policy through:
- B1 preview;
- managed draft/compose render;
- launch validation;
- delivery-time render immediately before provider submission.

Invalid CTA must result in:
- MANAGED_SMS_CTA_NOT_APPROVED;
- zero provider calls;
- no usage/billing commit.

Do not weaken or delete:
ManagedSmsRound2CtaParityQaTests.

## 4. Regression matrix for previously CLOSED findings

Retest source/tests for:

### MS-SMS-R1-001
- ManagedSmsControlContractTests references current Managed SMS migration contract and does not regress to old V61 path.

### MS-SMS-R1-004
- delivery-time profile/Header/template/provider-binding freshness is checked at send time;
- ACTIVE-but-expired resources fail closed;
- scheduler state is not the enforcement boundary.

### MS-SMS-R1-005
- exact webhook duplicate remains idempotent;
- new old event outside replay window rejects;
- future event rejects;
- missing/malformed ts rejects;
- no Instant.now fallback for malformed provider timestamp.

### MS-SMS-R1-006
- wrong company correlation rejects;
- same-company wrong run rejects;
- foreign recipient rejects;
- usage/billing tuple verified before UsageBillingService.record;
- duplicate/concurrent acceptance remains exactly once.

### MS-SMS-R1-008
- raw MSG91/provider diagnostic text never reaches customer recipient failureReason/error response;
- 429/credit/reject/5xx stay customer-safe.

### MS-SMS-R1-009
- global/account/company/run rates remain distinct;
- campaign maximum recipient cap enforced;
- Redis exception remains fail closed.

### MS-SMS-R1-010
- approved body SHA-256 fingerprint mismatch rejects before substitution/provider call.

### MS-SMS-R1-011
- ACTIVE DLT profile review-relevant edits invalidate approval;
- PE ID cannot mutate in place;
- audit record created;
- sending becomes ineligible until re-review.

### MS-SMS-R1-012
- providerTemplateRef alone cannot make binding READY;
- nonblank runtime Flow ref and ownership/state requirements remain enforced.

## 5. External findings — do not fake closure

### MS-SMS-R1-002
Expected status unless real provider evidence exists:
BLOCKED_EXTERNAL / code-safe fail-closed.

Need real evidence for:
- production HTTPS provider observation/balance endpoint;
- parent vs child wallet authority;
- low-credit/credit recovery behavior;
- provider charging/refund semantics.

### MS-SMS-R1-007
Expected status unless real provider evidence exists:
BLOCKED_EXTERNAL / code-safe fail-closed.

Need real evidence for:
- child Send SMS AuthKey behavior;
- two child accounts;
- wrong-child Flow/credential rejection;
- shared-master commercial approval if applicable.

### MS-SMS-R1-013
Expected status unless real provider evidence exists:
BLOCKED_EXTERNAL / account-bound fail-closed.

Need real evidence for:
- per-account custom webhook headers or dedicated account endpoint;
- credential/token rotation;
- provider-native HMAC/signature if available.

Do not change any BLOCKED_EXTERNAL finding to CLOSED based only on unit tests.

## 6. Final executable verification — MS-SMS-R1-014

This is the most important final test gate.

### Backend

From:
unifydesk-app/backend

Run:

./mvnw -B spotless:check test

Then:

./mvnw -B clean test

Record:
- command;
- exit code;
- test count;
- failed test names, if any;
- relevant log/output.

### Frontend

From:
unifydesk-app/frontend

Run:

npm ci
npm test
npm run build
npm run lint
npm run format:check

Record the result of each command independently.

### Flyway / PostgreSQL

Using the repository test database contract:

Clean-path:
- create clean PostgreSQL test schema/database;
- run all migrations through V66;
- prove success.

Upgrade-path:
- create representative schema through V64;
- upgrade through V65 and V66;
- prove success.

Also run:
ManagedSmsRound2FlywayMigrationTests.

No destructive workaround is allowed to make upgrade pass.

### Redis

Use real Redis test service and run:
ManagedSmsRateLimiterRedisIntegrationTests.

Verify:
- global scope;
- account scope;
- company scope;
- run scope;
- noisy-tenant contention;
- Redis outage/failure => fail closed;
- recovery after Redis returns.

## 7. Existing QA tests to execute

At minimum execute or include in the backend full suite:

- ManagedSmsRound2CtaParityQaTests
- ManagedSmsTemplateServiceTests
- ManagedSmsDeliveryGuardRound2Tests
- ManagedSmsEventInboxServiceTests
- ManagedSmsUsageServiceDatabaseTests
- ManagedSmsProviderSafetyDatabaseTests
- ManagedSmsRateLimiterTests
- ManagedSmsRateLimiterRedisIntegrationTests
- ManagedSmsRound2FlywayMigrationTests
- ManagedMsg91GatewayRound2Tests
- ManagedSmsProviderCredentialResolverTests
- ManagedSmsWebhookTokenServiceTests
- ManagedSmsControlContractTests
- ManagedSmsProfileServiceSecurityTests
- ManagedSmsAdminTemplateDecisionTests

Do not replace the full-suite commands with only focused tests.

## 8. CUSTOMER_MANAGED regression

Prove existing customer-managed MSG91/Broadcast behavior remains unaffected.

At minimum:
- existing customer-managed MSG91 transport/regression tests remain green;
- managed-specific CTA policy does not affect CUSTOMER_MANAGED SMS;
- no managed credential resolver/rate policy is invoked on CUSTOMER_MANAGED path.

## 9. Frontend privacy regression

No Round-3 frontend production code changed, but verify existing protections remain:

- customer sees “UnifyDesk Managed SMS”, not internal MSG91 details;
- providerAccountRef/providerClientRef/AuthKey/Flow/route/shared-wallet data are not rendered;
- company switch clears prior Managed SMS state and late previous-company responses are discarded;
- managed sender still requires managed template/variables rather than arbitrary body editing.

## 10. Final feedback update

Update:

unifydesk-app/docs/managed-sms/SECURITY_QA_FEEDBACK.md

Preserve Round-1 and Round-2 history.
Append:

# Round-3 Final Retest

For each relevant original finding mark:
- CLOSED
- STILL_OPEN
- BLOCKED_EXTERNAL
- REGRESSION_FOUND

Required expected target:
- R1-003 => CLOSED
- R1-014 => CLOSED only if the complete executable gate actually ran and passed
- R1-002/R1-007/R1-013 => BLOCKED_EXTERNAL unless real provider evidence exists
- all previously CLOSED findings => remain CLOSED.

## 11. Final release recommendation

QA must give two separate conclusions:

### Application-code readiness
GO only when:
- R1-003 is CLOSED;
- no regression is found;
- R1-014 executable gate passes.

### Production Managed SMS GA readiness
GO only when application-code readiness is GO AND required MSG91/DLT external blockers are closed/accepted with real evidence.

It is valid for the final result to be:
APPLICATION CODE READY / PRODUCTION GA BLOCKED BY EXTERNAL PROVIDER VERIFICATION.

## 12. Required final report

Report:
- review branch;
- exact tested base SHA;
- final QA commit SHA;
- R1-003 result;
- regression matrix;
- backend commands/results;
- frontend commands/results;
- Flyway clean/upgrade results;
- Redis results;
- CUSTOMER_MANAGED regression result;
- remaining BLOCKED_EXTERNAL findings;
- application-code readiness;
- production GA readiness;
- any new regression/finding.

Do not merge the QA branch.
