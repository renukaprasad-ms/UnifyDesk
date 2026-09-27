# Notification Event Contracts

Status: frozen for Round 1

## 1. Principle

Business features emit **product facts**, not delivery commands.

A source producer does not decide SMTP headers, Push API behavior or frontend rendering. The notification type registry converts a validated product event into user-visible materialization and channel intents.

## 2. Durable source-event contract

Conceptual JSON representation of `notification_events`:

```json
{
  "eventId": "922337203685470000",
  "eventKey": "company.created:922337203685470001",
  "eventType": "company.created",
  "schemaVersion": 1,
  "occurredAt": "2026-09-27T06:30:00Z",
  "companyId": "922337203685470001",
  "actorUserId": "922337203685470002",
  "recipient": {
    "mode": "USER",
    "userId": "922337203685470002",
    "requiredPermission": null
  },
  "subject": {
    "type": "COMPANY",
    "id": "922337203685470001"
  },
  "priority": "MEDIUM",
  "groupKey": null,
  "variables": {
    "companyName": "Acme"
  },
  "correlationId": "01K...",
  "causationId": null
}
```

IDs are strings in serialized contracts.

## 3. Recipient selectors

### USER

```json
{
  "mode": "USER",
  "userId": "123",
  "requiredPermission": null
}
```

For company scope, materializer still requires active membership unless the notification type explicitly defines account ownership independent of membership.

### COMPANY_PERMISSION

```json
{
  "mode": "COMPANY_PERMISSION",
  "userId": null,
  "requiredPermission": "email.read"
}
```

Requires `companyId`.

Round 1 intentionally does not support arbitrary role names, SQL-like filters, user-provided recipient expressions or external email addresses in the event contract.

## 4. Round-1 source event types

### 4.1 `company.created` v1

Producer: B1 hook in `CompanyService.create`.

Contract:

```json
{
  "eventKey": "company.created:<companyId>",
  "eventType": "company.created",
  "schemaVersion": 1,
  "companyId": "<companyId>",
  "actorUserId": "<creatorUserId>",
  "recipient": {
    "mode": "USER",
    "userId": "<creatorUserId>",
    "requiredPermission": null
  },
  "subject": {
    "type": "COMPANY",
    "id": "<companyId>"
  },
  "priority": "MEDIUM",
  "variables": {
    "companyName": "<server-read company name>"
  }
}
```

Validation:

- company ID and creator user ID must match the just-created persisted graph;
- companyName comes from persisted server data, not arbitrary HTML;
- duplicate eventKey returns the existing logical event/no-op.

Materialized type: `COMPANY_CREATED`.

Channels:

- in-app;
- email immediate;
- no browser push.

### 4.2 `inbox.activity` v1

Producer: B1 `InboxMessagePostProcessor`.

```json
{
  "eventKey": "inbox.message.received:<messageId>",
  "eventType": "inbox.activity",
  "schemaVersion": 1,
  "companyId": "<companyId>",
  "actorUserId": null,
  "recipient": {
    "mode": "COMPANY_PERMISSION",
    "userId": null,
    "requiredPermission": "email.read"
  },
  "subject": {
    "type": "INBOX_MESSAGE",
    "id": "<messageId>"
  },
  "priority": "MEDIUM",
  "groupKey": "inbox:<connectionId>",
  "variables": {
    "connectionId": "<connectionId>"
  }
}
```

Validation:

- message exists;
- direction = INBOUND;
- message is unread at record time;
- message company/connection matches event company;
- no subject, sender email, body, snippet or attachment name is copied into variables.

Materialized type: `INBOX_ACTIVITY`.

## 5. Kafka contract

Existing `KafkaEvent<T>` is reused.

Envelope:

```json
{
  "eventId": "<notificationEventId>",
  "eventType": "notification.materialize.requested",
  "eventVersion": "1",
  "occurredAt": "2026-09-27T06:30:00Z",
  "companyId": "<companyId-or-null>",
  "userId": "<direct-user-or-null>",
  "correlationId": "<correlationId>",
  "causationId": "<causationId-or-null>",
  "priority": "MEDIUM",
  "payload": {
    "notificationEventId": "<notificationEventId>",
    "eventKey": "<eventKey>",
    "eventType": "<sourceEventType>",
    "schemaVersion": 1
  }
}
```

Payload is intentionally small. The B1 handler reloads the authoritative full event row from PostgreSQL rather than trusting duplicated broker payload content.

### Kafka event ID

Use the durable notification event ID as the Kafka `eventId` string, not a fresh UUID on every outbox republish. This improves existing Redis dedupe but database idempotency remains authoritative.

### Ordering

No global ordering guarantee is required.

Kafka key:

- direct company user => existing `companyId:user:userId`;
- company fanout => existing company key.

Consumers must tolerate duplicate and out-of-order transport.

### Invalid event

Unknown source event type/schema or malformed payload:

- throw non-retryable classification;
- route transport record to DLT through existing error handler;
- set safe event error state when the event row can be identified;
- emit metric/log without raw variables.

### Poison/retry

Retryable infrastructure failure follows existing Kafka exponential backoff/DLT. B1 recovery may also re-enqueue a published-but-unmaterialized event after a configured stale interval. Duplicate protection makes that safe.

## 6. Registry contract

Every supported source event maps to a code-owned notification definition:

```text
sourceEventType
materializedNotificationType
category
severity
allowed variable names/types/max lengths
render title/body
scope
recipient semantics
required permission semantics
coalesce policy
action route builder
default channel policy
mandatory email flag
delivery priority
retention class
```

A new event type cannot be accepted until its registry definition and tests exist.

## 7. Rendering contract

Renderers consume normalized, bounded variables.

Forbidden event/render input:

- arbitrary HTML;
- arbitrary markdown that is later rendered unsafely;
- arbitrary absolute URLs;
- `javascript:`, `data:`, protocol-relative URLs;
- raw provider errors;
- authentication tokens;
- email message bodies/snippets for inbox activity.

Action routes are built from trusted internal route patterns. Example:

```text
/inbox/email?connectionId=<validated-string-id>
```

## 8. Realtime contract

Existing WebSocket envelope stays unchanged:

```json
{
  "type": "notification.changed.v1",
  "occurredAt": "2026-09-27T06:31:00Z",
  "payload": {
    "reason": "CREATED",
    "notificationId": "123",
    "companyId": "456",
    "accountUnread": 2,
    "companyUnread": 7,
    "totalUnread": 9
  }
}
```

`reason` enum:

- `CREATED`
- `COALESCED`
- `READ_STATE`
- `SEEN_STATE`
- `PREFERENCE_RELEVANCE`
- `COUNTER_RECONCILED`

For account notification, companyId is null.

Realtime payload is a **hint**, not the item body. F1 may optimistically update counts, but durable API is authoritative.

## 9. Service-worker/client message contract

F1 service worker -> window:

```json
{
  "type": "UNIFYDESK_NOTIFICATION_PUSH",
  "notificationId": "123",
  "companyId": "456"
}
```

No title/body is required for focused-client messages; page fetches durable state.

## 10. Push plaintext payload contract

Before Web Push encryption:

```json
{
  "v": 1,
  "notificationId": "123",
  "companyId": "456",
  "title": "New inbox activity",
  "body": "You have new activity in UnifyDesk.",
  "openPath": "/notification-open?n=123&c=456",
  "tag": "ud:inbox:456:connection:789"
}
```

Rules:

- <= 2 KiB target payload;
- generic privacy-safe copy;
- same-origin relative openPath;
- no access token, email address, message subject/body/snippet, provider ID or secret;
- tag is non-secret and bounded.

## 11. Versioning

- source schema version is an integer, starting at 1;
- Kafka wrapper `eventVersion="1"`;
- push payload `v=1`;
- breaking change => new source schema version with backward-compatible consumer support during rollout;
- never reinterpret an existing eventType/version payload with incompatible semantics.

## 12. Idempotency summary

| Layer | Key |
|---|---|
| source record | `notification_events.event_key` |
| broker | stable Kafka eventId + existing Redis TTL helper |
| recipient materialization | `(notification_event_id,user_id)` |
| coalescing | company/user/type/group/bucket unique index |
| logical channel intent | `notification_delivery_intents.idempotency_key` |
| push subscription | SHA-256 endpoint hash |
| push logical subscription send | `(intent_id,subscription_id)` |
| digest contribution | unique delivery_intent_id in digest items |

The system promises exactly-once **logical local effects** where these keys apply, not unrealistic exactly-once external SMTP/push-service behavior.
