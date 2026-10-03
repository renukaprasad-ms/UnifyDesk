# UnifyDesk Billing, Subscription & Payments R1

## Status

Architecture/source-audit round only. No B1/B2/F1 implementation has started.

- Frozen audited source SHA: `23527be323a87643246dabf046639458d0c44788`
- `dev` was compared to the frozen SHA at audit start and was identical (0 ahead / 0 behind).
- Source execution evidence for this audit: **BLOCKED — EXECUTION ENVIRONMENT**. Existing source tests were inspected but were not executed by L1.
- Cashfree sandbox/live flows: **NOT EXECUTED — PROVIDER VERIFICATION REQUIRED**.

## Frozen product model

UnifyDesk billing has two commercial layers:

1. **Platform subscription** — recurring SaaS plan price for entitlements/limits.
2. **Managed services PAYG** — usage-based charges only for UnifyDesk-managed provider traffic.

Customer-owned/direct provider traffic is not Managed Service PAYG by default.

Public plans are generally discoverable. Private plans require explicit company assignment. Negotiated managed-service rates remain separate from private plans through `CompanyRateOverride`.

## Source conclusion

The repository already contains meaningful billing foundations: versioned plan rows, prices, entitlements, company subscriptions, rate cards, company rate overrides, immutable-rated usage events, managed SMS metering, managed email quota accounting, admin/company billing APIs, and billing UI.

It does **not** yet contain a complete financial lifecycle. Invoice/payment/Cashfree/mandate/refund/dunning/billing-period orchestration is absent, published entitlements are mutable, private-plan isolation is absent, and historical rating/managed-vs-direct guarantees need remediation.

## Documents

- [CURRENT-ARCHITECTURE.md](CURRENT-ARCHITECTURE.md)
- [CURRENT-STATE-AUDIT.md](CURRENT-STATE-AUDIT.md)
- [PRODUCT-BILLING-MODEL.md](PRODUCT-BILLING-MODEL.md)
- [PLAN-CATALOG-AND-VISIBILITY.md](PLAN-CATALOG-AND-VISIBILITY.md)
- [END-TO-END-FLOW.md](END-TO-END-FLOW.md)
- [CANONICAL-CONTRACTS.md](CANONICAL-CONTRACTS.md)
- [STATE-MACHINES.md](STATE-MACHINES.md)
- [CASHFREE-CAPABILITY-MATRIX.md](CASHFREE-CAPABILITY-MATRIX.md)
- [USAGE-METERING-AND-RATING.md](USAGE-METERING-AND-RATING.md)
- [SECURITY-AND-IDEMPOTENCY.md](SECURITY-AND-IDEMPOTENCY.md)
- [GAP-MATRIX.md](GAP-MATRIX.md)
- [WORK-BREAKDOWN.md](WORK-BREAKDOWN.md)
- [MAIN-DECISIONS-REQUIRED.md](MAIN-DECISIONS-REQUIRED.md)

## Architecture rule

No worker may reinterpret the commercial model from implementation convenience. Financial records must be append-only or immutable after finalization, external-provider state must be reconciled, and backend authorization/enforcement is authoritative.
