# UnifyDesk Email — R1–R3 QA and staged release playbook

Status: **source implemented, no executable verification or production readiness evidence in this GitHub-only session**.

This is the historical GitHub-only audit. For October 21 local builds,
database tests and the current independent-account MFA/recovery and role
implementation, see [local implementation and QA](MAIL_LOCAL_IMPLEMENTATION_AND_QA.md).
Production acceptance and commercial release gates below still apply.

## Git integration order

1. #347 — `fix/email-provider-security-delivery-r1`, based on frozen dev `c0526cce`
2. #348 — `feature/unifydesk-email-product-r1`, based on #347
3. #349 — `feature/unifydesk-email-product-r2`, based on #348
4. #350 — `feature/unifydesk-email-app-connector-r1`, based on #349

**Do not merge or enable production sending before an independent QA pass.**
CI/GitHub Actions files have intentionally not been changed.

## Product boundaries

- `email-provider/` is **UnifyDesk Email**: independent DB, public scoped API keys,
  organizations, projects, users/roles, product console, billable usage ledger,
  DKIM/Postfix, queues, sending domains, validation, feedback, suppression and webhooks.
- `unifydesk-app/` is a distinct SaaS customer of that API. It stores a project-specific
  API key encrypted in its existing Broadcast connection and submits one opted-in recipient
  at a time through the existing consent/retry/sending pipeline.
- The independent product must function even if `unifydesk-app/` is offline.
- The current AWS SES Managed Email sending and billing flow must remain unchanged.

## Mandatory configuration — email provider

Start from `email-provider/.env.example` and deploy secrets through Kubernetes or
another secret manager, **not git**:

- `EMAIL_PROVIDER_INTERNAL_API_TOKEN` — random >=32-byte operator/service secret.
  Internal administration must be accessible **only on private network**.
- `EMAIL_PROVIDER_MASTER_KEY` — base64 32-byte AES-GCM master key. Back up securely.
- `EMAIL_PROVIDER_FEEDBACK_TOKEN` — separate secret for trusted Postfix event agents.
- `EMAIL_PROVIDER_UNSUBSCRIBE_SIGNING_KEY` — random >=32-byte HMAC key, stable across
  deployments. Must be configured before any marketing campaign.
- `EMAIL_PROVIDER_PUBLIC_BASE_URL` — HTTPS origin serving
  `/v1/unsubscribe/{token}`, e.g. `https://api.email.unifydesk.in`.
- `EMAIL_PROVIDER_PRODUCT_WEBHOOKS_ENABLED=false` until an **exact HTTPS allowed-host
  list**, certificate/DNS and egress restrictions are verified.
- `EMAIL_PROVIDER_WEBHOOK_ALLOWED_HOSTS` — comma-delimited exact trusted destination
  hostnames, no wildcards. Outbound destination SSRF prevention also needs an egress firewall.
- `EMAIL_PROVIDER_PRODUCT_PROJECT_DAILY_QUOTA` and
  `EMAIL_PROVIDER_PRODUCT_PLATFORM_DAILY_QUOTA` for admission quotas.
- `EMAIL_PROVIDER_RATE_PLATFORM_PER_SECOND`, `_COMPANY_PER_SECOND`,
  `_DOMAIN_PER_SECOND` for Postfix handoff rates.
- `SPRING_FLYWAY_ENABLED=true`; enforce `FLYWAY_BASELINE_ON_MIGRATE=false`
  on production DB (review clean/upgrade migrations **V1–V13**).
- Redis, Kafka, PostgreSQL and Postfix addresses/credentials configured and healthy.
- `EMAIL_PROVIDER_DELIVERY_ENABLED=false` until approved. Reconcile uncertain
  `UNKNOWN` Postfix sends manually; never blindly retry.

Product front end: `GET /console/index.html` on email-provider host.
An operator must first create an organization and invite its owner via private
`/internal/v1/email-product/*` endpoints. The owner activates and creates an API key
in the browser. Projects initially have **sending disabled** until separately approved.
No automated invitation email is implemented; deliver invites securely out of band.

## Mandatory configuration — UnifyDesk App

- `APP_UNIFYDESK_EMAIL_ENABLED=false` (backend feature gate).
- `VITE_UNIFYDESK_EMAIL_ENABLED=false` (frontend visibility gate).
- `APP_UNIFYDESK_EMAIL_API_BASE_URL` — actual HTTPS product API origin reachable
  by the backend, **not by the browser**.
- Each workspace provisions its own Email product project with sending enabled, a
  verified sending domain and a project API key with `SEND, DOMAINS, READ` scopes.
  The key is encrypted in BroadcastSenderConnection credentials and must never be
  logged or returned to React. Cross-workspace project key sharing is not recommended.

Do NOT enable both flags until customer authentication, consent, independent product
billing policies, webhooks/status reconciliation and production mail reputation are QA-approved.

## Executable verification — to be run on a development/staging machine

```bash
cd email-provider
./mvnw -B spotless:check
./mvnw -B clean test
./mvnw -B package -DskipTests

cd ../unifydesk-app/backend
./mvnw -B spotless:check
./mvnw -B clean test

cd ../frontend
npm ci
npm test
npm run build
npm run lint
npm run format:check
```

**Not executed here.** Passing Maven tests by itself is not live mail verification.

## QA acceptance matrix — currently NOT confirmed

- Fresh PostgreSQL migration V1–V13, upgrade from existing schema V7/V9; no destructive
  migrations or duplicate unique-index violations. Correct BCrypt/PBKDF2 password format,
  role access, invitation reuse, parallel activation, session revoke and session timeout.
- Console UI: login, invalid credentials/login throttle, organization isolation,
  project creation, suspended project, read-only VIEWER restrictions, API-key issue/list/revoke,
  one-time secret display and refresh behavior, domain verification and dashboard.
- Public APIs: missing/wrong/revoked/expired key; scope violations; correct project
  isolation, project sending approval, domain ownership, invalid From, idempotency replays,
  concurrent same-key submissions, multipart message correctness.
- Bulk: 1–1000 distinct recipient bounds, duplicate/case folding, consent reference
  required, per-recipient BCC privacy, non-opted-in contact rejected by App, correct
  queue state, interrupted Postfix DATA acceptance marked UNKNOWN and never resent.
- Rate limits: atomic Redis quotas across several instances and time boundaries,
  fail closed under Redis unavailability, no over-release or erroneous accounting.
- Unsubscribe: POST requires valid signed token and authenticated recipient ownership
  mapping, GET/scanner never unsubscribes, repeated POST idempotent; one-click header,
  body URL and DKIM signatures verified using a real mail header inspector.
- Feedback/webhooks: trusted token only, duplicated provider events, retries, exact
  HMAC timestamp/payload verification, replay defense in consumers, failed endpoint
  disable, DNS changes, redirect behavior, SSRF/egress protection and queue restart.
- Metering: unique handoff ledger, correct INR micro-unit price snapshot, UTC period
  boundary, no double billing on retries, required tax/invoicing/commercial terms.
- App connector: existing permission & consent/suppression checks, encrypted
  per-workspace API keys, verified project/domain, SES unchanged when flags disabled,
  unknown HTTP outcome, provider 429/4xx handling, reconciliation/rollback.
- Ops: static dedicated public outbound IP, PTR/A/EHLO, SPF/DKIM/DMARC aligned,
  STARTTLS, Postfix outbound TCP25 availability, reputation/warmup plan, dedicated
  marketing vs transactional pools, bounce processing, abuse escalation, backups,
  monitoring, rate suspension and operator kill switch.

## Known release blockers (not claims of completion)

1. **Independent payments/invoices:** ledger and rate snapshots exist; Cashfree/other
   collection, GST-compliant tax invoices, refund/credit notes and reconciliation
   are not implemented for this product.
2. **External customer self-registration/email verification/2FA/reset:** initial
   R2 console uses private invitation provisioning and PBKDF2 session accounts.
3. **Webhook cross-product reconciliation:** signed event publisher exists;
   UnifyDesk App's durable signed webhook consumer and complete consent/billing
   reconciliation are pending. Unsubscribes at the Email product suppress per-project
   future sends but are not yet synchronized to the App's Contacts consent ledger.
4. **Mail infrastructure:** IP pools/warm-up, real Postfix hosts, DNS/PTR/port25,
   feedback agent runtime and end-to-end inbox placement evidence are missing.
5. **Reliability:** HTTP idempotency races, quota reservation leaks on aborted transactions,
   Postfix UNKNOWN resolution and webhook delivery race QA need hardening.
6. **Mailbox hosting:** inbound IMAP/SMTP/webmail is a distinct *future* product and
   not part of the independent outbound API release.

**The project must remain marked DRAFT / NOT PRODUCTION READY until each blocker is
reviewed and documented as closed.**
