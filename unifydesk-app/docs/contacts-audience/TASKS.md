# Contacts & Audience Round-1 Tasks and Ownership

Status: PRE-FREEZE  
Architecture branch: `docs/contacts-audience-architecture-r0`  
Frozen Round-1 worker base SHA: `__ROUND1_BASE_SHA__`

After architecture merge, L1 replaces the token above with the exact immutable SHA.

Workers read prompts/docs from dev after the metadata commit but create branches from exactly the frozen SHA.

## Branches

- B1: `feature/contacts-core-r1`
- B2: `feature/contacts-audience-runtime-r1`
- F1: `feature/contacts-audience-ui-r1`
- QA: `review/contacts-audience-security-qa-r1` from later integrated SHA

## Shared frozen docs

All workers read:

- README.md
- ARCHITECTURE.md
- DECISIONS.md
- DATA-MODEL.md
- API-CONTRACT.md
- SECURITY.md
- PERFORMANCE.md
- INTEGRATIONS.md
- TEAM_WORKFLOW.md

B2 additionally reads:
- IMPORT-EXPORT.md
- SEGMENTATION.md
- CONSENT-SUPPRESSION.md
- CONTACT-MERGE.md

F1 reads relevant UX/API/security sections.

## B1 — Contacts Core / Control Plane

Owns:

- backend `com.messaging.contacts.core/**`
- contact CRUD
- identities
- email/phone normalization
- custom field definitions/validation
- tags
- static lists/memberships
- merge/archive/delete/restore
- contact search/detail
- Contacts permissions backend definitions/default-role wiring if no hot-file conflict
- core activity refs
- B1 tests

Migration allocation:
- V90
- V91
- V92

B1 must not implement:
- segments
- consent/suppression
- import/export
- bulk jobs
- audience snapshots
- Ads integration
- Broadcast runtime
- frontend

## B2 — Audience Runtime / Data Processing

Owns:

- backend `com.messaging.contacts.audience/**`
- segments
- segment compiler
- consent preferences/events
- suppressions/events
- eligibility
- imports
- exports
- bulk jobs
- audience snapshots
- Contacts outbox/integration events
- AsyncJobType additions if ownership remains conflict-free
- private import/export file adapter
- B2 tests

Migration allocation:
- V93
- V94
- V95

B2 reads B1 tables through frozen JDBC seam.

B2 must not modify B1 entities/repositories/services/migrations.

## F1 — Contacts UI

Owns:

- new `frontend/src/features/contacts/**`
- new `frontend/src/components/contacts/**`
- new `frontend/src/pages/contacts/**`
- AppRoutes Contacts routes
- AppSidebar Contacts navigation
- frontend permission constants
- frontend tests/fixtures

Pages:
- contacts home/table
- contact detail
- lists
- segments
- import wizard
- tags/custom-field settings where UX requires
- consent/preferences
- bulk actions
- export/job status

F1 must not modify backend/Flyway/Ads.

## L1 integration ownership

L1 alone handles after worker completion:

- V96 cross-worker constraints
- hot shared permission/default role glue if B1/F1 ownership collides
- company purge lifecycle glue
- Inbox identity resolver integration
- Broadcast audience snapshot adapter/legacy compatibility
- Managed Email/legacy Broadcast suppression bridge
- conflict resolution between worker PRs
- final documentation status

No live Meta/Google audience sync.

## Migration rules

B1 and B2 branches must migrate independently from frozen base.

Therefore B2 V93-V95 must not FK directly to B1-only tables if that would make its branch migration fail.

Use documented bigint IDs + indexes in B2 migration.

L1 adds cross-worker FKs/checks in V96 after both migrations coexist.

## Required checks

Backend workers:

```bash
cd unifydesk-app/backend
./mvnw spotless:check
./mvnw test
./mvnw package -DskipTests
```

Frontend:

```bash
cd unifydesk-app/frontend
npm ci
npm run format:check
npm run lint
npm test
npm run build
```

## Pull request contract

Each worker PR includes:

- exact frozen base SHA;
- scope completed;
- migration list;
- tests;
- performance/security notes;
- contract deviation/integration request;
- no unrelated refactor.

Workers do not merge.
