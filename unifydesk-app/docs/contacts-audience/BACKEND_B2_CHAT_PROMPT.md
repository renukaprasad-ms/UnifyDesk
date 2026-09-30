# Prompt — B2 Backend Audience / Data Processing

You are B2 for UnifyDesk Contacts & Audience Management Round 1.

Repository:
`renukaprasad-ms/messaging`

Application:
`unifydesk-app/`

Docs:
`unifydesk-app/docs/contacts-audience/`

Your branch:
`feature/contacts-audience-runtime-r1`

Your EXACT frozen base SHA:
`__ROUND1_BASE_SHA__`

Create your branch from exactly that SHA.

Do NOT branch from moving dev.
Do NOT merge.
Do NOT cherry-pick B1/F1.

## Read first

Read completely:

- README.md
- RESEARCH.md
- ARCHITECTURE.md
- DECISIONS.md
- DATA-MODEL.md
- API-CONTRACT.md
- SEGMENTATION.md
- IMPORT-EXPORT.md
- CONSENT-SUPPRESSION.md
- CONTACT-MERGE.md
- SECURITY.md
- PERFORMANCE.md
- INTEGRATIONS.md
- TASKS.md
- TEAM_WORKFLOW.md

Inspect:

- AsyncJob entity/service/repository/type
- realtime progress
- StorageQuotaService/StorageAccounting
- storage provider adapters
- Broadcast snapshot/worker/idempotency patterns
- Kafka EventPublisher/consumer conventions
- current provider bounce/suppression flow
- authorization conventions

## Mission

Implement Audience Runtime/Data Processing only:

- dynamic segments/compiler;
- consent preference projection + immutable events;
- suppressions + audit;
- central channel eligibility;
- CSV import;
- CSV export;
- bulk operations;
- audience snapshots;
- compact contacts outbox/events;
- async job integration;
- private file storage adapter;
- performance-safe worker/recovery.

## Independence rule

Your branch does NOT contain B1 tables/classes/migrations.

Do NOT import B1 entities/repositories/services.

Use the frozen B1 SQL seam in DATA-MODEL.md through B2-owned JDBC projections.

In tests:
- mock that seam; or
- create fixture SQL tables matching frozen columns.

Your migrations must work without V90-V92.

L1 adds cross-worker FKs in V96.

## Exact ownership

New:

`backend/src/main/java/com/messaging/contacts/audience/**`

Tests:

`backend/src/test/java/com/messaging/contacts/audience/**`

Shared existing files you MAY modify:

- `backend/src/main/java/com/messaging/asyncjob/enums/AsyncJobType.java`
- minimal storage/provider interfaces only if strictly required and backward compatible
- no broad Media refactor

If shared file is contested, report L1 integration request.

## Migrations

Your allocation only:

- `V93__contacts_audience_runtime.sql`
- `V94__contacts_import_export.sql`
- `V95__contacts_audience_hardening.sql`

Do not use V90-V92 or V96+.

## Segments

Implement SEGMENTATION.md:

- versioned AST;
- whitelisted fields/operators;
- safe parameterized compiler;
- company/status injection;
- list/tag/identity/consent/suppression/custom-field predicates;
- keyset members;
- cached count/version;
- bounded sync preview;
- async expensive count.

No raw SQL.
No Java in-memory filtering of whole audiences.

## Consent

Implement:

- current preference projection;
- append-only consent events;
- MARKETING purpose;
- EMAIL/SMS/WHATSAPP;
- UNKNOWN/OPTED_IN/OPTED_OUT;
- explicit source/evidence;
- idempotency;
- server-derived actor;
- no implicit opt-in.

## Suppression

Implement:

- current suppressions;
- append-only events;
- internal provider/system seam;
- manual authorized release;
- reason/source;
- idempotency;
- active index.

Do not remove legacy Broadcast suppression behavior on your branch.

## Eligibility

Return structured reason.

For MARKETING:
- active contact;
- selected valid identity;
- OPTED_IN;
- no blocking suppression.

Implement deterministic primary/fallback identity selection from frozen B1 seam.

High-volume code uses SQL/JDBC, not HTTP per recipient.

## Import

CSV only.

Use Commons CSV already present.

Implement IMPORT-EXPORT.md:

- private object storage;
- CONTACT_DATA accounting;
- upload safety;
- preview;
- mapping;
- CREATE_ONLY/SKIP_MATCHED/UPSERT_MATCHED;
- default country;
- custom fields through frozen seam;
- optional target list;
- consent mapping;
- chunk/checkpoint;
- AsyncJob;
- stale reclaim;
- bounded errors;
- private error report;
- cancellation;
- Idempotency-Key.

Do not add XLSX.

## Export

- async CSV;
- list/segment/filter/explicit selection;
- keyset streaming;
- formula-injection mitigation;
- EXPORT storage accounting;
- short authorized access URL;
- expiry/purge.

Never load entire export in memory.

## Bulk

Implement async:

- ADD_TAG
- REMOVE_TAG
- ADD_TO_LIST
- REMOVE_FROM_LIST
- UPDATE_FIELD
- ARCHIVE
- DELETE
- EXPORT may delegate export service

Use selection contract, checkpoint, idempotency.

For B1 writes, use a narrow frozen JDBC core writer seam or report exact L1 adapter need. Do not invent B1 Java APIs.

## Audience snapshots

Implement immutable snapshot tables/service.

Input:
- selection
- channel
- purpose
- idempotency

Process:
- enumerate source;
- choose identity;
- apply eligibility;
- dedupe destination;
- record exclusions/counts;
- READY immutable.

No Broadcast modification on your branch.

Expose L1-facing internal service/repository seam to iterate eligible rows.

## Outbox

Compact no-PII events.

Claim/retry/idempotency.

Use existing EventPublisher.

Kafka outage must not lose Contacts mutation truth.

## Async jobs

Add:

- CONTACT_IMPORT
- CONTACT_EXPORT
- CONTACT_BULK
- CONTACT_SEGMENT_COUNT

Use AsyncJobService progress/reclaim semantics.

Avoid duplicate worker framework.

## Storage

Do not force CSV through current image/PDF Media upload API.

Create Contacts private file adapter using existing StorageProvider/StorageQuota primitives safely.

Do not create a separate quota subsystem.

## Permissions

Enforce frozen keys:

- contacts.import
- contacts.export
- contacts.consent.manage
- audiences.read/create/modify/delete
- contacts.read/modify/delete where operations require them

If CompanyPermissionKeys does not contain them on base, use constants isolated in B2 and report L1/B1 shared catalog integration; do not edit a hot file owned by B1 unless TASKS ownership is amended.

## Security tests

At minimum:

- foreign IDs
- consent actor/source spoof
- reimport cannot re-opt-in stale opt-out
- suppression override
- segment injection/complexity
- upload abuse
- CSV formula
- export permission revoke
- signed URL expiry
- idempotency
- worker crash/reclaim
- quota
- contact limit seam
- cross-company identical identity
- logs contain no row PII

## Performance tests

- 10k import/segment/export fixture
- 100k where environment permits
- query plans/index assertions
- bounded memory
- chunk transactions
- no N+1 row processing

Report 1M NOT EXECUTED unless actually tested.

## Forbidden

Do not modify:

- B1 package
- B1 migrations
- frontend
- Ads
- Broadcast runtime
- Inbox runtime
- architecture docs

Do not add Elasticsearch/OpenSearch/XLSX.

## Required checks

```bash
cd unifydesk-app/backend
./mvnw spotless:check
./mvnw test
./mvnw package -DskipTests
```

Also migrate B2 branch cleanly from frozen base despite no B1 migrations.

## PR requirements

PR to dev includes:

- frozen base SHA;
- V93-V95 usage;
- B1 SQL seam assumptions;
- job/storage behavior;
- security/performance evidence;
- tests;
- integration requests/deviations.

## Final report

Report:

- branch
- final SHA
- PR
- files
- migrations
- segments/consent/suppression/import/export/bulk/snapshot status
- tests/perf
- B1 seam assumptions
- integration requests
- blocker

Do not merge.
