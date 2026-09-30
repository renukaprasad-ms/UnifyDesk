# Contacts & Audience Architecture

Status: Round-1 contract candidate  
Research date: 2026-09-30

## 1. Goals

Build one production-grade, multi-tenant customer-data foundation that can serve:

- Broadcast recipient selection;
- Inbox identity/context resolution;
- customer profile management;
- consent/suppression eligibility;
- future Ad Center customer-list activation;
- later CRM/CDP features.

The design must remain operational with companies ranging from hundreds to millions of contacts without requiring an immediate database rewrite.

## 2. Existing infrastructure reused

| Concern | Existing implementation | Contacts decision |
|---|---|---|
| Auth/session | existing session/JWT + CurrentUserService | reuse |
| Company authorization | CompanyAuthorizationService + IAM permission keys | reuse/extend keys |
| IDs | Snowflake Long | reuse; JSON string IDs |
| DB | PostgreSQL + Flyway | authoritative |
| Plan limits | EntitlementService | reuse CONTACT_LIMIT/SEGMENT_LIMIT |
| Storage quota | StorageQuotaService | reuse CONTACT_DATA / EXPORT |
| Object storage | existing StorageProvider adapters | reuse through Contacts file adapter |
| Async progress | AsyncJobService + realtime event | reuse |
| Kafka | existing EventPublisher/consumer conventions | reuse for outbox/integration events |
| Realtime | Redis/WebSocket | hints only, durable API authoritative |
| Notifications | Notification subsystem | optional job completion notifications later |
| Metrics | Micrometer/Prometheus | reuse |
| Company purge | existing lifecycle bounded purge | integrate |
| Broadcast snapshot runtime | existing broadcast run/recipient model | adapt, do not replace in Round 1 |
| Ad audience projection | ad_audiences | future adapter only |

## 3. Domain topology

```text
                    ┌───────────────────────┐
UI / API / imports ─► Contacts Core (B1)    │
                    │ profile + identities  │
                    │ fields/tags/lists     │
                    │ merge/search          │
                    └───────────┬───────────┘
                                │ frozen SQL/domain seam
                                ▼
                    ┌───────────────────────┐
                    │ Audience Runtime (B2) │
                    │ segments              │
                    │ consent/suppression   │
                    │ eligibility           │
                    │ import/export/bulk    │
                    └──────┬────────┬───────┘
                           │        │
                  durable  │        │ audience resolution
                  outbox   │        ▼
                           │   ┌──────────────────┐
                           │   │ Audience Snapshot│
                           │   │ immutable IDs +  │
                           │   │ channel identity │
                           │   └──────┬───────────┘
                           ▼          │
                        Kafka         ├──── Broadcast adapter
                                     ├──── future Ads adapter
                                     └──── future workflow/CDP
```

Inbox uses a read-only identity resolver and remains independent.

## 4. Core identity model

A contact is a workspace-scoped profile with a stable internal ID.

Identity rows hold EMAIL or PHONE values.

Each identity stores:

- raw/display value;
- normalized value;
- type;
- label;
- primary marker;
- source;
- validation state;
- lifecycle timestamps.

The database enforces one active normalized identity per company/type.

Multiple identities of each type are allowed within bounded product limits.

### Why PHONE instead of WHATSAPP identity

SMS and WhatsApp both address phone numbers. Duplicating the same E.164 number as separate identity records would create inconsistent dedupe/merge semantics.

Channel-specific sendability is modeled in preferences/suppressions and provider connection policy.

## 5. Contact profile

The contact row contains common profile data, not CRM pipeline state:

- names;
- organization string;
- job title;
- geography;
- timezone;
- language;
- source;
- owner user;
- notes;
- lifecycle status;
- bounded JSONB custom values;
- last_activity_at;
- merge/delete metadata.

Owner is optional and must be an active member of the same company when set.

## 6. Custom fields

Definitions are normalized and company scoped.

Values are stored in one bounded `custom_fields JSONB` object on the contact.

Reasons:

- avoids millions of sparse EAV rows;
- atomic profile read/update;
- easy export/import mapping;
- definitions still provide schema/type validation.

Constraints:

- field count and JSON bytes are capped;
- only known active definition keys may be written;
- segment compiler uses whitelist/typed operators;
- large/range-heavy custom-field segmentation is allowed to be async/stale-count rather than blocking request threads.

## 7. Lists and tags

### Tags

Reusable labels with membership joins.

### Lists

Static reusable audience groupings.

Membership records capture source and timestamps.

Neither changes consent.

Counts are maintained incrementally with reconciliation rather than doing N full counts for list index pages.

## 8. Dynamic segments

A segment is:

- name/description;
- versioned predicate AST;
- status;
- cached count + evaluation timestamp;
- created/updated metadata.

No authoritative membership table exists in Round 1.

The evaluator:

1. validates the AST;
2. resolves custom-field definitions;
3. compiles whitelisted predicates to parameterized SQL;
4. always injects company scope + active-contact scope;
5. uses keyset pagination for members;
6. allows bounded synchronous preview;
7. uses async count refresh when needed.

See `SEGMENTATION.md`.

## 9. Consent, suppression and eligibility

### Preference projection

One effective state per:

`company + identity + channel + purpose`

Round-1 purpose:
- MARKETING

Schema remains extensible for future topics/purposes.

States:
- UNKNOWN
- OPTED_IN
- OPTED_OUT

### Immutable consent events

All changes append an audit event.

### Suppression

Independent blocking facts:

- HARD_BOUNCE
- COMPLAINT
- INVALID_DESTINATION
- USER_REQUEST
- ADMIN
- PROVIDER
- LEGAL
- OTHER

A suppression is channel-specific and can block even an opted-in identity.

### Eligibility

The central service returns a structured decision, not a boolean only:

```text
ELIGIBLE
NO_CHANNEL_IDENTITY
CONTACT_INACTIVE
CONSENT_UNKNOWN
OPTED_OUT
HARD_BOUNCE
COMPLAINT
SUPPRESSED
IDENTITY_INVALID
...
```

Callers can count exclusion reasons.

Provider modules may impose additional constraints after common eligibility.

## 10. Import architecture

```text
upload private CSV
  -> validate size/type
  -> create CONTACT_IMPORT async job
  -> parse bounded header/sample
  -> user confirms mapping + duplicate mode
  -> worker claims job
       -> streaming CSV reader
       -> validate/normalize row
       -> deterministic identity lookup
       -> create/update
       -> list/tag operations
       -> checkpoint same transaction as chunk
       -> progress
       -> bounded error writer
  -> private error CSV artifact
  -> complete/realtime hint
```

The worker never reads the entire file into memory.

CSV only in Round 1.

## 11. Export architecture

Export is always asynchronous for one consistent security/performance model.

Worker:

1. authorizes user at request creation;
2. stores frozen export selection/config;
3. streams rows with keyset pagination;
4. CSV-escapes and formula-sanitizes cells;
5. writes to private object storage;
6. accounts storage bytes;
7. exposes short-lived authenticated access;
8. purges artifact after TTL.

## 12. Bulk operations

Operations:

- add/remove tag;
- add/remove static list;
- update one allowed field/custom field;
- archive;
- soft delete;
- export.

Small explicit selections may run synchronously under a low cap.

Large/filter/list/segment selections use a CONTACT_BULK async job.

Jobs store selection definition, not a million IDs in one JSON value.

## 13. Audience resolution and snapshots

Consumers use an `AudienceSelection`:

- CONTACT_IDS
- STATIC_LIST
- DYNAMIC_SEGMENT
- ALL_CONTACTS with bounded filter (admin UI only where permitted)

Before delivery/activation, resolver produces a snapshot for a channel/purpose.

Snapshot semantics:

- company-scoped;
- definition version captured;
- prepared timestamp;
- immutable;
- deduped by normalized destination;
- channel identity selected deterministically;
- consent/suppression applied;
- exclusion reason counts stored;
- snapshot rows keyset iterable.

Primary identity selection:

1. active primary identity of required type;
2. otherwise oldest active valid identity of required type;
3. no identity => exclusion.

A provider adapter may apply further exclusions, but must not re-enable centrally ineligible identities.

## 14. Broadcast integration

Current Broadcast remains functional.

Lead integration introduces a narrow resolver seam so future broadcast drafts can reference a Contacts list/segment.

At send preparation:

1. resolve audience snapshot;
2. materialize existing broadcast recipient records from snapshot rows;
3. persist snapshot reference/version on run;
4. use existing delivery/retry/reporting unchanged.

Legacy Broadcast groups remain supported until an explicit migration project.

No Contacts worker modifies active Ad Center implementation.

## 15. Inbox integration

A read-only resolver accepts:

- company ID;
- identity type inferred from channel;
- normalized address.

Returns minimal contact summary if one active match exists.

Inbox can use it to show "known contact" context.

No automatic contact creation from messages in Round 1.

No Contacts delete cascades into Inbox messages/conversations.

## 16. Ads integration — future

Future activation flow:

```text
Contacts list/segment
  -> immutable activation snapshot
  -> eligibility/purpose check
  -> Ad Center provider adapter
  -> provider-specific normalization/hash
  -> ad_audiences remote projection
  -> add/remove/reconcile jobs
```

The adapter owns Google/Meta API details.

Core Contacts never stores provider audience IDs.

## 17. Events/outbox

Contacts will publish compact, idempotent domain events such as:

- `contact.created.v1`
- `contact.updated.v1`
- `contact.deleted.v1`
- `contact.restored.v1`
- `contact.merged.v1`
- `contact.identity.changed.v1`
- `contact.preference.changed.v1`
- `contact.suppression.changed.v1`
- `contact.list.membership.changed.v1`
- `contact.segment.changed.v1`

Payloads contain IDs, company scope, version/timestamps and safe changed-field metadata, not full contact PII.

A Contacts outbox uses claim/lease/retry semantics and publishes through existing EventPublisher.

## 18. Realtime

Realtime is a hint only.

Emit compact changes for:

- async job progress (already provided);
- contact changed;
- list/segment count changed.

Frontend always recovers via durable APIs on reconnect/focus.

## 19. Deletion lifecycle

### Archive

Reversible normal business state; profile hidden from default active lists and ineligible for new marketing sends.

### Soft delete

Tombstone, restore allowed during retention.

Identity values are not available to normal UI/export and cannot be attached to another active contact until restore/purge decision completes.

### Merge

Source tombstone points to target.

### Purge

Batched, asynchronous:

- delete/anonymize profile PII;
- clear raw identity values;
- retain only minimal non-reversible suppression fingerprint when policy requires;
- preserve historical broadcast recipient snapshots/reporting independently.

Company purge is integrated into the existing bounded lifecycle service.

## 20. Plan entitlements

Reuse:

- `CONTACT_LIMIT`: active + archived + deleted-within-retention contacts, excluding merged tombstones after policy-defined accounting;
- `SEGMENT_LIMIT`: active segment definitions;
- `STORAGE_GB`: private import/export artifacts.

Potential new Round-1 entitlements only if product needs plan controls:

- `CONTACT_LIST_LIMIT`
- `CONTACT_CUSTOM_FIELD_LIMIT`
- `CONTACT_IMPORT_ROWS_PER_JOB`
- `CONTACT_EXPORT_ROWS_PER_JOB`

No hardcoded Starter/Growth/Pro numbers.

## 21. Worker boundaries

### B1 Core

Owns contact/profile/identity/custom-field/tag/static-list/search/merge/core activity + permissions + V90-V92.

### B2 Audience Runtime

Owns segments/consent/suppression/eligibility/import/export/bulk/outbox/async job extensions + V93-V95.

B2 codes to the frozen SQL seam rather than importing B1-only classes.

### F1

Owns Contacts UI and frontend routes/nav/permission constants.

### L1 after worker completion

Owns V96 cross-worker constraints, Broadcast/Inbox/lifecycle glue, conflict resolution and integrated verification.

## 22. Failure/degradation model

| Failure | Required behavior |
|---|---|
| duplicate API request | idempotency/unique constraints prevent duplicate effect |
| import worker crash | stale job reclaimed from committed checkpoint |
| Redis down | async work continues; UI refetch recovers |
| object storage down | job retries/fails safely; no fake success |
| Kafka down | Contacts data remains authoritative; outbox retries |
| segment count timeout | return stale/unknown count, not wrong members |
| provider suppression signal replay | idempotent event/suppression update |
| merge race | row/identity locks + unique constraints; one succeeds |
| quota race | company-scoped locking/DB constraints prevent over-limit commit |
| user permission revoked mid-export | artifact access re-authorizes; no stale bearer URL in API response |
| contact changed after snapshot | active run uses frozen snapshot |

## 23. Explicit non-goals

Round 1 does not become:

- a full CRM;
- a full CDP;
- a workflow engine;
- an advertising provider integration;
- a data warehouse;
- a legal compliance oracle.
