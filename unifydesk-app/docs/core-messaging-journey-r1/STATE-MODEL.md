# State Model

## Broadcast intent

`DRAFT -> SCHEDULED -> QUEUED -> RUNNING -> COMPLETED | PARTIALLY_FAILED | FAILED`

Control transitions:
- DRAFT/SCHEDULED -> CANCELLED
- RUNNING -> PAUSED -> RUNNING
- QUEUED/RUNNING/PAUSED -> CANCELLED where current source permits.

R1 preserves existing states. It adds an explicit audience-freeze rule, not another user-facing Broadcast state.

## Audience state

Preview is live and disposable.

Frozen execution audience:
`PREPARING -> READY | FAILED`

R1 freeze point:
- immediate send: freeze on send confirmation;
- scheduled send: freeze on schedule confirmation.

After READY, List/Segment membership changes do not mutate the snapshot.

Send-time Contact eligibility may still exclude a frozen recipient for safety.

## Recipient core state

Existing internal state remains:

`PENDING -> PROCESSING`

From PROCESSING:
- `ACCEPTED_BY_PROVIDER`
- `RETRY -> PROCESSING`
- `FAILED`
- `UNKNOWN`
- `SKIPPED`

Recovery:
- stale PROCESSING with no provider attempt -> RETRY;
- stale PROCESSING after provider attempt began -> UNKNOWN;
- cancelled-before-send -> SKIPPED.

## Provider delivery projection

Provider-specific states must not be forced into the core enum if a provider cannot report them.

Projection progression may include:
`ACCEPTED/SENT -> DELIVERED -> READ`

Failure projections:
- BOUNCED
- COMPLAINED
- REJECTED
- FAILED

Monotonicity:
- READ never regresses to DELIVERED/SENT.
- DELIVERED never regresses to FAILED/REJECTED due to a late older event.
- duplicate event keys are idempotent.
- provider timestamps plus rank/state rules decide out-of-order application.
- an UNKNOWN core send outcome may be reconciled to accepted when a trusted provider event proves acceptance.

## Accounting

Preview:
`selected = eligible + excluded`

Execution snapshot:
`frozenEligible = pending + processing + accepted + failed + bounced + unknown + skipped`
when all snapshot recipients have been materialized.

Terminal run:
no PENDING/PROCESSING/RETRY batches or recipients remain.

Provider-delivery metrics are separate from provider-acceptance accounting. A Broadcast may be execution-complete while later delivery/read events continue to arrive.

## Cancellation

Cancellation prevents new claims/sends. A provider request already in flight cannot be recalled.

Any in-flight ambiguous request follows UNKNOWN semantics rather than being rewritten as unsent.

## Failure

Run-level infrastructure/configuration failure must:
- make the run/message terminal FAILED;
- terminalize unstarted recipients safely;
- preserve already accepted/unknown outcomes;
- not manufacture delivery failures for provider-accepted recipients.
