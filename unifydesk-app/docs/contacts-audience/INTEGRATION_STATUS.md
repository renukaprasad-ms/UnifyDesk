# Contacts & Audience Integration Status

## Architecture

- [x] Current dev inspected
- [x] Existing module/team workflows reviewed
- [x] Broadcast/Inbox/RBAC/entitlement/storage/async primitives reviewed
- [x] External Contacts/Audience research performed
- [x] V1 scope frozen
- [x] Migration slots V80/V81/V82 reserved
- [x] B1/B2/F1/QA ownership defined
- [x] Architecture PR #198 merged to dev
- [x] Exact worker base SHA frozen

## Frozen Round-1 worker base

`f31f4f950cc257d4d1edd947a5038a3447599f85`

## Round-1 workers

B1:
- branch: `feature/contacts-core-r1`
- PR: #202
- final reviewed head: `4c86c66607721c85b5e3f75d4b27bddae96dc421`
- migration: `V80__contacts_core.sql`
- status: MERGED

B2:
- branch: `feature/contacts-audience-processing-r1`
- PR: #201
- final head: `3c37f504fb5d256154f6c9f045567e514695a37a`
- migration: `V81__contacts_audience_processing.sql`
- status: MERGED

F1:
- branch: `feature/contacts-audience-ui-r1`
- PR: #200
- final head: `e9b4d7b7fdae1efd23b3ed53962a2882b12b9787`
- status: MERGED

Integrated worker tip before Lead V82:
`290f316fb47f5fb88e201779f008b82fab034253`

## Lead integration

Branch:
`feature/contacts-audience-integration-r1`

PR:
#203 initial Lead integration; #206 final Lead integration corrections

Migration:
`V82__contacts_audience_integration.sql`

Status:
MERGED

Lead integration includes:
- cross-worker constraints
- Broadcast LIST/SEGMENT source support
- frozen audience snapshot materialization
- Broadcast recipient Contact/identity/snapshot provenance
- pre-provider current eligibility recheck
- source-drift failure handling
- Broadcast Contact activity read adapter
- consent/suppression merge reconciliation
- async Contact privacy purge
- suppression HMAC tombstones across relevant identities
- bounded Contacts company-purge registration/private artifact deletion
- B2 import/bulk mutation ports wired to B1 production services/repositories
- bounded batch tag/List helpers and batch quota enforcement
- privacy-safe audience snapshot provenance with purged Contact/identity handling

Legacy `broadcast_groups` remain supported.

Exact integrated QA code base:

`f6a04c4596240d0c43bde6ef83c66619abbd9a02`

## QA/SEC

Status:
READY TO START

Branch:

`review/contacts-audience-security-qa-r1`

Create it from EXACTLY:

`be6de61a7c46db4785457f3f9bbb0d622f026101`

Do NOT branch from moving `dev`.

QA report path:

`unifydesk-app/docs/contacts-audience/SECURITY_QA_FEEDBACK.md`

## CI execution state at freeze

GitHub Actions for PR #206 completed as failures, but all inspected affected jobs contained zero workflow steps.

Therefore their product build/test status is:
`EXECUTION BLOCKED / NOT VERIFIED BY GITHUB ACTIONS`

QA must execute the backend/frontend commands on a functioning environment when possible and must not interpret zero-step jobs as either PASS or a product-code failure.

## Architecture lineage

Architecture parent before docs:
`b81f68c9d162c5778042f338afbdfe561d164506`

Architecture merge / frozen worker base:
`f31f4f950cc257d4d1edd947a5038a3447599f85`

Integrated Round-1 QA code base:
`be6de61a7c46db4785457f3f9bbb0d622f026101`
