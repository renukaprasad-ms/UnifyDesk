# Razorpay direct recurring billing — R1 cutover runbook

Status: **integration PR / NOT CLEARED FOR LIVE RECURRING DEBITS**.
The application keeps its own commercial `CompanySubscription` model; it does **not** create a Razorpay Subscription or Razorpay Plan.
One-time purchases use Razorpay Orders + Checkout; recurring charges use a Razorpay customer UPI token after confirmed mandate registration.

## Feature gates and provider credentials

- `APP_BILLING_RAZORPAY_ENABLED=true` (one-time Orders/Checkout).
- `APP_BILLING_RAZORPAY_RECURRING_ENABLED=false` by default; leave **false** until all release checks below pass.
- `APP_BILLING_RAZORPAY_KEY_ID`, `APP_BILLING_RAZORPAY_KEY_SECRET`, `APP_BILLING_RAZORPAY_WEBHOOK_SECRET`: backend only.
- `VITE_RAZORPAY_KEY_ID`: frontend public key only.
- Webhook: `POST /api/v1/webhooks/razorpay`, signed using the *raw* request body.
- Events: `payment.captured`, `payment.failed`, `order.paid`; enable token lifecycle webhooks if supported (`token.confirmed`, `token.paused`, `token.cancelled`, `token.rejected`).
- Renewal scheduler: 02:00 Asia/Kolkata, gated by `recurring-enabled`.

**Razorpay merchant prerequisites**: written confirmation that direct merchant-initiated UPI recurring API is enabled for the account and specific commercial purposes. Confirm mandate limits, `as_presented` cadence, cancellation, required pre-debit notices and channel restrictions with Razorpay. A working one-time Checkout is **not** proof of direct recurring eligibility.

**DO NOT enable production recurring debits** until pre-debit notification orchestration and purpose-specific consent have been verified with Razorpay. Plan renewals and variable wallet top-ups have different commercial triggers; do not assume a single mandate is permitted for both.

## Money-safety invariants

1. A mandate becomes ACTIVE only after validating checkout HMAC, *that* authorization order, the matching captured ₹1 UPI payment, and the corresponding **confirmed** provider token.
2. Every wallet top-up has a company-owned `wallet_topup_intents` row with exact INR amount and one provider order. The browser can never specify the amount credited.
3. A captured payment is verified from Razorpay with matching order, currency and amount before wallet ledger or invoice confirmation. The wallet row and its ledger/intent updates are atomic and serialized per company.
4. Every recurring debit command has one `razorpay_recurring_charge_intents` row created **before** provider I/O. If order creation, payment submission, or read times out, the command is ambiguous and cannot silently retry as another charge. A missing provider order reference requires investigation.
5. Webhooks are signature-verified and durably recorded; failures remain retriable. Reconciliation must be safe to repeat.
6. Cancelling a mandate invokes Razorpay token revocation (and does not report success if revocation fails). The ability to cancel remains usable even if new recurring charges are disabled.
7. Immutable Flyway history and old payment/audit records remain intact; never delete customer financial records during migration.

## Required test-mode acceptance cases

- One-time checkout: success; browser closes; malformed signature; webhook arrives before UI confirmation; duplicate webhook; delayed event.
- Mandate: customer already exists; authorization creation fails; wrong order, customer, signature, payment or amount; authorized but *not captured*; token not confirmed; token cancelled/paused; expiry.
- Wallet: wrong company/order; client amount mismatch; duplicate click/webhook; webhook missing; paid order with zero/multiple captured payments; provider 500/timeout; post-crash reconciliation.
- Recurring: same command replay; crash before/after remote order; timeout after debit submission; authorized-only response; wrong captured amount; duplicate captured provider payment; user disables autopay while debit is pending.
- Renewal: monthly/yearly boundary; 2 AM IST; active vs past-due/cancelled; plan downgrade at period boundary; retries and grace period; no charge without token and opt-in.
- DB: migrate clean database, adopted legacy schema, and upgrade from older versions; unique indexes and row locks; zero negative/duplicate wallet credits.
- Frontend: valid Razorpay order/customer required for mandate; no `sub_` or fake token fallback; error/cancel messaging.

## Operations

- `wallet_topup_intents.status='CREATE_PENDING'`: provider order creation may be ambiguous; investigate receipt and provider dashboard rather than creating another order.
- `razorpay_recurring_charge_intents.provider_order_id is null`: provider order result was not bound; investigate merchant dashboard before deciding to retry with a **new** commercial command.
- `wallet_topup_intents.status='CREATED'`: pending verified payment; scheduled wallet reconciliation checks provider order.
- Reconciliation tasks and signed webhook retries use persisted state; alert on sustained age/queue growth and provider/API failures.
- If Razorpay is unavailable, **fail closed**, show payment pending, and do not grant wallet credit/paid entitlements.

## Deployment

1. Apply migrations V132–V133 using Flyway from a backup-tested current DB (do not edit previously deployed migration history).
2. Deploy backend/frontend while recurring gate is false. Validate one-time checkout/webhooks and manual top-up in Razorpay test mode.
3. Run `billing-payment-regression`, database migration CI, backend tests, and frontend typecheck/build.
4. Obtain merchant enablement + validate purpose-specific consent and pre-debit notification timing; run end-to-end UPI test mandates and actual cancellation on Razorpay sandbox.
5. Roll out recurring to internal test tenants only, monitor every debit and ledger. Enable broader use only after approval.

**Do not merge or release this work as production-grade until the checklist is green.**
