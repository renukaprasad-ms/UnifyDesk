# Work Breakdown

No worker should start until MAIN approves these contracts and L1 freezes the implementation base.

All workers must branch from the same exact approved SHA, never moving `dev`, never another worker, no rebase, no self-merge.

## B1 — Billing commercial core

Own only backend/domain/migrations/tests for:

- plan visibility and `PlanCompanyAssignment`;
- published-plan immutability;
- company-visible catalog eligibility;
- subscription state machine/history;
- `SubscriptionChange` scheduling contract;
- BillingPeriod;
- Invoice / InvoiceLineItem / credit-adjustment accounting core;
- historical subscription lookup used by billing;
- backend entitlement lifecycle semantics including PAST_DUE/SUSPENDED policy hooks;
- restart-safe non-provider period/finalization/cancel-at-period-end jobs.

B1 does **not** implement Cashfree HTTP/webhooks or frontend.

## B2 — Usage financial boundary + Payments/Cashfree

Own only backend/provider/runtime/migrations/tests for:

- explicit MANAGED/CUSTOMER_OWNED financial ownership + billing SKU;
- Managed Email -> rated UsageEvent bridge;
- Managed SMS financial truth/reconciliation hardening;
- immutable/effective CompanyRateOverride revisions;
- Payment / PaymentAttempt / Refund / provider-reference domain;
- Cashfree adapter with pinned API versions;
- checkout/subscription/mandate/charge operations;
- signed raw-body webhook endpoint + durable provider-event inbox;
- payment/refund reconciliation;
- recurring collection/dunning retry orchestration that invokes B1 state-machine contracts;
- provider sandbox contract/integration tests where environment supports them.

B2 does not own plan visibility/catalog UI or invoice calculation rules beyond calling B1 contracts.

## F1 — Billing UI

Own frontend only:

Company:
- eligible PUBLIC + assigned PRIVATE plan picker;
- checkout/authorization pending/confirmed UX;
- current subscription + scheduled change/cancel controls;
- managed usage summary;
- invoices and payments/refunds status views.

Platform:
- PUBLIC/PRIVATE/INTERNAL visibility controls;
- private plan company assignment;
- immutable published-plan UX;
- rate cards/company overrides;
- subscription/payment/invoice inspection.

F1 must preserve company/workspace epoch isolation and never make frontend state authoritative for payment success or entitlement activation.

## L1 integration order

Recommended integration dependency:

1. B1 domain/contracts.
2. B2 against frozen B1 interfaces.
3. F1 against frozen APIs.
4. L1 integration tests.
5. independent SEC/QA.

If parallel worker branching is required, interfaces/DTOs/migrations must be frozen in this architecture round so ownership remains non-overlapping.
