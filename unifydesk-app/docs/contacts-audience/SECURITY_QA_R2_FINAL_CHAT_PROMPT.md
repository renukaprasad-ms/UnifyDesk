# QA/SEC Prompt — Contacts & Audience Round-2 Final Verification

Status: PREPARED — DO NOT START UNTIL LEAD STAMPS THE FINAL R2 INTEGRATED SHA

Review branch:
`review/contacts-audience-security-qa-r2`

Round-2 remediation source base for reference only:

`f6a04c4596240d0c43bde6ef83c66619abbd9a02`

The actual QA branch MUST be created from the later exact integrated Round-2 SHA after B1+B2+F1+Lead remediation is merged.

Exact integrated Round-2 QA base:

`<LEAD_TO_STAMP_AFTER_R2_INTEGRATION>`

Read:
- `SECURITY_QA_FEEDBACK.md`
- `ROUND2_TASKS.md`
- all frozen Contacts/Audience contracts
- final Round-2 integration status

## Primary mission

Re-test every Round-1 finding:

- CA-R1-001 through CA-R1-013

Do not assume a finding is fixed because the implementation branch says so.

Verify each precise acceptance condition.

## Mandatory Round-2 regression areas

In addition to targeted findings, verify no regression in:

- tenant isolation
- Contact/Segment optimistic concurrency
- Lists/tags/custom fields
- consent/suppression/eligibility
- import/export
- bulk processing
- Contact delete/purge
- company purge
- List/Segment Broadcast snapshots
- pre-provider eligibility recheck
- legacy Broadcast groups
- Inbox identity lookup/activity
- JS-safe IDs
- responsive frontend behavior

## Special Round-2 checks

### Authorization

Prove:
- MODIFY-only cannot queue bulk DELETE;
- revoked users cannot execute user-owned queued import/export/bulk jobs;
- delete privacy purge still completes after actor permission revocation;
- import consent mappings require consent.modify at queue and execution.

### Idempotency

Prove:
- bulk same-key/same-request replays;
- same-key/different request conflicts.

### Provenance

Prove public users cannot persist internal/provider/system preference source classes.

### Identity relation

Prove:
- preference/suppression Contact A + Contact B identity is rejected;
- DB invariant rejects mismatched triple;
- null tombstone state remains valid;
- channel/identity mismatch is rejected.

### Import crash recovery

Inject rollback/crash around the mutation/checkpoint boundary and prove uninterrupted vs reclaimed execution converges for SKIP/MERGE_EXACT/FAIL.

### Company purge

Use merged Contacts spanning multiple batches and prove company purge reaches completion without self-FK failure.

### Bulk performance

Measure/query-count review representative 500/2000 operations.

Verify UPDATE_FIELD is batched and DELETE avoids one long transaction across the whole batch.

### Cursor key

Prove two replicas/restarts sharing configured key accept the same cursor and missing stable key fails explicitly.

### Frontend

Prove default export request matches backend supported fields and DELETED is not sent by normal UI.

### Freeze integrity

Every final R2 QA handoff/status document must contain one identical exact integrated SHA.

## Commands

Backend:
`./mvnw spotless:check`
`./mvnw test`
`./mvnw clean test`
`./mvnw package -DskipTests`

Frontend:
`npm ci`
`npm run format:check`
`npm run lint`
`npm test`
`npm run build`

PostgreSQL:
- clean -> V83
- representative V79 -> V83

Performance:
- DB-backed representative tests where feasible
- EXPLAIN/query-plan review for changed bulk/import/purge paths

If execution is unavailable, mark `EXECUTION BLOCKED`; do not call it PASS.

## Final disposition

Write:

`unifydesk-app/docs/contacts-audience/SECURITY_QA_FEEDBACK_R2_FINAL.md`

Conclude exactly one:

`RELEASE-READY`

or

`NOT RELEASE-READY`

Do not silently modify production code.
Do not merge the QA branch.
