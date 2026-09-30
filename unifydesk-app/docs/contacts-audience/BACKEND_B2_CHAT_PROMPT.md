# B2 Prompt — Contacts Audience/Data Processing Round 1

You are B2 for UnifyDesk Contacts & Audience Management.

Repository:
renukaprasad-ms/messaging

Application:
unifydesk-app/

Your EXACT frozen worker base SHA:
f31f4f950cc257d4d1edd947a5038a3447599f85

Your branch:
feature/contacts-audience-processing-r1

Create the branch from EXACTLY that SHA.
Do NOT branch from later moving dev.
Do NOT wait for B1 code.

Read current dev docs before coding:
- contacts-audience/ARCHITECTURE.md
- DATA-MODEL.md
- API-CONTRACT.md
- SEGMENTATION.md
- CONSENT-SUPPRESSION.md
- IMPORT-EXPORT.md
- SECURITY.md
- PERFORMANCE.md
- INTEGRATIONS.md
- TASKS.md

## Mission

Implement the independent audience/data-processing plane:
- Segments/filter AST/compiler
- preference/consent current projection + immutable events
- suppression current projection + immutable events
- shared eligibility evaluator
- CSV import/export
- bulk operations
- Contacts-domain activity events and read adapters
- List/Segment audience resolver
- audience snapshot persistence/materialization
- async workers/progress
- Contacts storage adapter over existing storage accounting

Do not implement B1 Contacts Java entities/services on your branch.

## Independence rule

B1 V80 tables do not exist on your branch.

Where production logic needs Contacts core data:
- define B2-owned interfaces/projections;
- implement SQL/JdbcTemplate repositories against the frozen table/column names in DATA-MODEL.md when that still compiles without V80 at test startup;
- otherwise keep production adapter behind interface and use mocks/unit tests until Lead integration;
- do NOT duplicate V80 tables in V81;
- do NOT import com.messaging.contacts.core implementation classes.

Lead will add cross-worker FK constraints in V82.

## Package ownership

Production:
- com/messaging/contacts/audience/**
- com/messaging/contacts/processing/**
- com/messaging/contacts/consent/**
- provider-neutral Contacts integration adapters under com/messaging/contacts/integration/**

Tests under matching packages.

Migration:
V81__contacts_audience_processing.sql

Shared files you may modify:
- AsyncJobType.java
- minimal scheduler/worker registration required by existing async-job pattern
- storage adapter/enums only when necessary to use existing CONTACT_DATA and EXPORT
- tests for those exact shared files

Do not modify B1, Broadcast, Inbox entity, provider runtime, Ads or frontend hot files.

## Segments

Implement SEGMENTATION.md.

Requirements:
- versioned typed AST DTO
- max depth/predicate/value/filter byte limits
- operator/type validation
- no raw SQL from client
- no arbitrary field/column names
- parameterized compiler
- structural company_id predicate
- active Contact predicate
- custom-field type resolution seam
- keyset result streaming
- preview sample
- cached count metadata
- expensive/exact count async path

Provide compiler tests that assert generated SQL never interpolates untrusted values/identifiers.

## Preference/consent

Implement current projection + append-only event.

V1:
channels EMAIL/SMS/WHATSAPP
purpose MARKETING
states UNKNOWN/OPTED_IN/OPTED_OUT

Actor fields are server-owned.
Untrusted occurredAt is bounded.
Projection and event append are atomic.
Idempotency keys prevent duplicate provider/import/API replay.

## Suppression

Implement separate suppression projection/events.

Reasons include:
MANUAL, HARD_BOUNCE, COMPLAINT, PROVIDER_SUPPRESSED, DND, INVALID_DESTINATION, ABUSE_POLICY, LEGAL_POLICY.

Suppression wins over OPTED_IN.

Implement keyed-HMAC tombstone abstraction for identity suppression after privacy purge. Secret comes from configuration/secret environment; never DB. Store key version.

## Eligibility

Provider-neutral service contract:
evaluate(companyId, contactId, identityId, channel, purpose, context)

Return:
ELIGIBLE / INELIGIBLE
stable reason code
policyVersion/evaluatedAt

Checks:
- Contact active projection
- identity active/owned/channel-compatible
- preference policy
- suppression
- pluggable provider/jurisdiction policy

Do not embed SES/MSG91/Meta DTOs.

## Import

CSV only.

Reuse:
AsyncJobService
Realtime async.job.progress
StorageProvider/StorageQuota/StorageAccounting

Do not weaken MediaValidationService to accept CSV.

Build Contacts-specific private-object/file adapter.

Import:
- upload/intake
- preview
- mapping
- custom-field mapping by ID
- duplicate strategy SKIP/MERGE_EXACT/FAIL
- optional target List
- checksum + mapping hash + idempotency key
- streaming/batches
- invalid-row error report
- phone/email normalization seam delegated to Contact core contract
- recoverable worker

On your independent branch, normalization/contact mutation uses interfaces/mocks. Lead connects them to B1.

Do not auto-create unlimited custom fields from headers.
Do not mark consent OPTED_IN unless explicit evidence mapping is supplied.

## Export

Async CSV:
- Contacts/List/Segment source
- selected fields
- streaming
- CSV formula-injection neutralization
- private temporary object
- authorized signed download
- expiration cleanup
- storage release

## Bulk

Operations:
ADD_TAG
REMOVE_TAG
ADD_TO_LIST
REMOVE_FROM_LIST
UPDATE_FIELD
DELETE

Implement selection abstraction:
IDS/LIST/SEGMENT/FILTER

Large/destructive operations -> job.
Hard cap synchronous explicit IDs <=500.

## Activity

Store only Contacts-domain activity events.

Build read-adapter interfaces for Inbox/Broadcast timeline summaries without importing/changing their entities. Lead can wire implementations if shared code is necessary.

Never copy full message bodies/provider webhook payloads into Contact activity storage.

## Audience snapshots

Implement:
contact_audience_snapshots
contact_audience_snapshot_members

Source LIST | SEGMENT.

Materializer:
- keyset candidate stream
- deterministic identity selection contract
- eligibility
- dedupe
- batch insert
- recoverable progress cursor
- candidate/eligible counts
- source version/filter hash frozen

No Broadcast shared-file change on B2 branch.
Expose internal service/interface Lead can call from Broadcast integration.

Snapshot member destination is immutable historical snapshot data.

## API

Implement B2-owned API-CONTRACT sections:
- segments
- preferences
- suppressions
- imports
- exports
- bulk operations
- activity endpoint/service where feasible
- eligibility diagnostic if included

All endpoints company-authorized and tenant scoped.

## Security tests

Mandatory:
- segment SQL injection
- AST abuse limits
- foreign segment/import/export/job
- actor spoof rejected
- preference replay
- suppression replay/lift auth
- CSV formula payload
- oversized/malformed CSV
- export formula neutralization
- expired/foreign download
- idempotency conflict
- raw PII not logged
- suppression HMAC no plaintext

## Performance/concurrency tests

- keyset segment pages
- expensive count queued
- import batch memory bounded
- export streams
- duplicate job claim/recovery
- list/segment snapshot batch recovery
- concurrent same idempotency key
- concurrent preference replay
- suppression projection consistency

## Required checks

cd unifydesk-app/backend
./mvnw spotless:check
./mvnw test
./mvnw clean test
./mvnw package -DskipTests

Validate V81 syntax/clean migration in a test fixture that does not illegally depend on V80. If full V79->V81 cannot run because V80 belongs to B1, state that clearly; Lead/QA own integrated V1->V82 and V79->V82 migration verification.

## Forbidden scope

Do NOT modify:
- com/messaging/contacts/core/**
- V80/V82
- Broadcast execution/entity files
- Inbox entities
- Managed Email/SMS/WhatsApp runtime files
- Ads
- frontend
- frozen docs

If a provider event hook needs a provider-owned file, implement the Contacts adapter endpoint/service and record a Lead integration request instead.

## Completion

Commit.
Open PR to dev.
Do not merge.

Report:
- branch
- final SHA
- PR
- files changed
- migration
- segment compiler design/tests
- consent/suppression/eligibility
- import/export/bulk
- snapshot contract
- async/storage reuse
- tests/checks
- execution blockers
- integration requests/deviations
