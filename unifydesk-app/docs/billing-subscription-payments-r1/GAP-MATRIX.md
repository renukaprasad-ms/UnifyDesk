# Gap Matrix

| ID | Area | State after MAIN freeze | R1 action |
|---|---|---|---|
| BSP-R1-001 | Published entitlements mutable | NEEDS REMEDIATION | Make PUBLISHED plan versions immutable. |
| BSP-R1-002 | No PUBLIC/PRIVATE/INTERNAL visibility | MISSING | Add visibility + PlanCompanyAssignment + backend eligibility. |
| BSP-R1-003 | Paid manual activation lacks explicit policy | NEEDS REMEDIATION | Add CASHFREE/COMPED/OFFLINE_CONTRACT policy and immutable audit. |
| BSP-R1-004 | Subscription lifecycle incomplete | PARTIAL | Add canonical state machine/history. |
| BSP-R1-005 | PAST_DUE handling | NEEDS REMEDIATION | 7-day grace + restriction policy + recovery. |
| BSP-R1-006 | BillingPeriod | MISSING | Add platform/managed financial periods; managed PAYG monthly. |
| BSP-R1-007 | Invoice/line items | MISSING | Add immutable INR invoice lifecycle/tax snapshots. |
| BSP-R1-008 | Payment/attempt | MISSING | Add append-only attempts and provider refs. |
| BSP-R1-009 | Cashfree integration | MISSING | Add provider adapter/webhook/reconciliation. |
| BSP-R1-010 | MANAGED vs CUSTOMER_OWNED usage | NEEDS REMEDIATION | Add explicit ownership and bill only MANAGED. |
| BSP-R1-011 | Managed Email financial rating | PARTIAL | Bridge accepted usage to UsageEvent exactly once. |
| BSP-R1-012 | Historical subscription pricing | NEEDS REMEDIATION | Resolve subscription effective at occurredAt. |
| BSP-R1-013 | CompanyRateOverride historical safety | NEEDS REMEDIATION | Immutable/effective-dated revision + retention. |
| BSP-R1-014 | Managed SMS provider truth | PROVIDER/RUNTIME VERIFICATION REQUIRED | Verify authoritative segment quantity and duplicate behavior. |
| BSP-R1-015 | Broadcast entitlement coverage | PARTIAL | Add authoritative backend guards. |
| BSP-R1-016 | Template/module entitlement coverage | PARTIAL | Add authoritative backend enforcement where catalog-gated. |
| BSP-R1-017 | Upgrade | MISSING | Immediate after confirmed payment + deterministic proration. |
| BSP-R1-018 | Downgrade | MISSING | Schedule at next platform boundary; preserve excess resources. |
| BSP-R1-019 | Cancellation executor | PARTIAL | Customer period-end + audited SUPER_ADMIN immediate path. |
| BSP-R1-020 | Refund/credit | MISSING | Separate explicit financial operations. |
| BSP-R1-021 | Dunning | MISSING | Due failure -> PAST_DUE; day ~2/~5 retries; day 7 unresolved -> SUSPENDED. |
| BSP-R1-022 | Company billing UI | PARTIAL | Plans/checkout/subscription/usage/invoices/payments. |
| BSP-R1-023 | Platform billing UI | PARTIAL | Visibility/assignments/manual-grant/payment inspection. |
| BSP-R1-024 | Provider/billing DTO drift | NEEDS REMEDIATION | Align runtime/provider/SKU contracts. |
| BSP-R1-025 | Financial retention | NEEDS REMEDIATION | Preserve financial history; provision >=8-year capability/legal hold. |
| BSP-R1-026 | GST/tax production policy | PRODUCTION-READINESS VALIDATION REQUIRED | Keep immutable tax/address snapshots; validate legal rules before launch. |
| BSP-R1-027 | Cashfree sandbox/provider evidence | PROVIDER VERIFICATION REQUIRED | Execute after integrated SEC/QA-complete code. |
