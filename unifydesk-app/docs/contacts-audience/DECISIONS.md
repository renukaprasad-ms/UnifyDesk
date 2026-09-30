# Contacts & Audience Architecture Decisions

Status: Round-1 frozen decisions

## ADR-001 — Contact uses internal ID, not email, as identity

A Contact is identified by opaque internal ID + company_id. Email/phone/external identifiers live in contact_identities.

Reason: Inbox, SMS, WhatsApp and Ads require a multi-identity model.

## ADR-002 — Exact normalized identity is company-scoped unique

Active normalized EMAIL/PHONE identities may belong to only one active Contact inside one company. The same value may exist in another company.

Reason: prevents cross-Contact ambiguity while preserving tenant isolation.

## ADR-003 — Email canonicalization is conservative

Canonical matching lowercases and IDNA-normalizes the domain. V1 does not strip plus aliases or dots.

Reason: provider-specific mailbox equivalence is not universally safe.

## ADR-004 — Phone identity is E.164

Phone ingestion normalizes to E.164 using a vetted library and rejects ambiguous invalid input unless a country context explicitly resolves it.

## ADR-005 — Lists are static, Segments are dynamic

List membership is persisted. Segment membership is evaluated from a bounded filter AST.

## ADR-006 — Typed sparse custom-field values

Use typed relational values rather than one unbounded JSONB profile blob or unrestricted generic EAV.

Reason: preserves indexing, range queries, type validation and predictable segment compilation.

## ADR-007 — No Elasticsearch/OpenSearch in V1

PostgreSQL is the source and query engine for V1. Search uses indexed exact/prefix/name/identity patterns and keyset pagination.

## ADR-008 — Consent and suppression are separate

Preference answers “what permission/evidence is currently known?” Suppression answers “must delivery be blocked?”

Suppression wins.

## ADR-009 — Consent audit is append-only

Current projection is mutable; event history is append-only and idempotent.

## ADR-010 — Eligibility is a shared platform service

Broadcast and future audience consumers use a single Contacts eligibility contract. Provider runtime guards remain additional downstream checks.

## ADR-011 — Audience membership is frozen at run materialization

Draft/schedule preview is not authoritative. At actual run preparation, List/Segment membership is resolved and persisted as an immutable snapshot.

Reason: deterministic reporting and replay.

## ADR-012 — Eligibility is rechecked before provider submission

A contact can opt out after snapshot. Frozen membership does not override current suppression/consent.

## ADR-013 — One selected destination per Contact/channel in V1

Primary active identity is selected; otherwise deterministic oldest active identity. If it is ineligible, do not silently fall back.

## ADR-014 — Existing Broadcast groups are compatibility-only

Round 1 does not destructively migrate broadcast_groups. New Contacts Lists/Segments become the forward audience source; legacy broadcasts continue working.

## ADR-015 — CSV only for V1 import/export

XLSX is deferred. CSV is sufficient for the foundation and has a smaller parser/security surface.

## ADR-016 — Reuse AsyncJob

Do not build a Contacts-specific job platform. Extend AsyncJobType and workers.

## ADR-017 — Reuse storage provider/accounting without weakening Media validation

Contacts CSV/error/export files use a Contacts storage adapter over existing storage/quota primitives. They do not masquerade as image/PDF Media uploads.

## ADR-018 — Explicit merge only

Exact incoming data may map to one existing Contact. Two existing Contacts are never automatically fuzzy-merged.

## ADR-019 — Soft delete then privacy purge

Delete immediately blocks use, then asynchronous purge removes mutable PII while preserving historical campaign snapshots and integrity/audit facts.

## ADR-020 — No self-service restore in V1

Restore semantics complicate identity/suppression safety. Re-creation is explicit and suppression tombstones remain effective.

## ADR-021 — Activity is federated

Contacts owns only Contacts-domain activity events. Inbox/Broadcast activity is read through adapters; message bodies/provider payloads are not duplicated into contact_activity_events.

## ADR-022 — Provider audience activation is deferred

Meta Custom Audiences / Google Customer Match integration is an adapter boundary, not a core Contacts table concern.

## ADR-023 — Provider hashing happens at activation time

Core stores normalized identities, not “Google hashes” or “Meta hashes.” Activation adapters perform current provider-required normalization/hashing.

## ADR-024 — Plan names never appear in Contacts

Limits use entitlement keys. Existing CONTACT_LIMIT and SEGMENT_LIMIT are reused; additional keys can be added without hardcoding Starter/Growth/etc.

## ADR-025 — Worker independence is contract-based

B1 and B2 own separate migrations/packages. B2 may consume B1 database contracts via its own SQL projection/mocks but cannot import B1 implementation classes on its independent branch.

## ADR-026 — Migration ownership

At architecture freeze the current highest migration is V79.

Reserved:
- B1: V80__contacts_core.sql
- B2: V81__contacts_audience_processing.sql
- Lead integration: V82__contacts_audience_integration.sql

Workers may not invent alternate migration numbers.

## ADR-027 — No Ad Center implementation changes in Round 1

Ad Center may consume the future activation projection later. Round-1 worker PRs must not modify the ongoing Ads implementation.

## ADR-028 — Tenant predicate is structural

company_id is mandatory in repository/service lookup contracts. A client-provided companyId is never trusted without CompanyAuthorizationService permission validation.
