# UnifyDesk Contacts & Audience Management

Status: Round-1 architecture frozen
Lead: Product Manager / Lead Architect
Application: `unifydesk-app/`
Architecture PR: #198
Frozen Round-1 worker base: `f31f4f950cc257d4d1edd947a5038a3447599f85`

## Purpose

Contacts & Audience Management is the shared first-party customer data primitive for UnifyDesk. It replaces the pattern where each channel or campaign stores unrelated recipient data.

The platform model is:

`Contact -> Identity -> Communication Preference / Suppression -> List / Segment -> Audience Snapshot -> Channel Execution`

Round 1 establishes a production foundation for Contacts, static Lists, dynamic Segments, Tags, Custom Fields, CSV import/export, consent/preferences, suppressions, eligibility, merge, bulk operations, activity summaries, and future channel/ad integrations.

## Round-1 principles

- Contact IDs are opaque internal IDs. Email is not the contact primary key.
- One contact can own multiple identities.
- Exact normalized identities are unique inside one company, never globally across companies.
- Lists are static membership; Segments are dynamic SQL-compiled filters.
- Consent/preferences and hard suppression are separate concepts.
- Provider and jurisdiction policy is evaluated by an eligibility layer, not stored as provider DTOs on contacts.
- Audience membership used by a Broadcast is frozen into a snapshot before delivery.
- A later opt-out/suppression still blocks provider submission even if the contact was in a frozen snapshot.
- PostgreSQL remains the V1 query engine. No Elasticsearch/OpenSearch is introduced.
- Long-running imports, exports, count refreshes and large bulk operations use the existing async-job model.
- Contact/import/export storage uses the existing storage provider + quota/accounting model.
- Existing `broadcast_groups` remain a compatibility path in Round 1; they are not the canonical Contacts data model.
- No provider-specific Meta Custom Audience or Google Customer Match sync is implemented in Round 1.

## Worker branches

- B1: `feature/contacts-core-r1`
- B2: `feature/contacts-audience-processing-r1`
- F1: `feature/contacts-audience-ui-r1`
- QA after integration: `review/contacts-audience-security-qa-r1`

B1/B2/F1 branch from exactly:
`f31f4f950cc257d4d1edd947a5038a3447599f85`

They must read the current `dev` worker manifest documents but must not branch from moving `dev`.

## Documents

- `ARCHITECTURE.md` — system boundaries and execution flows
- `DATA-MODEL.md` — relational model and invariants
- `API-CONTRACT.md` — frozen HTTP/internal contracts
- `SECURITY.md` — threat model and security invariants
- `PERFORMANCE.md` — scale model and query/batch rules
- `DECISIONS.md` — architecture decisions
- `SEGMENTATION.md` — filter AST and SQL compilation
- `CONSENT-SUPPRESSION.md` — preferences, suppression and eligibility
- `IMPORT-EXPORT.md` — file/job lifecycle
- `CONTACT-MERGE.md` — deterministic merge contract
- `INTEGRATIONS.md` — Inbox, Broadcast, managed channels, Ads, billing and storage
- `RESEARCH.md` — product/API research findings
- `TASKS.md` — Round-1 ownership and migration allocation
- `TEAM_WORKFLOW.md` — branch/integration workflow
- `QA-SEC-PLAN.md` — post-integration verification plan
- `INTEGRATION_STATUS.md` — Lead integration ledger
- `BACKEND_B1_CHAT_PROMPT.md` — standalone B1 assignment
- `BACKEND_B2_CHAT_PROMPT.md` — standalone B2 assignment
- `FRONTEND_CHAT_PROMPT.md` — standalone F1 assignment
- `SECURITY_QA_CHAT_PROMPT.md` — standalone QA/SEC assignment

QA does not start from the worker base. Lead first integrates B1/B2/F1 + V82, freezes the integrated SHA, then stamps the QA prompt.
