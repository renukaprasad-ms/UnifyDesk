# Contacts & Audience Data Model

Status: FROZEN contract for Round 1 after worker-base publication.

All IDs are Snowflake `bigint` internally and strings at the API boundary.

All timestamps are `timestamp with time zone`.

## 1. B1-owned tables

### contact_records

Canonical profile row.

Columns:

- id bigint PK
- company_id bigint NOT NULL
- first_name varchar(160)
- last_name varchar(160)
- display_name varchar(255)
- organization_name varchar(255)
- job_title varchar(255)
- country_code varchar(2)
- timezone varchar(80)
- language_code varchar(32)
- source varchar(48) NOT NULL
- source_detail varchar(255)
- owner_user_id bigint NULL
- notes text NULL
- status varchar(24) NOT NULL
  - ACTIVE
  - ARCHIVED
  - DELETED
  - MERGED
- custom_fields jsonb NOT NULL default '{}'
- last_activity_at timestamptz NULL
- merged_into_contact_id bigint NULL
- deleted_at timestamptz NULL
- purge_after timestamptz NULL
- created_by_user_id bigint NULL
- updated_by_user_id bigint NULL
- version bigint NOT NULL default 0
- created_at timestamptz NOT NULL
- updated_at timestamptz NOT NULL

Constraints:

- company FK
- owner user FK is not sufficient to prove company membership; service validates active membership.
- merged target may be enforced by L1 V96 because B1 must migrate independently.
- `status='MERGED'` requires `merged_into_contact_id` in service logic; L1 may add DB check if safe.

Indexes:

- `(company_id, status, id)`
- `(company_id, updated_at desc, id desc)`
- `(company_id, owner_user_id, status, id)`
- `(company_id, country_code, status, id)`
- optional normalized-name/trigram indexes only after deployment support is confirmed.

### contact_identities

Columns:

- id bigint PK
- company_id bigint NOT NULL
- contact_id bigint NOT NULL
- identity_type varchar(16) NOT NULL: EMAIL | PHONE
- raw_value varchar(320) NOT NULL
- normalized_value varchar(320) NOT NULL
- label varchar(48)
- primary_identity boolean NOT NULL default false
- validation_state varchar(24) NOT NULL
  - VALID
  - INVALID
  - UNVERIFIED
- source varchar(48) NOT NULL
- status varchar(16) NOT NULL
  - ACTIVE
  - DELETED
- deleted_at timestamptz NULL
- created_at
- updated_at

Required database invariant:

A normalized active identity is unique inside a company/type.

Preferred PostgreSQL partial unique index:

```sql
create unique index ...
on contact_identities(company_id, identity_type, normalized_value)
where status = 'ACTIVE';
```

Required indexes:

- `(company_id, contact_id, status, id)`
- exact lookup `(company_id, identity_type, normalized_value)`
- primary lookup `(company_id, contact_id, identity_type, primary_identity)`

Service invariant:

At most one active primary identity per contact/type. Use locking plus a partial unique index where feasible.

### contact_custom_field_definitions

Columns:

- id
- company_id
- field_key varchar(80)
- normalized_key varchar(80)
- label varchar(160)
- field_type varchar(24)
- options_json jsonb NULL
- required boolean NOT NULL default false
- status varchar(16): ACTIVE | ARCHIVED
- position integer
- created_by_user_id
- version
- created_at
- updated_at

Unique active normalized key per company.

Field key is stable after creation. Label/options may change subject to validation.

### contact_tags

Columns:

- id
- company_id
- name varchar(160)
- normalized_name varchar(160)
- color_token varchar(32) NULL
- status varchar(16): ACTIVE | ARCHIVED
- contact_count bigint NOT NULL default 0
- created_by_user_id
- created_at
- updated_at

Unique active normalized name per company.

### contact_tag_memberships

Columns:

- id
- company_id
- contact_id
- tag_id
- source varchar(32)
- created_by_user_id NULL
- created_at

Unique `(company_id, contact_id, tag_id)`.

Indexes:

- `(company_id, tag_id, contact_id)`
- `(company_id, contact_id, tag_id)`

### contact_lists

Columns:

- id
- company_id
- name varchar(160)
- normalized_name varchar(160)
- description varchar(500)
- status varchar(16): ACTIVE | ARCHIVED
- member_count bigint NOT NULL default 0
- created_by_user_id
- version
- created_at
- updated_at

Unique active normalized name per company.

### contact_list_memberships

Columns:

- id
- company_id
- list_id
- contact_id
- source varchar(32)
  - MANUAL
  - IMPORT
  - API
  - BULK
  - MIGRATION
- source_ref varchar(160) NULL
- created_by_user_id NULL
- created_at

Unique `(company_id, list_id, contact_id)`.

Indexes:

- `(company_id, list_id, contact_id)`
- `(company_id, contact_id, list_id)`

### contact_merge_audit

Append-only merge audit.

Columns:

- id
- company_id
- source_contact_id
- target_contact_id
- requested_by_user_id
- merge_policy_version
- summary_json bounded
- created_at

This table is not the legal consent audit.

### contact_activity_refs

Contacts-native/cross-module reference timeline, not raw event storage.

Columns:

- id
- company_id
- contact_id
- activity_type varchar(48)
- source_type varchar(48)
- source_id varchar(160) NULL
- summary_code varchar(80)
- metadata_json jsonb NULL, bounded and PII-minimized
- occurred_at
- created_at

Indexes:

- `(company_id, contact_id, occurred_at desc, id desc)`
- `(company_id, source_type, source_id)` if source ID exists.

## 2. B2-owned tables

### contact_segments

Columns:

- id
- company_id
- name varchar(160)
- normalized_name varchar(160)
- description varchar(500)
- definition_json jsonb NOT NULL
- definition_version bigint NOT NULL
- status varchar(16): ACTIVE | ARCHIVED
- cached_count bigint NULL
- cached_count_version bigint NULL
- count_evaluated_at timestamptz NULL
- created_by_user_id
- version
- created_at
- updated_at

Unique active normalized name per company.

### contact_channel_preferences

Current projection for fast eligibility.

Columns:

- id
- company_id
- identity_id
- channel varchar(24)
  - EMAIL
  - SMS
  - WHATSAPP
- purpose varchar(32)
  - MARKETING in Round 1
- state varchar(24)
  - UNKNOWN
  - OPTED_IN
  - OPTED_OUT
- source varchar(48)
- source_ref varchar(160) NULL
- effective_at timestamptz NOT NULL
- updated_by_user_id NULL
- version bigint
- created_at
- updated_at

Unique `(company_id, identity_id, channel, purpose)`.

### contact_consent_events

Append-only.

Columns:

- id
- company_id
- identity_id
- channel
- purpose
- previous_state
- new_state
- source
- source_ref
- actor_user_id NULL
- reason_code varchar(80) NULL
- evidence_json jsonb NULL, bounded
- occurred_at
- recorded_at
- idempotency_key varchar(160) NULL

Unique idempotency key within company when present.

Never update or delete individual events through public CRUD APIs.

### contact_suppressions

Current suppression projection.

Columns:

- id
- company_id
- identity_id
- channel
- reason varchar(40)
- source varchar(48)
- source_ref varchar(160) NULL
- active boolean NOT NULL
- suppressed_at
- released_at NULL
- version
- created_at
- updated_at

Unique one current row per `company_id + identity_id + channel + reason + source_ref` as needed by implementation.

Hot lookup index:

- `(company_id, identity_id, channel, active)`

### contact_suppression_events

Append-only audit for suppression create/release/reason change.

Same idempotency requirements as consent events.

### contact_imports

Control row for an import lifecycle.

Columns:

- id
- company_id
- requested_by_user_id
- async_job_id
- source_object_key or storage reference
- original_file_name
- file_size_bytes
- checksum_sha256
- status
  - UPLOADED
  - PREVIEW_READY
  - QUEUED
  - RUNNING
  - COMPLETED
  - COMPLETED_WITH_ERRORS
  - FAILED
  - CANCELLED
  - EXPIRED
- duplicate_mode
- default_country_code NULL
- target_list_id NULL
- mapping_json bounded
- header_json bounded
- total_rows
- processed_rows
- created_count
- updated_count
- skipped_count
- error_count
- checkpoint_row_number
- idempotency_key
- error_artifact_id NULL
- started_at
- completed_at
- expires_at
- created_at
- updated_at

Unique `(company_id, requested_by_user_id, idempotency_key)`.

### contact_import_row_errors

Use only for bounded error detail needed to build reports.

Columns:

- id
- company_id
- import_id
- row_number
- error_code
- field_key NULL
- safe_value_preview NULL
- created_at

Do not store entire invalid rows indefinitely.

Index `(company_id, import_id, row_number, id)`.

### contact_exports

Columns:

- id
- company_id
- requested_by_user_id
- async_job_id
- status
- selection_type
- selection_ref_id NULL
- selection_json bounded
- field_selection_json bounded
- object_key NULL
- file_size_bytes
- checksum_sha256 NULL
- row_count
- expires_at
- created_at
- completed_at
- updated_at

### contact_bulk_jobs

Columns:

- id
- company_id
- requested_by_user_id
- async_job_id
- operation_type
- selection_type
- selection_ref_id NULL
- selection_json bounded
- payload_json bounded
- cursor_contact_id NULL
- status
- affected_count
- skipped_count
- error_count
- idempotency_key
- created_at
- updated_at

### contact_audience_snapshots

Reusable immutable selection result.

Columns:

- id
- company_id
- source_type
  - LIST
  - SEGMENT
  - CONTACT_IDS
  - FILTER
- source_id NULL
- source_version NULL
- channel
- purpose
- status
  - PREPARING
  - READY
  - FAILED
  - EXPIRED
- total_matched
- eligible_count
- excluded_count
- exclusion_counts_json bounded
- prepared_by_user_id NULL
- prepared_at NULL
- expires_at NULL
- idempotency_key
- created_at
- updated_at

Unique caller idempotency key within company.

### contact_audience_snapshot_members

Columns:

- id
- company_id
- snapshot_id
- contact_id
- identity_id
- normalized_destination varchar(320)
- status
  - ELIGIBLE
  - EXCLUDED
- exclusion_reason varchar(48) NULL
- ordinal bigint or contact-key order if needed
- created_at

Unique:

- `(snapshot_id, contact_id, identity_id)`
- and for eligible rows `(snapshot_id, normalized_destination)` to guarantee destination dedupe.

Indexes:

- `(company_id, snapshot_id, status, id)`
- `(company_id, contact_id, snapshot_id)`

### contact_outbox

Compact integration event outbox.

Columns:

- id
- company_id
- aggregate_type
- aggregate_id
- event_type
- event_version
- payload_json bounded
- status
- attempt_count
- available_at
- claimed_at
- published_at
- last_error
- idempotency_key
- created_at
- updated_at

## 3. Cross-worker seams

B2 Round 1 is allowed to read B1-owned tables via B2-owned JDBC projections documented here.

B2 must not import B1-only repositories/services/classes.

B2 read seam includes:

### contact_records

- id
- company_id
- status
- owner_user_id
- country_code
- timezone
- language_code
- source
- custom_fields
- last_activity_at
- version
- created_at
- updated_at

### contact_identities

- id
- company_id
- contact_id
- identity_type
- normalized_value
- primary_identity
- validation_state
- status
- created_at
- updated_at

### contact_tags / memberships

- id/company/name/normalized_name/status
- company/contact/tag join

### contact_lists / memberships

- id/company/status/version
- company/list/contact join

### custom field definitions

- id/company/field_key/field_type/options/status

B2 tests mock this seam or create SQL fixture tables matching the frozen columns.

## 4. L1 V96 cross-module responsibilities

Only after B1+B2 integration, L1 may add:

- cross-worker FKs that would break isolated worker migrations;
- missing partial unique/check constraints;
- Broadcast audience snapshot reference columns/adapters if integrated in Round 1;
- Inbox contact-resolution indexes/adapters;
- company lifecycle purge hooks requiring shared code;
- entitlement/catalog glue requiring a hot shared file.

## 5. Data size bounds

Default implementation caps must be configurable but cannot be unbounded.

Recommended Round-1 hard safety ceilings:

- identities/contact: 20 total
- tags/contact: 100
- lists/contact: 200
- custom field definitions/company: entitlement + hard safety ceiling 250
- custom_fields JSON serialized bytes/contact: 64 KiB
- notes: 16 KiB
- segment clauses: 50
- segment nesting depth: 5
- import header columns: 200
- import row CSV bytes: 1 MiB
- import error rows retained in DB: bounded; complete error artifact streamed separately
- explicit contact IDs in one synchronous request: 500

Product entitlements may be lower.
