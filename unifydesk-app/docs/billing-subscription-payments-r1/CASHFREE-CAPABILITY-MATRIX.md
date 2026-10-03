# Cashfree Capability Matrix

Research basis remains the official Cashfree material recorded during the L1 audit. Provider execution is still **PROVIDER VERIFICATION REQUIRED**.

## Frozen UnifyDesk / Cashfree responsibility boundary

### UnifyDesk is source of truth for

- commercial Plan / PlanPrice / entitlements;
- CompanySubscription state;
- subscription changes;
- billing periods;
- usage rating;
- invoices and invoice line items;
- credits/adjustments/tax snapshots;
- dunning policy;
- entitlement state.

### Cashfree is responsible for

- external customer payment authorization;
- supported mandate/subscription rails;
- collection attempts;
- provider payment/refund state;
- signed webhook events;
- provider-side reconciliation APIs.

Cashfree's own plan constructs must not become the source of truth for UnifyDesk plans.

## Preferred collection model

Where the merchant account and payment rail support it, prefer recurring authorization/on-demand subscription charging so UnifyDesk can request the exact variable invoice amount:

```
platform fee
+ monthly Managed PAYG
+ prior-period adjustments
+/- credits
+ applicable taxes
```

The final amount originates from the UnifyDesk finalized invoice snapshot.

## Customer/browser flow

Frontend redirect is never authoritative payment confirmation.

Only:

- verified provider webhook; and/or
- explicit server-side reconciliation

may confirm payment/authorization state.

## R1 currency

UnifyDesk customer-facing billing is INR-only in R1 even if Cashfree exposes additional currency capabilities.

## Provider verification required before production

Execute and record:

- one-time payment success/failure/user-drop;
- subscription/mandate authorization success/failure/pending;
- recurring/on-demand exact-amount charge;
- duplicate webhook delivery;
- out-of-order webhook delivery;
- webhook signature validation on exact raw body;
- provider event identity/retry behavior;
- payment/subscription GET reconciliation;
- full and partial refund;
- mandate cancellation/expiry;
- actual merchant-enabled recurring rails;
- current rail-specific collection/retry constraints.

Until those are executed, provider behavior is not PASS.
