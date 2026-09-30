# Contacts & Audience Event Contract

Status: FROZEN Round-1 integration contract.

Contacts events are compact integration facts. PostgreSQL remains authoritative.

## 1. Transport

Use the existing UnifyDesk EventPublisher/Kafka envelope and priority conventions.

Contacts persists a domain outbox row in the same transaction as the state change where practical. A bounded publisher claims due outbox rows, publishes, and records success/retry.

At-least-once transport is assumed.

Consumers must be idempotent.

A Kafka outage must not roll back or lose already committed Contacts truth.

## 2. Privacy

Events must not contain:

- complete contact profiles;
- notes;
- custom-field JSON;
- raw CSV rows;
- consent evidence blobs;
- export URLs;
- credentials/secrets;
- complete audience member lists.

Events may contain:

- companyId;
- contactId;
- identityId where needed;
- list/tag/segment ID;
- version;
- state/reason enum;
- source type;
- safe field-name codes;
- timestamps;
- aggregate counts.

Raw email/phone values are omitted from the generic event bus unless a future narrowly scoped consumer contract explicitly requires them.

## 3. Envelope

Existing Kafka envelope metadata is reused.

Contacts payload example:

```json
{
  "companyId": "100",
  "aggregateType": "CONTACT",
  "aggregateId": "200",
  "aggregateVersion": 5,
  "occurredAt": "2026-09-30T10:00:00Z",
  "change": {}
}
```

## 4. Event types

### contact.created.v1

```json
{
  "companyId":"100",
  "aggregateType":"CONTACT",
  "aggregateId":"200",
  "aggregateVersion":1,
  "occurredAt":"...",
  "change":{"source":"IMPORT"}
}
```

### contact.updated.v1

`change.fields` is a bounded array of field codes, for example:

- firstName
- ownerUserId
- custom:customer_tier
- identity.email
- tags
- lists

Do not include old/new PII values.

### contact.archived.v1
### contact.restored.v1
### contact.deleted.v1

State-transition facts only.

### contact.merged.v1

```json
{
  "companyId":"100",
  "aggregateType":"CONTACT",
  "aggregateId":"200",
  "aggregateVersion":6,
  "occurredAt":"...",
  "change":{
    "sourceContactId":"201",
    "targetContactId":"200",
    "policy":"TARGET_WINS_FILL_NULLS"
  }
}
```

### contact.identity.changed.v1

Contains:
- contactId
- identityId
- identityType
- action: ADDED | PRIMARY_CHANGED | DELETED | VALIDATION_CHANGED

No raw destination.

### contact.preference.changed.v1

Contains:
- contactId where resolvable
- identityId
- channel
- purpose
- state
- source code
- preference version

No evidence blob.

### contact.suppression.changed.v1

Contains:
- identityId
- contactId where resolvable
- channel
- reason
- active
- source code

### contact.list.membership.changed.v1

For single/small mutations:
- listId
- contactId
- action

For large bulk work, prefer a compact invalidation/completion event with listId, affectedCount and list version rather than one event per row when no consumer needs row events.

### contact.segment.changed.v1

Contains:
- segmentId
- definitionVersion
- action
- optional cachedCount freshness change

### contact.audience.snapshot.ready.v1

Contains:
- snapshotId
- sourceType/sourceId
- sourceVersion
- channel
- purpose
- matchedCount
- eligibleCount
- excludedCount

No member destinations.

### contact.import.completed.v1
### contact.export.completed.v1
### contact.bulk.completed.v1

Optional product integration events. Existing AsyncJob progress remains the primary UI progress mechanism.

Payload:
- job/resource ID
- terminal status
- safe counts

No object access URL.

## 5. Idempotency

Every outbox row has a deterministic idempotency key appropriate to the originating mutation.

Recommended shapes:

- contact update: `contact:<id>:version:<version>:updated`
- preference event: `preference-event:<consentEventId>`
- suppression event: `suppression-event:<suppressionEventId>`
- snapshot ready: `audience-snapshot:<snapshotId>:ready`

Outbox insert must be protected by DB uniqueness where replay can occur.

## 6. Ordering

Do not assume global ordering.

For the same aggregate, consumers use:

- aggregate ID;
- aggregate version;
- occurredAt only as supporting metadata.

Preference/suppression audit uses its own event sequence/current projection.

A stale contact.updated event must not overwrite newer state in a projection.

## 7. Retry/dead-letter

Use existing Kafka retry/DLT conventions.

Contacts outbox publisher itself:

- bounded claims;
- short lease;
- exponential/backoff policy consistent with platform;
- attempt count;
- safe last error;
- recovery of expired claims.

If process dies after publish but before mark-published, duplicate transport is expected and safe.

## 8. Consumer boundaries

Round 1 expected consumers/adapters:

- future Inbox contact cache/invalidation;
- Broadcast audience integration;
- future Ads activation/reconciliation;
- analytics/audit projections.

No consumer may treat an event as authorization to read a foreign company contact.

Consumers that need profile data re-fetch through an authorized/internal company-scoped service.

## 9. Versioning

Breaking payload changes require a new event version/name.

Adding an optional safe field is allowed only when old consumers remain correct.

Do not repurpose enum meaning silently.

## 10. QA

Test:

- duplicate outbox publish;
- publisher crash after Kafka ack;
- out-of-order updates;
- Kafka down;
- DLT;
- PII scan of event payloads;
- bulk event storms;
- foreign-company consumer lookup;
- merge sequence;
- preference/suppression replay.
