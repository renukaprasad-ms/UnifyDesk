# Contacts & Audience Architecture

Status: FROZEN FOR ROUND 1 after worker-base stamping

## 1. Goal

Create one company-scoped customer/contact system consumed by Inbox, Broadcast, Managed Email, Managed SMS, Meta WhatsApp, future WhatsApp Broadcast, Ad Center, Automation and Analytics.

This is not a new microservice. Round 1 remains inside the existing Spring Boot/PostgreSQL application and reuses company authorization, entitlements, Redis/Kafka where appropriate, async jobs, storage accounting, realtime and observability.

## 2. Module boundaries

B1 core domain:
- contacts and identities
- tags and tag membership
- custom-field definitions/values
- static lists and list membership
- deterministic merge
- contact CRUD/search
- Contacts permissions and entitlements

B2 audience/data plane:
- segments and SQL compiler
- communication preferences and immutable preference events
- suppressions and suppression events
- shared eligibility evaluator
- imports/exports
- large bulk operations
- Contacts-domain activity events and cross-module activity aggregation
- audience resolution/snapshot provider contract
- async workers and storage adapters

F1:
- Contacts table/detail
- Lists
- Segments/filter builder
- CSV import wizard
- consent/suppression UI
- bulk UX
- responsive navigation and routes

Lead integration:
- B1/B2 cross-table constraints
- Broadcast audience-source wiring and recipient provenance
- any tiny Inbox/contact lookup adapter that necessarily touches shared modules
- cross-module regression fixes only

## 3. Contact identity model

Contact is the durable person/customer record. It is identified by internal ID + company_id.

Identity types in V1:
- EMAIL
- PHONE
- EXTERNAL

EXTERNAL identities require a namespace. EMAIL and PHONE do not.

The same normalized email/phone may exist in different companies. It may not actively belong to two Contacts inside the same company.

Normalization:
- email: trim, Unicode-safe validation, IDNA ASCII domain, lowercase canonical matching value; preserve user-facing value. Do not apply Gmail dot/plus rewriting to the canonical Contact identity.
- phone: parse/validate to E.164 using a vetted phone-number library; preserve entered/display form.
- external: namespace-specific opaque normalization defined by the integration adapter.

A Contact can have multiple emails/numbers but at most one primary active identity of each channel type.

## 4. Dedupe and merge

Creation/import uses exact normalized identity matches only.

If multiple supplied identities point to the same Contact, update/merge policy may proceed.
If supplied identities point to different existing Contacts, return IDENTITY_CONFLICT; never auto-merge two records.

Explicit merge locks both Contacts in deterministic ID order and follows CONTACT-MERGE.md. Fuzzy merge is deferred.

## 5. Lists and Segments

Lists are static, tenant-level containers of Contact IDs.

Segments store a versioned bounded filter AST. Server-side compiler emits parameterized SQL/EXISTS queries with company_id as an unavoidable predicate.

No segment evaluation loads all Contacts into Java memory.
No materialized segment membership table is required in V1. Counts may be asynchronously cached with timestamp/version metadata.

## 6. Preferences, suppression and eligibility

Communication preference:
- channel: EMAIL, SMS, WHATSAPP
- purpose: MARKETING in V1
- state: UNKNOWN, OPTED_IN, OPTED_OUT
- source/evidence/timestamp/actor

Suppression:
- separate active/lifted record for delivery-prohibiting facts such as complaint, hard bounce, provider block, DND or manual suppression.

Eligibility evaluates:
1. company/contact/identity active state;
2. usable selected channel identity;
3. current channel-purpose preference;
4. active local suppression;
5. channel/provider/jurisdiction policy.

Round-1 marketing default is fail closed when affirmative consent is required by the selected channel policy. The policy engine decides legal/provider requirements; the Contact table does not.

## 7. Broadcast audience model

Legacy Broadcast groups remain supported.

New audience sources use:
- sourceType = LIST or SEGMENT
- sourceId
- sourceVersion/filter hash where applicable

At actual run materialization, not draft creation, the audience is evaluated and an immutable snapshot is created. Scheduling UI may show an estimate, but the final candidate/eligible counts are calculated at run time.

Snapshot records contact/identity provenance and writes destination snapshots into BroadcastRecipient so historical reporting never depends on mutable Contact data.

Once a run snapshot begins, membership is frozen. The delivery path still re-checks current eligibility immediately before provider submission so an opt-out/suppression after snapshot blocks send.

One destination per Contact per channel in V1:
- use active primary identity;
- if no primary, deterministic oldest active identity;
- if selected identity is ineligible, do not silently fall back to another identity.
“All identities” campaign behavior is deferred.

## 8. Inbox resolution

Inbox persistence does not get a hard FK to Contacts in Round 1.

A provider-neutral ContactIdentityLookup resolves normalized sender/participant identity by company. Unknown senders remain valid Inbox participants.

The Contact Detail activity API can query existing Inbox/Broadcast data through read adapters. It stores only lightweight Contacts-domain audit/activity events, not copies of message bodies/provider payloads.

A later Inbox side panel can call Contacts by resolved contactId without coupling Inbox entities to Contacts implementation classes.

## 9. Import/export

CSV is the V1 interchange format. XLSX is deferred because the current backend does not contain an XLSX parser and adding spreadsheet parsing expands security/validation surface without being necessary for the foundation.

Files use the existing StorageProvider, StorageQuotaService and StorageAccountingService through a Contacts-specific adapter. The Media validation pipeline is not weakened to accept CSV.

Processing is streaming/batched and asynchronous through AsyncJob.

## 10. Ads activation seam

Contacts provides a future AudienceActivationProjection:
- contactId
- eligible identity values
- source audience/version
- applicable consent facts

Provider adapters later perform provider-specific normalization/hashing and own provider audience IDs, sync cursors, add/remove reconciliation and policy eligibility. Hashes are never the canonical Contact identity.

No Google/Meta audience sync implementation is in Round 1.

## 11. Deletion

User delete makes Contact immediately non-queryable/non-sendable and queues purge.

Purge removes mutable profile data/identities/custom values/current memberships while preserving:
- historical Broadcast recipient snapshots;
- immutable audit facts required for integrity;
- keyed suppression tombstones necessary to avoid accidental re-subscription after re-import.

No self-service restore is exposed in V1. Merge source records remain tombstones pointing to target Contact.

## 12. Infrastructure reuse

Reuse:
- CompanyAuthorizationService + permission keys
- EntitlementService
- AsyncJobService/realtime progress
- StorageProvider/StorageQuota/StorageAccounting
- PostgreSQL/Flyway
- existing Broadcast outbox/runtime
- existing Inbox tables and query services via adapters
- existing logging/metrics conventions

Do not add:
- a second job framework
- a second storage quota
- a contact-specific auth system
- Elasticsearch/OpenSearch
- a new message broker
- provider-specific Contact tables in the core module
