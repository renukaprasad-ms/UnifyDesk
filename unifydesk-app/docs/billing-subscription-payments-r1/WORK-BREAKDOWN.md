# Work Breakdown and Sequential Integration Strategy

## Mandatory sequencing

B1, B2 and F1 must **not** start from one common audit SHA.

The frozen execution sequence is:

```
1. MAIN/L1 architecture freeze
2. Merge architecture PR #254 to dev
3. Freeze resulting dev SHA
4. B1 branches from exactly that SHA
5. L1 reviews/integrates B1 into dev
6. Freeze the new integrated dev SHA
7. B2 branches from exactly that integrated SHA
8. L1 reviews/integrates B2 into dev
9. Freeze the new integrated dev SHA
10. F1 branches from exactly that integrated backend SHA
11. L1 reviews/integrates F1 into dev
12. Freeze integrated production SHA
13. SEC/QA
14. remediation if required
15. final SEC/QA
16. Cashfree/runtime/provider verification
```

No worker:

- branches from moving `dev`;
- branches from another worker branch/PR;
- rebases;
- self-merges.

L1 owns every integration and every next frozen base.

## B1 — Commercial core

B1 starts first.

Own:

- Plan visibility PUBLIC/PRIVATE/INTERNAL;
- PlanCompanyAssignment and private-plan authorization;
- published-plan commercial immutability;
- CompanySubscription state machine/history;
- 7-day PAST_DUE grace policy hooks;
- SubscriptionChange;
- deterministic upgrade-proration domain/quote contract;
- next-boundary downgrade scheduling;
- cancellation scheduling and audited SUPER_ADMIN immediate cancellation contract;
- payment policy: CASHFREE / COMPED / OFFLINE_CONTRACT;
- BillingPeriod;
- managed monthly vs platform cadence separation;
- Invoice / InvoiceLineItem;
- prior-period adjustment line support;
- immutable address/tax identity snapshots;
- INR R1 invoice policy;
- financial-retention schema/lifecycle protections;
- non-provider restart-safe period/finalization/cancellation lifecycle jobs.

B1 does **not** call Cashfree.

B1 must freeze the domain/API/service contracts B2 needs.

## L1 after B1

L1 must:

- review B1 against frozen architecture;
- integrate B1 into dev;
- verify no scope leakage;
- freeze exact integrated dev SHA;
- prepare B2 prompt from that SHA.

B2 must not branch until this freeze exists.

## B2 — Payments/Cashfree + usage financial boundary

B2 branches from the exact post-B1 integrated SHA.

Own:

- MANAGED/CUSTOMER_OWNED financial ownership;
- billing SKU contract;
- Managed Email -> UsageEvent bridge;
- Managed SMS rating/reconciliation hardening;
- historical subscription/rate resolution integration with B1 contracts;
- immutable/effective CompanyRateOverride history;
- Payment;
- PaymentAttempt;
- Refund/provider references;
- Cashfree authorization/order/subscription/mandate adapter;
- exact invoice amount collection;
- signed raw-body webhook;
- durable provider-event inbox;
- reconciliation;
- collection retries around due date/day ~2/day ~5 subject to verified rail behavior;
- transition requests into B1 lifecycle services;
- recovery from PAST_DUE/SUSPENDED after confirmed payment.

B2 must not redefine Invoice, BillingPeriod, CompanySubscription or SubscriptionChange.

## L1 after B2

L1 integrates B2, freezes a new exact dev SHA, and only then releases F1.

## F1 — Billing UI

F1 branches from the exact integrated B1+B2 SHA.

Own frontend only:

Company:
- eligible PUBLIC + assigned PRIVATE plans;
- checkout/authorization;
- payment pending/confirmed display;
- scheduled downgrade/cancel-at-period-end;
- usage;
- invoices;
- payments/refunds status.

Platform:
- visibility;
- private-plan assignment;
- COMPED/OFFLINE_CONTRACT grant UX;
- audited immediate cancellation UX;
- rates/overrides;
- subscription/invoice/payment inspection.

F1 never decides payment success or entitlement activation.

## Verification order

SEC/QA is performed only after full L1 integration.

Cashfree/runtime/provider execution comes after final SEC/QA source acceptance.
