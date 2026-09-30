# Contacts & Audience QA / Security Plan

QA begins only after B1+B2+F1+Lead integration is frozen.

## 1. Build/regression

Backend:
- ./mvnw spotless:check
- ./mvnw test
- ./mvnw clean test
- ./mvnw package -DskipTests

Frontend:
- npm ci
- npm run format:check
- npm run lint
- npm test
- npm run build

## 2. Flyway

Current pre-module head: V79.

Verify:
- clean V1 -> V82
- representative V79 -> V82 upgrade
- schema indexes/constraints
- rollback is not assumed; forward-fix strategy documented

## 3. Tenant isolation / authorization

Test every resource type with:
- owner company
- other company resource ID
- nonexistent ID
- permission missing
- stale/revoked permission for async job

No cross-company existence oracle.

## 4. Contact/identity

- multiple identities
- primary uniqueness
- email normalization
- IDN domain
- phone E.164
- duplicate concurrent create
- identity transfer forbidden except merge
- delete/purge blocks lookup/send

## 5. Lists/tags/custom fields

- duplicate concurrent membership
- count correctness/reconciliation
- archived metadata behavior
- typed field validation
- select option validation
- multi-select indexes/query
- foreign field/tag/list ID

## 6. Segments

- AST size/depth/predicate limits
- operator/type matrix
- malicious field/operator payload
- SQL injection attempts
- company predicate cannot be bypassed
- pagination stability
- count cache invalidation
- index-friendly representative EXPLAIN
- large OR/expensive count -> async behavior

## 7. Consent/suppression

- preference event append + projection atomicity
- replay idempotency
- OPTED_OUT precedence
- hard suppression precedence
- provider event replay
- lift authorization
- post-snapshot opt-out blocks send
- delete/reimport retains suppression tombstone behavior

## 8. Merge/concurrency

- reversed merge no deadlock
- stale versions
- merge vs identity create
- merge vs delete
- suppression preserved
- list/tag membership union

## 9. Import abuse

- oversized file
- oversized row/cell/column count
- invalid encoding
- malformed CSV
- formula payload
- invalid email/phone
- duplicate identities
- same idempotency key same request
- same key different checksum/mapping
- worker crash/reclaim
- error report tenant isolation
- storage release on failure/expiry

## 10. Export abuse

- foreign source
- unauthorized requester
- revoked permission before download
- formula injection
- large streaming export memory bound
- expired download
- guessed export ID
- signed URL TTL
- cleanup/storage release

## 11. Bulk

- huge explicit ID payload rejected
- selection job bounded
- delete vs update
- duplicate idempotency
- partial failure reporting
- foreign IDs ignored safely/not leaked

## 12. Broadcast integration

Legacy broadcast group:
- unchanged

List/Segment:
- snapshot at run materialization
- deterministic identity selection
- destination dedupe
- candidate vs eligible count
- suppression exclusion
- current eligibility recheck
- historical recipient survives Contact edit/delete
- retry does not rematerialize a different audience

## 13. Inbox integration

- known sender resolves
- unknown sender remains valid
- cross-company same identity resolves within correct tenant only
- activity timeline has no copied raw provider payload

## 14. Performance

Representative DB-backed tests for:
- 10k
- 100k
- synthetic plan/EXPLAIN for million-scale indexes
- import batch memory
- export streaming memory
- segment keyset paging
- snapshot batching

Do not claim 1m production SLA from synthetic unit tests alone.

## 15. Final disposition

QA report concludes:
RELEASE-READY
or
NOT RELEASE-READY

Real provider integration is reported separately from local/mock verification.
