# Broadcast architecture

Broadcast is a persistent-group messaging workflow. A `BroadcastGroup` is reusable like a chat group; every send creates an immutable execution snapshot so later group edits never rewrite history.

## Provider boundary

Mailbox providers and Broadcast providers are intentionally separate:

```text
Inbox / normal email
├── Gmail
├── Microsoft
└── IMAP / SMTP

High-volume Broadcast
└── Amazon SES             phase 1
    ├── Customer-managed   implemented
    └── Messaging-managed  later

Broadcast email transports
├── Amazon SES
└── SendGrid                 implemented in multi-provider phase

Future Broadcast transports
└── Azure Communication Services Email
```

Gmail, Microsoft and IMAP/SMTP are not valid senders for new Broadcasts. Historical mailbox-based Broadcast rows remain readable through nullable legacy references.

## Amazon SES phase 1

Phase 1 is bring-your-own Amazon SES. The company connects its own AWS account, AWS bills that account directly, and Messaging provides orchestration, scheduling, groups, batching, progress and recipient-level status.

The sender model is deliberately separate from `ChannelConnection`:

```text
BroadcastSenderConnection
  provider = AMAZON_SES
  managementMode = CUSTOMER_MANAGED
  authMode = IAM_ROLE | ACCESS_KEYS
  region
  provider quota snapshot
        │
        └── BroadcastSender
              fromAddress
              displayName
              replyToAddress
              verifiedIdentity
```

Access-key credentials are encrypted using the existing AES-GCM credential cipher and are never returned by an API. IAM Role mode uses STS AssumeRole and temporary credentials; it requires the Messaging runtime itself to have AWS credentials allowed to assume the customer role.

Connect flow:

```text
Connect Amazon SES
 -> normalize From/Reply-To
 -> validate auth configuration
 -> create temporary SES client
 -> SES GetAccount
 -> read production access + sending enabled + quotas
 -> SES GetEmailIdentity
 -> verify exact From address or parent domain
 -> atomically persist connection + sender
```

No AWS network call is executed while the sender creation database transaction is open.

## Request path

```text
UI
 -> BroadcastController
 -> BroadcastMessageService
 -> validate active Amazon SES BroadcastSender
 -> BroadcastLaunchService
 -> BroadcastRun + BroadcastStats + outbox row (same DB transaction)
 -> BroadcastOutboxPublisher
 -> Kafka MEDIUM priority
 -> BroadcastRunPrepareEventHandler
 -> bounded recipient snapshot pages
 -> BroadcastBatch rows
 -> BroadcastBatchWorkerScheduler
 -> BroadcastEmailTransportRegistry
 -> AmazonSesBroadcastEmailTransport
 -> Amazon SES SendEmail
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
- One SES API request is made per recipient so recipient status, retries and provider message ids remain independent.

Approximate memory complexity:

```text
CSV import            O(memberWriteBatchSize)
snapshot preparation  O(snapshotBatchSize)
delivery              O(workerConcurrency * recipientClaimSize)
stats read             O(1)
```

## Rate limiting

Amazon SES exposes the account's current maximum send rate. Messaging snapshots that rate on connect/refresh and enforces it with a Redis distributed token bucket shared by all backend pods.

```text
effective rate =
min(
  SES account maxSendRate,
  BROADCAST_SES_MAX_PER_SECOND_CAP
)
```

The application cap is a safety ceiling. It does not increase the AWS account's approved SES throughput.

The Redis key is connection scoped, so several Kubernetes replicas cannot independently exceed the same SES account budget.

## Status semantics

Provider acceptance is intentionally distinct from final delivery:

```text
PENDING
 -> PROCESSING
 -> ACCEPTED_BY_PROVIDER

PROCESSING
 -> RETRY          definite transient failure
 -> FAILED         definite permanent failure
 -> UNKNOWN        provider outcome is ambiguous
```

A client/network failure after an SES request begins can be ambiguous. Messaging does not blindly retry ambiguous sends because SES may already have accepted the email and a retry could create a duplicate.

The UI may label `ACCEPTED_BY_PROVIDER` as **Sent**, but the backend keeps the stronger semantic distinction.

## SES delivery events

Phase 1 connect/send stores the SES provider message id returned by `SendEmail`.

Full SES event integration is the next provider step. It should use an SES configuration set plus SNS/EventBridge delivery events to reconcile:

```text
DELIVERY
BOUNCE
COMPLAINT
REJECT
OPEN
CLICK
```

into `BroadcastRecipient` and suppression state.

The existing DSN/NDR mailbox parser is retained for historical mailbox sends. It is not the primary delivery-status mechanism for SES.

## Crash recovery

A recipient is first claimed as `PROCESSING`.

- If a worker dies before a provider attempt begins, recovery returns the recipient to `RETRY`.
- If a worker dies after provider delivery begins, recovery marks the recipient `UNKNOWN` instead of risking a duplicate send.
- Stale batch and outbox claims are recovered separately.

## Scheduling and control

Broadcast message and run state are explicit:

```text
DRAFT -> SCHEDULED -> QUEUED -> RUNNING
RUNNING -> PAUSED -> RUNNING
SCHEDULED/QUEUED/RUNNING/PAUSED -> CANCELLED
RUNNING -> COMPLETED / PARTIALLY_FAILED / FAILED
```

Pause/cancel stop new delivery claims. A provider request already in flight cannot be recalled.

A legacy scheduled mailbox broadcast that becomes due after this migration fails launch validation instead of being retried indefinitely.

## Runtime tuning

Important runtime knobs:

- `BROADCAST_MEMBER_WRITE_BATCH_SIZE`
- `BROADCAST_SNAPSHOT_BATCH_SIZE`
- `BROADCAST_RECIPIENT_CLAIM_SIZE`
- `BROADCAST_WORKER_CONCURRENCY`
- `BROADCAST_MAX_DELIVERY_ATTEMPTS`
- `BROADCAST_SES_MAX_PER_SECOND_CAP`

The actual SES account quota/rate remains authoritative.

## Billing modes

The data model supports both:

```text
CUSTOMER_MANAGED
  customer owns AWS account
  AWS bills customer directly
  implemented now

MESSAGING_MANAGED
  Messaging owns provider infrastructure
  Messaging meters provider usage and bills customer
  future phase
```

Managed SES must add abuse controls, tenant isolation, domain onboarding, reputation monitoring, usage metering, credits/invoicing and provider event processing before it is enabled.

## Future transports

Azure Communication Services Email and SendGrid should implement the same Broadcast sender/transport contract rather than becoming mailbox `ChannelConnection` providers.

The Broadcast group/run/batch/recipient orchestration should not change when those providers are added.

## SendGrid

SendGrid uses the same `BroadcastSenderConnection -> BroadcastSender -> BroadcastEmailTransport`
boundary as Amazon SES.

Connect flow:

```text
Connect SendGrid
 -> validate API key
 -> read verified sender identities
 -> fall back to authenticated domains
 -> verify exact From address or parent domain
 -> atomically persist ChannelConnection + BroadcastSenderConnection + sender identity
```

The API key is encrypted with the existing channel credential cipher. The SendGrid connection stores
only the selected regional API endpoint (`GLOBAL` or `EU`) and the provider safety-rate snapshot in
plain connection metadata.

Delivery uses one `POST /v3/mail/send` request per recipient and stores the `X-Message-Id` returned
with the 202 response. The recipient correlation id is also sent as a SendGrid custom argument so a
future signed Event Webhook integration can reconcile delivered, bounced, dropped, complaint/open and
click events without changing the batch execution pipeline.

The default local SendGrid safety ceiling is controlled by
`BROADCAST_SENDGRID_MAX_PER_SECOND_CAP`. Provider HTTP 429 and 5xx responses are retryable; ambiguous
network failures remain `UNKNOWN` to avoid accidental duplicate sends.

Signed Event Webhook ingestion is intentionally a separate step because customer-managed SendGrid
accounts have per-webhook verification keys. Those keys must be stored per provider connection and
verified before delivery events are allowed to mutate recipient state.
