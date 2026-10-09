# UnifyDesk Billing V2 R2 — Mail product reporting bridge

**Status:** implemented on draft feature branch, not deployed/merged; tests and staging migration not yet verified.

## Why an explicit identity link is mandatory

`email-provider` is a separate email product whose `email_product_projects.id` is stored in
`email_messages.company_id`. These Mail project IDs are **not** UnifyDesk App `companies.id`.
Inferring a company from a matching numeric ID can expose data and attribute revenue to the wrong tenant.

The R2 bridge stores an explicit, audited, immutable project→company association (unique per Mail
project) **only when a Super Admin checks independent ownership proof** and the service responds
that the Mail project exists. The Mail service cannot itself prove that its project belongs to the
UnifyDesk company, so a real onboarding document, ticket, or signed contract is required.

Linking takes a source high-water ID and imports **future accepted recipient handoffs only**.
Previously sent Mail messages are not automatically re-rated or back-billed.

## API contracts

### Protected Mail service (bearer INTERNAL token)

- `GET /internal/v1/email-product/projects/{projectId}/billing/usage-head`: greatest
  immutable handoff usage ID, string representation.
- `GET /internal/v1/email-product/projects/{projectId}/billing/usage-entries?afterId=0&limit=100`:
  project-scoped immutable handoff page, entries ordered by source usage ID, at most 500 per page.
  Each entry has string-form `usageId`, `recipientId`, `messageId`, RFC3339 `occurredAt`,
  and `productPriceMicroInr`. The last value is a Mail product-side snapshot, **NOT**
  a UnifyDesk platform invoice or collected payment.

The API is protected by existing `InternalApiTokenFilter`; do not route it publicly.

### UnifyDesk Backend (JWT + Super Admin authorization)

- `GET /api/v1/admin/billing/mail-project-links`
- `POST /api/v1/admin/billing/mail-project-links`: JSON
  `{"companyId":"...","mailProjectId":"...","ownershipProof":"approved ticket ...","ownershipVerified":true}`
- `GET /api/v1/admin/billing/mail-project-links/usage?from=...&to=...`
- `POST /api/v1/admin/billing/mail-project-links/{projectId}/sync`
- `POST /api/v1/admin/billing/mail-project-links/{projectId}/pause`
- `POST /api/v1/admin/billing/mail-project-links/{projectId}/resume`

All IDs are strings across HTTP to avoid losing 64-bit Snowflake precision.

## Pricing and accounting safety

The ingestion mode is **REPORT_ONLY** (DB check constraint). The bridge **never**
calls `UsageBillingService.record`, `InvoiceService`, or payment provider methods.
It creates **no** `usage_events` or invoice allocations.

- New Mail handoffs are imported idempotently into `billing_mail_usage_imports` with unique
  `(mail_project_id,remote_usage_id)` and unique `(mail_project_id,remote_recipient_id)`.
- Imports preserve the original source's micro-INR price for independent reconciliation.
- Analytics count linked Mail handoffs as outbound email traffic, separate from central PAYG
  charge amounts. Do not add Mail product estimates to platform collections/revenue.
- Existing managed SES email charges still use **INR 0.012 per accepted message** as a
  global fallback, subject to company override and plan rate precedence.
- Global SMS INR 0.25/segment remains **inactive** until the commercial multipart SMS rule
  is approved and explicitly activated.
- WhatsApp remains traffic-only.

## Configuration

From `email-provider/k8s/app.yaml`, the in-cluster Service name is
`email-provider.email-platform.svc.cluster.local` on TCP port `8010`.

Set in **UnifyDesk App backend** environment after both services and V106 migration are deployed:

```dotenv
APP_BILLING_MAIL_BRIDGE_ENABLED=true
APP_BILLING_MAIL_BRIDGE_BASE_URL=http://email-provider.email-platform.svc.cluster.local:8010
APP_BILLING_MAIL_BRIDGE_INTERNAL_TOKEN=<same strong 32+-character secret used by EMAIL_PROVIDER_INTERNAL_API_TOKEN>
APP_BILLING_MAIL_BRIDGE_BATCH_SIZE=100
APP_BILLING_MAIL_BRIDGE_SYNC_INTERVAL_MS=300000
APP_BILLING_MAIL_BRIDGE_INITIAL_DELAY_MS=60000
```

Set **EMAIL_PROVIDER_INTERNAL_API_TOKEN** in the **Mail backend** environment (not frontend)
to the same strong secret. Do not put the token in Cloudflare Pages, Next.js or browser storage.
The bridge defaults to disabled if not configured. HTTPS for internal transit (or a service
mesh with mTLS and network policy) is recommended for production; private HTTP within the
cluster is accepted only for internal `.svc` DNS, localhost or loopback.

The scheduler polls up to 25 active projects per run (one page/project each); manual
Sync can import another page. A paused project stops importing. One project's failed
fetch does not stop others.

## QA / known limitations

1. **Not yet run on real PostgreSQL or compiled in CI:** current GitHub Actions jobs fail before
   checkout/runner steps; review and execute tests before merge.
2. Link ownership is operator-attested, not cryptographically proven by the Mail server.
3. Initial high-water cursor creates **no historical backfill**. Source ID ordering is suitable
   for one-instance ingestion, but concurrently committed lower-ID records may be missed by
   forward-only cursors; implement bounded lookback reconciliation before relying on this
   as complete externally audited traffic accounting.
4. The Mail service's separate project rate/collection truth must be reconciled before adding
   an optional **central billable** mode. This phase must not charge users twice.
5. The operational dashboard combines several message stores; investigate overlap across
   the same provider message identity before treating aggregated values as deduplicated totals.
6. Test paused link, duplicate/invalid page cursor, malformed payload, cross-project response,
   Snowflake ID precision, high-volume paging, company ownership, disabled flag and internal
   token failures. Confirm `V106` migrates both clean and existing databases.
7. Merging the PR at the end requires a fresh latest-dev comparison, successful backend+Mail
   compilation, frontend checks, database migrations, and independent financial/security QA.
