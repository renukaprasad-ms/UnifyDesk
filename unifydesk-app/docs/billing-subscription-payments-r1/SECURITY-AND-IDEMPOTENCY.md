# Security and Idempotency

## Private-plan tenant isolation

Customer discovery must return only:

- eligible PUBLIC plans;
- PRIVATE plans assigned to the requesting company.

INTERNAL plans never appear.

A direct/guessed PRIVATE plan ID is re-authorized at subscription/change time and must fail if assignment is absent.

Assignment revocation prevents future selection but does not silently destroy an existing subscription.

## Financial authorization

Frontend never supplies authoritative amounts, rates, taxes or entitlement values.

Backend resolves all commercial terms from immutable catalog/snapshot state.

SUPER_ADMIN-only operations requiring explicit audit include:

- immediate cancellation;
- COMPED/OFFLINE_CONTRACT grant;
- manual financial adjustment/credit;
- refund;
- private-plan assignment changes.

## Idempotency

Stable idempotency is required for:

- subscription creation;
- SubscriptionChange;
- upgrade quote/charge/application;
- downgrade scheduling/application;
- cancellation;
- provider order/subscription/mandate creation;
- billing-period generation/close;
- invoice generation/finalization;
- payment creation/attempt;
- recurring charge;
- webhook ingest/effect;
- refund;
- credit/adjustment;
- usage ingestion.

Same key + materially different payload = conflict.

## Dunning and financial exposure

During PAST_DUE grace:

- no destructive tenant/data operations;
- block new MANAGED financial exposure;
- block appropriate net-new entitlement-consuming resources.

Confirmed recovery restores ACTIVE state without tenant recreation.

## Webhook security

- use exact raw request body;
- verify signature before financial state mutation;
- durable event inbox;
- provider-event dedupe;
- monotonic/out-of-order-safe transitions;
- amount/currency/object ownership checks;
- no secrets/payment-sensitive payloads in logs;
- reconciliation for ambiguous outcomes.

Frontend redirect is not payment truth.

## Financial retention

Normal company deletion must not purge required:

- subscriptions and transition history;
- billing periods;
- invoices/line items;
- payments/attempts;
- refunds/credits;
- provider events;
- UsageEvents and rating lineage;
- immutable plan/rate/override snapshots required for audit.

R1 must be technically capable of retaining financial history for at least 8 years and supporting legal hold.

Final legal/accounting retention duration remains production-readiness validation.
