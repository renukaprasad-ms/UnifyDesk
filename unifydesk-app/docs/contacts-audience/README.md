# UnifyDesk Contacts & Audience Management

Status: Round-0 architecture in progress  
Product owner: Product Manager / L1  
Application: `unifydesk-app/`  
Architecture source base: `dev @ 88af16fe1cd6966730c26447119416104039e6bb`

## Product purpose

Contacts & Audience Management is the shared customer-data foundation for UnifyDesk.

It owns canonical workspace-scoped contacts, deterministic identities, custom fields, tags, static lists, dynamic segments, channel consent/suppression state, bulk import/export, merge/delete semantics and reusable audience-resolution contracts.

It is intentionally **not** a second Broadcast database, a CRM deal pipeline, an Ad Center audience implementation, or a copy of Inbox message history.

## Round-1 product boundary

Round 1 includes:

- contact CRUD, search, filtering and detail;
- multiple email addresses and phone numbers per contact;
- deterministic normalization, duplicate detection and explicit merge;
- optional owner assignment to an active workspace member;
- bounded typed custom fields;
- tags;
- static lists and membership;
- dynamic segment definitions and evaluated previews/counts;
- CSV import with preview/mapping, duplicate policy and error report;
- asynchronous CSV export;
- asynchronous bulk tag/list/field/archive/delete operations;
- channel preference/consent audit;
- channel suppressions and central eligibility decisions;
- contact-native activity/audit references;
- reusable audience resolution/snapshot contract for Broadcast and future activation consumers;
- plan entitlements, storage accounting, async jobs, realtime progress, observability and lifecycle integration;
- Contacts workspace UI, contact detail, import/export, lists, tags, segments, consent and bulk UX.

Round 1 deliberately defers:

- deals, pipelines, tasks and sales automation;
- forms/landing-page capture;
- fuzzy/probabilistic/AI identity matching;
- full CDP event ingestion and computed traits;
- per-campaign behavioral segment predicates beyond supported rollups;
- journey/workflow automation;
- XLS/XLSX imports;
- external CRM sync connectors;
- public customer-data API tokens;
- automated Meta/Google audience activation;
- lead scoring/predictive traits;
- legal-policy automation that attempts to decide jurisdictional compliance for the customer.

## Non-negotiable invariants

1. Every domain row is workspace/company scoped.
2. A contact ID is the canonical identity. Email/phone are identities attached to it, never the contact primary key.
3. Identity resolution in Round 1 is deterministic only.
4. One active normalized email or phone identity can belong to only one active contact within a company.
5. Lists do not imply marketing consent.
6. Consent and suppression are separate concepts.
7. A suppression can block delivery even when an opt-in exists.
8. Unknown consent is not silently converted into opt-in.
9. Dynamic segments are predicates, not duplicated contact tables.
10. Bulk work is bounded, restart-safe and observable.
11. Audience resolution is frozen before a send; a live segment is never re-evaluated mid-send.
12. No cross-company search, import, export, merge, audience or side-channel leak.
13. Existing Auth/RBAC, Billing/Entitlements, Storage, Async Jobs, Kafka/realtime and observability are reused.
14. Ad Center implementation remains separate during Round 1.

## Documents

- `RESEARCH.md` — repo + public product/API research
- `ARCHITECTURE.md` — system shape and integration boundaries
- `DATA-MODEL.md` — PostgreSQL model and invariants
- `API-CONTRACT.md` — frozen Round-1 HTTP/DTO semantics
- `IMPORT-EXPORT.md` — bulk ingestion/export behavior
- `SEGMENTATION.md` — dynamic segment AST/evaluation
- `CONSENT-SUPPRESSION.md` — channel preference and eligibility semantics
- `CONTACT-MERGE.md` — dedupe/merge/delete/restore rules
- `INTEGRATIONS.md` — Inbox/Broadcast/Ads/plan/storage seams
- `SECURITY.md` — security model
- `PERFORMANCE.md` — scale requirements
- `DECISIONS.md` — frozen architecture decisions
- `INTEGRATION_STATUS.md` — implementation/integration status
- `TEAM_WORKFLOW.md` — five-chat workflow
- `TASKS.md` — worker ownership and migration plan
- `QA-SEC-PLAN.md` — integrated verification plan
- worker prompts for B1, B2, F1 and QA/SEC.

## Workflow

Round 0 produces and merges architecture/contracts only.

After architecture merge, L1 freezes the exact architecture merge SHA as the Round-1 worker base. A follow-up coordinator metadata commit records that immutable SHA in `TASKS.md` and worker prompts. This mirrors the established UnifyDesk WhatsApp/Managed modules workflow and avoids the impossible requirement for a Git commit to contain its own SHA.

Workers branch from the frozen SHA, never from a later moving `dev`.
