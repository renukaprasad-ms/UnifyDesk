# End-to-End Flow

## New paid subscription

1. Company requests an eligible plan version.
2. Backend re-checks PUBLIC/PRIVATE eligibility and price effectiveness.
3. Create an idempotent subscription-change/checkout intent.
4. Create draft invoice/quote from immutable plan-price snapshot.
5. Create Cashfree order/subscription/mandate through provider adapter as required.
6. Customer authorizes payment/mandate.
7. Browser return is informational only.
8. Server verifies signed webhook and/or reconciles provider state.
9. Record immutable `PaymentAttempt`; mark payment success only from provider-confirmed state.
10. Finalize/mark invoice paid according to collection model.
11. Activate the CompanySubscription and entitlements atomically in local DB.
12. Emit auditable subscription-change event.

## Managed usage

1. Messaging runtime determines `MANAGED` vs `CUSTOMER_OWNED` from canonical connection ownership.
2. Provider-accepted billable event is deduplicated.
3. Persist immutable raw/accounting usage identity.
4. Resolve the subscription/plan effective at occurrence time.
5. Resolve CompanyRateOverride first, otherwise plan RateCard.
6. Snapshot price/rule/currency/ownership into `UsageEvent`.
7. Billing-period close includes only eligible MANAGED events in the defined period.
8. Apply included allowance if configured.
9. Produce immutable invoice line items/usage-detail references.

CUSTOMER_OWNED usage may still be operationally measured, but it must have zero managed-service financial treatment unless a future explicit fee model is introduced.

## Renewal

1. Create next financial period/invoice idempotently.
2. Collect required amount through active authorization.
3. Provider success updates PaymentAttempt/Payment.
4. Successful collection renews period and keeps subscription ACTIVE.
5. Failure moves to PAST_DUE and starts policy-driven dunning.
6. Reconciliation repairs missed/delayed webhook outcomes.

## Upgrade

Recommended technical flow: quote -> payment required -> provider-confirmed success -> apply target plan. Increased entitlements do not activate before required collection succeeds.

## Downgrade

Schedule target plan for next period. Do not delete resources. If existing resource counts exceed new limits after effective time, retain existing resources but block net-new creation according to entitlement policy.

## Cancellation

Cancel-at-period-end is a flag/scheduled action, not a pseudo-state. Immediate cancellation, if allowed, is an explicit command with separate refund/credit policy. Provider mandate cancellation and local subscription cancellation are separate operations that must reconcile.

## Company deletion

Stop future collection/sending immediately, cancel future subscription activity, but preserve required financial records. Tenant purge must not destructively remove invoice/payment/refund/usage/rating lineage required by retention policy.
