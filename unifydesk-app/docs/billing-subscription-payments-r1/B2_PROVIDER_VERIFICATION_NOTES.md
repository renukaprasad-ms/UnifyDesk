# B2 Provider Verification Notes

B2 source implementation pins Cashfree Payments/Subscriptions API version `2025-01-01`.

## Implemented source contracts

- provider-neutral Payment / PaymentAttempt / Refund;
- one-time Cashfree PG order creation and payment fetch/reconciliation;
- Cashfree webhook raw-body signature verification;
- durable webhook inbox and payment reconciliation;
- Cashfree on-demand subscription creation/fetch abstraction;
- full/partial refund execution;
- restart-safe R1 dunning schedule;
- server-side-only payment confirmation boundary.

## Provider verification still required

The repository has no Cashfree sandbox credentials or merchant rail enablement available to this worker.
Cashfree documents that ON_DEMAND subscriptions require the merchant to raise recurring charges and that
`POST /pg/subscriptions/pay` supports AUTH/CHARGE. The merchant-enabled rail, exact CHARGE request/response
behavior, mandate authorization, retry constraints, cancellation/expiry, and webhook identities must be
executed in Cashfree sandbox before production enablement.

Until that execution is recorded, recurring provider execution is intentionally not asserted as verified.
The dunning worker reconciles stored provider identities and never invents a second charge after an
ambiguous outcome.

Status: PROVIDER VERIFICATION REQUIRED
