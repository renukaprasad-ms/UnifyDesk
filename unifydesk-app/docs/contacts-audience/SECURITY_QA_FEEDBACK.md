# Contacts & Audience Round-1 Security / QA Feedback

## Review identity

- Review branch: `review/contacts-audience-security-qa-r1`
- Frozen production-code base reviewed: `f6a04c4596240d0c43bde6ef83c66619abbd9a02`
- Latest QA manifest read from `dev`
- Production code modified during QA: **NO**
- Final disposition: **NOT RELEASE-READY**

The latest `SECURITY_QA_CHAT_PROMPT.md` freezes QA on
`f6a04c4596240d0c43bde6ef83c66619abbd9a02`.
`INTEGRATION_STATUS.md` still contains stale references to the older
`be6de61a7c46db4785457f3f9bbb0d622f026101` QA base; this inconsistency is recorded as
CA-R1-012.

## Evidence classification

### SOURCE / CONTRACT REVIEWED

Reviewed the frozen Contacts/Audience backend/frontend implementation and the latest architecture,
API, security, consent/suppression, segmentation, import/export, merge, performance, integration,
task and QA documents.

Source review covered:

- Contact CRUD, identities, tags, custom fields and Lists
- Contact optimistic version handling
- Segment public AST and Segment optimistic version handling
- tenant scoping and permission calls
- consent/preferences, suppressions and eligibility
- import/export/bulk processing and worker recovery
- audience snapshot generation
- Broadcast List/Segment provenance and pre-send eligibility
- Contact delete/privacy purge and company purge
- Inbox identity lookup and Broadcast activity seam
- frontend types/API calls, Snowflake ID string handling and selection behavior
- V80/V81/V82 schema constraints
- accidental literal escaped-newline corruption

The escaped-newline scan of PRs #200, #201, #202, #203 and #206 found no accidental Java
source corruption. The only added-line match was the intentional
`"[\\r\\n\\t]+"` sanitizer in `ContactImportRepository.safe(...)`.

### EXECUTION BLOCKED

The required Maven, npm and PostgreSQL migration commands could not be executed in this review
environment because no repository working tree is mounted here.

GitHub Actions does not provide substitute execution evidence:

- exact implementation merge SHA `f6a04c4596240d0c43bde6ef83c66619abbd9a02` has no workflow run;
- the PR #206 head `9b1e25f3e0bb2c36ae81718f08bafe95f17e9b9f` has workflow runs;
- every inspected affected job in those runs completed with **zero workflow steps**.

Therefore none of the following is marked PASS:

Backend:
- `./mvnw spotless:check`
- `./mvnw test`
- `./mvnw clean test`
- `./mvnw package -DskipTests`

Frontend:
- `npm ci`
- `npm run format:check`
- `npm run lint`
- `npm test`
- `npm run build`

Database:
- clean V1 -> V82
- representative V79 -> V82
- PostgreSQL FK/index/runtime migration verification
- 10k/100k DB-backed performance runs and EXPLAIN plans

No real provider call was executed.

---

# Findings

## CA-R1-001 — High — B2 — Bulk DELETE bypasses `contact.delete`

### Violated invariant

Destructive Contact deletion requires the dedicated `contact.delete` permission. Frontend
permission hiding is not authorization, and Manager defaults intentionally include
`contact.modify` while excluding `contact.delete`.

### Exact source evidence

`ContactProcessingController.bulk(...)` authorizes every bulk operation using only:

`require(companyId, ContactAudiencePermissions.MODIFY)`

The same endpoint accepts `BulkOperation.type = DELETE`.

`ContactBulkMutationAdapter.delete(...)` then calls
`contactService.delete(companyId, contactId, userId)`.

`ContactPermissionDefaultsTests.managerGetsOperationalContactPermissionsButNotDestructiveDelete()`
explicitly asserts that Managers have `CONTACT_MODIFY` but do not have `CONTACT_DELETE`.

### Reproduction

1. Use a Manager or custom role with `contact.modify` and without `contact.delete`.
2. POST `/api/v1/companies/{companyId}/contacts/bulk-operations`.
3. Supply an IDS/FILTER/LIST/SEGMENT selection and `{"type":"DELETE"}`.
4. The request is authorized by `contact.modify` and queues the destructive operation.
5. Worker execution reaches `ContactService.delete(...)` without another `contact.delete`
   authorization check.

### Impact

A non-destructive operational role can mass-delete Contacts and queue permanent privacy cleanup.
This directly defeats the frozen permission model and is release-blocking.

### Expected behavior

Bulk DELETE must require `contact.delete`; non-delete bulk mutations may use
`contact.modify`.

### Precise acceptance condition

- Backend selects the permission from the requested bulk operation before queueing.
- DELETE is rejected for a Manager/default role lacking `contact.delete`.
- Worker/reclaim authorization also enforces the destructive permission.
- Add controller/service tests proving MODIFY-only cannot queue DELETE and DELETE-authorized users
  can.

---

## CA-R1-002 — High — B2 — Async Contact jobs do not revalidate current authorization

### Violated invariant

`SECURITY.md` explicitly requires bulk jobs to persist company/user and revalidate
authorization/policy when execution begins if privileges may have changed.

### Exact source evidence

`AsyncJobService.ClaimedAsyncJob` contains:

- `companyId`
- `userId`
- `resourceId`

`ContactJobDispatcher` claims Contact import/export/bulk/segment-count/delete-purge jobs, but it
does not use `ClaimedAsyncJob.userId()` to call `CompanyAuthorizationService` before invoking
workers.

The import/export/bulk workers mutate or export tenant data solely from persisted
`companyId/resourceId`.

### Reproduction

1. User has permission and queues an export, import or bulk mutation.
2. Before the worker claims the job, remove the user from the company or revoke the relevant
   permission.
3. Worker later claims the job.
4. The dispatcher executes it without checking the user's current membership/permission.

### Impact

Revoked users retain delayed capabilities through queued jobs. This is especially severe for
destructive bulk operations and exports of customer PII.

### Expected behavior

Execution/reclaim must reauthorize the stored initiating user for the job's current company and
required operation permission.

### Precise acceptance condition

- Each Contact async job maps to its required current permission(s).
- Claim/execution rechecks company membership and permission.
- Revoked/removed users cause the job to fail safely before mutation/export/provider/storage
  publication.
- Recovery/reclaim performs the same check.
- Tests cover permission revocation between queue and claim.

---

## CA-R1-003 — High — B2 — Import permission can write marketing consent without `contact.consent.modify`

### Violated invariant

Consent modification is a separate privileged capability. Importing a Contact must not implicitly
grant authority to change marketing consent.

### Exact source evidence

`ContactProcessingController.commitImport(...)` checks only:

`ContactAudiencePermissions.IMPORT`

It accepts `request.consentMappings()` and passes those mappings to
`ContactImportMappingMapper`.

`ContactImportMutationAdapter` processes those mappings and invokes
`ContactPreferenceService.setFromUser(...)`, which persists OPTED_IN/OPTED_OUT/UNKNOWN
preferences.

No `contact.consent.modify` check occurs on this path.

### Reproduction

1. Create a custom role with `contact.import` but not `contact.consent.modify`.
2. Initiate/upload a CSV.
3. Commit it with an EMAIL/SMS/WHATSAPP consent mapping and valid evidence for OPTED_IN.
4. Worker writes marketing preference projections/events.

### Impact

A role intentionally denied consent modification can create or change marketing consent through
CSV import. That can alter legal/marketing eligibility.

### Expected behavior

Any import commit containing consent mappings must require `contact.consent.modify` in addition
to `contact.import`, and that permission must be rechecked by the worker.

### Precise acceptance condition

- Consent-free imports continue to require only import permission.
- Imports with one or more consent mappings require consent-modify at commit and execution.
- Tests cover a custom role with import-only permission.

---

## CA-R1-004 — High — Lead — Import mutation and durable cursor are not crash-atomic

### Violated invariant

The frozen import design requires durable row/batch progress so a reclaimed worker does not
blindly duplicate mutations. Crash recovery must be correct for SKIP, MERGE_EXACT and FAIL.

### Exact source evidence

`ContactImportWorker.applyBatch(...)` performs, in order:

1. `port.applyBatch(...)`
2. `repository.appendFailures(...)`
3. `repository.advance(...)`

`ContactImportMutationAdapter.applyBatch(...)` is independently `@Transactional`. Its Contact,
identity, tag, field, List and preference mutations therefore commit when the port call returns,
before the import row cursor/counters are advanced in the subsequent repository call.

### Reproduction

1. Import a previously unseen identity with duplicate strategy FAIL.
2. Let `ContactImportMutationAdapter.applyBatch(...)` commit the new Contact.
3. Crash the worker before `ContactImportRepository.advance(...)`.
4. Reclaim/restart the same job.
5. Cursor still points before that row, so it is replayed.
6. Exact identity now exists; FAIL records the replayed row as a duplicate failure even though its
   Contact was already created by the first execution.

MERGE_EXACT can also reapply mutable updates/version increments during this window.

### Impact

Persisted Contacts can disagree with import success/failure counters and error reports. Recovery
semantics depend on crash timing, violating idempotency and auditability.

### Expected behavior

A committed row mutation must have an atomic/durable processing marker, or every mutation must be
idempotent against an import-row operation identity.

### Precise acceptance condition

- Inject a crash after row/batch mutation but before current cursor advancement.
- Reclaiming the job produces exactly the same final Contacts, memberships, consent projections,
  counters and row outcomes as uninterrupted execution for SKIP/MERGE_EXACT/FAIL.
- No successful first execution becomes a failure solely due to replay.

---

## CA-R1-005 — High — Lead — Company purge can deadlock on merged-Contact self-FK

### Violated invariant

Contacts company cleanup must be bounded, retryable and FK-safe, and V82/V80 must not prevent
company lifecycle completion.

### Exact source evidence

V80 defines:

`fk_contacts_merged_into foreign key (merged_into_contact_id) references contacts(id) on delete restrict`

A merged source Contact therefore references its target Contact.

`ContactCompanyPurgeService` later drains Contacts using an unordered bounded delete:

`delete from contacts where ctid in (select ctid from contacts where company_id = ? limit ?)`

It does not clear the self-reference or guarantee that merged source rows are removed before their
targets.

### Reproduction

1. Create enough Contacts to span more than one purge batch.
2. Merge source S into target T so S.merged_into_contact_id = T.
3. Arrange heap/batch selection so T is selected in a delete batch while S remains outside that
   batch.
4. Run company purge.
5. PostgreSQL rejects deletion of T because S still references it with ON DELETE RESTRICT.

### Impact

Company purge can fail repeatedly and retain Contacts PII/data indefinitely, blocking lifecycle
completion for tenants with merged Contacts.

### Expected behavior

Physical Contact deletion must respect merge dependency order or explicitly remove the historical
self-reference safely before bounded physical deletion.

### Precise acceptance condition

- DB-backed test with interleaved merged pairs spanning multiple purge batches completes to the
  company PURGED state without FK exceptions.
- Cleanup remains bounded and retryable.
- No merged Contact PII remains afterward.

---

## CA-R1-006 — Medium — B2 — Bulk idempotency key is not bound to request payload

### Violated invariant

Idempotency replay must represent the same logical request; reusing a key with a different
selection/operation must conflict rather than silently returning an unrelated operation.

### Exact source evidence

`ContactBulkService.create(...)` locks the idempotency key and then:

`if (existing.isPresent()) return new CreateResult(existing.get().id(), null);`

It does not compare `selection_json`, `operation_json` or an equivalent canonical request hash.

### Reproduction

1. POST bulk operation key K: ADD_TAG tag A to selection X.
2. Reuse key K with DELETE, or ADD_TAG tag B, or a different selection.
3. Service returns the existing operation ID instead of rejecting the changed request.

### Impact

Clients can receive a successful-looking replay for an operation different from the request they
just submitted. This is unsafe for destructive automation and retrying APIs.

### Expected behavior

The idempotency key must be bound to the normalized selection + operation request.

### Precise acceptance condition

- Same key + same canonical request returns the original operation.
- Same key + different selection or operation returns a stable conflict code.
- Concurrency test covers simultaneous same-key requests.

---

## CA-R1-007 — Medium — Lead — Large bulk field/delete mutation is one-query-per-contact inside a batch transaction

### Violated invariant

Bulk processing must remain O(batch-size) memory with bounded transactions and avoid N+1
query/write patterns when batching is required.

### Exact source evidence

`ContactBulkMutationAdapter.apply(...)` is `@Transactional`.

For UPDATE_FIELD it loops every Contact ID and invokes both:

- `customFieldRepository.deleteValues(...)`
- `customFieldRepository.upsertValues(...)`

per Contact.

For DELETE it loops every Contact ID and invokes `contactService.delete(...)`, which performs
multiple repository mutations and queues a purge job for each Contact.

The configured hard batch maximum is 2000.

### Reproduction

Run UPDATE_FIELD or DELETE over a selection producing a 500-2000 Contact worker batch and inspect
query count/transaction duration.

### Impact

Query/write count scales linearly with a large constant factor while one transaction is held for
the full batch. This can increase lock duration, contention and worker latency at 100k-scale.

### Expected behavior

Use bounded batch SQL/mutation primitives where semantics allow, and avoid a single large
transaction containing many independent Contact service workflows.

### Precise acceptance condition

- Representative 500/2000-row tests demonstrate bounded transaction duration.
- UPDATE_FIELD uses batch writes rather than per-contact delete/upsert queries.
- Destructive delete processing is chunked/recoverable without holding one long transaction across
  hundreds/thousands of Contact workflows.
- Query-count/performance evidence is added.

---

## CA-R1-008 — Medium — Lead — Consent/suppression identity references are not constrained to the specified Contact

### Violated invariant

An identity-scoped preference/suppression for Contact A must reference an identity owned by
Contact A and appropriate to the channel.

### Exact source evidence

`ContactPreferenceService.setFromUser(...)` validates state/source/time/idempotency but does not
load or validate `identityId` ownership.

`ContactSuppressionService.addManual(...)` similarly does not validate that the supplied
`identityId` belongs to `contactId`.

V82 adds separate company-safe FKs:

- preference -> Contact
- preference -> identity
- suppression -> Contact
- suppression -> identity

but no relational constraint that the identity's `contact_id` equals the preference/suppression
`contact_id`.

### Reproduction

1. In one company create Contact A with identity IA and Contact B with identity IB.
2. Write a preference or manual suppression for Contact A while supplying IB.
3. Both referenced rows exist in the same company, so the database accepts the relationship.
4. Eligibility for A using IA does not necessarily observe the supposedly identity-scoped record.

### Impact

Consent/suppression records can be internally inconsistent and fail to protect the destination the
operator intended, undermining eligibility correctness.

### Expected behavior

Identity-scoped consent/suppression commands must validate Contact ownership and channel
compatibility.

### Precise acceptance condition

- Foreign-within-company identity IDs are rejected with safe not-found/invalid behavior.
- Cross-company IDs remain non-enumerable.
- DB/service invariants prevent storing a Contact/identity mismatch.
- Tests cover preference and suppression paths.

---

## CA-R1-009 — Medium — B2 — User-facing preference API allows spoofing server-owned source trust class

### Violated invariant

`SECURITY.md` identifies provider/source trust class as server-owned provenance. A caller must not
label a UI consent change as an internal/import/provider integration event.

### Exact source evidence

The public preference request accepts `source`.

`ConsentController.setPreference(...)` passes `request.source()` directly into
`ContactPreferenceService.setFromUser(...)`.

`setFromUser(...)` uses the supplied source unchanged unless it is null:

`source == null ? USER_UI : source`

`PreferenceSource` contains trusted/internal-looking values including IMPORT, API,
MANAGED_EMAIL_EVENT, MANAGED_SMS_EVENT, META_WHATSAPP and SYSTEM_MIGRATION.

The actor is correctly forced to USER, but the source provenance is forgeable.

### Reproduction

With `contact.consent.modify`, call the public preference endpoint and set
`source = SYSTEM_MIGRATION` (or another non-UI source).

### Impact

Consent audit/provenance can falsely claim an internal trust source, weakening compliance evidence
and incident analysis.

### Expected behavior

Public user mutation paths set source to an allowed user-facing source server-side. Trusted sources
are accepted only through internal adapters.

### Precise acceptance condition

- Public user request cannot choose internal/provider/import/system source classes.
- Actor and source provenance are both server-owned.
- Tests attempt every non-user source against the public endpoint.

---

## CA-R1-010 — Medium — B1 — Cursor HMAC key silently becomes process-local random state

### Violated invariant

Keyset cursors must remain valid and deterministic across a production deployment while still
rejecting tampering.

### Exact source evidence

`ContactCursorCodec` reads:

`APP_CONTACTS_CURSOR_HMAC_KEY` falling back to `CHANNEL_CREDENTIAL_MASTER_KEY`.

If both are missing/blank, its constructor generates a new random 32-byte key with
`SecureRandom`.

### Reproduction

1. Start two backend replicas without either configured key.
2. Fetch page 1 from replica A and receive cursor C.
3. Route page 2 to replica B.
4. B verifies C with a different random key and returns CONTACT_INVALID_CURSOR.
5. The same occurs across application restart.

### Impact

Pagination intermittently fails in multi-replica/restarted production environments if secret
configuration is missing. This is an availability and operability defect.

### Expected behavior

Production cursor signing must use stable shared secret material; missing required key must fail
configuration clearly rather than silently inventing process-local state.

### Precise acceptance condition

- Multiple replicas/restarts using the configured shared key accept each other's cursors.
- Production startup/config validation rejects a missing signing secret, or a documented stable
  shared platform key is mandatory and tested.

---

## CA-R1-011 — Medium — F1 — Default Contacts export requests backend-rejected identity fields

### Violated invariant

Frontend export field selection must match the frozen backend allow-list/contract.

### Exact source evidence

`ContactsPage.tsx` renders the default Contacts export with:

- `displayName`
- `primaryEmail`
- `primaryPhone`
- `organization`

`ContactExportRowSource.ALLOWED_FIELDS` contains only:

- id
- displayName
- firstName
- lastName
- organization
- jobTitle
- countryCode
- language

It does not allow primaryEmail or primaryPhone.

`ContactExportRowSource.validateFields(...)` rejects any field outside that set with
`CONTACT_FIELD_INVALID`.

### Reproduction

1. Open Contacts with no filters.
2. Click the normal export control.
3. Frontend submits the field list containing primaryEmail/primaryPhone.
4. Backend rejects export creation.

### Impact

The principal Contacts export action is nonfunctional.

### Expected behavior

Frontend and backend share one supported export field contract, including identity fields only if
the product contract intentionally supports them.

### Precise acceptance condition

- Default Contacts export request is accepted and completes.
- Add frontend/API contract test using the exact UI-generated field list.
- If email/phone export is supported, backend streams those fields without N+1 queries; otherwise
  frontend must not request them.

---

## CA-R1-012 — Medium — Lead — QA freeze metadata contains two different base SHAs

### Violated invariant

QA must be frozen to one exact integrated SHA and must never branch from an ambiguous or moving
base.

### Exact source evidence

Latest `SECURITY_QA_CHAT_PROMPT.md` says the exact integrated QA base is:

`f6a04c4596240d0c43bde6ef83c66619abbd9a02`

Latest `INTEGRATION_STATUS.md` still contains QA branch instructions/lineage using:

`be6de61a7c46db4785457f3f9bbb0d622f026101`

The externally supplied QA assignment also carried the stale `be6de61...` value.

### Reproduction

Follow `INTEGRATION_STATUS.md` literally instead of the newer QA prompt and create the QA branch
from `be6de61...`; the final Lead corrections in f6a are then not reviewed.

### Impact

Reviewers can verify the wrong production code and report a false release result.

### Expected behavior

Every Contacts/Audience freeze document names one identical exact QA base.

### Precise acceptance condition

Update all freeze/lineage docs and generated QA handoff text to one SHA and add a simple
consistency check to the release procedure.

---

## CA-R1-013 — Low — F1 — UI exposes a DELETED Contact search filter that backend rejects

### Violated invariant

Public frontend filters must be valid against the Contact search API.

### Exact source evidence

`ContactsPage.tsx` offers:

`<option value="DELETED">Deleted</option>`

`ContactService.search(...)` explicitly rejects:

`if (status == ContactStatus.DELETED) throw ContactCoreException.invalidRequest();`

### Reproduction

Select Deleted in the Contacts status filter.

### Impact

The user receives an avoidable API error from a first-party UI option.

### Expected behavior

Either remove the unsupported DELETED option or deliberately support an authorized tombstone view
without exposing purged PII.

### Precise acceptance condition

Selecting every status option exposed by F1 produces a valid documented backend request.

---

# Areas reviewed without a source-level release finding

The following areas were source-reviewed and no concrete defect was established from the available
evidence:

- Contact API IDs are represented as strings in public Contact/identity/tag/list/segment/export
  DTOs where Snowflake IDs cross the browser boundary.
- Contact PATCH carries optimistic `version`; frontend update payload includes the loaded version.
- Segment PUT carries optimistic version and reports CONTACT_VERSION_CONFLICT.
- Public Segment HTTP model uses `filterVersion: 1` plus lower-case group/predicate AST; internal
  `SegmentFilter.root`/typed wrappers are mapped behind `SegmentApiMapper`.
- Segment compiler injects company + ACTIVE predicates and uses parameterized values.
- Segment compiler enforces bounded depth/predicate/serialized-size limits through
  `ContactAudienceProperties`; list-value hard limits are part of compiler validation.
- Identity normalization uses conservative email normalization, IDNA domain canonicalization and
  libphonenumber E.164 handling; national numbers require country context.
- `JdbcContactIdentityLookup` scopes exact identity lookup by company and returns empty for unknown
  identities.
- Contact delete immediately changes status away from ACTIVE and disables identities before async
  privacy purge.
- `ContactDeletePurgeWorker` preserves active Contact-wide suppression safety for multiple
  same-channel identities by generating keyed-HMAC tombstones for every relevant identity.
- Direct Broadcast delivery calls Contacts eligibility before provider submission for normal
  Contact-backed recipients; legacy recipients remain on the legacy path.
- Broadcast activity adapter is tenant/contact scoped and emits normalized status summaries rather
  than message bodies/provider webhook payloads.
- Frontend Segment model does not expose SQL or internal Java serialization.
- Frontend all-matching bulk selection uses a FILTER descriptor rather than accumulating the full
  audience in the browser.
- Import preview is server-bounded and frontend does not parse the complete CSV.
- Import status polling is bounded (120 attempts at 3 seconds) rather than uncontrolled.

These are **SOURCE/CONTRACT REVIEWED**, not runtime PASS results.

# Migration verification

Status: **EXECUTION BLOCKED**

Source inspection of V80/V81/V82 was completed. In particular:

- V82 adds company-safe composite ownership FKs for multiple cross-worker structures.
- privacy tombstone references are nullable where intended.
- legacy Broadcast source rows remain represented by the source-member branch of the V82 checks.
- List/Segment Broadcast provenance uses audience snapshot references.
- CA-R1-005 identifies a source-level FK/purge-order defect that must be fixed before runtime
  migration/lifecycle verification can pass.

Mandatory clean V1->V82 and representative V79->V82 PostgreSQL execution remains unverified.

# Concurrency / recovery result

Status: **NOT RELEASE-READY / EXECUTION BLOCKED FOR RUNTIME TESTS**

Source review found release-impacting recovery/authorization defects:

- CA-R1-002 async privilege revocation
- CA-R1-004 import mutation/checkpoint crash window
- CA-R1-006 bulk idempotency request mismatch

Required DB-backed duplicate-identity, preference/suppression replay, merge race, snapshot duplicate
claim and delete-vs-bulk tests could not be executed here.

# Performance / scale result

Status: **SOURCE REVIEWED; LOAD EXECUTION BLOCKED**

Positive source observations:

- Contact/List/Segment/audience scans use keyset-style ID cursors rather than OFFSET for the major
  high-cardinality paths.
- snapshot and import workers use configured bounded batches.
- Segment queries are parameterized.
- frontend does not materialize an all-matching audience for bulk actions.

Release concern:

- CA-R1-007 identifies per-Contact writes and long batch transactions in bulk UPDATE_FIELD/DELETE.

10k/100k DB-backed runs, representative EXPLAIN plans and 1M-readiness evidence were not executable
in this environment.

# Findings by severity / owner

## Critical

None proven from the source evidence reviewed.

## High

- CA-R1-001 — B2 — bulk DELETE permission bypass
- CA-R1-002 — B2 — async permission revalidation missing
- CA-R1-003 — B2 — import can mutate consent without consent-modify
- CA-R1-004 — Lead — import crash/recovery mutation-checkpoint gap
- CA-R1-005 — Lead — company purge unsafe with merged Contact self-FK

## Medium

- CA-R1-006 — B2 — bulk idempotency key not request-bound
- CA-R1-007 — Lead — per-Contact bulk writes / long batch transaction
- CA-R1-008 — Lead — Contact/identity relation not enforced for consent/suppression
- CA-R1-009 — B2 — caller can spoof preference source trust class
- CA-R1-010 — B1 — random per-process cursor signing-key fallback
- CA-R1-011 — F1 — default export asks for unsupported fields
- CA-R1-012 — Lead — contradictory QA freeze SHAs

## Low

- CA-R1-013 — F1 — unsupported DELETED status exposed by UI

# Regressions / compatibility observations

- Legacy Broadcast source handling remains present and separately represented in V82.
- No source-level evidence was found that Inbox requires a hard Contact FK.
- No accidental literal escaped-newline source corruption was found in the integrated Contacts PRs.
- The final Lead adapters are present at the f6a QA base; the older worker-port-not-integrated
  failure is not reported against this base.

# Blocked verification

The following remain explicitly **EXECUTION BLOCKED**:

- all required Maven commands
- all required npm commands
- clean V1->V82 migration
- V79->V82 migration
- PostgreSQL constraint behavior tests
- runtime IDOR matrix
- real concurrency/crash injection
- 10k/100k performance and EXPLAIN
- accessibility/browser responsive execution
- real provider verification

GitHub zero-step jobs must not be interpreted as either PASS or product-code failure.

# Final disposition

**NOT RELEASE-READY**

The five High findings are release blockers. CA-R1-001, CA-R1-002 and CA-R1-003 are authorization /
consent security boundaries; CA-R1-004 breaks required crash-recovery correctness; CA-R1-005 can
prevent company privacy lifecycle completion. Runtime verification is additionally blocked until a
functioning build/database environment executes the required commands.
