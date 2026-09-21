# Broadcast architecture

Broadcast is a persistent-group messaging workflow. A `BroadcastGroup` is reusable like a chat group; every send creates an immutable execution snapshot so later group edits never rewrite history.

## Request path

```text
UI
 -> BroadcastController
 -> BroadcastMessageService
 -> BroadcastLaunchService
 -> BroadcastRun + BroadcastStats + outbox row (same DB transaction)
 -> BroadcastOutboxPublisher
 -> Kafka MEDIUM priority
 -> BroadcastRunPrepareEventHandler
 -> bounded recipient snapshot pages
 -> BroadcastBatch rows
 -> BroadcastBatchWorkerScheduler
 -> provider transport strategy
 -> Gmail / Microsoft / SMTP
```

The HTTP send endpoint only queues work. It never sends recipients inline.

## High-volume invariants

- CSV import is streamed and flushed in bounded chunks.
- Recipient snapshot preparation is keyset paged by member id.
- A run records a snapshot high-water mark and membership timestamps so concurrent group edits do not change that run.
- Recipient rows are created in bounded snapshot batches.
- Workers claim batches/recipients with `FOR UPDATE SKIP LOCKED`.
- Provider network calls run outside the claim transaction.
- Stats are incrementally maintained; dashboard reads do not scan all recipient rows.
- WebSocket progress is throttled and carries aggregate counters, not one event per recipient.

Approximate memory complexity:

```text
CSV import            O(memberWriteBatchSize)
snapshot preparation  O(snapshotBatchSize)
delivery              O(workerConcurrency * recipientClaimSize)
stats read             O(1)
```

## Status semantics

Provider acceptance is intentionally distinct from final delivery:

```text
PENDING
 -> PROCESSING
 -> ACCEPTED_BY_PROVIDER
 -> BOUNCED        (if a later DSN/NDR is received)

PROCESSING
 -> RETRY          (definite transient failure)
 -> FAILED         (definite permanent failure)
 -> UNKNOWN        (provider outcome is ambiguous)
```

Ambiguous sends are not blindly retried because a timeout can happen after the provider accepted the message, which would risk duplicate email.

The UI may label `ACCEPTED_BY_PROVIDER` as **Sent**, but the backend preserves the stronger semantic distinction.

## Provider isolation

Interactive email send and broadcast send share low-level provider APIs but not inbox persistence behavior.

Broadcast mail carries:

```text
X-Messaging-Broadcast-Recipient: <correlation-id>
```

When Gmail, Microsoft, or IMAP later exposes the provider Sent copy, the normalized mailbox persistence layer offers it to `EmailSentMessageHandler`. `BroadcastSentMessageHandler` reconciles provider ids/status and consumes the message so 10k broadcast sends do not become 10k normal Inbox/Sent conversations.

## Delivery reports and bounces

Inbound normalized email runs through `InboxMessagePostProcessor`. The delivery module detects DSN/NDR content and stores a durable `EmailDeliveryReport`.

Correlation order is deliberately conservative:

1. broadcast correlation id
2. original Internet Message-ID
3. connection + destination fallback only when there is exactly one recent candidate

A hard bounce moves the recipient to `BOUNCED` and activates a company-level email suppression. Future snapshots skip suppressed recipients. Soft/unknown bounces do not permanently suppress by default.

## Crash recovery

A recipient is first claimed as `PROCESSING`.

- If a worker dies before a delivery attempt is created, recovery returns the recipient to `RETRY`.
- If a worker dies after provider delivery began, recovery marks the recipient `UNKNOWN` instead of risking a duplicate send.
- Stale batch and outbox claims are also recovered.

## Scheduling and control

Broadcast message state and run state are explicit. Supported controls are:

```text
DRAFT -> SCHEDULED -> QUEUED -> RUNNING
RUNNING -> PAUSED -> RUNNING
SCHEDULED/QUEUED/RUNNING/PAUSED -> CANCELLED
RUNNING -> COMPLETED / PARTIALLY_FAILED / FAILED
```

Pause/cancel stop new delivery claims. A provider call already in flight cannot be recalled.

## Runtime tuning

Production knobs are exposed under `app.broadcast` and `app.delivery.email` and map to environment variables:

- `BROADCAST_MEMBER_WRITE_BATCH_SIZE`
- `BROADCAST_SNAPSHOT_BATCH_SIZE`
- `BROADCAST_RECIPIENT_CLAIM_SIZE`
- `BROADCAST_WORKER_CONCURRENCY`
- `BROADCAST_MAX_DELIVERY_ATTEMPTS`
- `BROADCAST_GMAIL_PER_SECOND`
- `BROADCAST_MICROSOFT_PER_SECOND`
- `BROADCAST_IMAP_SMTP_PER_SECOND`
- `EMAIL_DELIVERY_REPORT_*`

Provider limits must be tuned conservatively per account/provider policy. The Redis rate limiter is connection-scoped so multiple Kubernetes replicas share the same send budget.

## Phase boundary

Phase 1 executes email only (Gmail, Microsoft, IMAP/SMTP). The group/run/batch/recipient orchestration is channel-aware so WhatsApp and SMS can add transport strategies later without rewriting the broadcast execution model.
