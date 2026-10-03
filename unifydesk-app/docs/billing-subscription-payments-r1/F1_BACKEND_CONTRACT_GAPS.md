# F1 Backend Contract Gaps — Billing R1

Reviewed against frozen F1 base `7a13f69218aec08db7bf9a7c56b1467a0f499897`.

F1 did not add or alter backend production code. These frontend requirements cannot be completed truthfully from the HTTP contracts exposed at this SHA.

## GAP-1 — Payment-return authoritative payment state

**Screen:** `/billing/payment-return`

**Missing:** normalized Payment, PaymentAttempt, invoice settlement and recurring-authorization state after a Cashfree redirect, including failed/cancelled/action-required states.

**Existing contracts inspected:** checkout creation POSTs under `/api/v1/companies/{companyId}/billing/payments/*` and `GET /api/v1/companies/{companyId}/plan`. The payment controller has no customer GET endpoint. Company plan state can prove activation but cannot distinguish payment failure, provider-pending payment, attempt history, or current recurring authorization.

**Smallest addition:** company-authorized GET keyed by local paymentId or invoiceId returning logical Payment status, attempts, invoice status, and normalized recurring-authorization state/action without raw provider secrets.

**F1 fallback:** ignore redirect/query-string success signals and bounded-poll company plan state. Render confirmed only when backend subscription truth matches the stored target plan; otherwise stop after a bounded window and remain pending/unknown.

## GAP-2 — Customer invoice history and invoice lines

No customer invoice GET controller is exposed. Add paginated company-authorized invoice list/detail endpoints returning normalized header and line items, including prior-period adjustment labels.

## GAP-3 — Customer payment / attempt / refund history

Payment, PaymentAttempt and Refund entities/services exist, but customer HTTP only exposes checkout POSTs. Add company-authorized read-only normalized payment endpoints with attempts/refund summaries and safe provider references.

## GAP-4 — Managed Services usage/rating read model

No company usage GET endpoint is exposed. Add a period-scoped managed-usage summary that returns only financially billable `serviceOwnership=MANAGED` records, amount/unit/status and normalized quantity source such as `MANAGED_SMS_LOCAL_SEGMENT_ESTIMATE`; exclude CUSTOMER_OWNED traffic.

## GAP-5 — Super Admin billing inspection

Admin controllers expose catalog configuration, rate overrides, private assignments, grants and cancellation, but not read-only inspection of BillingPeriods, invoices/lines, payments/attempts, refunds or managed usage. Add Super-Admin-only paginated inspection endpoints or a normalized company billing-inspection aggregate without secrets or sensitive raw provider payloads.

## GAP-6 — Recurring authorization re-read

Initial checkout returns a recurring authorization session/action URL at preparation time, but no endpoint can re-establish current authorization state later. Add a company-authorized normalized recurring-authorization read returning pending/action-required/active/failed and a safe continuation URL when applicable. F1 preserves the initial backend-issued continuation only as navigation context and never treats it as proof of success.
