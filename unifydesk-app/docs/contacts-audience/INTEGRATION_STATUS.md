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

This is the only valid B1/B2/F1 base.

## Worker branches

B1:
`feature/contacts-core-r1`

B2:
`feature/contacts-audience-processing-r1`

F1:
`feature/contacts-audience-ui-r1`

QA after integration:
`review/contacts-audience-security-qa-r1`

## Migration ownership

- B1: V80__contacts_core.sql
- B2: V81__contacts_audience_processing.sql
- Lead: V82__contacts_audience_integration.sql

## Round 1

B1: NOT STARTED
B2: NOT STARTED
F1: NOT STARTED

## Lead integration

NOT STARTED

After B1/B2/F1 integration, Lead must stamp:
- exact integrated SHA
- B1/B2/F1 final SHAs/PRs
- V82 integration status
- QA base

## QA/SEC

NOT STARTED

Exact QA base:
`<LEAD_TO_STAMP_AFTER_ROUND1_INTEGRATION>`

QA must never branch from moving dev.

## Architecture lineage

Architecture parent before docs:
`b81f68c9d162c5778042f338afbdfe561d164506`

Architecture merge / frozen worker base:
`f31f4f950cc257d4d1edd947a5038a3447599f85`
