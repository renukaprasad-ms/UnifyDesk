# Contacts & Audience Data Model

Status: Round-1 database contract

All mutable domain tables carry company_id even when ownership is derivable through another FK. Repository/API queries must include company_id. Cross-company collisions are allowed; cross-company reads/writes are not.

## B1 / V80 tables

### contacts

Core columns:
- id bigint PK
- company_id bigint not null
- first_name, last_name, display_name
- organization, job_title
- country_code, timezone, language
- source
- notes
- owner_user_id nullable
- status ACTIVE | MERGED | DELETED
- last_activity_at nullable
- merged_into_contact_id nullable
- created_by_user_id
- created_at, updated_at, deleted_at
- version

Indexes:
- (company_id, id)
- (company_id, updated_at desc, id desc)
- (company_id, created_at desc, id desc)
- (company_id, status, id)
- optional lower-name search expression/trigram only if extension already available; V1 must work without pg_trgm.

### contact_identities

- id
- company_id
- contact_id
- identity_type EMAIL | PHONE | EXTERNAL
- namespace nullable
- value
- normalized_value
- is_primary
- status ACTIVE | DISABLED
- created_at, updated_at, deleted_at

Required invariants:
- same-company active normalized identity unique across Contacts for identity_type + namespace
- same Contact has at most one active primary EMAIL and one active primary PHONE
- FK Contact and duplicate company ownership validated in service and Lead V82 composite constraints where practical

### contact_tags

- id, company_id
- name
- normalized_name
- color nullable
- metadata_json bounded nullable
- created_at, updated_at, archived_at

Unique active normalized tag name per company.

### contact_tag_memberships

- company_id, tag_id, contact_id
- assigned_by_user_id nullable
- assigned_at
- PK/unique(tag_id, contact_id)
- indexes(contact_id, tag_id), (company_id, tag_id, contact_id)

### contact_custom_field_definitions

- id, company_id
- field_key normalized immutable
- label
- field_type TEXT | NUMBER | BOOLEAN | DATE | DATETIME | SINGLE_SELECT | MULTI_SELECT
- options_json bounded for select fields
- required flag is false in V1 for bulk compatibility
- position
- status ACTIVE | ARCHIVED
- created_at, updated_at

Unique field_key and case-normalized label policy per company.

### contact_custom_field_values

Sparse typed values:
- company_id, contact_id, field_id
- text_value
- number_value numeric
- boolean_value
- date_value
- datetime_value
- text_array_value text[]
- updated_at

Exactly one typed value column is populated according to definition.
Unique(contact_id, field_id).

Indexes are typed by (company_id, field_id, value, contact_id); multi-select uses GIN. This is a bounded typed-EAV design. It is intentionally chosen over one JSONB blob so range/filter operations remain indexable and types remain enforceable. Entitlements bound field-definition count.

### contact_lists

- id, company_id
- name, description
- status ACTIVE | ARCHIVED
- member_count cached counter
- created_by_user_id
- created_at, updated_at, archived_at
- version

Unique active normalized list name per company.

### contact_list_memberships

- company_id, list_id, contact_id
- source MANUAL | IMPORT | API
- source_ref nullable
- added_by_user_id nullable
- added_at

Unique(list_id, contact_id). Index(contact_id, list_id).

### contact_merge_audit

- id, company_id
- source_contact_id
- target_contact_id
- strategy_json bounded
- actor_user_id
- merged_at

Append-only.

## B2 / V81 tables

### contact_segments

- id, company_id
- name, description
- filter_version
- filter_json
- filter_hash
- status ACTIVE | ARCHIVED
- cached_count nullable
- count_computed_at nullable
- created_by_user_id
- created_at, updated_at
- version

### contact_channel_preferences

Current projection:
- id, company_id, contact_id
- identity_id nullable
- channel EMAIL | SMS | WHATSAPP
- purpose MARKETING
- state UNKNOWN | OPTED_IN | OPTED_OUT
- source
- source_ref nullable
- occurred_at
- updated_at
- version

Unique current preference key by company/contact/identity/channel/purpose.

### contact_preference_events

Append-only history:
- id, company_id, contact_id
- identity_id nullable
- channel, purpose
- previous_state, new_state
- source, source_ref
- actor_type USER | CONTACT | IMPORT | API | PROVIDER | SYSTEM
- actor_id nullable
- reason bounded
- occurred_at
- created_at
- idempotency_key

Unique(company_id, idempotency_key).

### contact_suppressions

Current suppression projection:
- id, company_id
- contact_id nullable after privacy purge
- identity_id nullable
- identity_fingerprint keyed-HMAC nullable
- channel
- reason_code
- source, source_ref
- state ACTIVE | LIFTED
- suppressed_at, lifted_at
- created_at, updated_at
- version

### contact_suppression_events

Append-only state changes with idempotency key and actor/source.

### contact_imports

- id, company_id, requested_by_user_id
- storage resource reference/object metadata
- content_sha256
- mapping_json
- mapping_hash
- duplicate_strategy SKIP | MERGE_EXACT | FAIL
- target_list_id nullable
- idempotency_key
- status
- row counters
- error_report_resource_id nullable
- created_at, started_at, completed_at, expires_at
- async_job_id

Unique(company_id, idempotency_key).

### contact_exports

- id, company_id, requested_by_user_id
- source_type CONTACTS | LIST | SEGMENT
- source_id nullable
- field_selection_json
- status, row_count
- output_resource_id nullable
- created_at, completed_at, expires_at
- async_job_id

### contact_bulk_operations

- id, company_id, requested_by_user_id
- operation_type
- selection_json
- idempotency_key
- status/counters
- created_at/completed_at
- async_job_id

### contact_activity_events

Only Contacts-domain actions:
- id, company_id, contact_id
- event_type
- actor_type/actor_id
- source_ref
- summary_json bounded/non-PII-heavy
- occurred_at

No Inbox message body, provider webhook payload or campaign payload is copied here.

### contact_audience_snapshots

- id, company_id
- source_type LIST | SEGMENT
- source_id
- source_version/hash
- channel
- purpose
- snapshot_at
- status PREPARING | READY | FAILED
- candidate_count
- eligible_count
- cursor
- created_at/completed_at

### contact_audience_snapshot_members

- snapshot_id, company_id
- contact_id
- identity_id
- normalized_destination snapshot
- display_name_snapshot nullable
- eligibility_version/reason metadata bounded
- sequence_id

Unique(snapshot_id, contact_id); unique(snapshot_id, normalized_destination).

## Lead / V82

Lead adds cross-module constraints/indexes and Broadcast provenance after B1+B2 are integrated. V82 may add audience source/snapshot references to BroadcastMessage/Run and contact_id/identity_id/snapshot_member_id provenance to BroadcastRecipient while preserving legacy group columns and history.

B2 V81 must not declare foreign keys to B1 V80 tables on its independent worker branch if doing so would make the branch migration uncompilable in isolation. Lead V82 owns those cross-worker constraints.
