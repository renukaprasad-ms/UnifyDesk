# State Transitions

## Core Broadcast message

Actual enum:

`DRAFT -> SCHEDULED | QUEUED -> RUNNING -> COMPLETED | PARTIALLY_FAILED | FAILED | CANCELLED`

`PAUSED` is also represented by the source state machine.

Important freeze behavior:

- immediate Send freezes canonical audience before launch;
- Schedule freezes canonical audience at scheduling confirmation;
- later launch reuses the already-frozen snapshot;
- source changes after freeze do not silently retarget membership;
- send-time identity/consent/suppression is still revalidated.

## Broadcast run

Actual enum:

`PREPARING, RUNNING, PAUSED, COMPLETED, PARTIALLY_FAILED, CANCELLED, FAILED`

Launch creates the run as `PREPARING`.

Preparation materializes recipients in bounded chunks.

When preparation ends:

- if pending == 0: run/message become `COMPLETED` and durable completion Notification is recorded;
- otherwise: run/message progress to `RUNNING`.

Finalizer makes a running, snapshot-complete run terminal only after active batches are gone.

A run is `PARTIALLY_FAILED` if final stats contain failed, unknown or bounced recipients; otherwise it becomes `COMPLETED`.

## Broadcast recipient

Actual core enum:

`PENDING -> PROCESSING -> ACCEPTED_BY_PROVIDER | RETRY | FAILED | UNKNOWN | SKIPPED`

Additional core terminal:

`BOUNCED`

### UnifyDesk-controlled transitions

- snapshot/member materialization -> `PENDING` or `SKIPPED`;
- worker claim -> `PROCESSING`;
- local validation/eligibility failure -> `SKIPPED` or `FAILED`;
- retryable transport result -> `RETRY`;
- ambiguous outcome -> `UNKNOWN`.

### Provider-acceptance transition

Successful provider submission commits `ACCEPTED_BY_PROVIDER` with provider/correlation identifiers.

Provider HTTP acceptance is not final delivery.

### Asynchronous provider truth

Delivered/read/complaint/rejection states may be held in provider-specific event/projection tables. Managed Email permanent bounce can transition the core recipient from `ACCEPTED_BY_PROVIDER` or `UNKNOWN` to `BOUNCED`.

Never label a recipient Delivered/Read without provider event evidence.

## Batch

Actual enum:

`PENDING, PROCESSING, RETRY, COMPLETED, CANCELLED, FAILED`

The execution engine uses bounded claims and source-reviewed `FOR UPDATE SKIP LOCKED` patterns.

## Managed Email event convergence

Source-reviewed chain:

`SES/SNS -> signature verification -> event parse -> runtime ownership/correlation checks -> durable event receipt/ledger -> projection/effects`

Hard bounce:

- legacy destination suppression upsert;
- canonical Contact suppression bridge;
- eligible core recipient -> `BOUNCED`;
- stats transition once.

Complaint:

- legacy suppression compatibility;
- canonical Contact suppression bridge.

Idempotency identity includes the SNS/provider event identity recorded by the Managed Email event ledger.

## Managed SMS event convergence

Runtime verification must prove:

`MSG91 request -> provider request/message identity -> authenticated callback -> durable dedupe/event processing -> projection/stats`

Do not promote source-only callback code to provider PASS.

## Meta WhatsApp event convergence

Runtime verification must prove:

`Cloud API request -> provider message ID -> signed webhook -> delivered/read/failed projection`

Inbound:

`signed webhook -> durable processing -> Inbox conversation/message -> participant Contact link`

## Notification terminal event

Completed event key:

`broadcast.completed:<runId>`

Failed/partial event key:

`broadcast.failed:<runId>`

`NotificationEventRecorder` uses:

`ON CONFLICT (event_key) DO NOTHING`

and requires an existing transaction, so terminal event recording is durable/idempotent rather than a realtime-only signal.

## Database entities/tables to inspect

Current source/migrations directly establish these names for the canonical journey:

- `contacts`
- Contact identity/preference/suppression repositories for canonical identity/consent state
- `contact_lists`
- `contact_list_memberships`
- `contact_segments`
- `contact_audience_selections`
- `contact_audience_selection_members`
- `contact_audience_snapshots`
- `contact_audience_snapshot_candidates`
- snapshot members via `AudienceSnapshotRepository`
- `broadcast_messages`
- `broadcast_runs`
- `broadcast_recipients`
- `broadcast_suppressions` (legacy compatibility protection)
- `managed_email_provider_events`
- Managed Email provider projections/runtime records
- Managed SMS runtime/provider event records
- `inbox_conversations`
- Inbox messages/participants
- `inbox_participant_contact_links`
- `notification_events`

Before writing production verification SQL, confirm the exact current physical table names for repository-backed entities not explicitly named above by inspecting their repository/migration definitions on the same frozen SHA.
