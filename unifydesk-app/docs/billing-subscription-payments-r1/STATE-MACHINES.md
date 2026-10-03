# State Machines

## CompanySubscription

Frozen R1 states:

- `TRIALING`
- `ACTIVE`
- `PAST_DUE`
- `SUSPENDED`
- `CANCELLED`
- `EXPIRED`

`cancelAtPeriodEnd` remains scheduling metadata, not a state.

Grace is represented as:

`PAST_DUE + graceEndsAt`

with a frozen R1 grace window of 7 days.

### Core transitions

| From | To | Trigger | Entitlement/payment behavior |
|---|---|---|---|
| none | TRIALING | eligible trial | trial policy |
| none/TRIALING | ACTIVE | free activation, valid admin grant or confirmed required payment | full target entitlement |
| ACTIVE | ACTIVE | successful renewal | preserve entitlement |
| ACTIVE | ACTIVE | paid upgrade confirmed | apply target plan immediately after successful required payment |
| ACTIVE | ACTIVE | scheduled downgrade boundary | switch to lower plan; preserve existing over-limit resources |
| ACTIVE | PAST_DUE | renewal/collection due-date failure | start 7-day grace |
| PAST_DUE | ACTIVE | confirmed recovery | restore normal financial/resource expansion behavior |
| PAST_DUE | SUSPENDED | unresolved after grace | restrict according to suspension policy; keep tenant/data |
| SUSPENDED | ACTIVE | confirmed recovery/reactivation | no tenant recreation |
| ACTIVE | EXPIRED | cancel-at-period-end reaches boundary | stop entitlement according to expiry policy |
| ACTIVE/PAST_DUE/SUSPENDED | CANCELLED | explicit SUPER_ADMIN immediate cancellation | audited; refund/credit separate |
| TRIALING | EXPIRED | trial ends without activation | expire |

### PAST_DUE grace behavior

During the 7-day grace:

- preserve data;
- preserve read/inbound visibility;
- do not destructively remove configuration;
- block new Managed-Service financial exposure;
- block net-new entitlement-consuming resource expansion where appropriate.

Target collection schedule:

- initial due-date attempt;
- retry approximately day 2;
- retry approximately day 5;
- unresolved after grace -> SUSPENDED.

Exact retry mechanics must respect the verified pinned Cashfree rail contract.

## Invoice

States:

- DRAFT
- OPEN
- PAID
- VOID
- UNCOLLECTIBLE where explicitly used.

Finalized/open amount and line snapshots are immutable.

Late usage never reopens a finalized invoice.

## Payment

Aggregate:

- CREATED
- PENDING
- SUCCEEDED
- FAILED
- CANCELLED

Attempt:

- CREATED
- PENDING
- SUCCEEDED
- FAILED
- USER_DROPPED
- CANCELLED
- EXPIRED

A stale/out-of-order failure cannot overwrite confirmed success.

## Refund

- REQUESTED
- PENDING
- SUCCEEDED
- FAILED
- CANCELLED where supported.

Refund lifecycle is independent of cancellation lifecycle.
