# Managed Email Data Model

Status: Frozen for Round 1  
Baseline: dev @ 8178d6831140f7bd8cd67d33e66e229c10c5f0b2

This schema extends the existing Broadcast model. It does not duplicate sender/run/recipient concepts.

## 1. Reused tables

### broadcast_sender_connections

Managed Email uses one row per company:

- provider = AMAZON_SES
- management_mode = MESSAGING_MANAGED
- auth_mode = platform-managed sentinel compatible with the existing enum/implementation
- encrypted_credentials = NULL
- role_arn = NULL
- external_id must not expose the SES tenant name to customer-facing DTOs
- region = configured Managed Email SES region
- status = ACTIVE / ERROR / DISABLED
- sending_enabled reflects platform/managed eligibility, not customer AWS credentials

Constraint to add:
A company may have at most one non-disabled MESSAGING_MANAGED AMAZON_SES connection.

Use a partial unique index rather than changing the existing CUSTOMER_MANAGED uniqueness behavior.

### broadcast_senders

Remains the customer-visible From-address object.

Managed sender invariants:

- company_id matches managed connection company;
- provider = AMAZON_SES;
- connection_id references the company's MESSAGING_MANAGED connection;
- from_address domain exactly equals the linked ACTIVE managed domain;
- status uses existing ACTIVE/DISABLED;
- verified_identity contains the normalized managed domain, not arbitrary customer input.

Existing unique (company_id, provider, from_address) remains useful.

### channel_connections / sender_identities

Continue to represent the generic Connections UI boundary.

Managed Email creates one channel connection corresponding to the managed BroadcastSenderConnection and one SenderIdentity per BroadcastSender.

Do not create a new frontend-only connection model.

## 2. managed_email_tenants

Purpose:
Persistent company -> SES tenant/configuration-set control-plane mapping and policy status.

Columns:

- id bigint PK
- company_id bigint NOT NULL FK companies(id)
- broadcast_connection_id bigint NULL FK broadcast_sender_connections(id)
- region varchar(64) NOT NULL
- provider_tenant_name varchar(128) NOT NULL
- provider_configuration_set_name varchar(128) NOT NULL
- status varchar(32) NOT NULL
- sending_status varchar(32) NOT NULL
- reputation_status varchar(32) NULL
- suspension_reason varchar(128) NULL
- ramp_stage varchar(32) NOT NULL
- last_provider_sync_at timestamptz NULL
- suspended_at timestamptz NULL
- created_at timestamptz NOT NULL
- updated_at timestamptz NOT NULL
- version bigint NOT NULL DEFAULT 0

States:
- PROVISIONING
- ACTIVE
- SUSPENDED
- ERROR
- DELETING
- DELETED

sending_status is separate operational truth, for example:
- ENABLED
- DISABLED
- PROVIDER_DISABLED

ramp_stage:
- NEW
- RAMPING
- ESTABLISHED
- RESTRICTED

Constraints/indexes:

- UNIQUE(company_id)
- UNIQUE(provider_tenant_name)
- UNIQUE(provider_configuration_set_name)
- UNIQUE(broadcast_connection_id) WHERE broadcast_connection_id IS NOT NULL
- index(status, region)
- FK company ON DELETE RESTRICT or controlled lifecycle; do not cascade provider-control rows before cleanup

Provider names:
Generate from an opaque stable internal token/ID. Do not derive them from company display name.

## 3. managed_email_domains

Purpose:
Own-domain and platform-hosted domain lifecycle.

Columns:

- id bigint PK
- company_id bigint NOT NULL FK companies(id)
- tenant_id bigint NOT NULL FK managed_email_tenants(id)
- domain_type varchar(32) NOT NULL
- normalized_domain varchar(253) NOT NULL
- display_domain varchar(253) NOT NULL
- hosted_slug varchar(63) NULL
- hosted_reservation_token varchar(32) NULL
- status varchar(40) NOT NULL
- provider_identity_name varchar(320) NULL
- verification_status varchar(32) NOT NULL
- dkim_status varchar(32) NOT NULL
- dmarc_status varchar(32) NOT NULL
- dmarc_policy varchar(32) NULL
- last_dns_check_at timestamptz NULL
- last_provider_sync_at timestamptz NULL
- verification_failed_at timestamptz NULL
- activated_at timestamptz NULL
- suspended_at timestamptz NULL
- released_at timestamptz NULL
- created_by_user_id bigint NOT NULL FK users(id)
- created_at timestamptz NOT NULL
- updated_at timestamptz NOT NULL
- version bigint NOT NULL DEFAULT 0

domain_type:
- CUSTOMER_DOMAIN
- UNIFYDESK_HOSTED

status:
- PROVISIONING
- PENDING_DNS
- VERIFYING
- ACTIVE
- SUSPENDED_VERIFICATION
- SUSPENDED_POLICY
- ERROR
- DELETING
- DELETED
- RETIRED

verification_status:
- PENDING
- SUCCESS
- FAILED
- UNKNOWN

dkim_status:
- PENDING
- SUCCESS
- FAILED
- UNKNOWN

dmarc_status:
- UNKNOWN
- ABSENT
- PRESENT_MONITOR
- PRESENT_ENFORCING
- INVALID

Uniqueness:

Customer-owned domains:
Only one live claim across all companies.

Create a partial unique index on lower(normalized_domain) for CUSTOMER_DOMAIN where status not in ('DELETED','RETIRED').

A previously released customer domain may be claimed again only through a fresh verification flow after cleanup.

Hosted:
The exact normalized_domain must never be reused even after RETIRED. Use a permanent unique index for UNIFYDESK_HOSTED rows and retain tombstones.

Additional:
- index(company_id, status, updated_at desc)
- index(tenant_id, status)
- check hosted fields present only for UNIFYDESK_HOSTED where practical

## 4. managed_email_dns_records

Purpose:
Persist exact required provider DNS records and hosted records required for deterministic onboarding/recovery.

Columns:

- id bigint PK
- domain_id bigint NOT NULL FK managed_email_domains(id)
- company_id bigint NOT NULL FK companies(id)
- record_purpose varchar(32) NOT NULL
- record_type varchar(16) NOT NULL
- record_name varchar(512) NOT NULL
- record_value varchar(2048) NOT NULL
- priority integer NULL
- required boolean NOT NULL
- status varchar(32) NOT NULL
- source varchar(32) NOT NULL
- last_checked_at timestamptz NULL
- created_at timestamptz NOT NULL
- updated_at timestamptz NOT NULL

record_purpose:
- DKIM
- DMARC
- MAIL_FROM_MX (future)
- MAIL_FROM_SPF (future)
- OTHER_PROVIDER_REQUIRED

record_type:
- CNAME
- TXT
- MX

source:
- SES
- UNIFYDESK

status:
- PENDING
- PRESENT
- MISSING
- MISMATCH
- NOT_APPLICABLE

Constraints:
- UNIQUE(domain_id, record_purpose, record_type, record_name, record_value)
- index(domain_id, required, status)
- index(company_id, domain_id)

MVP customer-domain required rows are Easy DKIM CNAME records.

Do not persist "SPF on visible domain" as a required record for MVP.

## 5. managed_email_sender_bindings

Purpose:
Relational proof that an existing BroadcastSender is a Managed Email sender belonging to a specific Managed Email domain.

Columns:

- sender_id bigint PK FK broadcast_senders(id)
- company_id bigint NOT NULL FK companies(id)
- domain_id bigint NOT NULL FK managed_email_domains(id)
- tenant_id bigint NOT NULL FK managed_email_tenants(id)
- created_at timestamptz NOT NULL
- updated_at timestamptz NOT NULL

Constraints:
- PK(sender_id)
- FK domain/tenant
- index(company_id, domain_id)
- index(tenant_id)

Application invariant:
sender.company_id = binding.company_id = domain.company_id = tenant.company_id.

Tests must prove cross-company substitutions fail.

No independent sender lifecycle enum is required here in Round 1 because BroadcastSender.status + domain/tenant state is authoritative.

## 6. managed_email_provisioning_operations

Purpose:
Durable, idempotent control-plane work queue.

Columns:

- id bigint PK
- company_id bigint NOT NULL
- resource_type varchar(32) NOT NULL
- resource_id bigint NOT NULL
- operation_type varchar(48) NOT NULL
- idempotency_key varchar(255) NOT NULL
- status varchar(32) NOT NULL
- checkpoint varchar(64) NULL
- attempt_count integer NOT NULL DEFAULT 0
- next_attempt_at timestamptz NULL
- claimed_at timestamptz NULL
- last_error_code varchar(128) NULL
- last_error_message varchar(1000) NULL
- created_at timestamptz NOT NULL
- updated_at timestamptz NOT NULL
- completed_at timestamptz NULL

resource_type:
- TENANT
- DOMAIN
- CONNECTION

operation_type examples:
- ENSURE_TENANT
- ENSURE_CONFIGURATION_SET
- ENSURE_DOMAIN_IDENTITY
- ENSURE_HOSTED_DNS
- REFRESH_DOMAIN_VERIFICATION
- DELETE_DOMAIN_IDENTITY
- DELETE_TENANT_RESOURCES

status:
- PENDING
- PROCESSING
- RETRY
- SUCCEEDED
- FAILED
- CANCELLED

Constraints:
- UNIQUE(idempotency_key)
- index(status, next_attempt_at, id)
- index(company_id, resource_type, resource_id)

Claim with FOR UPDATE SKIP LOCKED or equivalent bounded lease pattern.

Do not store AWS credentials in this table.

## 7. managed_email_usage_periods

Owner: B2

Purpose:
Materialized company/month quota accounting.

Columns:

- company_id bigint NOT NULL FK companies(id)
- period_start date NOT NULL
- accepted_count bigint NOT NULL DEFAULT 0
- reserved_count bigint NOT NULL DEFAULT 0
- adjusted_count bigint NOT NULL DEFAULT 0
- updated_at timestamptz NOT NULL
- version bigint NOT NULL DEFAULT 0

PK:
(company_id, period_start)

Billable total:
max(0, accepted_count + adjusted_count)

Quota allocation uses accepted_count + reserved_count + positive adjustments.

Period boundary:
UTC calendar month for Round 1 unless billing later supplies subscription-period-specific boundaries. This must be explicit in API/usage code and can be migrated later behind a period resolver.

## 8. managed_email_usage_events

Owner: B2

Purpose:
Append-only idempotent usage ledger.

Columns:

- id bigint PK
- company_id bigint NOT NULL
- recipient_id bigint NOT NULL FK broadcast_recipients(id)
- run_id bigint NOT NULL FK broadcast_runs(id)
- provider_message_id varchar(512) NULL
- event_type varchar(32) NOT NULL
- units bigint NOT NULL
- period_start date NOT NULL
- idempotency_key varchar(255) NOT NULL
- created_at timestamptz NOT NULL
- metadata_json text NULL

event_type:
- RESERVE
- COMMIT
- RELEASE
- ADJUSTMENT

Rules:
- reserve key uniquely identifies recipient send reservation;
- one COMMIT per reservation;
- one RELEASE per reservation;
- metadata_json is non-authoritative diagnostics only;
- core counters are relational.

Constraints:
- UNIQUE(idempotency_key)
- index(company_id, period_start, created_at)
- index(recipient_id, created_at)
- index(provider_message_id) WHERE provider_message_id IS NOT NULL

## 9. managed_email_provider_events

Owner: B2

Purpose:
Immutable normalized SES/SNS event ledger.

Columns:

- id bigint PK
- sns_message_id varchar(128) NOT NULL
- normalized_event_key varchar(255) NOT NULL
- company_id bigint NULL
- tenant_id bigint NULL
- domain_id bigint NULL
- sender_id bigint NULL
- run_id bigint NULL
- recipient_id bigint NULL
- provider_message_id varchar(512) NULL
- event_type varchar(40) NOT NULL
- event_timestamp timestamptz NOT NULL
- bounce_type varchar(32) NULL
- diagnostic_code varchar(128) NULL
- correlation_status varchar(32) NOT NULL
- payload_hash varchar(128) NOT NULL
- received_at timestamptz NOT NULL
- processed_at timestamptz NULL

correlation_status:
- MATCHED
- UNMATCHED
- REJECTED_SCOPE_MISMATCH
- DUPLICATE

Do not persist email bodies or arbitrary full MIME headers.

Constraints:
- UNIQUE(sns_message_id)
- UNIQUE(normalized_event_key)
- index(provider_message_id, event_type)
- index(company_id, event_timestamp desc)
- index(recipient_id, event_timestamp)

For malformed/unmatched events, company_id may remain NULL and no tenant data is mutated.

## 10. managed_email_recipient_delivery

Owner: B2

Purpose:
Latest delivery/engagement projection without expanding BroadcastRecipient into a non-mutually-exclusive state machine.

Columns:

- recipient_id bigint PK FK broadcast_recipients(id)
- company_id bigint NOT NULL
- provider_message_id varchar(512) NULL
- sent_event_at timestamptz NULL
- delivered_at timestamptz NULL
- complained_at timestamptz NULL
- rejected_at timestamptz NULL
- first_open_at timestamptz NULL
- last_open_at timestamptz NULL
- open_count bigint NOT NULL DEFAULT 0
- first_click_at timestamptz NULL
- last_click_at timestamptz NULL
- click_count bigint NOT NULL DEFAULT 0
- last_delivery_delay_at timestamptz NULL
- last_event_at timestamptz NOT NULL
- updated_at timestamptz NOT NULL
- version bigint NOT NULL DEFAULT 0

Constraints:
- index(company_id, updated_at desc)
- index(provider_message_id) WHERE provider_message_id IS NOT NULL

Updates are monotonic:
earliest first_* remains earliest, last_* moves forward, terminal negative feedback is never cleared by a later success event.

## 11. managed_email_reputation_snapshots

Owner: B2

Purpose:
Operational tenant/company health history and suspension decisions.

Columns:

- id bigint PK
- company_id bigint NOT NULL
- tenant_id bigint NOT NULL
- window_start timestamptz NOT NULL
- window_end timestamptz NOT NULL
- accepted_count bigint NOT NULL
- hard_bounce_count bigint NOT NULL
- complaint_count bigint NOT NULL
- hard_bounce_rate numeric(12,8) NOT NULL
- complaint_rate numeric(12,8) NOT NULL
- provider_reputation_status varchar(64) NULL
- decision varchar(32) NOT NULL
- created_at timestamptz NOT NULL

decision:
- HEALTHY
- WARNING
- THROTTLED
- SUSPENDED

Exact thresholds remain configuration, not rows/constants hard-coded into migrations.

## 12. Entitlement definitions

B1/control-plane entitlements:
- MANAGED_EMAIL_ENABLED
- MANAGED_EMAIL_DOMAINS
- MANAGED_EMAIL_SENDERS

B2/send-plane entitlement:
- MANAGED_EMAIL_MESSAGES_MONTHLY

Default values must fail closed for plans that have not explicitly enabled Managed Email.

Do not silently grant unlimited managed sending to existing plans.

Plan seeding/rollout values are a product/admin decision; schema only establishes definitions and safe defaults.

## 13. Locking / concurrency

Tenant:
- unique company constraint prevents duplicate tenant creation;
- optimistic version for normal updates;
- provisioning operation uniqueness for external convergence.

Domain claim:
- database unique indexes are final authority;
- do not rely on pre-check alone;
- retry/return existing resource on same-company idempotent conflict;
- reject other-company active claim.

Hosted reservation:
- unique permanent domain reservation;
- random suffix + unique constraint;
- collision retry.

Usage:
- lock company/month usage row or use atomic SQL update;
- reserve + limit check in one transaction;
- idempotency key prevents duplicate consumption.

Provider events:
- unique event keys dedupe;
- recipient projection update uses row lock/version;
- cross-company mismatch records rejected event and does not mutate target.

## 14. Deletion and retention

Historical Broadcast runs/recipients/senders used by audit/billing are not cascade-deleted merely because a domain is deprovisioned.

Control-plane deletion order:

1. make domain/senders non-sendable locally;
2. retire hosted DNS as appropriate;
3. remove SES tenant resource association;
4. remove SES identity where safe;
5. mark customer domain DELETED or hosted domain RETIRED;
6. retain hosted reservation tombstone.

Tenant deletion happens only when Managed Email is fully decommissioned for the company and no required historical/provider cleanup remains.

## 15. Migration ownership

Round 1 reserves:

B1:
V56__managed_email_control_plane.sql

B2:
V57__managed_email_delivery_usage.sql

No other Round-1 worker may create V56 or V57.

If dev receives another migration before merge and a Flyway collision appears, workers must not independently renumber. Architecture Lead resolves migration ordering during integration.


## 16. Parallel migration rule

B1 and B2 must be independently runnable from the Round-1 base.

Therefore V57 must not declare foreign keys to B1-owned managed_email_tenants, managed_email_domains, or managed_email_sender_bindings because those tables do not exist on the isolated B2 branch.

B2 stores the corresponding IDs as validated bigint correlation columns and uses its own runtime repository contract.

After B1 and B2 are integrated, the Architecture Lead owns V58__managed_email_cross_module_constraints.sql to add the cross-module foreign keys/indexes that are valid only when both schemas are present.

Workers must not create V58.
