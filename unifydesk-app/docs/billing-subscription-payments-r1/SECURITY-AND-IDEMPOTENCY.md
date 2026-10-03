# Security and Idempotency

## Tenant isolation

All company-scoped billing reads/mutations must authorize company membership/permission or SUPER_ADMIN scope.

PRIVATE plan controls must be enforced in repository/service predicates and subscription command validation:

- other tenants cannot discover;
- guessed plan IDs fail;
- assignment/removal requires platform authorization;
- active subscriptions are not invalidated by silent assignment removal.

## Financial authorization

Company checkout APIs never accept authoritative price/amount/plan entitlement values from frontend. Client submits identifiers and an idempotency key; backend resolves eligible immutable commercial data.

SUPER_ADMIN manual adjustments/refunds/private-plan assignments require explicit permission and immutable actor/reason audit.

## Idempotency requirements

At minimum stable idempotency keys/unique constraints for:

- subscription creation/change/reactivation;
- checkout/order creation;
- mandate/subscription creation;
- upgrade/downgrade/cancellation;
- billing-period create/close;
- invoice generate/finalize;
- payment create;
- recurring charge;
- provider webhook ingest/effect;
- refund;
- credit/adjustment;
- usage ingestion.

The same key with a materially different payload is a conflict, not a second operation.

## External calls

Cashfree cannot join a local DB transaction. Use durable state machines/outbox-style orchestration:

1. persist local operation intent;
2. execute provider call with stable provider/idempotency identity where supported;
3. persist response/reference;
4. recover ambiguous timeouts through provider reconciliation;
5. never blindly retry a charge with a new identity.

## Webhooks

- public endpoint;
- raw-body capture;
- signature verification before parsing effects;
- secrets never logged;
- raw payload access restricted and retained only as policy permits;
- durable provider-event inbox;
- duplicate/out-of-order safe;
- verify amount/currency/company/payment ownership before applying;
- server-side provider reconciliation is authoritative;
- frontend redirect never activates paid entitlements.

## Money state

Use database numeric/decimal values; never floating-point money.

Invoice/payment/refund currencies must match their intended operation.

A success event cannot be downgraded by a later stale failure event.

## Financial retention

Company deletion keeps company tombstone/id needed for financial lineage. Purge must preserve invoices, payments, attempts, refunds, provider events needed by retention policy, subscriptions, usage events, and rate/override snapshots or immutable references.

Current deletion of `company_rate_overrides` should be remediated or replaced by fully self-contained immutable historical snapshots before deletion can be considered safe.
