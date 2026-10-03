# UnifyDesk Billing, Subscription & Payments R1

## Architecture freeze status

MAIN has reviewed and accepted the L1 audit/architecture direction.

- Audited production baseline: `23527be323a87643246dabf046639458d0c44788`
- Architecture PR: `#254`
- This PR remains documentation-only.
- No B1/B2/F1 implementation has started.
- Cashfree sandbox/runtime/provider execution remains **PROVIDER VERIFICATION REQUIRED**.

## Frozen R1 commercial model

UnifyDesk Billing R1 has two distinct commercial layers:

1. **Platform Subscription**
   - recurring SaaS fee;
   - buys platform entitlements/limits such as seats, Broadcast limits, connected channels, domains and feature access.

2. **Managed Services PAYG**
   - separate usage-based billing;
   - Managed Email is PAYG;
   - Managed SMS is PAYG;
   - future managed channels are PAYG only where explicitly enabled.

R1 includes **no free Managed Email/SMS allowance inside plans**.

Only usage with:

`serviceOwnership = MANAGED`

is eligible for UnifyDesk Managed-Service PAYG.

`CUSTOMER_OWNED` provider traffic must not be charged as UnifyDesk Managed-Service usage.

## Frozen R1 plan visibility

- `PUBLIC`
- `PRIVATE`
- `INTERNAL`

PRIVATE plan discovery/selection requires explicit company assignment and backend eligibility checks.
INTERNAL plans are never customer-discoverable.
Removing a private-plan assignment does not silently invalidate an already-valid subscription.

## Frozen lifecycle policy

- Paid upgrades: immediate only after required payment is provider-confirmed; deterministic proration applies.
- Downgrades: effective next platform billing-period boundary.
- Customer cancellation: cancel at period end.
- Immediate cancellation: SUPER_ADMIN-only, explicit, audited, reason required.
- Renewal collection failure: `ACTIVE -> PAST_DUE` with a 7-day grace window.
- Target retries: due-date failure, then approximately day 2 and day 5; unresolved after grace -> `SUSPENDED`.
- Managed-Service PAYG closes monthly even when the platform plan is yearly.
- R1 customer invoicing currency: INR.
- Finalized billing periods/invoices are immutable; late usage goes to the next invoice as an explicit prior-period adjustment.
- SUPER_ADMIN may grant paid/private plans outside Cashfree only through explicit audited policies such as `COMPED` or `OFFLINE_CONTRACT`.
- Financial history must support long-lived retention and legal hold; R1 must be technically capable of at least 8 years of retention.

## Cashfree boundary

UnifyDesk owns:

- subscription state;
- billing periods;
- invoice calculation/truth;
- entitlement state;
- usage rating.

Cashfree is the external authorization/collection rail.

Frontend redirects are never payment truth.
Verified provider webhook/reconciliation is authoritative.

## Required execution sequence

```
MAIN/L1 architecture freeze
-> merge PR #254
-> freeze resulting dev SHA
-> B1
-> L1 integration
-> freeze new dev SHA
-> B2
-> L1 integration
-> freeze new dev SHA
-> F1
-> L1 integration
-> integrated exact production SHA
-> SEC/QA
-> remediation if required
-> final SEC/QA
-> Cashfree/runtime/provider verification
```

Workers never branch from another worker branch or PR.
