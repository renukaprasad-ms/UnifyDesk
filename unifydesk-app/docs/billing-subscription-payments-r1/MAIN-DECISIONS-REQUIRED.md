# MAIN Decisions Required

Architecture can proceed without guessing these commercial policies only after MAIN chooses them.

## D1 — Upgrade charging

Choose one default:

A. immediate prorated difference (recommended SaaS default);
B. immediate full difference;
C. next-period charge but delayed entitlement upgrade;
D. no proration with another explicit rule.

Architecture rule regardless: increased paid entitlements activate only after the required charge is provider-confirmed.

## D2 — Downgrade

Recommended default: effective next billing period.

Confirm over-limit behavior after downgrade:
- preserve all existing resources;
- block net-new creation until below limit;
- no automatic deletion.

## D3 — Cancellation

Choose customer default:
- cancel at period end (recommended);
- whether immediate cancellation is customer-accessible or admin-only;
- refund/credit behavior for immediate cancellation.

## D4 — Dunning

Define:
- retry cadence/count;
- grace duration;
- which read/write/send actions remain in PAST_DUE;
- when SUSPENDED occurs;
- manual reactivation requirements.

## D5 — Billing cadence

Confirm how PAYG invoices interact with YEARLY platform plans:
- monthly managed-usage invoice plus annual platform renewal (recommended), or
- align usage to platform renewal cadence.

## D6 — Taxes / GST

Define R1 tax engine scope:
- whether UnifyDesk initially invoices only INR/India;
- GST registration/tax calculation/invoice fields;
- B2B GSTIN handling;
- place-of-supply policy;
- credit-note requirements.

Tax/legal policy should be separately reviewed before production invoicing.

## D7 — Included usage

Decide whether any plan version has included Managed Email/SMS allowance in R1. Architecture supports it but no allowance/prices are assumed.

## D8 — INTERNAL visibility

Recommendation: include INTERNAL in the visibility enum now because it is low-cost and prevents misuse of PUBLIC/PRIVATE for migration/support/testing. Confirm.

## D9 — Late usage

Recommended: finalized period remains immutable; late accepted usage is shown as a prior-period adjustment on the next invoice. Confirm whether supplemental invoice is preferred.

## D10 — Currency

Recommendation: R1 product policy = INR only even though Cashfree PG supports broader currencies. Expand later after tax/rail policy.

## D11 — Manual admin subscription grant

Decide whether SUPER_ADMIN may intentionally grant a paid plan without provider payment (e.g. comped/internal contract). If yes, require an explicit grant reason and payment policy enum; do not reuse normal customer checkout activation.

## D12 — Financial retention

Define retention duration and legal/accounting requirements for invoices, payments, provider events, usage, refunds and company tombstones. Current operational company purge must be aligned to this policy.
