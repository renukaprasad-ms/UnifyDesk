# Current-State Audit

Audited at `23527be323a87643246dabf046639458d0c44788`.

| Capability | Classification | Evidence / reason |
|---|---|---|
| Versioned plan rows | IMPLEMENTED | `Plan(code, version)` unique; admin creates versions. |
| Plan prices/effective dates | IMPLEMENTED | `PlanPrice`; published commercial prices are draft-only mutable. |
| Published entitlement immutability | NEEDS REMEDIATION | `BillingCatalogService` allows PUBLISHED entitlement upsert/delete; a source test explicitly expects this. |
| Public plan discovery | PARTIALLY IMPLEMENTED | All PUBLISHED plans are returned; no visibility policy. |
| Private/company-specific plans | MISSING | No visibility field or assignment relation. |
| INTERNAL plan scope | PRODUCT DECISION REQUIRED | No current model. Architecture recommends reserving it. |
| Default/free subscription | IMPLEMENTED | New companies receive FREE; zero-price self-selection works. |
| Paid checkout | MISSING | Company endpoint rejects paid plans; no checkout/payment provider. |
| Subscription lifecycle | PARTIALLY IMPLEMENTED | ACTIVE/TRIALING/PAST_DUE/CANCELLED fields exist, but no transition engine/payment dependency/jobs. |
| Cancel at period end | PARTIALLY IMPLEMENTED | Flag can be set; no audited scheduler applies it at period end. |
| Renewal | MISSING | No renewal orchestration. |
| Upgrade/downgrade | MISSING | Admin assignment immediately cancels/replaces current subscription. |
| Grace/suspension | MISSING | PAST_DUE exists, but `EntitlementService` treats it as no effective plan. |
| Entitlement catalog | IMPLEMENTED | Definitions initialized in source. |
| Entitlement enforcement | PARTIALLY IMPLEMENTED | Strong in several modules; Broadcast/template/module coverage is incomplete. |
| Plan rate cards | IMPLEMENTED | Effective-dated, provider/category/direction/unit dimensions. |
| Company rate overrides | IMPLEMENTED | Override precedence exists. |
| Override history immutability | NEEDS REMEDIATION | Rows can be edited and company purge deletes them. |
| UsageEvent rating snapshot | IMPLEMENTED | Price/rule/plan snapshot + idempotent insert. |
| Historical subscription resolution | NEEDS REMEDIATION | Pricing resolves the current ACTIVE/TRIALING subscription rather than subscription effective at `occurredAt`. |
| Managed-vs-direct financial dimension | NEEDS REMEDIATION | Runtime has management mode, but `UsageEvent` lacks explicit ownership/billing-source field. |
| Managed SMS rated usage | PARTIALLY IMPLEMENTED | Accepted usage enters `UsageBillingService`; actual billable segment truth/provider reconciliation needs runtime verification. |
| Managed Email rated usage | MISSING/PARTIAL | Quota ledger exists; generic rated financial `UsageEvent` integration absent. |
| Billing periods | MISSING | Operational managed-service counters use UTC calendar month, not immutable financial billing periods. |
| Invoices/line items | MISSING | No source model/API/job. |
| Credits/adjustments/credit notes | MISSING | No canonical financial model. |
| Payments/attempt history | MISSING | No source model/API/provider integration. |
| Refund financial model | MISSING | No source model/provider adapter. |
| Cashfree | MISSING | No Cashfree source/config/webhook integration found. |
| Webhook financial event inbox | MISSING | Must be added. |
| Dunning/retry | MISSING | No billing collection jobs. |
| Company billing UI | PARTIALLY IMPLEMENTED | Plan view only; no checkout, usage, invoices, payments. |
| Platform billing UI | PARTIALLY IMPLEMENTED | Catalog/rates/manual subscription exist; no visibility/private assignment/payment operations. |
| Tenant isolation for private plans | MISSING | All published plans discoverable; no assignment authorization. |
| Runtime tests in this audit | BLOCKED — EXECUTION ENVIRONMENT | No workflow/status run was associated with the frozen audit commit and L1 did not execute local tests. |
| Cashfree sandbox verification | PROVIDER VERIFICATION REQUIRED | Not executed. |

## Source-test note

Existing unit tests were inspected, including billing catalog, entitlement, and pricing resolver tests. Inspection is not execution evidence. In particular, `BillingCatalogServiceTests.publishedPlanEntitlementsRemainEditable` confirms the current mutable-published-entitlement behavior is deliberate source behavior today, not an inference.
