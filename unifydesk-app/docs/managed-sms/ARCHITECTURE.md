# UnifyDesk Managed SMS Architecture

Status: Round-0 frozen architecture  
Provider: MSG91 SMS  
Jurisdiction: India-first

## 1. Scope

Managed SMS adds:
- company managed-SMS tenant state;
- DLT/PE compliance profile and evidence;
- Header lifecycle;
- immutable approved Content Template versions;
- PE/Header/Template/provider bindings;
- MSG91 child/client provisioning/mapping;
- managed Broadcast sender lifecycle;
- exact-template launch/runtime guard;
- global/company/campaign limits and provider wallet protection;
- DLR ingestion/reconciliation;
- managed usage reservation/metering;
- suspension, audit and abuse controls.

It does not replace any Broadcast execution primitive.

## 2. Provider/account topology

Preferred topology:

UnifyDesk MSG91 master/subaccount
  |
  +-- child/client A  <-> Company A / PE A
  |      +-- Header A1
  |      +-- DLT Template A1/v1 -> provider template/Flow
  |
  +-- child/client B  <-> Company B / PE B
         +-- Header B1
         +-- DLT Template B1/v3 -> provider template/Flow

UnifyDesk owns provider credentials and billing. Customers never log into these child accounts through UnifyDesk and never receive their AuthKeys.

If MSG91 contract uses a shared master account rather than child accounts, the same local one-company ownership constraints remain mandatory and the arrangement requires provider confirmation before GA.

## 3. Control-plane lifecycle

Tenant:
NOT_STARTED -> ONBOARDING -> PROVIDER_SYNCING -> READY
ONBOARDING/PROVIDER_SYNCING/READY -> SUSPENDED
recoverable ERROR may return to PROVIDER_SYNCING
retirement -> ARCHIVED

DLT profile:
DRAFT
-> ACTION_REQUIRED
-> WAITING_DLT_APPROVAL / WAITING_UNIFYDESK_REVIEW
-> ACTIVE
ACTIVE -> STALE / SUSPENDED / REJECTED

Header:
DRAFT -> PENDING_VERIFICATION -> ACTIVE
ACTIVE -> SUSPENDED -> ACTIVE
any non-historical resource -> ARCHIVED

Template version:
DRAFT -> PENDING_DLT_APPROVAL -> APPROVED_DLT
-> PROVIDER_SYNCING -> ACTIVE
provider rejection -> REJECTED
ACTIVE -> SUSPENDED / SUPERSEDED
historical versions never mutate content.

Provider operation:
PENDING -> RUNNING -> SUCCEEDED
RUNNING -> RETRY
PENDING/RUNNING/RETRY -> ACTION_REQUIRED / FAILED
External network calls never run inside a long database transaction.

## 4. Customer onboarding

### Stage A — introduction
Explain:
- customer keeps ownership of business/DLT identity;
- UnifyDesk operates delivery infrastructure;
- no MSG91 account is required from customer.

### Stage B — business/DLT
Collect:
- registered legal/business name;
- DLT portal/access provider used;
- PE/Entity ID;
- evidence/reference needed for manual validation;
- PE–TM chain state.

Do not collect customer DLT passwords.

### Stage C — Header
Customer enters an already approved Header and category/use. B1 verifies format/ownership evidence and, where provider API allows, maps PE+Header into the company's MSG91 child account.

### Stage D — Content Template
Customer enters:
- DLT Template ID;
- regulatory category;
- exact DLT-approved fixed/variable body;
- variable definitions/tag types;
- Header(s) approved for the template;
- CTA declarations;
- consent/opt-out metadata where applicable.

UnifyDesk canonicalizes and fingerprints the exact version.

### Stage E — provider sync
B1 creates/maps provider SMS template/Flow. UI shows PROVIDER_SYNCING until provider status is known.

### Stage F — ready
A managed BroadcastSender becomes selectable only when tenant/profile/header and at least one compatible template binding are ACTIVE.

## 5. Managed connection/sender seam

Managed connection:
BroadcastSenderConnection
- provider = MSG91_SMS
- managementMode = MESSAGING_MANAGED
- companyId = tenant company
- encryptedCredentials = null
- provider_config_json does not contain master/child secrets
- status/sendingEnabled mirror managed tenant eligibility

Managed sender:
BroadcastSender
- provider = MSG91_SMS
- companyId = same company
- connectionId = managed connection
- fromAddress = base approved Header
- verifiedIdentity = non-secret Header identity
- status reflects managed header eligibility

managed_sms_sender_bindings creates the authoritative link to managed_sms_headers.

CUSTOMER_MANAGED MSG91 continues using existing Msg91Gateway config/authkey/Flow path unchanged.

## 6. Managed Broadcast compose

For MESSAGING_MANAGED SMS:
- scratch body is prohibited;
- request identifies one managedSmsTemplateId and variable map;
- server loads exact ACTIVE version for the selected Header;
- server validates variables against declared schema;
- server renders a preview/final body from the immutable approved pattern;
- no unknown placeholders;
- no extra prefix/suffix/footer;
- CTA variables satisfy the approved CTA type/whitelist policy.

The existing generic Email template feature is not reused as the compliance system. Managed SMS has its own DLT-aware template model because DLT IDs/Header mappings/category/variable tags are regulatory facts.

## 7. Launch eligibility

At create/update:
company owns group
AND company owns active managed sender
AND sender provider/channel matches SMS
AND company owns active managed template
AND selected Header is allowed for template.

At launch/schedule execution, revalidate:
feature/entitlement enabled
AND global managed-SMS switch enabled
AND tenant READY
AND DLT profile ACTIVE and freshness valid
AND PE–TM chain ACTIVE/fresh
AND Header ACTIVE
AND template version ACTIVE
AND template/Header mapping ACTIVE
AND provider binding ACTIVE
AND managed connection/sender ACTIVE
AND company not suspended
AND campaign size within policy.

If any gate fails, no recipient provider call occurs.

## 8. Run snapshot

Launch creates one managed_sms_run_bindings row:
- run_id unique;
- company_id;
- managed_sms_tenant_id;
- dlt_profile_id;
- pe_id_snapshot;
- header_id + header_snapshot;
- template_id/version;
- dlt_template_id_snapshot;
- approved_body_snapshot/fingerprint;
- variables_schema_snapshot;
- provider_binding_id;
- provider_client_ref snapshot;
- provider_template/flow ref snapshot;
- compliance_ruleset_snapshot;
- created_at.

Delivery uses this snapshot, never “latest current template”.

## 9. Runtime delivery path

BroadcastBatchProcessor
 -> BroadcastDeliveryService
 -> managed provider dispatch by provider
 -> ManagedSmsDeliveryGuard (B2)
 -> ManagedSmsSuppression/recipient-policy gate
 -> ManagedSmsQuotaReservation
 -> global + provider-account + company + campaign Redis limiters
 -> render exact snapshotted template
 -> ManagedMsg91SmsTransport/Gateway
 -> MSG91
 -> provider result
 -> BroadcastDeliveryStateService
 -> ManagedSmsUsage commit/release/hold

The existing CUSTOMER_MANAGED branch remains as-is.

## 10. Provider request

Managed provider request is built server-side from:
- provider child/account credential selected by tenant;
- snapshotted Flow/provider-template binding;
- approved Header;
- normalized recipient;
- only declared variable values;
- opaque CRQID correlation token;
- PE_ID / DLT Template fields when required by the selected MSG91 API/binding mode.

Client-supplied provider IDs are ignored at send time.

## 11. Provider acceptance semantics

Definite accepted:
- mark BroadcastRecipient ACCEPTED_BY_PROVIDER;
- store provider request/message ID when available;
- commit usage reservation exactly once;
- DLR later refines delivery projection.

Definite pre-acceptance permanent failure:
- mark FAILED;
- release reservation.

Definite transient pre-acceptance failure:
- RETRY under existing retry policy;
- release or retain reservation according to attempt-scoped design, never double-reserve.

Ambiguous after network submission may have occurred:
- UNKNOWN;
- retain reconciliation reservation;
- do not blindly resend.

## 12. DLR architecture

MSG91 Webhook (New)
 -> public ManagedSmsWebhookController
 -> endpoint/account secret verification + schema limits
 -> raw-body hash
 -> managed_sms_provider_events inbox insert with unique idempotency key
 -> 2xx quickly
 -> asynchronous reconciliation worker
 -> correlate using opaque CRQID + provider request ID
 -> assert event account/company/binding consistency
 -> project managed_sms_recipient_delivery
 -> update BroadcastRecipient final delivery fields/state only via B2 state service
 -> emit metrics / safety signals.

Webhook retries and duplicates are expected.

If MSG91 provides a cryptographic signature during production onboarding, B2 must verify it over the exact raw bytes before parsing. If it does not, GA requires the strongest supported shared-secret-header/dedicated-endpoint model plus provider confirmation.

## 13. Delivery projection

Provider acceptance is not “delivered”.

Projection states:
- ACCEPTED
- DELIVERED
- FAILED
- REJECTED
- UNKNOWN

Events may arrive late/out of order. Terminal precedence is deterministic and event history is immutable. Duplicate event cannot increment stats or usage twice.

## 14. Metering and billing

### Reservation dimensions
Before send reserve:
- monthly/plan managed SMS segment allowance;
- company safety allowance;
- shared provider wallet safety budget.

### Billable boundary
Default: provider acceptance, measured in SMS segments/credits as known at submission/provider report.

DELIVERED does not create a second charge.
FAILED/REJECTED after provider acceptance does not automatically reverse charge.
A confirmed provider credit/refund creates an adjustment event.

### Idempotency
usage key:
managed-sms:recipient:<recipientId>:attempt:<attemptNo>:accepted

Unique DB constraint makes commit exactly once.

## 15. Distributed limits

Acquire all required scopes before provider call:
- platform kill switch;
- provider account/client limiter;
- global requests/second;
- company requests/second;
- campaign requests/second;
- new-tenant hourly/daily cap;
- optional template/category caps.

Redis Lua/token-bucket keys include provider account + company/run where appropriate. If Redis/policy backend is unavailable, managed sending fails closed or releases for retry; it never bypasses limits.

## 16. Provider balance protection

Maintain a periodically refreshed provider-balance snapshot if supported. Sending stops before a configured reserve floor.

Even without a reliable balance API:
- platform hard daily cap;
- per-company daily/monthly cap;
- conservative maximum segment estimate before send;
- provider “insufficient credit” response trips circuit breaker;
- ops alert and global managed-SMS pause.

## 17. Recipient policy

Application-level suppression/opt-out remains required even though DLT/TSP scrubbing also enforces preferences.

For Promotional:
- only approved promotional template;
- approved opt-out mechanism remains part of template;
- internal opted-out recipient is skipped;
- provider/DLT DND/preference rejection is final non-retryable recipient outcome.

For Service/Transactional:
- category semantics and consent basis are those stored on approved template/ruleset;
- do not let UI relabel promotional copy as service.

## 18. Staleness and refresh

Compliance truth can change.

Refresh jobs:
- provider template/Flow status;
- child/account health;
- known Header/template binding status where APIs support it;
- PE/TM chain evidence via manual/admin re-attestation where no API exists.

Each compliance object has checkedAt / staleAfter. Launch fails closed when a release-blocking fact is stale beyond configured policy.

## 19. Suspension hierarchy

Any of these stops new sends:
- global platform suspension;
- provider-account suspension;
- company managed-SMS suspension;
- DLT profile/chain suspension;
- Header suspension;
- template/provider-binding suspension;
- managed connection/sender disable.

Pause does not rewrite historical runs. In-flight provider requests cannot be recalled.

## 20. Observability

Metrics:
- sends attempted/accepted/unknown;
- DLR delivered/failed/rejected;
- DLT/template/chain failure reason classes;
- limiter denials by scope;
- provider wallet/credit alarms;
- event duplicates/unmatched;
- provisioning lag;
- tenant/template suspension events.

Logs use IDs/correlation only. Do not log AuthKeys, full bodies, full recipient phones, raw consent evidence or provider webhook secrets.

## 21. Parallel implementation boundary

B1 owns control/compliance/provider provisioning.
B2 owns runtime delivery/event/metering/safety and the existing Broadcast delivery hot files.
F1 owns all frontend code.
UX1 owns docs/design specification only.
QA/SEC begins after B1+B2+F1 integration.

B2 consumes B1's frozen SQL contract, not B1 Java code.
