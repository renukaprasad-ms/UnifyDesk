# Contacts & Audience Architecture Decisions

Status: FROZEN for Round 1 once worker base SHA is published.

## D-001 Canonical contact ID

Use UnifyDesk Snowflake `Long` internally and string IDs externally.

Email and phone are identities, not primary keys.

Reason: one person can have multiple addresses/numbers and identifiers can change.

## D-002 Tenant scope

Every contact-domain table carries `company_id` even where a parent FK could imply it.

All hot unique/index predicates begin with company where practical.

Cross-company identity reuse is allowed. Cross-company matching is forbidden.

## D-003 Identity types

Round 1 canonical identities:

- EMAIL
- PHONE

WhatsApp uses a PHONE identity plus channel eligibility/state; there is no duplicate "WhatsApp identity" row for the same E.164 number.

## D-004 Email normalization

For uniqueness/matching:

1. trim surrounding Unicode whitespace;
2. Unicode normalize;
3. validate address structure;
4. lower-case the domain;
5. lower-case the mailbox for UnifyDesk matching consistency;
6. preserve dots and plus-tags exactly;
7. do not apply Gmail/provider-specific aliases.

Store both raw/display value and normalized value.

## D-005 Phone normalization

Use a proven phone-number parser.

- valid international input normalizes to E.164;
- national input requires an explicit/default workspace/import country;
- ambiguous/invalid input is rejected for identity use;
- raw value is retained for display/audit where appropriate;
- no guessed country from unrelated contact fields.

## D-006 Identity uniqueness and duplicate detection

One active `(company_id, identity_type, normalized_value)` maps to one active contact.

Exact normalized identity overlap is a deterministic match.

Names, organization, job title, country and similar fields never trigger automatic merge.

If one import row's identifiers resolve to different existing contacts, the row is `IDENTITY_CONFLICT`; no automatic merge occurs.

## D-007 Manual merge

Round 1 supports explicit merge.

- target contact survives;
- source becomes `MERGED` and records `merged_into_contact_id`;
- identities move to target after conflict checks;
- list/tag membership union;
- target non-null standard/custom values win by default;
- missing target values may be filled from source;
- consent/suppression events remain immutable and follow their identities;
- delivery eligibility resolves to the safest effective state;
- merge is idempotent and audited.

No fuzzy merge suggestions in Round 1.

## D-008 Profile fields

Contacts contain useful communication profile fields only, not CRM pipeline objects.

Round 1 standard fields include:

- first/last/display name;
- organization/company name string;
- job title;
- country;
- timezone;
- language;
- source + source detail;
- optional owner user;
- notes;
- lifecycle status;
- last activity timestamp;
- bounded custom fields.

## D-009 Custom-field storage

Definitions are normalized rows. Per-contact values are bounded JSONB.

Do not create an unbounded EAV row for every custom field value.

Supported Round-1 types:

- TEXT
- NUMBER
- BOOLEAN
- DATE
- DATETIME
- SINGLE_SELECT
- MULTI_SELECT

Definitions validate value type/options before persistence.

A JSONB GIN index may support equality/containment, but arbitrary range predicates are not promised to be instant at million-contact scale. Segment count refresh can be asynchronous.

## D-010 Tags

Tags are reusable company-scoped objects with normalized unique names and a membership join table.

Tags do not change consent.

## D-011 Static lists

Lists are manually/API/import managed reusable groups.

List membership does not change consent or suppression.

Deleting/archiving a list never deletes contacts.

## D-012 Dynamic segments

Segments store a versioned predicate AST.

The source of truth is the predicate, not a duplicated membership table.

Evaluation compiles to bounded parameterized SQL.

Raw member enumeration is keyset paginated.

Counts are cached with `definition_version` and `evaluated_at`; stale counts are labeled.

## D-013 Segment condition limits

Round 1 has bounded complexity:

- max nested depth;
- max clause count;
- whitelisted fields/operators;
- bounded IN-list sizes;
- no raw SQL/expression input;
- no regex supplied by users.

Exact defaults are in `SEGMENTATION.md`.

## D-014 V1 behavioral segmentation

Round 1 supports `last_activity_at` and Contacts-native activity fields.

Per-campaign open/click/message-history predicates are deferred until a proper activity rollup/event contract exists.

Do not query enormous raw delivery tables from every segment evaluation.

## D-015 Consent vs suppression

Consent/preference and suppression are separate.

A profile may be opted in but still suppressed due to hard bounce, complaint, invalid destination, admin block or provider block.

Static lists never imply opt-in.

## D-016 Consent history

Every material channel preference change writes an append-only consent event containing:

- company;
- identity;
- channel;
- purpose;
- old/new state;
- source;
- actor/source reference;
- occurred/recorded timestamps;
- bounded evidence metadata.

The current preference row is a projection for fast eligibility.

## D-017 Marketing default

Central eligibility takes `channel` and `purpose`.

Default Round-1 policy for MARKETING is conservative: an explicit effective opt-in is required and no active blocking suppression may exist.

TRANSACTIONAL eligibility does not reinterpret a marketing opt-out as marketing permission; provider/channel modules can impose additional rules.

Provider-specific regulatory requirements remain outside generic Contacts.

## D-018 Bulk import format

Round 1 supports CSV only.

Reason:

- Commons CSV is already present;
- streaming is straightforward;
- avoids a new XLSX/ZIP parsing attack and memory surface;
- keeps Round 1 focused.

XLS/XLSX is a later feature.

## D-019 Import duplicate modes

Round 1 modes:

- `CREATE_ONLY`: matched existing contacts are skipped/conflicted, never modified.
- `UPSERT_MATCHED`: deterministic identity match updates mapped fields.
- `SKIP_MATCHED`: only unmatched valid rows are created.

No "force merge" of two existing contacts from import.

## D-020 Import idempotency

Import creation requires a caller idempotency key.

Job + mapping configuration is immutable after processing begins.

Worker commits row-batch effects and checkpoint atomically, so crash/reclaim cannot double-apply a completed chunk.

Unique identity/list/tag constraints provide second-line replay safety.

## D-021 Export

Exports are asynchronous CSV artifacts.

Access requires fresh authorization and a short-lived private download URL.

CSV formula injection is mitigated.

Artifacts expire and are deleted; bytes count against existing `EXPORT` storage accounting while retained.

## D-022 Bulk operations

Large tag/list/update/archive/delete/export operations use existing Async Jobs.

Never load all selected contact IDs into application memory.

Selection is represented by explicit IDs within a strict small cap or by a frozen filter/list/segment selection contract and processed keyset/chunked.

## D-023 Audience snapshot timing

A reusable list/segment is live before launch.

Final recipient selection is frozen at **send preparation**, meaning:

- immediate send: when the run is accepted for preparation;
- scheduled send: when the scheduled run becomes runnable/preparation starts.

The snapshot is immutable for retries/reporting.

No live segment re-evaluation occurs inside delivery loops.

## D-024 Snapshot eligibility

Snapshot creation resolves:

- source list/segment;
- identity required by channel;
- duplicates;
- effective consent/preferences;
- active suppressions;
- contact lifecycle state.

Provider/channel-specific policy can add exclusions after this common eligibility layer.

## D-025 Broadcast compatibility

Round 1 does not delete existing `broadcast_groups`.

A Lead-owned integration adapter will later allow Broadcast to consume Contacts audience snapshots. Existing group behavior remains backward compatible.

Existing email hard-bounce suppression must be mirrored/bridged into central Contacts suppression before Contacts audiences become authoritative for Broadcast eligibility.

## D-026 Inbox compatibility

Inbox keeps its own participant/message model.

A company-scoped identity resolver can decorate Inbox participants with contact data.

No hard FK and no automatic contact creation from inbound messages in Round 1.

## D-027 Ads compatibility

Core Contacts does not write `ad_audiences` in Round 1.

Future Ad Center activation maps an internal audience/snapshot to provider-specific audience records and performs provider normalization/hashing at that boundary.

Google's 2026 Customer Match API transition reinforces this adapter design.

## D-028 Search

PostgreSQL remains the only search datastore in Round 1.

No Elasticsearch/OpenSearch.

Exact identity lookup uses B-tree indexes. Name/email/phone text search uses bounded normalized search with PostgreSQL indexing; `pg_trgm` may be used only if deployment support is confirmed.

## D-029 Pagination

Do not use deep offset pagination on million-row contact/member endpoints.

Public collection APIs use opaque keyset cursors.

## D-030 Contact deletion

User delete is a soft-delete/tombstone first.

Round 1 supports restore during retention.

Purge/anonymization is asynchronous and bounded.

Historical send snapshots/reporting are not cascaded away.

Where lawful/necessary, a non-reversible identity fingerprint may remain in suppression tombstones to prevent a deleted unsubscribed address from being silently re-enabled on re-import; plain profile PII is not retained merely for convenience.

## D-031 Audit/activity

Do not duplicate full Inbox/Broadcast payloads into Contacts.

Contacts stores bounded activity/audit references and Contacts-native changes.

Cross-module timeline enrichment resolves source references or consumes compact future activity events.

## D-032 Migration allocation

Reserve the 90-series for Contacts Round 1 to avoid colliding with ongoing Ad Center work:

- B1: V90-V92
- B2: V93-V95
- L1 integration: V96

Workers may use fewer migrations but must not leave their allocation or renumber another worker.

## D-033 Worker independence

B1/B2/F1 branch from the exact frozen architecture SHA.

B2 may rely on a documented B1 SQL seam but may not import classes that exist only on B1's branch.

Cross-worker foreign keys that prevent isolated migration testing are added by L1 in V96 after integration.

## D-034 No premature provider activation

Round 1 finishes reusable audience contracts but does not implement live Meta/Google audience sync while Ad Center work is in progress.
