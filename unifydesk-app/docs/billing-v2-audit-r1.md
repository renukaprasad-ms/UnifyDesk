# Billing V2 – dev branch audit and implementation plan (R1)

Audit baseline: dev tree SHA `a71cc239170ac401182fbfb7895287d2338abcf0` (2026-10-09).

## Confirmed commercial requirements
- Managed outbound email: INR 0.012 per MESSAGE.
- Managed SMS: INR 0.25 per SMS **customer wording**. Current code records `MANAGED_SMS_SEGMENT` in `SEGMENT` units; product must expressly decide whether a multipart SMS incurs one charge or one per segment before provisioning the default rate. Do not silently switch counting.
- Company-specific effective-dated pricing overrides.
- Private company subscription plans.
- Company and daily/weekly/monthly channel volumes, managed charges, subscription charges, invoices, collected payments and outstanding amounts.
- WhatsApp volumes regardless of service ownership; financial charges only where billable metering exists.

## Existing backend (verified)
- `billing/service/PricingResolver.java`: company override -> effective plan RateCard; no standalone global default rate tier.
- `billing/service/UsageBillingService.java`: validates managed ownership/SKU, resolves effective prices, records price snapshot, uses idempotency key; managed email MESSAGE and managed SMS SEGMENT.
- `billing/service/ManagedUsageInvoiceService.java`: materializes usage rows at period closing and allocates to invoice once.
- `billing/entity/UsageEvent.java`: source IDs, provider references, unit, quantity, unit price and total snapshots. Numeric monetary scale 6.
- `billing/controller/AdminBillingController.java`: CRUD for plans, prices, rate cards, company overrides.
- `billing/controller/BillingCommercialAdminController.java`: private-plan assignment/revocation, manual grants/cancellations.
- `frontend/src/pages/platform/PlatformBilling.tsx`: existing admin operations UI already has company override and private plan assignment forms.
- `email-provider/backend/.../V12__email_usage_billing_ledger.sql`: independent new-mail product usage ledger; no proof of central billing integration yet.

## R1 implemented in this branch
- Read-only, super-admin-only `GET /api/v1/admin/billing/analytics/managed-usage?from=...&to=...` aggregates recorded priced MANAGED usage by company and UTC day.
- Frontend overview integrated into the existing PlatformBilling page. Displays managed charges separately from actual collected revenue.
- Backend endpoint does *not* purport to report WhatsApp traffic lacking usage ledger records. SMS metric is labelled segments; bar sums unlike units for trend display only.

## Required subsequent implementation (not yet done)
1. Establish `global_rate_cards` with effective-dated INR MESSAGE/SEGMENT prices; extend pricing precedence to company override -> plan-specific negotiated rate -> global default, or agree on a different contractual order. Migrate without changing existing published plan pricing.
2. Determine SMS billing behavior for multipart messages and customer-visible vs provider-billed segments, align ledgers, display explicit units and rounding.
3. Trace SES, MSG91, MetaReach, mail service, WhatsApp provider events end-to-end into a unified canonical metering pipeline, guard against cross-provider retry double counting and record delivery/failed statuses separately from billable usage.
4. Add full traffic reporting for customer-owned channels; do not charge these by default.
5. Introduce subscription invoice/paid/unpaid/refund reporting; distinguish billed charges, earned revenue and cash payments; include financial reconciliation and audit.
6. Build global pricing UI and end-to-end private plan editor (not only assignment); full dashboard date filters, company drilldowns, charts, csv export.
7. Run backend and frontend tests; test effective-time boundaries, tenant isolation, retries, multipart SMS, WhatsApp categories, invoice allocation, historical override protection, taxes and reconciliation.

## Safety constraints
- No retroactive rewriting of historical usage price snapshots.
- No fabricated revenue metrics or cash collection values.
- Financial admin routes require super-admin authorization before querying company data.
- Do not deploy or merge without verification.
