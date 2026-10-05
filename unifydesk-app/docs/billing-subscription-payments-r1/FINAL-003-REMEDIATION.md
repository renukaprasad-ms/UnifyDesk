# Billing R1 FINAL-003 Remediation

## Scope

This remediation addresses only `BILL-R1-QA-FINAL-003` on frozen production base
`b96793f15d4387f7873abd6f8b9326d42a421e85`.

Previously accepted Billing R1 contracts remain unchanged.

## Root cause

`SubscriptionChangeService.scheduleDowngrade(...)` scoped idempotency by company and idempotency
key, but a different idempotency key could create another authoritative
`DOWNGRADE + SCHEDULED` row for the same subscription and effective billing boundary.

The boundary resolver correctly failed closed when multiple exact-boundary rows were visible.
However, that defensive conflict could terminalize the automatic renewal job while the subscription
row remained `ACTIVE`. The entitlement read path previously treated `ACTIVE` alone as sufficient
and did not bound a paid plan by its paid service-period end.

## Frozen product rule

The first successfully committed `SCHEDULED DOWNGRADE` owns its
`subscription_id + effective_at` boundary.

A later request for that same subscription/boundary with a different idempotency key is rejected,
whether it targets the same plan or another plan.

The accepted row is not overwritten, superseded, cancelled, or mutated. An exact retry through the
original idempotency key keeps the existing idempotent response behavior.

## Database invariant and migration

Migration
`V95__billing_authoritative_scheduled_downgrade.sql` adds the PostgreSQL partial unique index:

`uk_subscription_changes_scheduled_downgrade_boundary`

over:

`(subscription_id, effective_at)`

only where:

`change_type = 'DOWNGRADE' AND status = 'SCHEDULED'`.

This is the authoritative cross-replica guarantee.

Before creating the index, the migration explicitly checks for legacy duplicate authoritative rows.
If any exist, migration fails with a clear uniqueness error. It does not select a winner, delete
history, or rewrite commercial state. Reconciliation must therefore be intentional and auditable.

## Transaction and concurrency behavior

Downgrade scheduling preserves the existing ordering:

1. company lock;
2. company-scoped idempotency-key replay check;
3. current subscription and canonical commercial validation;
4. effective-boundary resolution;
5. plan/price eligibility and downgrade-direction validation;
6. same-boundary authoritative downgrade check;
7. insert/flush under the database uniqueness invariant.

The service-level check provides a deterministic normal conflict before persistence. The database
partial unique index remains authoritative for a true concurrent race. A persistence uniqueness
failure is translated to the same commercial `ConflictException`; raw SQL integrity errors are not
exposed through the public service contract.

The boundary resolver's existing multiple-row defensive conflict is intentionally retained for
impossible/corrupt historical states.

## Entitlement hardening

Entitlement lookup remains read-only and does not mutate lifecycle state.

For an `ACTIVE` paid subscription, the current paid service period is interpreted with the existing
half-open `[start,end)` semantics:

- `now < currentPeriodEnd`: paid entitlement remains effective;
- `now == currentPeriodEnd`: the paid period is expired;
- `now > currentPeriodEnd`: the paid period is expired;
- missing paid `currentPeriodEnd`: fail closed.

An expired `ACTIVE` paid row does not fall through to an older trial or grace record.

Legitimate FREE/open-ended subscriptions remain effective without a paid-period end. Existing
explicit `PAST_DUE` grace behavior is preserved.

## Renewal convergence

With the database invariant, normal customer usage can expose at most one authoritative scheduled
downgrade at a subscription boundary. Existing renewal preparation and lifecycle tests continue to
exercise the canonical path in which that single downgrade owns the next immutable PLATFORM period
and is applied after the accepted renewal/zero-due boundary flow.

No new invoice, provider, payment, recurring-charge, or renewal financial ownership is introduced.
Existing defensive commercial-conflict and provider-I/O guards remain unchanged.

## Regression coverage

Focused coverage includes:

- same target with different idempotency keys: later request conflicts;
- different targets with different idempotency keys: later request conflicts and winner is unchanged;
- exact downgrade idempotent retry: existing result is returned;
- service translation of a persistence uniqueness race to commercial conflict;
- real PostgreSQL concurrent inserts proving exactly one authoritative row can commit;
- migration refusal when legacy authoritative duplicates exist;
- exact boundary resolver still resolves one row and fails closed for impossible duplicates;
- existing renewal preparation/application tests preserve successful single-downgrade convergence;
- expired `ACTIVE` paid subscription denies entitlements;
- valid in-period paid subscription remains entitled;
- equality at `currentPeriodEnd` is end-exclusive;
- open-ended FREE subscription remains entitled.

## Remaining runtime/provider verification

No Cashfree provider contract is changed by FINAL-003.

The existing Billing R1 provider verification dependency remains: merchant-enabled Cashfree
authorization/CHARGE, duplicate/out-of-order events, reconciliation, refunds, mandate lifecycle,
signed webhook timestamp behavior, plus Managed Email/SES and MSG91 runtime verification.
