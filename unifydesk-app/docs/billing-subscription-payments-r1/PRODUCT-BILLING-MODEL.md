# Product Billing Model

## 1. Platform subscription

A recurring plan price buys SaaS access and entitlements. Examples of entitlement dimensions include seats, Broadcast limits, connected channels, domains, feature gates, reporting and automation.

The plan fee does not imply bundled Managed Email/SMS usage unless an explicit included-usage allowance is configured.

## 2. Managed services PAYG

Financial model:

```
platform subscription fee
+ Managed Email billable usage
+ Managed SMS billable usage
+ future managed-channel billable usage
- credits / discounts
+ taxes
= amount due
```

No example unit price in product discussions is a canonical price. Pricing remains catalog data controlled by MAIN/admin configuration.

## 3. Customer-owned providers

Traffic using customer-owned/direct provider credentials is **not** Managed Service PAYG by default.

Runtime already has a useful distinction in Broadcast sender connections:

- `MESSAGING_MANAGED`
- `CUSTOMER_MANAGED`

Billing R1 should normalize this into a financial field such as:

`serviceOwnership = MANAGED | CUSTOMER_OWNED`

Only MANAGED usage is eligible for managed-service rating unless a future, separately named commercial fee says otherwise.

Provider name alone must never determine ownership.

## 4. Private plan vs rate override

Use a PRIVATE plan when platform price/entitlements differ.

Use `CompanyRateOverride` when the company remains on a normal platform plan but has negotiated managed-service rates.

Do not clone a plan merely to express a usage unit-rate negotiation.

## 5. Included managed usage

Architecture may support an allowance without making it mandatory:

- plan version defines an allowance by managed usage SKU/unit;
- rated usage remains immutable;
- invoice calculation applies allowance deterministically to the financial billing period;
- overage uses the resolved effective rate;
- allowance consumption is never implemented by deleting or rewriting usage events.

MAIN must decide which plans, if any, include usage.
