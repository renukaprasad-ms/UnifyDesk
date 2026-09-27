# Managed SMS QA / Security Review Plan

Status: defined in Round 0; execute after integrated B1+B2+F1 candidate.

## Entry criteria

- architecture docs frozen;
- B1/B2/F1 merged into one candidate;
- Flyway succeeds from clean DB and current dev schema;
- backend compile/tests pass;
- frontend build/tests pass;
- no worker changed frozen contracts without Lead review;
- production blockers are clearly marked rather than mocked as complete.

## Functional onboarding

Test:
- no profile -> action required;
- valid PE submission;
- invalid/rejected PE;
- PE–TM chain missing;
- Header pending/approved/rejected;
- template pending/approved/provider syncing/ready/rejected;
- new template version supersedes prior without changing history;
- CTA validation;
- consent/opt-out state;
- tenant suspension/resume.

## Broadcast integration

- CUSTOMER_MANAGED MSG91 unchanged;
- managed sender appears only when Header eligible;
- managed compose requires approved template;
- scratch managed SMS rejected;
- sender/template Header mismatch rejected;
- create/update/schedule/send revalidation;
- existing run/outbox/Kafka/batch/recipient/retry/pause/cancel/stats reused;
- no parallel campaign engine.

## Template abuse tests

Attempt:
- add text before/after template;
- inject placeholder syntax through variable;
- overlong variable;
- URL in non-URL variable;
- non-whitelisted/different-domain URL;
- callback number mismatch;
- undeclared variable;
- missing required variable;
- use superseded/rejected template version.

Expected: no provider request.

## Tenant isolation / IDOR

Substitute another company's:
profileId, headerId, templateId/versionId, senderId, runId, recipientId, provider binding IDs.
Expected: not found/forbidden with no data leakage.

## Provider/runtime

Inject:
- provider 2xx accepted;
- 4xx permanent;
- 429;
- 5xx;
- timeout before request;
- connection reset after request may have been sent;
- insufficient credit;
- account/child suspended.

Verify correct ACCEPTED/FAILED/RETRY/UNKNOWN and no unsafe retry.

## DLR/event tests

Exercise:
- delivered;
- failed;
- rejected;
- duplicate;
- out-of-order;
- malformed;
- forged secret/signature;
- wrong provider account;
- wrong company correlation;
- unmatched correlation;
- late event;
- repeated retries from provider.

Verify immutable inbox, exactly-once projection/stat mutation and no duplicate billing.

## Metering

- reserve;
- commit acceptance;
- release definite pre-accept failure;
- hold UNKNOWN;
- duplicate acceptance;
- duplicate DLR;
- segment count;
- period rollover;
- hard limit concurrent sends;
- adjustment/refund if supported.

## Rate/balance safety

Simulate multiple backend pods and tenants:
- platform cap;
- account cap;
- company cap;
- campaign cap;
- new-tenant cap;
- global kill switch;
- company suspension;
- Redis unavailable;
- provider balance floor;
- noisy tenant starvation attempt.

## Security review

Review:
- company-scoped repository methods;
- raw-ID trust;
- secret storage;
- exception/log redaction;
- admin permissions/audits;
- webhook trust;
- event payload retention;
- SQL ownership constraints;
- mass assignment/DTO validation;
- SSRF possibilities in evidence/URL fields;
- XSS rendering of evidence/rejection text;
- dependency scans.

## Compliance verification

With real MSG91/DLT account validate:
- child/client creation and billing relationship;
- customer PE/Header mapping;
- PE_ID pass-through mode if used;
- DLT Template ID mapping;
- Flow/template creation API;
- actual PE–TM chain expected by DLT scrubber;
- CTA behavior;
- Webhook (New) auth options;
- CRQID echo/correlation;
- provider rate/429 behavior;
- credit/balance behavior;
- rejected/failed credit semantics.

## Load/resilience

- large Broadcast recipient set;
- event duplicate storm;
- DB locking on usage reservation;
- Redis contention;
- provider callback burst;
- worker crash around provider submission;
- provisioning operation retries/leases.

No unbounded in-memory accumulation.

## Exit criteria

QA/SEC produces SECURITY_QA_FEEDBACK.md with:
- finding ID;
- severity;
- invariant violated;
- evidence/reproduction;
- affected worker;
- recommended fix boundary;
- retest status.

Production release additionally requires closure of external blockers in RESEARCH-DLT-MSG91.md.
