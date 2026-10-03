# End-to-End Billing Flow

## Paid new subscription

1. Company requests an eligible plan version.
2. Backend re-checks PUBLIC/PRIVATE eligibility.
3. Resolve immutable effective PlanPrice.
4. Create an idempotent subscription/checkout intent.
5. Build invoice/quote snapshot in INR.
6. Create required Cashfree authorization/order/subscription operation.
7. Customer completes provider flow.
8. Frontend redirect remains informational only.
9. Verified webhook and/or provider reconciliation confirms payment/authorization.
10. Record PaymentAttempt and Payment truth.
11. Activate CompanySubscription and entitlements.
12. Record immutable transition/audit history.

## Paid upgrade

R1 policy:

- upgrade becomes effective immediately only after required upgrade payment is confirmed;
- deterministic proration is applied for the unused portion of the current platform service period;
- quote/invoice terms are snapshotted;
- retries use a stable idempotency identity;
- no entitlement increase occurs from frontend redirect state.

Canonical proration principle:

```
upgrade_due =
prorated target platform charge for remaining current service period
- prorated unused current platform charge
```

Use the exact service-period timestamps and immutable price snapshots. Round monetary results only according to the invoice currency's minor-unit rule.

Cross-cycle edge cases must use the same deterministic quote contract and be covered by B1 tests rather than ad-hoc UI calculations.

## Downgrade

1. Validate target-plan eligibility.
2. Create a scheduled SubscriptionChange.
3. Effective time = next platform billing-period boundary.
4. Continue current entitlements until then.
5. At boundary, switch to target plan.
6. Existing resources above a lower limit remain.
7. No automatic deletion occurs.
8. Net-new entitlement-consuming resource creation is blocked while already above the new limit.

## Managed-Service usage

1. Runtime determines `MANAGED` vs `CUSTOMER_OWNED`.
2. Only MANAGED events can enter Managed-Service financial rating.
3. Accepted/provider-confirmed billable usage is deduplicated.
4. Resolve subscription effective at occurrence time.
5. Resolve company override, otherwise plan rate.
6. Snapshot price/rule/currency/ownership into UsageEvent.
7. Managed PAYG financial period closes monthly.
8. No R1 included allowance is applied.
9. Invoice includes rated monthly managed usage.

## YEARLY platform plan

Platform fee renews yearly.

Managed-Service PAYG still uses monthly financial periods/invoices.

Do not defer managed usage billing until the yearly platform renewal.

## Renewal and dunning

1. Raise/finalize due invoice.
2. Attempt provider collection.
3. If confirmed successful, remain ACTIVE.
4. If due-date collection fails, transition ACTIVE -> PAST_DUE.
5. Grace window = 7 days.
6. Target retries: approximately day 2 and day 5 after initial failure.
7. During grace, preserve data/read/inbound state but block new Managed-Service exposure and appropriate net-new entitlement expansion.
8. If unresolved after grace, transition PAST_DUE -> SUSPENDED.
9. Confirmed later payment transitions PAST_DUE/SUSPENDED -> ACTIVE without tenant recreation.

## Cancellation

Customer default = cancel at period end.

SUPER_ADMIN may perform immediate cancellation only through an explicit audited operation containing actor and reason.

Refund/credit is a separate financial operation and is never implied by cancellation.

## Late usage

Finalized BillingPeriod and Invoice are immutable.

Legitimate late usage is carried to the next invoice as an explicit prior-period adjustment.

Never reopen or silently rewrite finalized history.

## Company deletion

Stop future collection/sending and operational access as policy requires, but preserve required financial history, legal-hold data and audit lineage.
