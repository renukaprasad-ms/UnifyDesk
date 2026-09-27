# Managed SMS Data Model

Status: FROZEN for Round 1

## B1-owned control-plane tables

### managed_sms_tenants
One row per company.
- id bigint PK
- company_id bigint NOT NULL UNIQUE
- broadcast_connection_id bigint NOT NULL UNIQUE
- status varchar(32) NOT NULL
- provider_account_ref varchar(255) NULL
- provider_client_ref varchar(255) NULL
- provider_mode varchar(32) NOT NULL
- sending_enabled boolean NOT NULL
- suspended_reason varchar(255) NULL
- last_provider_sync_at timestamptz NULL
- created_at/updated_at timestamptz NOT NULL

Constraints:
- company ownership is immutable.
- customer-facing APIs never return provider account/client refs.
- managed connection must be MSG91_SMS + MESSAGING_MANAGED.

### managed_sms_dlt_profiles
- id PK
- company_id NOT NULL
- tenant_id NOT NULL
- legal_name varchar(255)
- dlt_platform varchar(80)
- pe_id varchar(160) NOT NULL
- status varchar(40) NOT NULL
- tm_chain_status varchar(40) NOT NULL
- tm_chain_reference varchar(255) NULL
- evidence_json text NULL
- ruleset_version varchar(80) NOT NULL
- verified_at / checked_at / stale_after timestamptz
- archived_at timestamptz NULL
- created_at/updated_at

Unique live PE claim: company_id + pe_id where archived_at is null.

### managed_sms_headers
- id PK
- company_id NOT NULL
- dlt_profile_id NOT NULL
- header varchar(32) NOT NULL
- message_category varchar(32) NOT NULL
- status varchar(40) NOT NULL
- provider_mapping_status varchar(40) NOT NULL
- approved_at / checked_at / stale_after
- rejection_reason varchar(255)
- archived_at
- created_at/updated_at

Unique live: company_id + upper(header).

### managed_sms_templates
Logical customer template.
- id PK
- company_id NOT NULL
- name varchar(160) NOT NULL
- purpose/category varchar(32) NOT NULL
- status varchar(32) NOT NULL
- created_by_user_id NOT NULL
- archived_at
- created_at/updated_at

### managed_sms_template_versions
Immutable approved version.
- id PK
- company_id NOT NULL
- template_id NOT NULL
- version_no bigint NOT NULL
- dlt_template_id varchar(255) NOT NULL
- body_text text NOT NULL
- body_fingerprint varchar(128) NOT NULL
- variables_schema_json text NOT NULL
- cta_policy_json text NOT NULL
- consent_policy_json text NULL
- opt_out_policy_json text NULL
- ruleset_version varchar(80) NOT NULL
- dlt_status varchar(40) NOT NULL
- status varchar(40) NOT NULL
- approved_at / checked_at / stale_after
- created_at

Unique: template_id + version_no.
No UPDATE of body/fingerprint/schema after ACTIVE; replacement creates a new version.

### managed_sms_template_headers
Many-to-many exact eligibility.
- template_version_id
- header_id
- company_id
Composite PK(template_version_id, header_id).

### managed_sms_provider_bindings
- id PK
- company_id NOT NULL
- tenant_id NOT NULL
- template_version_id NOT NULL
- header_id NOT NULL
- provider varchar(40) = MSG91_SMS
- provider_template_ref varchar(255) NULL
- provider_flow_ref varchar(255) NULL
- provider_mapping_ref varchar(255) NULL
- status varchar(40) NOT NULL
- last_sync_at
- rejection_reason
- version bigint
- created_at/updated_at

Unique live binding per template_version + header + provider.

### managed_sms_sender_bindings
- sender_id bigint PK/FK broadcast_senders
- company_id NOT NULL
- tenant_id NOT NULL
- header_id NOT NULL
- created_at

Unique sender_id. Header must belong to same company.

### managed_sms_provisioning_operations
Idempotent external-work ledger.
- id PK
- company_id
- tenant_id
- resource_type
- resource_id
- operation_type
- idempotency_key UNIQUE
- status
- attempt_count
- next_attempt_at
- lease_until
- last_error_code/message
- created_at/updated_at

## B2-owned runtime tables

### managed_sms_run_bindings
Immutable launch snapshot.
- run_id bigint PK
- company_id NOT NULL
- tenant_id NOT NULL
- dlt_profile_id NOT NULL
- pe_id_snapshot
- header_id
- header_snapshot
- template_id
- template_version_id
- dlt_template_id_snapshot
- approved_body_snapshot
- body_fingerprint_snapshot
- variables_schema_snapshot
- cta_policy_snapshot
- provider_binding_id
- provider_client_ref_snapshot
- provider_template_ref_snapshot
- provider_flow_ref_snapshot
- ruleset_version_snapshot
- created_at

### managed_sms_correlations
- correlation_token varchar(52) PK
- company_id
- run_id
- recipient_id
- attempt_no
- provider_request_id NULL
- created_at
- expires_at

Token is opaque alphanumeric and contains no PII.

### managed_sms_usage_reservations
- id PK
- company_id
- run_id
- recipient_id
- attempt_no
- estimated_segments
- state RESERVED|COMMITTED|RELEASED|HELD_UNKNOWN
- idempotency_key UNIQUE
- provider_credit_snapshot numeric NULL
- created_at/updated_at

### managed_sms_provider_events
Immutable inbox.
- id PK
- provider
- provider_account_key
- provider_event_key
- raw_hash
- event_type
- correlation_token
- provider_request_id
- payload_json
- trust_status
- processing_status
- received_at
- processed_at
Unique(provider, provider_account_key, provider_event_key).

If provider gives no stable event ID, provider_event_key is a deterministic hash of canonical identity fields.

### managed_sms_recipient_delivery
- recipient_id PK
- company_id
- accepted_at
- delivered_at
- failed_at
- rejected_at
- last_provider_status
- last_failure_code
- last_failure_reason
- last_event_id
- updated_at

### managed_sms_provider_snapshots
- id PK
- provider_account_key
- provider_client_ref NULL
- balance/credit fields nullable
- rate fields nullable
- sending_enabled
- observed_at
- valid_until
Unique provider_account_key + observed_at.

## Lead-owned integration constraints

After B1+B2 merge, Lead adds cross-module FKs/checks that cannot exist on isolated worker branches:
- run binding -> B1 tenant/profile/header/template version/provider binding;
- sender binding -> broadcast sender;
- B2 company/resource ownership consistency checks;
- unique one managed connection per company/provider/mode if not expressible in existing schema.

## Existing tables touched

broadcast_sender_connections:
- reuse, no new customer credentials for managed SMS.

broadcast_senders:
- reuse Header as fromAddress.

broadcast_messages:
- B2 adds nullable managed_sms_template_id only if required by implementation contract.

broadcast_runs:
- no DLT columns; managed snapshot belongs in managed_sms_run_bindings.

usage_events:
- B2 may record final billing through existing UsageBillingService after managed reservation commit.

## State vocabulary

Customer projection:
ACTION_REQUIRED
WAITING_DLT_APPROVAL
WAITING_UNIFYDESK_REVIEW
PROVIDER_SYNCING
READY
REJECTED
SUSPENDED

Internal resources may additionally use:
DRAFT, PENDING_VERIFICATION, ACTIVE, STALE, SUPERSEDED, ARCHIVED, ERROR, RETRY.
