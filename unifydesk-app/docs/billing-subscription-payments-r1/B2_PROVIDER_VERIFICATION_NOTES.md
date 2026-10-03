# B2 Provider Verification Notes

B2 keeps Cashfree Payment Gateway and Subscription API versions independently configurable.

Current source defaults after the FINAL LAST remediation:

- Payments API: `2025-01-01`
- Subscriptions API: `2025-01-01`

These defaults match the current public Cashfree Dev Studio examples reviewed on 2026-10-03. Merchant-enabled sandbox execution is still required before production enablement.

## Official Cashfree material reviewed

- Payment Gateway checkout / Create Order:
  https://www.cashfree.com/devstudio/preview/pg/web/checkout
- Subscription seamless API flow:
  https://www.cashfree.com/devstudio/preview/subs/seamless
- Subscription hosted checkout / Fetch Subscription:
  https://www.cashfree.com/devstudio/preview/subs/web/checkout
- Payment webhook signature verification:
  https://www.cashfree.com/devstudio/preview/pg/tools/webhookVerification
- Cashfree developer guidance confirming current PG examples use `x-api-version: 2025-01-01` and server-side verification:
  https://www.cashfree.com/blog/building-cashfree-agent-skills-a-task-aware-knowledge-layer-for-ai-coding-assistants/

## Source contracts aligned to those docs

### Create Order

- `POST /pg/orders`
- Payments API version: `2025-01-01`
- exact canonical invoice amount/currency supplied by UnifyDesk;
- `customer_details` plus `order_meta.return_url` / `notify_url`;
- stable UnifyDesk provider order identity and deterministic idempotency key.

### Create Subscription

- `POST /pg/subscriptions`
- Subscriptions API version: `2025-01-01`
- `plan_type = ON_DEMAND`;
- INR plan;
- durable UnifyDesk provider subscription identity;
- subscription is separate from the one-time initial platform invoice payment.

### AUTH

- `POST /pg/subscriptions/pay`
- `payment_type = AUTH`
- stable `payment_id`;
- durable `subscription_session_id`;
- payment method shape is exactly:
  `{"UPI":{"channel":"link"}}`
  inside `payment_method`.

### CHARGE

- `POST /pg/subscriptions/pay`
- `payment_type = CHARGE`
- stable recurring command `payment_id`;
- `payment_amount` is the exact canonical UnifyDesk renewal invoice amount;
- `payment_schedule_date` is supplied for the claimed renewal/dunning boundary.

### Refund

- `POST /pg/orders/{order_id}/refunds`
- stable merchant `refund_id`;
- exact refund amount;
- follow-up retrieval uses
  `GET /pg/orders/{order_id}/refunds/{refund_id}`;
- refund provider execution/webhook variants remain merchant/sandbox verification items.

### Webhook signature contract

Cashfree webhook verification must use:

- the exact raw HTTP request body;
- `x-webhook-timestamp`;
- `x-webhook-signature`;
- server-side signature verification before any event is persisted as verified/processed.

UnifyDesk persists the verified raw body and hash, then claims `RECEIVED`, due `RETRY`, or expired `PROCESSING` rows with a durable claim/lease.

## Implemented source contracts

- provider-neutral `Payment` / append-only `PaymentAttempt` / `Refund`;
- durable local payment intent before Cashfree order creation;
- persisted provider order identity and checkout session for exact idempotent replay;
- ambiguous order recovery through provider fetch before any same-identity re-submit;
- durable local refund intent before provider refund creation;
- stable merchant refund identity plus Cashfree refund reference and scheduled refund reconciliation;
- raw-body Cashfree webhook signature verification;
- durable verified webhook inbox with recoverable `RECEIVED`, claimed retries/backoff, and terminal dead state;
- command-first recurring webhook correlation using `provider_command_id`;
- independently configurable Payment Gateway and Subscriptions API versions;
- initial-checkout recurring authorization bootstrap, pre-bound to the B1 checkout intent and bound to the resulting CompanySubscription only after confirmed activation;
- durable ON_DEMAND recurring authorization identity and AUTH operation;
- automatic restart-safe renewal scheduler using durable jobs and `FOR UPDATE SKIP LOCKED`;
- exact canonical-invoice CHARGE at the renewal boundary without customer Billing UI/API interaction;
- early failed renewal is picked up again at the canonical due boundary;
- explicit D0 -> D2 -> D5 -> D7 dunning progression;
- ambiguous recurring commands are reconciled without consuming the next dunning stage;
- refund reconciliation, partial-refund cap, and terminal success monotonicity;
- Managed Email and Managed SMS financial usage remains separate from platform subscription pricing;
- Managed SMS financial quantity remains the deterministic local estimate
  `MANAGED_SMS_LOCAL_SEGMENT_ESTIMATE` pending authoritative provider reconciliation.

## Provider verification still required

The repository does not expose Cashfree sandbox credentials or confirmed merchant rail enablement to this worker. Therefore the following are intentionally **not** claimed as executed/verified:

- merchant-enabled ON_DEMAND mandate authorization;
- supported recurring rail behavior for the configured merchant account;
- exact sandbox AUTH/CHARGE response variants;
- exact subscription-payment fetch behavior for the merchant-enabled API surface;
- refund webhook payload variants for the merchant account;
- cancellation/expiry and rail-specific retry constraints;
- provider-side SMS segment reconciliation;
- any Cashfree behavior that depends on merchant configuration rather than the public API contract.

The source implementation remains fail-closed around canonical invoice amount/currency/company identity, stable provider identities, and ambiguous outcomes.

Status: **PROVIDER VERIFICATION REQUIRED**
