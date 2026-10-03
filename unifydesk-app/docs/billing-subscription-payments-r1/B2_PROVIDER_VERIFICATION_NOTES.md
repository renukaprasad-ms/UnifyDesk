# B2 Provider Verification Notes

B2 keeps Cashfree Payment Gateway and Subscription API versions independently configurable.
Both source defaults are currently pinned to `2026-01-01`, but production behavior must be
validated against the merchant-enabled Cashfree sandbox before enablement.

## Implemented source contracts

- provider-neutral `Payment` / append-only `PaymentAttempt` / `Refund`;
- durable local payment intent before Cashfree order creation;
- persisted provider order identity and checkout session for exact idempotent replay;
- ambiguous order recovery through provider fetch before any same-identity re-submit;
- durable local refund intent before provider refund creation;
- stable merchant refund identity plus Cashfree refund reference and scheduled refund reconciliation;
- raw-body Cashfree webhook signature verification;
- durable verified webhook inbox with payload hash, retry lease/claim, bounded backoff, and terminal dead state;
- webhook correlation for ordinary payments, refunds, recurring authorization, and recurring charge events;
- independently pinned Cashfree Payment Gateway and Subscriptions API version settings;
- durable ON_DEMAND recurring authorization identity and AUTH operation;
- exact canonical-invoice CHARGE operation through `POST /pg/subscriptions/pay`;
- append-only recurring retry `PaymentAttempt` identities and ambiguous-attempt reconciliation;
- restart-safe/multi-instance-safe dunning claims with retry schedule anchored to the canonical renewal boundary;
- early renewal failure does not mark an active subscription past due;
- provider-confirmed early renewal does not advance the subscription before the canonical next service-period boundary;
- server-side-only payment confirmation and B1-owned subscription lifecycle activation/renewal boundary;
- full/partial refund over-refund protection and terminal success monotonicity;
- Managed Email and Managed SMS financial usage remains separate from platform subscription pricing;
- Managed SMS financial quantity is explicitly recorded as a deterministic local GSM/UCS segmentation estimate
  (`MANAGED_SMS_LOCAL_SEGMENT_ESTIMATE`) with provider request identity retained for later authoritative reconciliation.

## Provider verification still required

The repository does not expose Cashfree sandbox credentials or confirmed merchant rail enablement to this worker.
Therefore the following are intentionally **not** claimed as executed/verified:

- actual merchant-enabled ON_DEMAND subscription authorization;
- supported recurring rail behavior for the configured merchant account;
- exact sandbox AUTH/CHARGE response and webhook payload variants;
- refund webhook payload variants for the merchant account;
- cancellation/expiry and rail-specific retry constraints;
- any Cashfree behavior that depends on merchant configuration rather than the public API contract.

The source implementation remains fail-closed around canonical invoice amount/currency/company identity,
stable provider identities, and ambiguous outcomes.

Status: **PROVIDER VERIFICATION REQUIRED**
