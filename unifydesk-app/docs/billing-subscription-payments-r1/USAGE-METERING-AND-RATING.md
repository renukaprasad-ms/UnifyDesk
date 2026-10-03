# Usage Metering and Rating

## Existing good foundation

`UsageEvent` already stores:

- company;
- plan snapshot;
- pricing-source/rule snapshot;
- source type/id;
- unique idempotency key;
- channel/provider/category/direction/unit/quantity;
- unit price/total/currency snapshots;
- occurred/created timestamps.

`UsageBillingService` uses insert-time idempotency and handles uniqueness races.

`PricingResolver` correctly prefers company override over plan rate.

## Required remediation

### Managed vs direct

Add explicit `serviceOwnership` and a stable billing SKU. Never infer managed status from provider type.

Runtime source of truth should be the connection/sender ownership already represented as `MESSAGING_MANAGED` vs `CUSTOMER_MANAGED`.

### Historical subscription

Pricing must resolve the subscription effective at `occurredAt`, not simply the current ACTIVE/TRIALING row. A late event from an old period must not use today's plan.

Introduce repository/query semantics:

```
subscription.startedAt <= occurredAt
AND (subscription.endsAt IS NULL OR occurredAt < subscription.endsAt)
```

with unambiguous non-overlap.

### Rate history

Published plan rate cards are already protected from edit. Company rate overrides need equivalent immutable/effective-dated revision behavior after they have affected billing. Never rewrite a historical negotiated rate used by financial events.

### Managed Email

Accepted Managed Email must bridge from its operational quota ledger into a canonical rated UsageEvent exactly once. Preserve the existing reservation/reconciliation mechanics; do not replace them with invoice counters.

### Managed SMS

Keep provider-accepted commit semantics, but verify that billable quantity is the authoritative segment quantity rather than only a pre-send estimate. Persist provider request/result evidence needed for audit.

### Duplicate provider events

Use business identity + provider event/payment/message identifiers. Provider duplicate events must converge on one financial usage effect.

### Late events / finalized periods

A UsageEvent may arrive late, but a FINALIZED BillingPeriod/Invoice is immutable. Canonical policy options:

- include late event in next invoice as prior-period adjustment (recommended);
- issue supplemental invoice/credit note;
- never mutate finalized historical totals.

MAIN must approve the late-usage commercial presentation.

## Aggregation

Financial truth remains atomic immutable UsageEvents. Aggregation tables/materialized summaries are derived acceleration only.

Invoice generation stores aggregation snapshots/line-item source references so the final invoice can be reproduced without using today's rates.

## Included allowances

If a plan includes managed usage, apply allowance during period rating/invoice generation:

`billable_overage = max(0, eligible_managed_quantity - included_quantity)`

Do not mark underlying UsageEvents as free/paid by destructive mutation; preserve allocation records or invoice calculation snapshots.
