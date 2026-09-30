# WhatsApp Data Model

Status: FROZEN FOR ROUND 1

## Existing canonical tables reused

### channel_connections
Used as the generic company/provider connection row.

For META_WHATSAPP:
- company_id = owning UnifyDesk company
- provider_type = META_WHATSAPP
- channel_type = WHATSAPP
- external_account_id = legacy/provider account ID chosen for generic compatibility
- status = generic PENDING/ACTIVE/ERROR/DISCONNECTED
- last_sync_at = last successful provider reconciliation

### sender_identities
One row per connected WhatsApp business phone.
- identity_type = PHONE_NUMBER
- external_id = Meta phone_number_id
- value = display phone number
- status = ACTIVE/ERROR/DISABLED

Hot-path invariant:
- one active Meta phone_number_id may belong to only one active UnifyDesk company.

### provider_credentials
Reuse OAUTH_TOKEN_SET and PROVIDER_SECRET:
- OAUTH_TOKEN_SET: encrypted access token + provider identifiers + graph version + token metadata
- PROVIDER_SECRET: encrypted six-digit registration/two-step-verification PIN
- expires_at: provider-reported token expiry if available

No plaintext token/PIN columns.

### inbox_conversations / inbox_messages / inbox_participants
Canonical user-visible conversation/message model.

WhatsApp:
- conversation external ID = `wa:<opaque-provider-user-id>`
- provider_type = META_WHATSAPP
- participant type = PHONE_NUMBER for current wa_id/phone payloads
- InboxMessage.external_message_id = Meta message ID
- provider_folder_key = `WHATSAPP`
- inbound read=false
- outbound InboxMessage.status remains SENT/FAILED at generic layer; detailed state lives in runtime table.

### media
Existing private media storage remains canonical.

## B1 migration V76 — control plane

### meta_whatsapp_connections

Purpose: detailed provider-specific state for one generic ChannelConnection.

Columns:
- connection_id BIGINT PRIMARY KEY
- company_id BIGINT NOT NULL
- account_model_version VARCHAR(24) NOT NULL DEFAULT 'LEGACY_WABA'
- legacy_waba_id VARCHAR(255)
- whatsapp_account_id VARCHAR(255)
- messaging_account_id VARCHAR(255)
- selected_phone_number_id VARCHAR(255) NOT NULL
- product_state VARCHAR(32) NOT NULL
- auth_state VARCHAR(32) NOT NULL
- registration_state VARCHAR(32) NOT NULL
- subscription_state VARCHAR(32) NOT NULL
- coexistence_state VARCHAR(32) NOT NULL DEFAULT 'DISABLED'
- coexistence_enabled BOOLEAN NOT NULL DEFAULT FALSE
- last_validated_at TIMESTAMPTZ
- next_reconcile_at TIMESTAMPTZ
- last_provider_http_status INTEGER
- last_provider_error_type VARCHAR(120)
- last_provider_error_code INTEGER
- last_provider_error_subcode INTEGER
- last_provider_fbtrace_id VARCHAR(255)
- last_safe_error_code VARCHAR(80)
- created_at TIMESTAMPTZ NOT NULL
- updated_at TIMESTAMPTZ NOT NULL

Constraints/indexes:
- unique(connection_id)
- unique partial/provider-safe index on selected_phone_number_id for non-disconnected rows if implementation supports state predicate safely
- index(company_id, product_state)
- index(selected_phone_number_id)
- index(next_reconcile_at) for reconcilable states

B1 must keep company_id duplicated intentionally for hot-path isolation checks and SQL projections.

### meta_whatsapp_provisioning_operations

Purpose: recoverable connect/reconnect steps.

Columns:
- id BIGINT PRIMARY KEY
- connection_id BIGINT NOT NULL
- company_id BIGINT NOT NULL
- operation_key VARCHAR(255) NOT NULL UNIQUE
- operation_type VARCHAR(40) NOT NULL
- state VARCHAR(24) NOT NULL
- step VARCHAR(40) NOT NULL
- attempt_count INTEGER NOT NULL DEFAULT 0
- next_attempt_at TIMESTAMPTZ
- claim_token VARCHAR(80)
- claim_expires_at TIMESTAMPTZ
- last_safe_error_code VARCHAR(80)
- created_at TIMESTAMPTZ NOT NULL
- updated_at TIMESTAMPTZ NOT NULL
- completed_at TIMESTAMPTZ

States:
PENDING, CLAIMED, RUNNING, SUCCEEDED, FAILED_RETRYABLE, FAILED_PERMANENT

Steps:
TOKEN_VALIDATED, ASSETS_VALIDATED, LOCAL_PERSISTED, PHONE_REGISTERED, APP_SUBSCRIBED, HEALTH_VALIDATED

Indexes:
- due-work partial index on next_attempt_at/id for retryable states
- connection_id
- company_id

## B2 migration V77 — runtime plane

V77 MUST migrate independently from the worker base. It therefore MUST NOT add foreign keys to V76-only tables. Cross-module FKs are L1 V78 integration work.

### meta_whatsapp_webhook_events

Purpose: signed, bounded durable ingress queue.

Columns:
- id BIGINT PRIMARY KEY
- event_key VARCHAR(255) NOT NULL UNIQUE
- provider_object_id VARCHAR(255)
- phone_number_id VARCHAR(255)
- provider_message_id VARCHAR(255)
- event_kind VARCHAR(32) NOT NULL
- provider_event_at TIMESTAMPTZ
- received_at TIMESTAMPTZ NOT NULL
- payload_json JSONB NOT NULL
- state VARCHAR(24) NOT NULL
- attempt_count INTEGER NOT NULL DEFAULT 0
- next_attempt_at TIMESTAMPTZ NOT NULL
- claim_token VARCHAR(80)
- claim_expires_at TIMESTAMPTZ
- last_error_code VARCHAR(80)
- processed_at TIMESTAMPTZ

States:
PENDING, CLAIMED, PROCESSED, IGNORED, FAILED_RETRYABLE, DEAD

Indexes:
- unique(event_key)
- due partial index(next_attempt_at,id)
- phone_number_id
- provider_message_id
- received_at

Retention:
- PROCESSED/IGNORED payload rows purge after configurable short retention (default 7 days)
- DEAD rows retain safe metadata longer (default 30 days)
- do not keep raw unsigned bodies

### meta_whatsapp_message_runtime

One runtime row per provider message known to UnifyDesk.

Columns:
- id BIGINT PRIMARY KEY
- company_id BIGINT NOT NULL
- connection_id BIGINT NOT NULL
- inbox_message_id BIGINT
- provider_message_id VARCHAR(255)
- client_request_id VARCHAR(120)
- direction VARCHAR(16) NOT NULL
- message_kind VARCHAR(24) NOT NULL
- detailed_status VARCHAR(24) NOT NULL
- status_rank INTEGER NOT NULL DEFAULT 0
- provider_status_at TIMESTAMPTZ
- last_provider_error_code INTEGER
- last_provider_error_subcode INTEGER
- last_provider_fbtrace_id VARCHAR(255)
- created_at TIMESTAMPTZ NOT NULL
- updated_at TIMESTAMPTZ NOT NULL

Constraints:
- unique(connection_id, provider_message_id) where provider_message_id is not null
- unique(company_id, connection_id, client_request_id) where client_request_id is not null

Indexes:
- inbox_message_id
- provider_message_id
- (company_id, connection_id, created_at desc)

Detailed states:
PENDING, ACCEPTED, SENT, DELIVERED, READ, FAILED, UNKNOWN

### meta_whatsapp_template_cache

Purpose: bounded local projection of provider templates.

Columns:
- id BIGINT PRIMARY KEY
- company_id BIGINT NOT NULL
- connection_id BIGINT NOT NULL
- provider_template_id VARCHAR(255)
- name VARCHAR(512) NOT NULL
- language VARCHAR(40) NOT NULL
- category VARCHAR(40)
- status VARCHAR(40) NOT NULL
- components_json JSONB NOT NULL
- synced_at TIMESTAMPTZ NOT NULL
- updated_at TIMESTAMPTZ NOT NULL

Constraints:
- unique(connection_id, name, language)

Indexes:
- (company_id,connection_id,status)
- synced_at

### meta_whatsapp_customer_sessions

Purpose: backend-authoritative free-form eligibility projection.

Columns:
- id BIGINT PRIMARY KEY
- company_id BIGINT NOT NULL
- connection_id BIGINT NOT NULL
- customer_key VARCHAR(255) NOT NULL
- last_customer_message_at TIMESTAMPTZ NOT NULL
- updated_at TIMESTAMPTZ NOT NULL

Constraint:
- unique(connection_id, customer_key)

Indexes:
- (connection_id, customer_key)
- (company_id, updated_at)

## Existing Inbox schema compatibility notes

Current Inbox is email-oriented in two important places:
1. InboxController permissions are EMAIL_READ/EMAIL_MODIFY.
2. InboxQueryService requires EmailFolder and folder-key existence.

B2 owns the provider-aware adaptation. Frozen behavior:
- EMAIL connections retain current behavior.
- META_WHATSAPP uses whatsapp.read/whatsapp.modify.
- WhatsApp list/detail uses providerFolderKey WHATSAPP internally.
- generic response DTOs remain backward compatible.
- no cross-company conversation/message is visible by ID.

## Media linkage

Current InboxMessageAttachment has no Media FK. Round 1 may:
- add nullable media_id in B2 V77, or
- create B2-owned mapping table `meta_whatsapp_message_media(message_id, media_id, provider_media_id,...)`.

Preferred: B2-owned mapping table to avoid changing generic attachment semantics prematurely.

Suggested:
- message_id BIGINT NOT NULL
- media_id BIGINT NOT NULL
- provider_media_id VARCHAR(255) NOT NULL
- created_at TIMESTAMPTZ NOT NULL
- unique(message_id,provider_media_id)

Any FK to existing Inbox/Media tables may be placed directly in V77 because those tables exist in the frozen base.

## L1 V78 after integration

L1 may add:
- FK meta_whatsapp_connections.connection_id -> channel_connections(id)
- FK provisioning connection_id -> channel_connections
- FK runtime connection_id -> channel_connections
- FK runtime inbox_message_id -> inbox_messages
- any cross-worker constraint/index that cannot exist independently.

If V76/V77 collide with moving dev migration numbers, only L1 renumbers at integration.

## Complexity targets

- connection by phone_number_id: O(log n) indexed
- webhook dedupe event_key: O(log n) unique index
- provider message status lookup: O(log n)
- template list per connection/status: O(log n + k)
- customer-service session lookup: O(log n)
- no webhook hot-path full scans
