# Canonical Contracts

## Plan

Existing Plan row remains the immutable commercial version.

Add:

- `visibility: PUBLIC | PRIVATE | INTERNAL`.

PUBLISHED rows are immutable for material commercial terms.

## PlanCompanyAssignment

Explicit PRIVATE-plan eligibility relation.

A company must have an active assignment both to discover and to newly select a PRIVATE plan.

## CompanySubscription

Recommended persisted states:

- TRIALING
- ACTIVE
- PAST_DUE
- SUSPENDED
- CANCELLED
- EXPIRED

Add/retain:

- startedAt;
- currentPeriodStart/currentPeriodEnd;
- billing/renewal anchor;
- trialEndsAt;
- cancelAtPeriodEnd;
- cancellation effective timestamp;
- graceEndsAt;
- paymentPolicy;
- optional external contract reference for non-Cashfree grants.

Payment policy values should at minimum distinguish:

- `CASHFREE`
- `COMPED`
- `OFFLINE_CONTRACT`

Do not synthesize provider payment rows for COMPED/OFFLINE_CONTRACT.

## SubscriptionChange

Fields:

- id;
- companyId/subscriptionId;
- fromPlanId;
- toPlanId;
- type UPGRADE/DOWNGRADE/REACTIVATION;
- requestedAt/requestedBy;
- effectiveAt;
- quote/invoice reference;
- status;
- idempotencyKey;
- proration snapshot for upgrade where applicable.

Upgrade application depends on confirmed required payment.
Downgrade applies at next platform billing-period boundary.

## BillingPeriod

Financial periods are explicit and immutable after finalization.

Fields:

- companyId;
- kind: PLATFORM | MANAGED_USAGE;
- cadence;
- startAt/endAt as UTC instants using half-open `[start,end)`;
- status OPEN/CLOSING/FINALIZED;
- finalizedAt.

Managed-Service periods are monthly even when platform cadence is yearly.

## Invoice / InvoiceLineItem

Invoice stores immutable snapshots for:

- company identity;
- billing address;
- tax identifiers required by configured tax policy;
- currency;
- subtotal;
- discounts/credits/adjustments;
- taxes;
- total;
- amount due;
- service periods;
- finalization/due/payment timestamps.

R1 invoice currency exposed to customers is INR.

Line types include:

- PLATFORM_PLAN;
- MANAGED_USAGE;
- PRIOR_PERIOD_USAGE_ADJUSTMENT;
- DISCOUNT;
- CREDIT;
- MANUAL_ADJUSTMENT;
- TAX.

R1 has no included managed-usage allowance line/calculation.

## Payment / PaymentAttempt

Payment is separate from Invoice.

Each attempt is append-only.

One invoice may have multiple failed/pending attempts before a successful attempt.

Frontend redirects cannot mark an attempt successful.

## Refund / credit

Refund is provider money movement.

Credit/credit note is an accounting operation.

Cancellation does not automatically imply either.

## Service ownership

Every financial usage event must carry:

`serviceOwnership = MANAGED | CUSTOMER_OWNED`

Only MANAGED is eligible for Managed-Service PAYG.

Prefer a stable `billingSku` independent of provider naming.

## Manual admin grant audit

For COMPED/OFFLINE_CONTRACT grants persist:

- actor;
- reason;
- effective period;
- paymentPolicy;
- optional contract/reference.

This audit record is immutable.
