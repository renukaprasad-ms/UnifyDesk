# UnifyDesk Contacts & Audience Management

Status: Round-1 architecture package
Lead: Product Manager / Lead Architect
Application: unifydesk-app
Architecture parent dev SHA: b81f68c9d162c5778042f338afbdfe561d164506

## Purpose

Contacts & Audience Management is the shared first-party customer data primitive for UnifyDesk. It replaces the pattern where each channel or campaign stores unrelated recipient data.

The platform model is:

Contact -> Identity -> Communication Preference / Suppression -> List / Segment -> Audience Snapshot -> Channel Execution

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
- Existing broadcast_groups remain a compatibility path in Round 1; they are not the canonical Contacts data model.
- No provider-specific Meta Custom Audience or Google Customer Match sync is implemented in Round 1.

## Documents

- ARCHITECTURE.md — system boundaries and execution flows
- DATA-MODEL.md — relational model and invariants
- API-CONTRACT.md — frozen HTTP/internal contracts
- SECURITY.md — threat model and security invariants
- PERFORMANCE.md — scale model and query/batch rules
- DECISIONS.md — architecture decisions
- SEGMENTATION.md — filter AST and SQL compilation
- CONSENT-SUPPRESSION.md — preferences, suppression and eligibility
- IMPORT-EXPORT.md — file/job lifecycle
- CONTACT-MERGE.md — deterministic merge contract
- INTEGRATIONS.md — Inbox, Broadcast, managed channels, Ads, billing and storage
- RESEARCH.md — product/API research findings
- TASKS.md — Round-1 ownership and migration allocation
- TEAM_WORKFLOW.md — branch/integration workflow
- QA-SEC-PLAN.md — post-integration verification plan
- INTEGRATION_STATUS.md — Lead integration ledger
- BACKEND_B1_CHAT_PROMPT.md — standalone B1 assignment
- BACKEND_B2_CHAT_PROMPT.md — standalone B2 assignment
- FRONTEND_CHAT_PROMPT.md — standalone F1 assignment
- SECURITY_QA_CHAT_PROMPT.md — standalone QA/SEC assignment

The exact frozen Round-1 worker base SHA is published after the architecture package is merged to dev. Worker prompts are then stamped with that SHA in a documentation-only manifest update. Workers must branch from the frozen SHA, not the later moving dev tip.
