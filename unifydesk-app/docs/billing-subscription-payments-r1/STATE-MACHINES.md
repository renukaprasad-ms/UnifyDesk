# State Machines

## CompanySubscription

Recommended R1 states:

- `TRIALING`
- `ACTIVE`
- `PAST_DUE`
- `SUSPENDED`
- `CANCELLED`
- `EXPIRED`

Do not add `CANCEL_AT_PERIOD_END` as a state; retain it as a flag plus effective timestamp.

Do not add a separate `GRACE_PERIOD` state. Use `PAST_DUE + graceEndsAt`; this preserves the failure reason while making grace a policy window.

### Transitions

| From | To | Trigger | Payment dependency | Entitlements |
|---|---|---|---|---|
| none | TRIALING | eligible trial starts | none | trial policy |
| none/TRIALING | ACTIVE | free activation or required payment confirmed | provider/server authoritative where paid | full plan |
| ACTIVE | ACTIVE | successful renewal | confirmed collection if required | full plan |
| ACTIVE | PAST_DUE | renewal collection fails/uncertain past due point | failed/unpaid invoice | MAIN-defined grace behavior |
| PAST_DUE | ACTIVE | successful recovery | confirmed collection | full plan |
| PAST_DUE | SUSPENDED | grace expires/retry policy exhausted | invoice unpaid | restricted; no destructive deletion |
| SUSPENDED | ACTIVE | recovery/reactivation | confirmed payment/policy | full plan |
| TRIALING | EXPIRED | trial ends without paid activation | no payment | restricted |
| ACTIVE/PAST_DUE/SUSPENDED | CANCELLED | explicit immediate cancellation | policy dependent | restricted |
| ACTIVE | EXPIRED | period-end cancellation/non-renewing term ends | no next renewal | restricted |
| EXPIRED/CANCELLED | ACTIVE | explicit reactivation/new subscription | eligibility + payment as required | full plan |

Every transition records actor/trigger/idempotency key/from/to/effectiveAt/provider or invoice reference.

## Invoice

Recommended states:

- DRAFT
- OPEN
- PAID
- VOID
- UNCOLLECTIBLE (optional operational/accounting terminal state)

`OVERDUE` is derived from OPEN + dueAt rather than required as a persisted state.

Transitions:

`DRAFT -> OPEN -> PAID`

`DRAFT -> VOID`

`OPEN -> VOID` only when accounting policy permits; otherwise issue a credit note.

`OPEN -> UNCOLLECTIBLE` only through explicit policy/admin action.

Finalized/open invoice amounts and line items are immutable.

## Payment

Payment aggregate:

- CREATED
- PENDING
- SUCCEEDED
- FAILED
- CANCELLED

A Payment can represent a collection intent while `PaymentAttempt` preserves retries.

PaymentAttempt states:

- CREATED
- PENDING
- SUCCEEDED
- FAILED
- USER_DROPPED
- CANCELLED
- EXPIRED

`SUCCEEDED` is terminal for an attempt. Out-of-order failure events must never overwrite a recorded provider-confirmed success.

## Refund

- REQUESTED
- PENDING
- SUCCEEDED
- FAILED
- CANCELLED where provider supports cancellation/status.

Refund retries use a stable internal refund id/idempotency key and retain provider attempts/events.
