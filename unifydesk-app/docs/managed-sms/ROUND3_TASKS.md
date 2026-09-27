# UnifyDesk Managed SMS — Round-3 Final Remediation Tasks

Status: FROZEN
Planning parent dev SHA: 05496585fd54983034a12a17822461c143148b67
Round-2 QA source: SECURITY_QA_FEEDBACK.md

## 1. Round-2 QA disposition

Round-2 QA result:
- CLOSED: 9
- PARTIALLY_FIXED: 1
- STILL_OPEN execution gate: 1
- BLOCKED_EXTERNAL: 3
- REGRESSION_FOUND: 0

No Round-3 B1 or F1 implementation work is required.

The only remaining application-code defect is:

MS-SMS-R1-003 — CTA URL/callback allowlist enforcement
Round-2 status: PARTIALLY_FIXED

Residual:
B1 control-plane preview correctly rejects non-default ports for HOST_ALLOWLIST.
B2 runtime ManagedSmsTemplateService currently accepts a non-default port when host/path otherwise match.

Example:
policy allowed host = example.com
candidate = https://example.com:8443/orders/123

Expected for HOST_ALLOWLIST:
REJECT unless a future explicit approved-port contract is introduced.

Current:
- B1 preview: REJECT
- B2 runtime: ACCEPT

Round 3 must make runtime behavior match the already-frozen B1 policy.

## 2. Worker

Only B2 is required.

Branch:
fix/managed-sms-cta-parity-r3

B2 branches from the exact frozen worker base SHA published in BACKEND_B2_R3_CHAT_PROMPT.md.

Do not create a B1 or F1 Round-3 branch.

## 3. B2 ownership

B2 may modify only:

- unifydesk-app/backend/src/main/java/com/messaging/managedsms/runtime/ManagedSmsTemplateService.java
- unifydesk-app/backend/src/test/java/com/messaging/managedsms/runtime/**
- existing QA CTA parity test only if needed to expand coverage without weakening its assertion

No database migration is required or allowed for this round.
Do not modify control-plane code, delivery/event/metering/safety code, frontend, application.yaml, .env.example, or architecture contracts.

## 4. Frozen port semantics

For CTA policyVersion=2:

### HOST_ALLOWLIST

Allowed ports:
- omitted port
- explicit 443 for https
- explicit 80 for http

After canonicalization, default ports may be normalized away.

Rejected:
- https://example.com:8443/...
- http://example.com:8080/...
- any other non-default explicit port

This must be enforced before host/subdomain/path allowlist acceptance.

Do NOT add an allowedPorts field in Round 3.
Do NOT broaden the contract.
A future explicit-port feature requires a new architecture/API decision.

### EXACT_VALUES

Keep the existing exact canonical comparison behavior.
An exact approved URL containing a non-default port remains governed by exact-value equality and is not changed by this HOST_ALLOWLIST fix.

Example:
approved exact value = https://example.com:8443/callback
candidate exactly equal after canonicalization = allowed.

The non-default-port rejection applies specifically to HOST_ALLOWLIST mode.

## 5. Implementation requirement

In B2 ManagedSmsTemplateService:

- ParsedUrl must retain enough information to determine the normalized port.
- HOST_ALLOWLIST validation must call a B1-equivalent safe-host-port check before host matching.
- Safe port rule:
  port < 0
  OR (scheme=https AND port=443)
  OR (scheme=http AND port=80)

Because parseUrl currently normalizes explicit default 80/443 to no port, port < 0 is sufficient after that normalization for default-port candidates.
Non-default retained ports must fail HOST_ALLOWLIST.

Do not alter:
- IP literal rejection
- user-info rejection
- IDNA/lowercase host normalization
- subdomain boundary matching
- path-prefix rules
- callback rules
- fingerprint validation
- template variable rules.

## 6. Required tests

At minimum prove B1/B2 parity for:

HOST_ALLOWLIST:
- https://example.com/orders/123 => allow
- https://example.com:443/orders/123 => allow
- https://example.com:8443/orders/123 => reject
- http://example.com/path with requireHttps=false => allow
- http://example.com:80/path with requireHttps=false => allow
- http://example.com:8080/path => reject
- allowed subdomain/default port => allow when allowSubdomains=true
- allowed subdomain/non-default port => reject
- disallowed host => reject
- path outside prefix => reject

EXACT_VALUES:
- exact approved URL without custom port => allow
- exact approved URL with non-default port => allow only when exact canonical value matches
- same URL with different port => reject

Existing QA test:
ManagedSmsRound2CtaParityQaTests

The test that captures https://example.com:8443/... must turn green because production code is fixed, not because the assertion is weakened.

## 7. Findings not assigned to B2

### MS-SMS-R1-002 — BLOCKED_EXTERNAL
Provider observation code is fail-closed. Requires real production-contract MSG91 balance/observation evidence.

### MS-SMS-R1-007 — BLOCKED_EXTERNAL
Child/master credential path is fail-closed. Requires real MSG91 child credential/Flow API evidence.

### MS-SMS-R1-013 — BLOCKED_EXTERNAL
Webhook code is account-bound/fail-closed. Requires actual provider account header/endpoint/signature capability evidence.

Do not change code for these in Round 3 unless QA discovers a new regression.

### MS-SMS-R1-014 — STILL_OPEN execution gate
This is not assigned as an application-code fix.

Final QA must actually execute, on an operational runner or authorized checkout:
- ./mvnw -B spotless:check test
- ./mvnw -B clean test
- npm ci
- npm test
- npm run build
- npm run lint
- npm run format:check
- clean Flyway through V66
- representative V64 -> V66 upgrade
- real Redis scope/concurrency tests
- Redis outage fail-closed test

If the runner again fails before steps start, result remains NOT EXECUTED.

## 8. Final integration/retest flow

1. B2 fixes CTA parity only.
2. Project Manager reviews the B2 diff for scope.
3. Merge B2 into current dev.
4. Freeze one exact integrated Round-3 SHA.
5. QA branches review/managed-sms-security-qa-r3 from exactly that SHA.
6. QA retests MS-SMS-R1-003 and confirms no regression in all previously closed findings.
7. QA executes the full R1-014 command matrix.
8. External MSG91/DLT blockers remain explicitly BLOCKED_EXTERNAL until real evidence exists.

## 9. Release target

Application code can be considered security-remediated when:
- R1-003 becomes CLOSED;
- all previously CLOSED findings remain closed;
- R1-014 execution gate passes.

Production GA additionally requires closure/acceptance of the external MSG91/DLT blockers with real provider evidence.
