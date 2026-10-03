# Product Billing Model

## Platform subscription

Plans such as Starter, Growth, Pro and Business primarily purchase platform entitlements and SaaS limits.

Examples:

- seats/users;
- Broadcast limits;
- connected-channel limits;
- domain limits;
- feature access;
- reporting/automation/module access.

The platform subscription price is not a bundle of Managed Email/SMS provider cost in R1.

## Managed Services PAYG

Managed Services are separate usage charges.

R1 billing composition:

```
platform subscription fee
+ Managed Email PAYG
+ Managed SMS PAYG
+ explicit future managed-channel PAYG
+/- adjustments / credits
+ applicable taxes
= invoice amount
```

There is no free included Managed Email/SMS allowance in R1.

## Service ownership boundary

Canonical field:

`serviceOwnership = MANAGED | CUSTOMER_OWNED`

Only `MANAGED` is eligible for Managed-Service PAYG.

Customer-owned/direct provider credentials remain upstream-billed to the customer and must not be charged as UnifyDesk Managed-Service usage by default.

Provider name is not ownership.

The runtime's existing sender management distinction should map into this financial ownership field.

## Public, private and internal plans

- PUBLIC: normal customer catalog.
- PRIVATE: assigned explicitly to one or more companies.
- INTERNAL: support/migration/staff/development/admin-only use; never customer-discoverable.

## Private plan vs CompanyRateOverride

A PRIVATE plan changes platform price/entitlements.

A CompanyRateOverride changes negotiated Managed-Service unit pricing while the company remains on its existing platform plan.

These remain separate concepts.

## Manual commercial grants

SUPER_ADMIN may grant a paid/private plan without Cashfree only through an explicit payment policy:

- `COMPED`
- `OFFLINE_CONTRACT`

Required audit fields:

- actor;
- reason;
- effective period;
- payment policy;
- optional external contract/reference.

Such a grant must not fabricate a successful Cashfree payment.

## Currency

R1 customer invoicing policy is INR-only.

Currency remains explicit in domain contracts for correctness and future extensibility, but multi-currency product behavior is not exposed in R1.

## Tax

The architecture is GST/tax-ready with immutable invoice identity/address/tax snapshots.

No unverified GST rate, registration, place-of-supply or legal rule is hard-coded into the architecture.
