# QA/SEC Prompt — Contacts & Audience Round-2 Final Verification

Status: READY FOR FINAL QA/SEC

You are QA/SEC for the FINAL Round-2 verification of UnifyDesk Contacts & Audience Management.

Repository:

`renukaprasad-ms/messaging`

Application:

`unifydesk-app/`

Review branch:

`review/contacts-audience-security-qa-r2`

Round-2 remediation source base for historical reference only:

`f6a04c4596240d0c43bde6ef83c66619abbd9a02`

Your EXACT integrated Round-2 QA production-code base is:

`75adcc7dd17105c2a62570c56a8e7c2509321398`

Create:

`review/contacts-audience-security-qa-r2`

from EXACTLY:

`75adcc7dd17105c2a62570c56a8e7c2509321398`

Do NOT branch from later moving `dev`.
Do NOT branch from the Round-1 base.
Do NOT branch from an individual remediation worker head.

Read the latest Contacts/Audience documentation from `dev`, but review/test production code exactly at the frozen SHA above.

## Integrated Round-2 lineage

- B1 PR #210
  - worker head: `c38f71b5e476937484aa62909e6d5ac37ff5eb46`
- B2 PR #212
  - worker head: `32dc3648d33ea701600ee33bf332c0ace0d8b421`
- F1 PR #211
  - worker head: `78ec9046db3ac3a40487c2ec8966a53468d8dff8`
- Lead PR #213
  - Lead head: `1417762df87dd51a36e705a8db291c9f70218f5b`
- integrated merge SHA:
  `75adcc7dd17105c2a62570c56a8e7c2509321398`
- Round-2 migration:
  `V83__contacts_audience_round2_security.sql`

## Read first

- `SECURITY_QA_FEEDBACK.md`
- `ROUND2_TASKS.md`
- `INTEGRATION_STATUS.md`
- `ARCHITECTURE.md`
- `DATA-MODEL.md`
- `API-CONTRACT.md`
- `SECURITY.md`
- `PERFORMANCE.md`
- `SEGMENTATION.md`
- `CONSENT-SUPPRESSION.md`
- `IMPORT-EXPORT.md`
- `CONTACT-MERGE.md`
- `INTEGRATIONS.md`
- `QA-SEC-PLAN.md`

## Primary mission

Re-test EVERY Round-1 finding:

- CA-R1-001
- CA-R1-002
- CA-R1-003
- CA-R1-004
- CA-R1-005
- CA-R1-006
- CA-R1-007
- CA-R1-008
- CA-R1-009
- CA-R1-010
- CA-R1-011
- CA-R1-012
- CA-R1-013

Do not accept a worker's claim that a finding is fixed without independently verifying the precise QA acceptance condition.

Do not silently modify production implementation.

Write the final report to:

`unifydesk-app/docs/contacts-audience/SECURITY_QA_FEEDBACK_R2_FINAL.md`

Use new finding IDs for newly discovered defects, for example:

`CA-R2-001`

## Mandatory Round-2 verification

### 1. Authorization

Prove:

- a role with `contact.modify` but without `contact.delete` cannot queue bulk DELETE;
- DELETE-authorized user can queue it;
- current authorization is rechecked when user-owned import/export/bulk jobs are claimed or reclaimed;
- revoked/removed users cannot execute those queued capabilities;
- Contact privacy purge continues after the already-authorized delete even if actor access is later revoked;
- import without consent mappings requires import only;
- import with consent mappings requires BOTH `contact.import` and `contact.consent.modify`;
- consent permission revoked after queue prevents consent mutation before worker execution.

### 2. Bulk idempotency

Prove:

- same company + same idempotency key + same canonical request returns original operation;
- same key + different selection conflicts;
- same key + different operation conflicts;
- concurrent same-key/same-request converges;
- concurrent same-key/different-request does not silently succeed as the wrong operation.

### 3. Preference provenance

Prove the public user endpoint cannot persist trusted/internal source classes such as:

- IMPORT
- API
- MANAGED_EMAIL_EVENT
- MANAGED_SMS_EVENT
- META_WHATSAPP
- SYSTEM_MIGRATION

Verify valid user changes persist server-owned USER_UI provenance.

Verify authorized import consent uses the explicit trusted import path and records IMPORT actor/source without accepting caller-spoofed provenance.

### 4. Contact/identity invariant

Service tests:

- identity belongs to another Contact in same company -> rejected;
- identity belongs to another company -> safe rejection;
- EMAIL preference/suppression with PHONE identity -> rejected;
- SMS/WhatsApp with EMAIL identity -> rejected;
- valid Contact-level null identity remains supported where contract allows.

Database/V83 tests:

- current preference mismatched Contact/identity triple is rejected;
- current suppression mismatched triple is rejected;
- tombstone state with null contact/identity remains valid;
- deleting/purging identities preserves intended suppression safety;
- V83 reconciliation of pre-existing malformed current rows is conservative.

### 5. Import crash atomicity

The parsed CSV/storage I/O must remain outside the mutation transaction.

For each bounded batch, prove one DB transaction covers:

- Contact/identity/tag/custom-field/List/preference mutations;
- row-failure persistence;
- durable import cursor/counters.

Inject rollback/crash between mutation logic and checkpoint completion.

Verify for SKIP, MERGE_EXACT and FAIL:

- rollback commits neither mutation nor cursor advancement;
- committed batch resumes after its durable cursor;
- reclaimed execution converges to the same final Contacts, memberships, preferences, counters and errors as uninterrupted execution;
- a successful row never becomes a duplicate failure solely because of replay.

Async progress may lag the durable DB checkpoint; it must not be the source of truth.

### 6. Company purge / merge self-FK

Create merged Contact pairs spanning more than one purge batch.

Verify bounded company purge:

- clears merge dependencies safely before physical Contact deletion;
- does not enter an FK retry loop;
- eventually reaches company PURGED lifecycle state;
- leaves no Contacts PII;
- remains retryable after a mid-purge failure.

### 7. Bulk performance and transactions

UPDATE_FIELD:
- validate type once per bounded batch;
- use batch database mutation rather than delete/upsert query pairs per Contact;
- inspect representative query count at 500 and 2000 Contacts.

DELETE:
- verify there is not one transaction holding locks across an entire 500/2000 Contact batch;
- each Contact/small bounded unit keeps deletion lifecycle and privacy-purge hook semantics;
- worker checkpoint/recovery remains correct.

Also regression-test ADD/REMOVE TAG and LIST batching.

### 8. Cursor signing

Prove:

- two backend instances using the same configured key accept each other's cursors;
- restart with the same key accepts pre-restart cursor;
- different keys reject;
- tampering rejects;
- missing/blank stable secret causes explicit configuration/startup failure;
- no secret appears in failure/log output;
- encode/decode remains O(1) relative to Contacts count.

### 9. Frontend contract

Verify:

- normal Contacts export sends only backend-supported fields;
- it no longer sends primaryEmail/primaryPhone unless backend contract explicitly supports them;
- DELETED is not offered in the normal status filter;
- stale/unsupported URL status is normalized safely;
- existing cursor paging, bulk selection, mobile/responsive and accessibility behavior remain intact.

### 10. Migrations

Mandatory PostgreSQL verification:

- clean database -> V83
- representative V79 -> V83
- V80/V81/V82/V83 ordering
- V83 unique/index/FK behavior
- existing valid preference/suppression data migrates
- malformed current Contact/identity relationships reconcile conservatively
- suppression tombstones remain valid
- legacy Broadcast rows remain valid
- List/Segment Broadcast rows remain valid
- company purge succeeds with merged Contacts

### 11. Security/tenant regression

Repeat the Round-1 tenant/IDOR matrix for:

- Contacts
- identities
- Lists
- tags
- custom fields
- Segments
- imports
- exports/downloads
- bulk operations
- preferences
- suppressions
- activity
- snapshots
- Broadcast provenance

No foreign ID may become an existence oracle.

### 12. Broadcast/Inbox regression

Verify unchanged:

- legacy `broadcast_groups`
- List/Segment frozen snapshot
- post-snapshot opt-out/suppression blocks provider submission
- changed identity cannot silently redirect a frozen send
- retries use same snapshot/destination
- Inbox unknown participants remain valid
- Contact identity lookup remains tenant-scoped
- Contact activity exposes safe summaries only

## Complexity/performance review

Review changed paths for:

- time complexity
- space complexity
- N+1 behavior
- transaction duration
- indexes/query plans
- bounded memory
- retry/idempotency
- lock behavior

Use DB-backed representative tests where feasible:

- 500 / 2000 bulk operations
- 10k Contacts
- 100k Contacts

Use EXPLAIN for relevant changed queries.

Do not claim a 1M production SLA from source inspection alone.

## Required commands

Backend:

`cd unifydesk-app/backend`

`./mvnw spotless:check`

`./mvnw test`

`./mvnw clean test`

`./mvnw package -DskipTests`

Frontend:

`cd unifydesk-app/frontend`

`npm ci`

`npm run format:check`

`npm run lint`

`npm test`

`npm run build`

## Current CI warning

At final Round-2 integration freeze, inspected GitHub Actions jobs for the Lead head still completed with zero workflow steps.

Those jobs are neither PASS nor proof of product-code failure.

Run required commands on a functioning environment if available. Otherwise mark them:

`EXECUTION BLOCKED`

Never report an unexecuted command as PASS.

## Evidence classification

Use:

- `SOURCE/CONTRACT REVIEWED`
- `LOCAL/MOCK VERIFIED`
- `DB INTEGRATION VERIFIED`
- `REAL PROVIDER VERIFIED`
- `EXECUTION BLOCKED`

## Freeze-integrity check

Before reviewing implementation, verify these active documents all name exactly:

`75adcc7dd17105c2a62570c56a8e7c2509321398`

- `INTEGRATION_STATUS.md`
- `ROUND2_TASKS.md`
- `SECURITY_QA_R2_FINAL_CHAT_PROMPT.md`

If they disagree, create a Lead-owned finding and do not silently choose another SHA.

## Final disposition

Conclude with exactly one:

`RELEASE-READY`

or

`NOT RELEASE-READY`

Do not merge the QA branch.

Report:

- review branch
- exact QA base SHA
- final QA SHA
- files changed
- commands actually executed
- migration results
- finding-by-finding CA-R1-001..013 disposition
- any new CA-R2 findings
- security/concurrency/performance results
- blocked verification
- final disposition
