# Idempotency and Transactions

## Current source strengths

### Run creation
`BroadcastLaunchService` locks the Broadcast message, creates at most one run per message, creates stats, and enqueues run preparation in one transaction. `broadcast_runs.broadcast_message_id` is unique.

### Preparation
Preparation uses a durable cursor. Replayed preparation events whose expected cursor does not match current cursor are ignored.

### Batches
`(run_id, sequence_no)` is unique. Recipient work is claimed with row locks/`SKIP LOCKED`.

### Delivery attempts
`(recipient_id, attempt_no)` is unique. A provider attempt begins only after the recipient is claimed.

### Crash recovery
A lost worker before provider delivery begins becomes RETRY. A lost worker after attempt creation becomes UNKNOWN, suppressing unsafe automatic resend.

### Managed Email events
SNS/event receipts use durable dedupe keys and correlation/ownership verification before mutation.

### Managed SMS events
Provider-account/event keys are unique, callbacks are authenticated/correlated, and event workers use bounded claims.

### WhatsApp Inbox sends
`clientRequestId` has a durable reservation model and ambiguous provider outcomes are not blindly replayed.

## R1 idempotency contract

### Preview
Preview is read-only. Repeating the same source descriptor and channel must not create durable execution state.

### Freeze/send/schedule
The send/schedule command must be idempotent for one Broadcast intent. Repeated client/API retries must return/reuse the same frozen execution intent rather than creating a second snapshot/run.

Recommended key:
`companyId + broadcastId + executionGeneration`

The current unique run-per-message model can remain the enforcement mechanism for R1 if scheduling freeze is attached to the same Broadcast intent.

### Snapshot
Snapshot creation must be unique for one execution generation and source descriptor. Replayed materialization must resume from the durable cursor.

### Recipient
One logical Contact identity appears at most once in an execution snapshot after dedupe.

### Provider
Do not claim exactly-once provider delivery when the provider does not offer an idempotency key. Use the current UNKNOWN policy for ambiguous outcomes.

For WhatsApp Broadcast, derive a stable provider client request key from run/recipient, and reuse the existing reservation/unknown-outcome discipline.

### Webhooks
Each provider event must have a stable provider/dedup key. Duplicate delivery callbacks must be no-ops after the first committed effect.

### Usage
Usage reservation/commit/release is keyed to company/run/recipient/attempt where current managed provider services already support it. R1 must not introduce a second billing counter.

## Transaction boundaries

Keep local atomicity around:
- draft mutation + version;
- schedule/freeze intent;
- run/stats/outbox creation;
- one bounded snapshot page + cursor checkpoint;
- recipient claim;
- delivery attempt state change;
- terminal run/message state;
- provider-event receipt + local projection/suppression effect where practical;
- Inbox message + Contact-link projection;
- notification source event with terminal Broadcast state.

Never hold a database transaction open across a provider network request.

## Dual-write guidance

Use the existing outbox/job pattern for DB -> Kafka/realtime/notification side effects.

Provider network calls cannot be part of the DB transaction. Persist enough correlation before/around the call to distinguish:
- definite pre-send failure;
- definite provider rejection;
- accepted;
- ambiguous/unknown.

## Authorization context

Background jobs must derive company ownership from durable server-owned run/snapshot/connection rows. A company id from an event payload is never sufficient authorization by itself.

Inbox Contact resolution uses the company id from the trusted Inbox conversation/connection, never an arbitrary participant-provided tenant id.
