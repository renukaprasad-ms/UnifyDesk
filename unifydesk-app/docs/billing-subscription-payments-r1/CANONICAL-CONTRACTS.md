# Canonical Contracts

## Core catalog

### Plan

Existing `Plan` remains the version entity.

Add/standardize:

- `visibility: PUBLIC | PRIVATE | INTERNAL`;
- published immutability for price, entitlements, rate cards and material commercial metadata.

### PlanCompanyAssignment

Explicit plan-version eligibility relation for PRIVATE plans.

### PlanPrice

Existing effective-dated row retained. Subscription must bind a specific effective `planPriceId` for paid plans.

### PlanEntitlement

Published rows immutable. Optional future allowance definitions should be separate from generic UI/feature entitlements unless semantics are precise.

## Subscription

Extend `CompanySubscription` with at least:

- explicit renewal/billing anchor;
- `graceEndsAt` where policy uses grace;
- provider authorization/subscription reference via separate provider-reference entity, not secret-bearing free text;
- pending scheduled plan change via separate `SubscriptionChange`;
- immutable transition/audit history.

Recommended `SubscriptionChange`:

- id, company/subscription, fromPlanId, toPlanId;
- type UPGRADE/DOWNGRADE/REACTIVATION;
- requestedAt/requestedBy;
- effectiveAt;
- quote/invoice reference;
- status REQUESTED/AWAITING_PAYMENT/SCHEDULED/APPLIED/CANCELLED/FAILED;
- idempotencyKey.

## BillingPeriod

Create durable financial periods:

- id, companyId;
- startAt/endAt as UTC instants using half-open `[start,end)`;
- kind/cadence;
- status OPEN/CLOSING/FINALIZED;
- finalizedAt;
- idempotency key/unique company+kind+start.

Finalized periods are immutable. Late usage goes to an explicit adjustment/next-period policy; it never silently rewrites a finalized invoice.

## Invoice

`Invoice`:

- company/subscription/billingPeriod;
- invoice number;
- currency;
- status;
- subtotal/discount/credit/tax/total/amountDue snapshots;
- issued/finalized/due/paid/void timestamps;
- immutable billing-address/tax identity snapshot as needed.

`InvoiceLineItem`:

- type PLATFORM_PLAN, MANAGED_USAGE, DISCOUNT, CREDIT, ADJUSTMENT, TAX (tax model can be extended);
- description snapshot;
- quantity/unit/unitPrice/amount/currency;
- source refs (plan price, usage aggregation/rating snapshot);
- service period start/end.

Finalized lines are never recomputed using current catalog data.

## Payment

Separate invoice from collection.

`Payment` = collection aggregate tied to invoice/amount/currency/provider.

`PaymentAttempt` = each provider attempt, append-only:

- attempt number;
- provider refs/order/payment id;
- status;
- requested/confirmed amounts;
- error code/category snapshot;
- timestamps.

A failed attempt is retained when a later attempt succeeds.

## Provider events

`PaymentProviderEvent` is a durable inbox:

- provider;
- provider event identity when supplied;
- event type;
- raw payload hash and protected raw payload/selected payload fields per retention policy;
- signature verification result;
- receivedAt/providerCreatedAt;
- processing state/attempt count/last error;
- linked payment/subscription/refund refs.

## Refund / credit

Provider refund and accounting credit are separate concepts.

`Refund` references a successful payment and has provider lifecycle/attempt identity.

`CreditNote` or immutable invoice credit/adjustment captures the accounting reason. A refund must not mutate the original successful PaymentAttempt.

## Usage ownership

Add an explicit financial dimension:

`serviceOwnership = MANAGED | CUSTOMER_OWNED`

and preferably a stable `billingSku` such as `MANAGED_EMAIL_OUTBOUND` / `MANAGED_SMS_SEGMENT`.

Rate resolution must fail closed for an unknown managed SKU; CUSTOMER_OWNED is excluded from managed-service rating by policy.
