# UnifyDesk System Overview

Baseline: `dev@d9717f93b3e10a308da9636750a4df6926bc6016`

UnifyDesk is a multi-tenant customer communication and marketing SaaS implemented primarily as a Spring Boot modular monolith with a React/Vite web application. PostgreSQL is the system of record; Redis supports distributed coordination/realtime/rate-limiting use cases; Kafka carries prioritized asynchronous work and retry/DLT flows. External integrations include email providers, Gmail/Microsoft mailboxes, MSG91, Meta WhatsApp, Google Ads, Meta Ads and object storage providers.

## Runtime components

### Backend

Java 25 / Spring Boot 4.1.1. Major packages:

- `auth`, `session`, `security` — registration/login, OTP/2FA/password reset, JWT cookies, refresh/logout, CSRF/CORS and authentication filters.
- `company`, `user` — companies/workspaces, memberships, roles and permissions.
- `billing` — plan catalog, company subscriptions, entitlement resolution, pricing and usage billing primitives.
- `channel` — provider connections and encrypted credentials for Gmail, Microsoft, IMAP/SMTP and Meta.
- `inbox` — persisted conversations/messages, sync/read state and provider-backed inbox operations.
- `broadcast` — campaign preparation, audience snapshots, recipients, delivery workers, sender connections, rate limits and provider transports.
- `emailtemplate` — versioned template catalog, assets, provider bindings and variables.
- `media`, `storage` — upload/download lifecycle, S3/Supabase providers, quotas, accounting and reconciliation.
- `managedemail` — SES-backed domain/sender control plane, delivery, events, metering, reputation and tenant safety controls.
- `managedsms` — MSG91-oriented DLT/control plane, delivery/events, metering and rate/safety controls.
- `notification.platform` — notification event recording, materialization/fanout, in-app API, email, browser push, digests and realtime publication.
- `channel.meta.runtime` — Meta WhatsApp messaging, templates, media, webhook processing, state machine, reconciliation and distributed rate limiting.
- `contacts` — Contact identities/profile, tags/lists/custom fields, consent/suppression, segments, import/export/bulk jobs and Broadcast audience integration.
- `ads` — Google/Meta account connectivity, campaign models, launch orchestration, provider capabilities, insights, audiences/conversions and optimization.
- `asyncjob`, `kafka` — durable job metadata plus prioritized Kafka topics, retries, DLT and idempotency.
- `realtime` — authenticated WebSocket connections and Redis-backed distribution.
- `admin` — platform access, users, companies, plans and channel analytics.
- `observability` — request/provider audit support and metrics-related infrastructure.

### Frontend

React 19 + TypeScript 6 + Vite 8. Feature areas mirror the backend: auth, company/workspace/IAM, billing, connections, Inbox, Broadcast, templates, Contacts, Managed Email/SMS, notifications, WhatsApp, Ads, media/storage and platform administration.

### Database

Flyway is the schema authority. The audited branch contains migrations V1 through V83, with intentional numbering gaps. Major migration eras are:

- V1–V14: initial schema, storage, Inbox/provider sync, deletion lifecycle and webhook auditing;
- V15–V33: Broadcast and email templates;
- V34–V55 plus V61: Ad Center;
- V56–V60: Managed Email;
- V62–V66: Managed SMS;
- V70–V75: Notifications;
- V76–V79: Meta WhatsApp;
- V80–V83: Contacts & Audience.

## Major user flows

### Authentication and workspace access

A user registers or logs in, may complete 2FA/email verification, receives JWT-based browser cookies, and operates within authenticated company/workspace membership and IAM permissions. Mutating authenticated browser requests are CSRF protected; the API uses credentialed allowlisted CORS.

### Connect a channel and use Inbox

A company connects Gmail, Microsoft, IMAP/SMTP or Meta WhatsApp. Provider callbacks/sync workers persist messages into common Inbox structures. Read-state changes and new activity can publish realtime updates. Disconnect/purge lifecycle and webhook auditing are implemented in the backend.

### Build an audience and broadcast

Contacts can be created/imported and organized by Lists, tags and dynamic Segments. Consent/suppression eligibility is part of the Contacts domain. Broadcast can snapshot a Contacts audience into recipients, prepare work asynchronously, then send through configured transports such as SES, SendGrid, Azure Communication Email, MSG91 or managed delivery paths. Delivery state and metering are projected back into application state.

### Managed Email

A company can use UnifyDesk-managed sending identities. The implementation includes domain/sender provisioning, SES integration, hosted/customer domain concepts, DNS/Cloudflare hooks, event ingestion, suppression/reputation logic, metering and safety caps. The feature is disabled by default and still requires live SES/DNS operational verification before production release.

### Managed SMS

A company configures DLT-related identity/template data and UnifyDesk coordinates MSG91-oriented provisioning and sending. The runtime includes campaign guards, delivery events, usage metering and safety limits. India DLT/provider topology and production webhook/API behavior remain external release dependencies.

### Meta WhatsApp

Embedded Signup/control-plane state connects a WABA/phone, runtime APIs send text/template/media, webhooks normalize inbound/status events, messages project into Inbox, and Redis-backed per-phone limiting/cooldowns protect outbound traffic. Source hardening reached Round 3 historically, but executable matrix and real Meta provider verification remain separate release gates.

### Notifications

Domain activity is recorded into a notification event/outbox path, materialized to recipients, exposed through in-app APIs, published realtime and optionally delivered by product email/browser push or digests. Current source is substantially later than its stale Round-0 integration status document.

### Ad Center

The backend includes Google Ads and Meta Ads connectivity plus account/resources, campaign construction/launch, audiences/conversions, insights, keyword planning and optimization-related code. Provider API availability, credentials, account eligibility and real launch verification must be treated independently from source completeness.

## Cross-cutting design

- PostgreSQL is authoritative durable state.
- Flyway validates/evolves schema; Hibernate uses `ddl-auto=validate`.
- Redis is used for fast shared state/distributed coordination, not as the primary business database.
- Kafka is configured with idempotent producers, manual consumer commit, prioritized topics, retry topics and DLTs.
- Async database jobs provide user-visible job state and bounded/background processing.
- Realtime WebSocket sessions require authentication and are distributed through the realtime layer.
- Actuator exposes health/info/metrics/Prometheus endpoints; current K8s probes still use TCP rather than actuator readiness/liveness.
- Provider secrets are configuration-driven and must not be committed.

For detailed feature contracts, follow links from [README.md](README.md) rather than copying those contracts into platform documents.
