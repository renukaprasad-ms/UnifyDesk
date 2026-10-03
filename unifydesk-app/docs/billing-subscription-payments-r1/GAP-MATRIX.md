# Gap Matrix

| ID | Area | Severity | State | Required R1 action |
|---|---|---:|---|---|
| BSP-R1-001 | Published entitlements mutable | HIGH | NEEDS REMEDIATION | Make PUBLISHED plan version commercially immutable. |
| BSP-R1-002 | No plan visibility/private assignment | HIGH | MISSING | Add visibility + PlanCompanyAssignment + backend eligibility. |
| BSP-R1-003 | Paid plan can be manually activated by admin without payment | HIGH | NEEDS REMEDIATION | Separate authorized manual admin grant from normal paid activation; paid customer flow requires payment policy. |
| BSP-R1-004 | Subscription lifecycle not state-machine driven | HIGH | PARTIAL | Add transition service/history/jobs. |
| BSP-R1-005 | PAST_DUE loses entitlement resolution | HIGH | NEEDS REMEDIATION | Add explicit grace/suspension entitlement policy. |
| BSP-R1-006 | No immutable financial BillingPeriod | HIGH | MISSING | Add open/close/finalized period domain. |
| BSP-R1-007 | No Invoice/line-item lifecycle | CRITICAL | MISSING | Add immutable finalization and source snapshots. |
| BSP-R1-008 | No Payment/PaymentAttempt | CRITICAL | MISSING | Add separate collection model. |
| BSP-R1-009 | No Cashfree integration/webhook reconciliation | CRITICAL | MISSING | Add provider adapter, durable inbox, reconciliation. |
| BSP-R1-010 | Usage has no MANAGED vs CUSTOMER_OWNED dimension | CRITICAL | NEEDS REMEDIATION | Add ownership/SKU and fail-safe rating eligibility. |
| BSP-R1-011 | Managed Email not financially rated | HIGH | PARTIAL | Bridge accepted usage to UsageEvent exactly once. |
| BSP-R1-012 | Historical usage rating uses current subscription | CRITICAL | NEEDS REMEDIATION | Resolve subscription effective at occurredAt. |
| BSP-R1-013 | Company rate override can mutate/gets purged | HIGH | NEEDS REMEDIATION | Immutable revisions + retention/audit policy. |
| BSP-R1-014 | Managed SMS quantity/provider truth | MEDIUM | RUNTIME VERIFICATION REQUIRED | Verify actual segment billing and provider duplicate behavior. |
| BSP-R1-015 | Broadcast entitlement coverage incomplete | HIGH | PARTIAL | Authoritative backend guards for plan Broadcast keys. |
| BSP-R1-016 | Email template entitlement not enforced in service | MEDIUM | PARTIAL | Enforce at create path. |
| BSP-R1-017 | Ad Center/general feature gating not represented in entitlement catalog | MEDIUM | PRODUCT DECISION REQUIRED | Decide which modules are paid gates; add explicit boolean entitlements if desired. |
| BSP-R1-018 | Cancel-at-period-end has no audited executor | HIGH | PARTIAL | Restart-safe transition job. |
| BSP-R1-019 | Upgrade/downgrade/proration absent | HIGH | MISSING | SubscriptionChange + quotes/payment gate/scheduling. |
| BSP-R1-020 | Refund/credit/adjustment absent | HIGH | MISSING | Separate provider Refund and accounting credit model. |
| BSP-R1-021 | Dunning/retry/grace/suspension absent | HIGH | MISSING | Policy-driven jobs + reconciliation. |
| BSP-R1-022 | Company UI lacks checkout/usage/invoices/payments | MEDIUM | PARTIAL | F1 flow after backend contracts. |
| BSP-R1-023 | Platform UI lacks visibility/private assignment | HIGH | PARTIAL | Admin plan scope/assignment controls. |
| BSP-R1-024 | Frontend billing provider enums are stale vs runtime providers | MEDIUM | NEEDS REMEDIATION | Align canonical provider/SKU DTOs; do not hard-code old Gmail/Microsoft/SMTP billing assumptions. |
| BSP-R1-025 | Runtime/provider evidence absent | HIGH | BLOCKED / NOT EXECUTED | Execute source tests and Cashfree sandbox verification before release. |
