# MAIN Decisions — R1 Freeze

The primary Billing R1 product decisions are now resolved and frozen.

## Frozen decisions

### Commercial model

- Platform subscription and Managed Services are separate.
- Managed Email and Managed SMS are PAYG.
- No included Managed Email/SMS allowance in R1.
- Only MANAGED ownership is Managed-Service billable.
- CUSTOMER_OWNED traffic is not Managed-Service PAYG.

### Plan visibility

- PUBLIC
- PRIVATE
- INTERNAL

PRIVATE requires explicit company assignment and backend enforcement.
INTERNAL is never customer-discoverable.

### Upgrade

- Immediate after any required payment is provider-confirmed.
- Deterministic unused-period proration.
- No frontend-redirect activation.

### Downgrade

- Effective next platform billing-period boundary.
- Preserve existing over-limit resources.
- Prevent net-new entitlement expansion while over limit.
- No automatic deletion.

### Cancellation

- Customer default: cancel at period end.
- Immediate cancellation: SUPER_ADMIN-only, explicit/audited, reason required.
- Refund/credit separate.

### Dunning

- Due-date failure -> PAST_DUE.
- 7-day grace.
- Target retries around day 2 and day 5.
- Unresolved after grace -> SUSPENDED.
- Recovery -> ACTIVE without tenant recreation.
- Grace preserves data/read/inbound visibility and blocks new managed financial exposure/net-new entitlement expansion as appropriate.

### Cadence

- Managed-Service PAYG closes monthly.
- YEARLY platform fee still renews yearly.

### Currency

- R1 customer billing = INR.

### Late usage

- Finalized periods/invoices immutable.
- Late legitimate usage appears next invoice as explicit prior-period adjustment.

### Manual grants

- SUPER_ADMIN may use COMPED or OFFLINE_CONTRACT.
- Actor/reason/effective period/payment policy required.
- Optional contract/reference.
- Never fabricate Cashfree success.

### Cashfree

UnifyDesk owns commercial/subscription/invoice/usage truth.
Cashfree is collection/authorization rail.
Prefer recurring authorization/on-demand exact invoice collection where merchant/rail supports it.
Provider-confirmed server state is authoritative.

### Financial retention

R1 must technically support at least 8-year financial retention capability and legal hold.

## Remaining production-readiness validations

These are not open product-model choices for worker implementation:

1. Confirm actual GST registration, tax rates, place-of-supply and invoice/credit-note legal requirements before production launch.
2. Confirm final legal/accounting retention period; implementation must already support >=8 years.
3. Execute Cashfree sandbox/provider verification for the pinned API versions and merchant-enabled rails.
4. Validate exact provider retry/mandate behavior and adapt scheduler execution without changing the frozen UnifyDesk state-machine policy.
5. Execute runtime verification of Managed Email/SMS usage and payment flows after final SEC/QA.

Any change to the frozen product decisions requires explicit MAIN re-open/revision.
