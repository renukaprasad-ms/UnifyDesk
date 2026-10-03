# Usage Metering and Rating

## Financial eligibility

Every usage event must carry:

`serviceOwnership = MANAGED | CUSTOMER_OWNED`

Only MANAGED events can be rated as UnifyDesk Managed-Service PAYG.

CUSTOMER_OWNED traffic may still be operationally measured but must not become Managed-Service invoice usage.

## R1 managed usage products

- Managed Email: PAYG.
- Managed SMS: PAYG.
- Future managed channels: PAYG only after explicit product/pricing enablement.

R1 has no free included Managed Email/SMS allowance.

## Existing UsageEvent foundation

Retain atomic immutable financial events with:

- company;
- source identity;
- idempotency key;
- occurrence time;
- service ownership;
- stable billing SKU;
- channel/provider/category/direction/unit;
- quantity;
- subscription/plan snapshot;
- pricing rule snapshot;
- unit price/total/currency snapshot.

## Historical pricing

Resolve the subscription effective at `occurredAt`, not simply today's ACTIVE subscription.

Then resolve:

1. effective CompanyRateOverride;
2. otherwise effective plan RateCard;
3. otherwise fail closed/no billable price.

CompanyRateOverride history must be immutable/effective-dated once financially used.

## Managed Email

Provider-accepted Managed Email usage must bridge into canonical UsageEvent exactly once.

Keep operational reservation/reconciliation logic separate from financial invoice aggregation.

## Managed SMS

Provider-accepted Managed SMS must enter canonical UsageEvent exactly once.

R1 still requires runtime verification that billed quantity reflects authoritative segment quantity and provider duplicate/retry behavior.

## Financial periods

Managed PAYG period cadence is monthly for all companies, including YEARLY platform subscribers.

Use explicit UTC instant boundaries.

## Finalization and late usage

Once BillingPeriod/Invoice is finalized, it is immutable.

Late legitimate usage is placed on the next invoice using a line type such as:

`PRIOR_PERIOD_USAGE_ADJUSTMENT`

The adjustment must identify the original service period.

Never silently recompute the previous invoice.

## Included allowance

No included allowance is implemented for R1.

The model may remain extensible, but R1 invoice/rating code should not introduce unnecessary allowance allocation complexity.
