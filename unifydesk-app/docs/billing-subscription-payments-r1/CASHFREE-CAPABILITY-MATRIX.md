# Cashfree Capability Matrix

Research date: 2026-10-03. Provider behavior must be rechecked against the selected account/API version at implementation and go-live.

Official sources consulted:

- Documentation index / version inventory: https://www.cashfree.com/docs/llms.txt
- Subscriptions Dev Studio: https://www.cashfree.com/devstudio/preview/subs/seamless
- PG webhook verification Dev Studio: https://www.cashfree.com/devstudio/preview/pg/tools/webhookVerification
- Recurring payments product: https://www.cashfree.com/recurring-payment/
- Refund capability: https://www.cashfree.com/instant-refunds/
- International PG: https://www.cashfree.com/international-payment-gateway/

| Capability | Cashfree finding | UnifyDesk R1 position |
|---|---|---|
| Payment Gateway | SUPPORTED | Provider adapter, not domain model. |
| Server-side orders/payments | SUPPORTED | Use provider refs; browser return is not confirmation. |
| Subscriptions/recurring | SUPPORTED | Cashfree current Dev Studio shows `/pg/subscriptions`. |
| On-demand subscription charging | SUPPORTED | Useful for variable invoice amount / managed usage collection, subject to merchant eligibility. |
| UPI AutoPay | SUPPORTED | Provider rail; do not bake rail semantics into CompanySubscription. |
| eMandate / recurring bank/card mechanisms | SUPPORTED at product level | Exact enabled rails/limits require merchant-account verification. |
| Subscription authorization | SUPPORTED | Store provider authorization status/reference separately. |
| Fetch/reconcile subscription | SUPPORTED | Required recovery path after redirect/webhook gaps. |
| Webhooks | SUPPORTED | Durable inbox required. |
| Signature verification | SUPPORTED | Current official Dev Studio requires raw body plus `x-webhook-signature` and `x-webhook-timestamp`. |
| Webhook duplicate/retry handling | SUPPORTED conceptually | Exact retry schedule: **PROVIDER VERIFICATION REQUIRED**. Handler must be idempotent regardless. |
| Refunds | SUPPORTED | Cashfree advertises full/partial refund support; use Refund domain + provider adapter. |
| Sandbox | SUPPORTED | Dev Studio uses `sandbox.cashfree.com`. |
| International currencies | PG supports broad multi-currency/card capability | Product decision: recommend INR-only R1 until tax/invoice/rail policy is frozen. |
| API versions | Multiple current versions exist | Do not use one global hard-coded version. Explicitly configure/test PG and Subscription versions. |

## Version note

The current documentation index exposes Payments OpenAPI `v2026-01-01` as well as older versions, while the current Subscriptions Dev Studio examples use `x-api-version: 2025-01-01`. The Cashfree adapter must therefore pin each API surface to a verified version rather than assuming the newest PG version is also the subscriptions version.

## Webhook security contract

1. capture raw body before JSON parsing;
2. verify signature using current Cashfree SDK/spec and timestamp header;
3. reject invalid signatures before state mutation;
4. durable-ingest event before side effects;
5. deduplicate by provider identity where available and object-level uniqueness;
6. apply monotonic state rules;
7. return success only after durable acceptance;
8. reconcile provider object status after uncertain/out-of-order processing.

A local replay-age window may be added as UnifyDesk defense-in-depth, but its tolerance is UnifyDesk policy unless Cashfree documents a mandatory tolerance for the pinned version.

## Provider verification still required

Before production freeze:

- execute sandbox one-time payment success/failure/user-drop;
- subscription/mandate auth success/failure/pending;
- recurring/on-demand charge success/failure;
- duplicate and out-of-order webhook replay;
- webhook retry behavior and event identity headers for the pinned version;
- payment/subscription GET reconciliation;
- full/partial refund and refund webhook;
- mandate cancellation/expiry;
- enabled merchant payment rails/currencies and current India-specific limits.
