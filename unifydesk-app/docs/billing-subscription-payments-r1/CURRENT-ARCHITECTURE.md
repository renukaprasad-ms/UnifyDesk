# Current Architecture

Audited at `23527be323a87643246dabf046639458d0c44788`.

## Existing billing domain

Current backend package: `backend/src/main/java/com/messaging/billing/`.

Existing domain objects:

- `Plan` — unique `(code, version)`; statuses DRAFT/PUBLISHED/RETIRED.
- `PlanPrice` — plan-scoped amount/currency/billing cycle with effective dates.
- `PlanEntitlement` + `EntitlementDefinition`.
- `CompanySubscription` — company, plan, optional price, status, period/trial/cancel timestamps.
- `RateCard` — plan/provider/category/direction/unit pricing with effective dates.
- `CompanyRateOverride` — company negotiated provider/category pricing.
- `UsageEvent` — rated financial usage snapshot with global idempotency key and rule/price snapshots.

Existing services:

- `BillingCatalogService`
- `DefaultPlanAssignmentService`
- `EntitlementService`
- `PricingResolver`
- `UsageBillingService`

Existing API surfaces:

- company plan catalog/current subscription/free-plan selection/cancellation;
- SUPER_ADMIN plan/prices/entitlements/rate-card/company-rate-override/subscription operations.

Existing frontend surfaces:

- workspace plan selection;
- company plan page;
- platform plans/details;
- platform billing/subscription/rate-override operations.

## What really executes today

### Plan selection

`GET /api/v1/plans` returns every PUBLISHED plan. The customer subscription endpoint only allows a zero-priced published plan; paid selection is explicitly deferred to future checkout/payment logic.

SUPER_ADMIN can directly assign a subscription, including ACTIVE, without payment confirmation.

### Default plan

New companies receive the published zero-price FREE plan through `DefaultPlanAssignmentService`.

### Entitlements

`EntitlementService` resolves the current ACTIVE subscription, then TRIALING, and reads the current plan's `PlanEntitlement`. Missing values fall back to definition defaults.

Observed backend enforcement includes:

- workspace users through `CompanyInvitationService`;
- contacts, custom fields and contact lists through `ContactQuotaGuard`;
- segments through `SegmentService`;
- generic channel connections/sender identities through `ChannelEntitlementService`/`ChannelConnectionService`;
- storage through `StorageQuotaService`;
- Managed Email enablement/domains/senders and monthly quota controls;
- Managed SMS monthly segment quota.

Observed gaps include no entitlement enforcement in the inspected core Broadcast create/send path for several catalog broadcast keys, and no enforcement in `EmailTemplateService` for `EMAIL_TEMPLATE_LIMIT`.

### Pricing

`PricingResolver` precedence is:

1. active matching `CompanyRateOverride`;
2. active matching `RateCard` for the currently effective subscription plan;
3. error/not billable because no price is configured.

Country/carrier-specific matches beat less-specific rows.

### Usage

`UsageBillingService.record` is idempotent by a unique `UsageEvent.idempotencyKey` and snapshots plan/rule/unit price/total/currency at ingestion.

Managed SMS commits provider-accepted usage into `UsageBillingService`.

Managed Email maintains its own reservation/accepted monthly quota ledger but does not currently create generic rated `UsageEvent` financial entries.

### Deletion

Company deletion cancels current subscriptions and intentionally preserves historical subscriptions/usage. The purge path currently deletes `company_rate_overrides`, so negotiated-pricing history is not fully retained.

## Major missing runtime

No application source was found for:

- Invoice / InvoiceLineItem;
- BillingPeriod financial close/finalization;
- Payment / PaymentAttempt;
- Cashfree adapter;
- mandate/subscription-provider reference;
- payment/refund webhooks;
- refund/credit-note financial lifecycle;
- recurring collection;
- payment reconciliation;
- dunning/grace/suspension jobs.

