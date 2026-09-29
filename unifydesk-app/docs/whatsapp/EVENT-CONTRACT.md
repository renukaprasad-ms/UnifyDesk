# WhatsApp Event Contract

Status: FROZEN FOR ROUND 1

## External webhook durable event

The durable DB event is the source of truth for webhook acknowledgement/retry.

Schema version: `meta.whatsapp.webhook.v1`

Normalized fields:
- eventId: local Snowflake ID
- eventKey: deterministic provider-effect dedupe key
- kind: MESSAGE | STATUS | TEMPLATE_STATUS | ACCOUNT_UPDATE | UNKNOWN
- providerObjectId: top-level Meta object/account ID if present
- phoneNumberId
- providerMessageId
- providerEventAt
- receivedAt
- payload: bounded signed provider JSON required to process this event

Do not include:
- app secret
- access token
- authorization code
- registration PIN
- HTTP authorization/signature headers

## Event key rules

MESSAGE:
`msg:<phoneNumberId>:<providerMessageId>`

STATUS:
`status:<phoneNumberId>:<providerMessageId>:<status>:<providerTimestamp>`

For provider events without stable provider message ID, use a SHA-256 over a canonical minimal provider identity tuple, never the full unbounded raw body.

Duplicate insert is success/no-op.

## Internal Kafka

Kafka is not the webhook acknowledgement boundary.

B2 may publish existing-platform events after DB commit for:
- realtime;
- metrics;
- operational notifications.

If a new Kafka event is required, use generic KafkaEvent envelope and event type:
`whatsapp.inbox.changed.v1`

Payload:
```json
{
  "companyId":"123",
  "connectionId":"123",
  "conversationIds":["123"],
  "messageIds":["123"],
  "changeType":"UPSERT|STATUS"
}
```

Kafka key: connectionId or conversationId to preserve useful partition locality.

No provider access token or raw webhook payload.

## Processing semantics

At-least-once processing is assumed.

A handler is successful only after:
- dedupe/lookup complete;
- canonical DB mutation committed;
- event state marked PROCESSED.

Crash after canonical mutation but before event-state update must be safe on retry because canonical constraints are idempotent.

## Retry classification

Retryable:
- transient DB lock/deadlock;
- provider media download 429/5xx;
- temporary storage outage;
- transient dependency outage.

Non-retryable/ignored:
- unknown disconnected phone with no trusted mapping;
- malformed signed event missing required IDs;
- unsupported message kind that can be represented as placeholder;
- cross-scope mismatch;
- provider status for an unknown message after retention horizon.

Security-significant mismatch should be recorded in safe metrics/audit; never mutate another tenant.

## Ordering

Do not rely on global ordering.

Per-message status uses provider timestamp + monotonic rank. Inbox conversation lastMessageAt updates only if incoming sent timestamp is newer than current value.

## Realtime

Realtime is an optimization. Failure to publish realtime must not roll back the durable Inbox message. Client focus/reconnect snapshot recovers current state.

## Post-processing

For a newly persisted inbound message, invoke all InboxMessagePostProcessor instances inside the canonical persistence transaction if their contract requires MANDATORY transaction semantics.

A duplicate webhook must not invoke the post-processor twice.
