# Billing V2 — Audit, implementation status and release checklist

## Scope and baseline

- Repository: `renukaprasad-ms/UnifyDesk`, base branch `dev`.
- Frozen tree audited: `a71cc239170ac401182fbfb7895287d2338abcf0`.
- Development branch: `feature/billing-v2-usage-analytics-r1`.
- Draft PR: #363 (not merged).
- Currency: INR.
- **Global fallback rate: Managed Email INR 0.012 per outbound MESSAGE.**
- **Global fallback rate: Managed SMS INR 0.25 per billable SEGMENT.** This is the current runtime's financial unit; a multipart SMS may incur multiple segments. Confirm the commercial messaging before production.
- WhatsApp: operational reporting only until managed financial SKU/rate contracts exist.

## Verified pre-existing functionality

- `PricingResolver` used effective dated company overrides, then plan-specific rate cards. No global, plan-independent default rate.
- `UsageBillingService`: MANAGED vs CUSTOMER_OWNED isolation, required SKU, idempotency key, resolved price snapshots and six-decimal money arithmetic.
- `ManagedUsageInvoiceService`: closing-period invoice line materialization, prior-period adjustment, usage-to-invoice allocation.
- `InvoiceService`: aggregates six-decimal line values and rounds finalized invoice totals to two decimal places.
- `BillingCommercialAdminController`: private-plan visibility assignment/revoke and audited manual subscription grant/cancel.
- `PlatformBilling`: company pricing override and private plan assignment UI existed, but no unified analytics.
- `PlatformPlans`: plan creation API already supports visibility; the form did not expose it.

## Implemented on feature branch (not deployed)

### Global pricing
- Flyway `V104__billing_global_managed_rates_v2.sql` seeds canonical email/SMS prices effective from migration activation. No backdating or rewriting of prior usage.
- `GlobalManagedRateService`: serializes price revisions per SKU with database advisory transaction lock, half-open effective intervals and immutable price history. Only previous interval end time is closed when publishing a revision.
- `PricingResolver`: company override > plan-specific rate > global SKU default; unsupported/inbound shapes fail closed.
- `PricingSource.GLOBAL_RATE` stored with numeric global rate row ID, while `rate_card_id` remains null for a global rate.
- Super Admin GET/POST `/api/v1/admin/billing/global-managed-rates`.
- Super Admin frontend: current rates, scheduled revisions, historical intervals and confirmation step.

### Operational volume and billing analytics
- `GET /api/v1/admin/billing/analytics/managed-usage`: priced managed usage events, totals by SKU/day/company. These are charges, not actual payments.
- `GET /api/v1/admin/billing/analytics/financial`: finalized invoiced totals, succeeded collections, successful refunds, current open balances, invoiced platform-plan and managed-usage line breakdown. Distinguishes invoice issuance from cash collection; NOT accrual revenue recognition.
- `GET /api/v1/admin/billing/analytics/channel-traffic`: operational message records from connected inbox email, legacy managed email recipients, managed SMS recipients and Meta WhatsApp runtime. Excludes independent `email-provider` mail service until integrated.
- `GET /api/v1/admin/billing/analytics/usage-reconciliation`: rated managed usage vs invoice allocation, finalized unallocated events, missing invoice lines and price arithmetic anomalies.
- Date-filtered admin UI, company tables, daily/UTC-weekly trends, financial CSV export. All endpoints enforce Super Admin.
- Company IDs serialized as **strings** to avoid JavaScript Snowflake precision loss.
- Flyway `V105__billing_v2_reporting_indexes.sql`: global reporting time-window indexes.

### Private plans
- `PlatformPlans` exposes visibility at creation (PUBLIC/PRIVATE/INTERNAL), filters by visibility and displays the private-plan count.
- Private plan details, pricing and assignment remain in existing plan/admin-billing screens; no duplicate plan lifecycle implementation.

## Reporting definitions and exclusions

1. **Processed email:** inbox messages status RECEIVED/SENT plus managed email delivery recipient records with a sent-event timestamp. It is not a count of raw provider webhook deliveries and cannot include separate mail-service traffic yet.
2. **Processed SMS:** one record per accepted managed SMS recipient, not provider-charged segments. Financial SMS units are segmented, so operational and monetary counts differ for multipart messages.
3. **Processed WhatsApp:** one Meta runtime row in accepted/sent/delivered/read status (inbound/outbound). Meta cloud API/other unsupported source gaps remain.
4. **Managed usage charges:** sum of six-decimal stored financial snapshot amounts, by occurrence time; no cash implication.
5. **Invoiced:** finalized invoice total (tax included), by invoice finalized date; excludes DRAFT/VOID.
6. **Collected:** SUCCEEDED payment amount by succeeded_at; refunds shown separately and net cash is collected minus refunded.
7. **Open balance:** current OPEN invoice amount_due across all dates, independent of the selected period.
8. **Plan and managed invoice lines:** pre-tax and pre-discount component amounts. They are not additive to tax-inclusive invoiced totals without adjustments.
9. **UTC reporting:** half-open intervals [from,to), daily boundaries in UTC, weekly buckets begin Monday UTC.
10. **Reconciliation:** exceptions for finalized-but-unallocated financial usage, allocated events missing invoice line, and stored total mismatching quantity × unit price rounded to six decimals.

## Coverage and release blockers

- **Unverified build:** PR GitHub Actions jobs are failing before any workflow steps execute (no checkout/test steps/logs), including unrelated jobs. Do not treat their failure as code-test results or merge without independent compilation.
- Audit possible overlap/dedup between inbox and legacy managed email event sources using live provider-level reconciliation.
- Connect the separate `email-provider` system's usage billing ledger and send/receive metrics to UnifyDesk central billing; no cross-database adapter is implemented here.
- MetaReach provider routing must be verified end-to-end against logical `MSG91_SMS` billing provider identity.
- WhatsApp managed billing remains unconfigured; do not charge until a validated SKU/category rate and usage rule is approved.
- Provider costs, profitability and tax/recognized-revenue accounting dashboards remain out of scope.
- Full authenticated integration tests, expiry/overlap/concurrent global rate revision tests, date-boundary reporting tests, provider cost reconciliation and invoice/PAYG production smoke tests are still required.
- Confirm the commercial decision: **INR 0.25 per SMS segment vs per logical SMS message** before enabling production billing and customer invoices.
- Review global fallback effects for existing plans with plan-specific rate cards: these continue to win and are NOT automatically repriced at new global defaults.
- This branch is **not merged**, **not deployed** and does **not** mutate live pricing until migration on a deployed environment.

## Verification commands once the repository runner is available

```bash
cd unifydesk-app/backend
./mvnw spotless:apply
./mvnw -Dtest=PricingResolverGlobalManagedRatesTests,UsageBillingGlobalRateTests,PlatformMigrationHardeningTests test
./mvnw clean test
cd ../frontend
npm ci
npm test
npm run build
npm run lint
npm run format:check
```

Before merging, also validate migrations on PostgreSQL in a clean and already-migrated staging database; exercise global pricing revisions, company override precedence, per-SMS-segment math, invoice allocations, payments, refunds, tenant isolation and all analytics filters.
