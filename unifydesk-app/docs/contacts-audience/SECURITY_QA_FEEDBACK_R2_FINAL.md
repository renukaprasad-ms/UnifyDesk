# Contacts & Audience Round-2 Final Security / QA Feedback

## Review identity

- Review branch: `review/contacts-audience-security-qa-r2`
- Exact frozen integrated Round-2 production base reviewed:
  `75adcc7dd17105c2a62570c56a8e7c2509321398`
- B1 PR #210 worker head: `c38f71b5e476937484aa62909e6d5ac37ff5eb46`
- B2 PR #212 worker head: `32dc3648d33ea701600ee33bf332c0ace0d8b421`
- F1 PR #211 worker head: `78ec9046db3ac3a40487c2ec8966a53468d8dff8`
- Lead PR #213 head: `1417762df87dd51a36e705a8db291c9f70218f5b`
- Round-2 migration reviewed: `V83__contacts_audience_round2_security.sql`
- Production implementation modified during QA: **NO**
- QA branch merged: **NO**

Latest Contacts/Audience documentation was read from `dev`, while all production implementation
review in this report is against the exact frozen SHA above.

## Evidence classification

This report deliberately distinguishes four evidence levels:

- **SOURCE/CONTRACT REVIEWED** — implementation and frozen contracts were independently inspected.
- **LOCAL/MOCK VERIFIED** — focused test source exists and directly exercises the acceptance
  condition, but the test command was not executable in this environment.
- **DB INTEGRATION VERIFIED** — requires real PostgreSQL execution. No item is labeled this unless
  actually executed.
- **EXECUTION BLOCKED** — the required command/runtime test could not be executed.

No zero-step GitHub Actions job is treated as PASS.

## Execution environment

The QA runtime does not contain a repository checkout. The available container contains only an
unrelated pasted text artifact under `/mnt/data`; therefore Maven, npm and Flyway/PostgreSQL
commands cannot be executed against the repository here.

GitHub Actions also cannot substitute for execution:

- exact integrated SHA `75adcc7dd17105c2a62570c56a8e7c2509321398` has no workflow run;
- Lead head `1417762df87dd51a36e705a8db291c9f70218f5b` has workflow runs;
- every inspected backend/frontend/full/matrix job for that head terminated with **zero workflow
  steps**.

The repository Kubernetes manifest targets PostgreSQL `18.6-alpine`, and V83 was source-reviewed
against that declared database family. This is not a substitute for executing the migration.

---

# Round-1 finding dispositions

| Finding | Final Round-2 disposition | Evidence |
| --- | --- | --- |
| CA-R1-001 | **SOURCE-RESOLVED; EXECUTION BLOCKED** | DELETE queue/claim now require `contact.delete`; focused policy tests exist. |
| CA-R1-002 | **SECURITY SOURCE-RESOLVED; new consistency defect CA-R2-002** | Claim/reclaim reauthorization blocks workers, but denied resources are not terminalized. |
| CA-R1-003 | **SOURCE-RESOLVED; EXECUTION BLOCKED** | Consent-bearing import requires import + consent.modify at queue and claim. |
| CA-R1-004 | **SOURCE-RESOLVED; DB CRASH INJECTION BLOCKED** | Batch coordinator now encloses mutation + failures + durable checkpoint in one transaction. |
| CA-R1-005 | **SOURCE-RESOLVED; DB PURGE TEST BLOCKED** | Company purge clears merged self-FK references before physical Contact deletion. |
| CA-R1-006 | **SOURCE-RESOLVED; concurrent DB test BLOCKED** | Bulk idempotency now binds canonical selection + operation and conflicts on mismatch. |
| CA-R1-007 | **NOT FULLY CLOSED — CA-R2-001** | Query/transaction design improved, but DELETE crash checkpoint/recovery accounting is incorrect. |
| CA-R1-008 | **SOURCE-RESOLVED; V83 DB execution BLOCKED** | Runtime target validation plus V83 composite owned-identity FKs are present. |
| CA-R1-009 | **SOURCE-RESOLVED; EXECUTION BLOCKED** | Public preference mutation only permits server-owned USER_UI provenance. |
| CA-R1-010 | **SOURCE-RESOLVED; EXECUTION BLOCKED** | Stable shared cursor key required; random process-local fallback removed. |
| CA-R1-011 | **SOURCE-RESOLVED; frontend execution BLOCKED** | Default exports use only frozen backend-supported fields. |
| CA-R1-012 | **RESOLVED** | Active Round-2 QA handoff/freeze docs consistently use `75adcc7...`. |
| CA-R1-013 | **SOURCE-RESOLVED; frontend execution BLOCKED** | DELETED is removed and stale URL status is normalized safely. |

---

# Detailed Round-2 verification

## 1. Authorization

### Bulk DELETE

**SOURCE/CONTRACT REVIEWED**

`ContactProcessingController.bulk(...)` no longer authorizes every operation with MODIFY. It:

1. resolves the authenticated user;
2. maps the requested operation;
3. calls `ContactProcessingAuthorizationPolicy.requireBulkQueue(...)`.

The policy maps:

- DELETE -> `ContactAudiencePermissions.DELETE`
- ADD/REMOVE TAG, ADD/REMOVE LIST, UPDATE_FIELD -> `ContactAudiencePermissions.MODIFY`

The async claim path independently reloads the stored bulk operation and applies the same permission
mapping before the worker runs.

Focused test source:
`ContactProcessingAuthorizationPolicyTests.bulkDeleteRequiresDeleteInsteadOfModify()`.

**CA-R1-001 source fix is valid.**

### Async permission revalidation

**SOURCE/CONTRACT REVIEWED / LOCAL-MOCK TEST SOURCE PRESENT**

`ContactJobDispatcher.processClaimedJob(...)` calls
`ContactProcessingAuthorizationPolicy.authorizeClaim(...)` before import/export/bulk workers.

For user-owned jobs, policy verifies:

- job has company/user/resource;
- current active company membership;
- resource is company-scoped;
- persisted requester matches async job user;
- current operation-specific permission.

Import, export and bulk therefore cannot continue through the worker after the initiating user's
membership/permission is revoked.

`CONTACT_DELETE_PURGE` is intentionally allowed without reauthorizing the original actor, so
already-authorized privacy deletion is not stopped by later permission revocation.

Focused test source covers:

- revoked membership on claim/reclaim;
- revoked consent permission on queued import;
- current DELETE permission on claimed bulk delete;
- privacy purge continuing after actor revocation;
- denied dispatcher claim never invoking the worker.

**Security boundary for CA-R1-002 is source-resolved.**

A separate lifecycle/status consistency defect remains and is recorded as **CA-R2-002**.

### Import + consent privilege separation

**SOURCE/CONTRACT REVIEWED / LOCAL-MOCK TEST SOURCE PRESENT**

At commit:

- `contact.import` is required;
- if the normalized stored mapping contains consent mappings,
  `contact.consent.modify` is also required.

At claim/reclaim, the stored import mapping is parsed and both permissions are rechecked before the
worker runs.

Consent-free import does not require consent.modify.

`ContactImportMappingMapper` accepts consent mappings only with source `IMPORT`.

**CA-R1-003 source fix is valid.**

---

## 2. Bulk idempotency

**SOURCE/CONTRACT REVIEWED / LOCAL-MOCK TEST SOURCE PRESENT**

`ContactBulkService.create(...)` now:

- normalizes explicit IDs by dedupe + sort;
- normalizes FILTER text/status;
- canonicalizes UPDATE_FIELD JSON object key ordering;
- serializes canonical selection and operation;
- acquires a company/key transaction-scoped advisory lock;
- compares an existing operation's canonical selection + operation;
- returns the existing operation only for the same canonical request;
- throws `CONTACT_BULK_IDEMPOTENCY_CONFLICT` for changed selection/operation.

Focused tests cover:

- same key + same logical request;
- different selection;
- different operation;
- equivalent JSON object field order.

Concurrent same-key behavior still requires PostgreSQL execution to prove the advisory-lock +
unique-index path under real concurrency.

**CA-R1-006 is source-resolved; concurrent DB verification is EXECUTION BLOCKED.**

---

## 3. Preference provenance

**SOURCE/CONTRACT REVIEWED / LOCAL-MOCK TEST SOURCE PRESENT**

`ContactPreferenceService.setFromUser(...)`:

- rejects every non-null source other than USER_UI;
- always persists `PreferenceSource.USER_UI`;
- always records `ActorType.USER` with the authenticated server-owned user ID.

`setFromImport(...)` is a separate trusted path and always persists:

- source = IMPORT;
- actorType = IMPORT;
- actorId = import ID.

The public controller cannot make a USER mutation appear as IMPORT, API,
MANAGED_EMAIL_EVENT, MANAGED_SMS_EVENT, META_WHATSAPP or SYSTEM_MIGRATION.

Focused tests iterate non-USER_UI source enum values and verify import provenance separately.

**CA-R1-009 source fix is valid.**

---

## 4. Contact / identity invariant and V83

### Runtime service validation

**SOURCE/CONTRACT REVIEWED / LOCAL-MOCK TEST SOURCE PRESENT**

`ContactConsentTargetValidator.requireWritableTarget(...)` enforces:

- contact-level null identity only for an ACTIVE Contact in the company;
- non-null identity must be ACTIVE;
- identity must belong to the specified company + Contact;
- EMAIL channel requires EMAIL identity;
- SMS/WHATSAPP require PHONE identity.

Focused tests cover same-company foreign Contact, cross-company identity, channel mismatch and valid
Contact-level null identity.

### Database invariant

**SOURCE/CONTRACT REVIEWED; DB EXECUTION BLOCKED**

V83:

1. reconciles malformed current preferences by deleting projections whose identity does not match
   company/contact;
2. reconciles malformed current suppressions conservatively by clearing identity_id while retaining
   the Contact-level suppression;
3. creates a unique identity key on `(company_id, contact_id, id)`;
4. adds current preference FK on `(company_id, contact_id, identity_id)`;
5. adds current suppression FK on the same triple with column-specific
   `ON DELETE SET NULL (identity_id)`.

This preserves company/contact on identity deletion and broadens an identity-scoped suppression to
Contact-level safety rather than silently losing it.

Null Contact/identity HMAC tombstones remain outside the owned-identity FK because the nullable
composite FK does not constrain the tombstone case.

`ContactDeletePurgeWorker` still fingerprints active suppression destinations before
`ContactRepository.purgeProfile(...)` deletes identities/profile PII.

**CA-R1-008 is source-resolved, but its mandatory V83 PostgreSQL acceptance test is
EXECUTION BLOCKED.**

---

## 5. Import crash atomicity

**SOURCE/CONTRACT REVIEWED; CRASH/ROLLBACK DB TEST EXECUTION BLOCKED**

Round-2 introduces `ContactImportBatchCoordinator`.

CSV/storage parsing remains outside the transaction in `ContactImportWorker`.

For each already-parsed bounded batch, the coordinator is `@Transactional` and performs:

1. `ContactImportMutationPort.applyBatch(...)`;
2. row-failure persistence;
3. durable import cursor/counter advancement.

The production `ContactImportMutationAdapter.applyBatch(...)` joins that outer transaction.
Per-row safe failures use nested savepoints through `PROPAGATION_NESTED`; successful Contact,
identity, tag, custom-field, List and consent mutations remain part of the outer batch transaction.

Async progress is incremented only after the coordinator returns, so async progress is not the
durable DB source of truth.

By Spring transaction semantics, an exception escaping the coordinator rolls back mutation,
failure rows and cursor/counters together. A committed coordinator transaction advances the durable
cursor together with its mutations.

No Lead DB integration/crash-injection test was added in PR #213, and this QA environment cannot
run PostgreSQL. Therefore the mandatory injected-crash proof for SKIP, MERGE_EXACT and FAIL is **not
called PASS**.

**CA-R1-004 is structurally source-resolved; DB crash-injection verification is EXECUTION BLOCKED.**

---

## 6. Company purge / merged Contact self-FK

**SOURCE/CONTRACT REVIEWED; DB EXECUTION BLOCKED**

Before physical Contact deletion, `ContactCompanyPurgeService.clearMergedReferences(...)` updates
merged tombstones in bounded ID-ordered batches:

- status -> DELETED;
- `merged_into_contact_id` -> null;
- `deleted_at` populated;
- version incremented.

Only after no merged references remain does the service physically drain Contacts.

This removes the Round-1 failure mode where an arbitrary deletion batch could select a merge target
while an unselected source still referenced it through ON DELETE RESTRICT.

The operation is retryable by source: clearing references is durable and subsequent invocations
continue remaining rows before physical deletion.

The required DB scenario with merged pairs spanning multiple purge batches, injected mid-purge
failure and final company PURGED state could not be executed.

**CA-R1-005 is source-resolved; mandatory DB lifecycle verification is EXECUTION BLOCKED.**

---

## 7. Bulk performance, transactions and recovery

### UPDATE_FIELD

**SOURCE/CONTRACT REVIEWED**

Round-2 removes the per-Contact delete/upsert query pair.

The adapter now:

- loads/validates the custom-field definition once per bounded worker batch;
- builds the typed value plan once;
- executes one set-based DELETE for the bounded Contact IDs when required;
- uses one JDBC prepared batch for value upserts across the bounded Contact IDs.

Memory remains O(batch-size). The default processing batch is 500 and the hard maximum is 2000.

### DELETE transaction duration

**SOURCE/CONTRACT REVIEWED**

DELETE no longer wraps the entire 500/2000 worker page in one transaction.

`deleteIndependently(...)` runs each Contact's `ContactService.delete(...)` lifecycle in its own
transaction, preserving:

- row locking;
- status transition;
- identity disabling;
- tag/List removal;
- privacy-purge job enqueue.

This fixes the original long batch-transaction concern.

### ADD/REMOVE TAG and LIST

**SOURCE/CONTRACT REVIEWED**

These remain bounded and use batched/set-oriented repository operations rather than whole-tenant
arrays.

### Recovery regression

The DELETE redesign introduced **CA-R2-001** below.

Because the Round-2 QA prompt explicitly requires DELETE worker checkpoint/recovery to remain
correct, CA-R1-007 cannot be declared fully closed.

500/2000 runtime query counts, transaction timing and lock duration are **EXECUTION BLOCKED**.

---

## 8. Cursor signing

**SOURCE/CONTRACT REVIEWED / LOCAL-MOCK TEST SOURCE PRESENT**

`ContactCursorCodec` no longer generates a process-local random signing key.

Key resolution is:

1. `APP_CONTACTS_CURSOR_HMAC_KEY`;
2. stable `CHANNEL_CREDENTIAL_MASTER_KEY` fallback;
3. explicit configuration failure if both are absent/blank.

Focused tests cover:

- two instances with same key;
- simulated restart;
- different keys;
- tampering;
- cross-company/sort/filter reuse;
- dedicated-key precedence;
- shared fallback;
- missing key failure without secret leakage.

Cursor operations are O(1) in page size.

**CA-R1-010 source fix is valid.**

---

## 9. Frontend export and stale status handling

**SOURCE/CONTRACT REVIEWED / LOCAL-MOCK TEST SOURCE PRESENT**

### Export

Frontend `ContactExportField` is now constrained to the frozen backend allow-list.

Default Contacts/List/Segment exports use:

- displayName
- organization

They no longer request primaryEmail/primaryPhone and do not generate exports client-side.

### Status filter

`ContactSearchStatus` excludes DELETED.

`parseContactSearchStatus(...)` accepts only ACTIVE/MERGED. Unsupported stale URL state becomes
undefined before `useContacts` receives the query, and an effect removes both stale status and
cursor from the URL.

The normal selector exposes only:

- Active/default
- Active
- Merged

Focused UI/model tests cover exact export fields, absence of identity export fields, absence of
Deleted, stale URL normalization and MERGED preservation.

**CA-R1-011 and CA-R1-013 are source-resolved; npm test/build execution is blocked.**

---

## 10. Freeze metadata

**SOURCE/CONTRACT REVIEWED**

The active Round-2 handoff documents independently inspected for this review consistently identify
the integrated QA production base as:

`75adcc7dd17105c2a62570c56a8e7c2509321398`

The QA branch was created from exactly that SHA.

**CA-R1-012 is resolved.**

---

# New Round-2 findings

## CA-R2-001 — Medium — Lead — Bulk DELETE commits Contacts before its durable worker checkpoint

### Violated invariant

The final Round-2 QA contract explicitly requires DELETE transaction duration to be bounded **and**
worker checkpoint/recovery to remain correct.

A crash/reclaim must not lose the durable accounting/progress for destructive Contact work that has
already committed.

### Exact evidence

`ContactBulkWorker.process(...)` executes a page in this order:

1. resolve active IDs from `ContactBulkSelectionResolver.page(...)`;
2. call `ContactBulkMutationPort.apply(...)`;
3. only after the entire page returns, call
   `ContactBulkRepository.advance(...)` once for the page.

For DELETE, `ContactBulkMutationAdapter.deleteIndependently(...)` commits each Contact in its own
independent transaction.

`ContactService.delete(...)` immediately changes the Contact away from ACTIVE and removes List
memberships.

`ContactBulkSelectionResolver` only returns ACTIVE Contacts for IDS/FILTER and the List/Segment
paths resolve current active audience membership.

There is no durable per-Contact bulk-operation marker tying those independently committed deletes
to the bulk cursor/counters.

### Reproduction

Explicit IDs make the defect easiest to see:

1. Queue DELETE for 500 active Contact IDs.
2. Let all 500 independent Contact-delete transactions commit.
3. Crash the process after `port.apply(...)` returns but before
   `ContactBulkRepository.advance(...)`.
4. Durable bulk cursor/counters are still zero.
5. Reclaim the job.
6. The explicit-ID resolver queries only `status = 'ACTIVE'`; the 500 already-deleted Contacts
   are no longer returned.
7. Worker sees an empty page and marks the bulk operation COMPLETED.
8. The Contacts were deleted, but candidate/processed/succeeded counters can remain zero.

With a partial page crash, only still-active IDs are subsequently counted. LIST/FILTER/SEGMENT
selection has the same accounting loss for Contacts deleted before the checkpoint.

### Impact

Destructive work and its durable bulk-operation record can disagree after a realistic crash window.
The system can report a completed bulk DELETE with materially incorrect candidate/processed/success
counts, weakening auditability and recovery guarantees.

The intended deletions generally still converge because deleted Contacts disappear from active
selection, but the operation's durable outcome does not converge to the uninterrupted execution.

### Expected behavior

Bounded DELETE transactions must remain short without separating committed destructive work from
recoverable durable operation progress.

### Acceptance condition

Add DB-backed crash-injection coverage that:

- commits one or more Contact deletes;
- crashes before page checkpoint advancement;
- reclaims the bulk job;
- converges to the same final Contact state **and the same candidate/processed/succeeded/failed
  counters** as uninterrupted execution.

Implementation may use a durable per-Contact operation marker, smaller atomic work/checkpoint unit,
or another bounded design, but must not return to one 500/2000-Contact transaction.

---

## CA-R2-002 — Medium — B2 — Authorization-denied async resources remain stuck in non-terminal state

### Violated invariant

Revoked user-owned async capabilities must fail safely and recover to a truthful durable resource
state. A terminal async-job denial must not leave the corresponding Contact import/export/bulk
resource permanently reporting QUEUED/RUNNING.

### Exact evidence

When current authorization fails,
`ContactJobDispatcher.processClaimedJob(...)` executes:

`asyncJobs.fail(job.jobId(), decision.failureCode())`

and returns before the resource worker.

It does **not** call:

- `ContactImportRepository.fail(...)`;
- `ContactExportRepository.fail(...)`;
- `ContactBulkRepository.fail(...)`.

Those resource rows therefore remain QUEUED/RUNNING.

The API mapper does not query the real async-job row for later resource reads. With no live
`AsyncJobResponse`, it derives nested job status from the resource row's status, so a resource
left QUEUED is presented as QUEUED even though its async job is terminal FAILED.

For import, the stuck queued resource is also not a normal PREVIEWED/UPLOADED state that can simply
be recommitted after permission is restored.

### Reproduction

1. User queues an import/export/bulk job.
2. Revoke membership or required permission before claim.
3. Dispatcher claims it.
4. Authorization policy correctly denies it.
5. Dispatcher marks only `async_jobs` FAILED.
6. Inspect the Contact resource row or fetch it through an authorized administrator:
   it remains QUEUED/RUNNING and the fallback API job representation also appears non-terminal.

### Impact

The security boundary prevents unauthorized execution, but durable resource/job state diverges.
Operators and UI can see a permanently queued operation that will never run; retry/idempotency
behavior is confusing, especially for import resources.

### Expected behavior

Authorization denial should terminalize both the async job and the corresponding user-owned
Contacts resource consistently, without running mutation/export work.

### Acceptance condition

For CONTACT_IMPORT, CONTACT_EXPORT and CONTACT_BULK:

- revoke authorization after queue;
- claim/reclaim;
- verify worker is not called;
- verify async job becomes FAILED with stable denial code;
- verify domain resource also reaches a truthful terminal failure state;
- verify API status reflects that terminal state;
- document/retest safe retry behavior after permission restoration.

CONTACT_DELETE_PURGE must remain exempt from actor reauthorization and continue privacy cleanup.

---

# Tenant isolation / IDOR review

**SOURCE/CONTRACT REVIEWED; runtime matrix EXECUTION BLOCKED**

Reviewed representative high-risk paths:

- Contact/identity reads and mutations remain company-scoped.
- import/export/bulk repositories require company ID on resource loads.
- export source validation is company-scoped.
- Segment repository get/update/count operations are company-scoped.
- Audience List/Segment resolution uses company-scoped source lookup and freezes source
  version/filter hash.
- Inbox exact identity lookup includes company ID.
- Broadcast activity adapter includes company ID + Contact ID.
- V83 runtime target validation rejects foreign Contact/identity relationships using the same safe
  not-found behavior.

No new source-level cross-tenant bypass was established.

A full HTTP/DB IDOR matrix with two real tenants is **EXECUTION BLOCKED**.

---

# Privacy deletion / suppression safety

**SOURCE/CONTRACT REVIEWED; DB execution BLOCKED**

Normal Contact deletion:

1. locks company/contact;
2. changes ACTIVE -> DELETED;
3. disables identities;
4. removes mutable memberships;
5. queues privacy purge.

The privacy-purge job is deliberately not canceled by later user permission revocation.

Before profile/identity deletion, active suppressions are transformed into keyed-HMAC tombstones.
Contact-wide suppressions protect every relevant channel identity by creating one tombstone per
identity.

V83's suppression owned-identity FK does not destroy null tombstones.

No source regression was identified.

---

# Broadcast / audience / Inbox regression review

**SOURCE/CONTRACT REVIEWED; provider execution BLOCKED**

### Legacy Broadcast

`BroadcastPreparationChunkService` retains a distinct legacy group branch. Legacy recipients
continue to use source_member_id and do not require Contact provenance.

### List / Segment audience

Audience path:

- freezes source version/filter hash;
- materializes bounded keyset snapshot members;
- stores immutable normalized destination;
- stores Contact/identity/snapshot provenance;
- skips members whose Contact/identity provenance disappeared before recipient materialization.

### Pre-provider eligibility

`BroadcastDeliveryService.deliver(...)` calls Contacts eligibility before entering managed or
existing provider submission paths.

For Contact-backed recipients it:

- re-evaluates current preference/suppression/policy state;
- terminally skips newly ineligible recipients;
- verifies the currently selected normalized destination still equals the immutable snapshot
  destination;
- skips on identity change.

Legacy recipients with no Contact provenance retain legacy behavior.

### Inbox seam

`JdbcContactIdentityLookup.findExact(...)` scopes lookup by company, identity type, namespace,
normalized value and ACTIVE status. Unknown senders can remain unresolved; no hard Inbox Contact FK
was introduced.

No source-level regression was found in these paths.

Real provider/network execution is **EXECUTION BLOCKED**.

---

# Complexity / performance review

## Contact and audience pagination

- high-cardinality Contact/audience processing remains keyset/cursor based;
- worker memory is O(batch-size);
- no whole-tenant browser array was introduced;
- F1 all-matching bulk selection keeps a FILTER descriptor rather than enumerating Contact IDs.

## Segment execution

- compiler SQL remains parameterized;
- company + ACTIVE Contact predicate is part of the compiled base;
- frozen depth/predicate/list-value/serialized-size limits remain in place;
- representative indexes exist for company/status/id, identity lookup, tags, Lists and custom-field
  access.

## Bulk 500 / 2000

Source design is bounded at 500 by default with 2000 hard maximum.

UPDATE_FIELD is now set/batch-oriented rather than two repository calls per Contact.

DELETE uses per-Contact transactions, avoiding one long 500/2000 lock-holding transaction, but
CA-R2-001 shows the page checkpoint is not crash-atomic with those independent commits.

Exact query counts and transaction timings at 500/2000 Contacts are **EXECUTION BLOCKED**.

## 10k / 100k

No source path reviewed requires loading the whole tenant for core paging/snapshot/bulk work.
However no 10k/100k dataset, latency measurement or real EXPLAIN plan was executable.

No 1M production SLA is claimed.

---

# Migration verification

## V83 source review

**SOURCE/CONTRACT REVIEWED**

V83 contains the expected forward-only Round-2 integrity work and does not rewrite V80/V81/V82.

Source review covered:

- owned Contact/identity triple index;
- preference triple FK;
- suppression triple FK;
- column-specific identity nulling for suppressions;
- conservative malformed-row reconciliation;
- null HMAC tombstone compatibility;
- merged-Contact company-purge source path;
- interaction with V82 Broadcast snapshot/provenance constraints.

## Required execution

- clean DB -> V83: **EXECUTION BLOCKED**
- representative V79 -> V83: **EXECUTION BLOCKED**
- mismatch insertion rejection: **EXECUTION BLOCKED**
- null tombstone insertion/purge behavior: **EXECUTION BLOCKED**
- identity deletion + suppression preservation: **EXECUTION BLOCKED**
- merged pairs across purge batches: **EXECUTION BLOCKED**
- legacy Broadcast row migration/runtime behavior: **EXECUTION BLOCKED**
- List/Segment Broadcast row behavior: **EXECUTION BLOCKED**

No migration test is called PASS.

---

# Commands actually executed

## Required backend commands

Not executed — **EXECUTION BLOCKED**:

- `cd unifydesk-app/backend`
- `./mvnw spotless:check`
- `./mvnw test`
- `./mvnw clean test`
- `./mvnw package -DskipTests`

## Required frontend commands

Not executed — **EXECUTION BLOCKED**:

- `cd unifydesk-app/frontend`
- `npm ci`
- `npm run format:check`
- `npm run lint`
- `npm test`
- `npm run build`

## QA environment / CI evidence actually inspected

- container filesystem availability probe confirming no repository checkout;
- GitHub workflow runs for exact integrated SHA and Lead head;
- job step lists for all available Lead-head workflows.

All available affected CI jobs had zero workflow steps, so they are neither PASS nor evidence of
production-code failure.

Round-2 PR added-line scan found no accidental literal escaped-newline source corruption.

---

# Security result

No new Critical/High authorization bypass was established by source review.

The Round-1 destructive DELETE permission bypass, async execution permission bypass, consent-import
privilege split and preference-provenance spoofing all have concrete source remediations.

V83/service code adds the required Contact/identity ownership boundary.

Runtime HTTP/DB adversarial execution remains blocked.

New CA-R2-002 is a post-denial resource-state consistency/recovery defect, not an authorization
bypass: workers remain blocked from unauthorized mutation/export.

---

# Concurrency / recovery result

- Import mutation + failure rows + checkpoint are now structured as one bounded DB transaction by
  source.
- Actual SKIP/MERGE_EXACT/FAIL crash injection is **EXECUTION BLOCKED**.
- Bulk idempotency canonical comparison is source-correct; concurrent advisory-lock verification is
  **EXECUTION BLOCKED**.
- DELETE transactions are now bounded individually.
- **CA-R2-001 remains open** because independently committed deletes and page-level checkpoint
  advancement can diverge after a process crash.

---

# Performance / query-plan result

- UPDATE_FIELD source query shape is materially improved and no longer uses per-Contact
  delete/upsert repository calls.
- DELETE avoids one large batch transaction.
- ADD/REMOVE TAG/LIST remain batched.
- keyset paging and O(batch-size) memory remain intact.
- 500/2000 query-count measurements: **EXECUTION BLOCKED**
- 10k load verification: **EXECUTION BLOCKED**
- 100k load verification: **EXECUTION BLOCKED**
- representative PostgreSQL EXPLAIN plans: **EXECUTION BLOCKED**
- 1M SLA: **NOT CLAIMED**

---

# Blocked verification

The following mandatory evidence remains unavailable:

- all Maven commands;
- all npm commands;
- clean V1/current migration chain -> V83;
- representative V79 -> V83;
- V83 real FK/tombstone tests;
- import SKIP/MERGE_EXACT/FAIL crash injection;
- merged-pair company-purge DB test;
- concurrent bulk idempotency DB test;
- 500/2000 query counts and lock timing;
- 10k/100k load and EXPLAIN;
- real multi-tenant HTTP IDOR matrix;
- real provider delivery test;
- browser accessibility/responsive execution.

These are explicitly **EXECUTION BLOCKED**, never PASS.

# Final disposition

Two new Medium defects remain, including a mandatory DELETE recovery/checkpoint violation, and
multiple final acceptance conditions require real DB/build execution that is unavailable.

**NOT RELEASE-READY**
