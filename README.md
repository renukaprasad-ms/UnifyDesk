# UnifyDesk

UnifyDesk is a multi-tenant customer communication and marketing platform. The current application combines shared Inbox and channel connections, Contacts & Audience Management, Broadcast, email templates, managed messaging services, notifications, advertising integrations, billing/entitlements, storage accounting and platform administration.

The primary application is a Spring Boot modular monolith with a React/Vite frontend. PostgreSQL is the durable system of record; Redis and Kafka support distributed/realtime and asynchronous workloads.

> For the authoritative platform map, current module status, production-readiness assessment and release process, start with [unifydesk-app/docs/platform/README.md](unifydesk-app/docs/platform/README.md).

## Repository structure

- `unifydesk-app/backend/` — Java 25 / Spring Boot 4.1.1 API and background workers.
- `unifydesk-app/frontend/` — React 19, TypeScript 6 and Vite 8 web application.
- `unifydesk-app/backend/src/main/resources/db/migration/` — Flyway migrations; the audited platform baseline contains migrations through V83.
- `unifydesk-app/k8s/` — current k3s/Traefik development/small-host manifests.
- `unifydesk-app/docs/` — feature contracts, architecture, QA history and platform documentation.
- `email-provider/` — **UnifyDesk Email**, independently operated email API product (its own tenant/project identity, API keys, Flyway schema and mail infrastructure), currently in development; see [product API](email-provider/docs/PRODUCT_API_R1.md).
- `unifydesk-website/` — public UnifyDesk website.
- `.github/workflows/` — repository checks and feature-specific QA workflows.

## Major capabilities

Current source includes:

- authentication, JWT sessions, email verification, password recovery and 2FA;
- companies/workspaces, memberships, IAM/RBAC and platform administration;
- billing catalog, subscriptions, pricing and entitlements;
- Gmail, Microsoft, IMAP/SMTP and Meta-related channel connections;
- unified Inbox, provider sync/read state and realtime WebSocket updates;
- Broadcast preparation/delivery with multiple email/SMS transport integrations;
- versioned email templates and media assets;
- media storage, quotas, deferred deletion and storage accounting;
- UnifyDesk Managed Email with SES-oriented domain/sender control and delivery events;
- UnifyDesk Managed SMS with MSG91/DLT-oriented control and delivery;
- in-app, email and browser-push notifications and digests;
- Meta WhatsApp control plane, messaging, templates, media and webhook processing;
- Contacts, identities, tags, Lists, Segments, consent/suppression, import/export and bulk jobs;
- Google Ads / Meta Ads Ad Center capabilities;
- durable async job state, prioritized Kafka topics/retry/DLT flows and Redis-backed coordination.

Source availability does not mean every module is production-ready. See [MODULE-STATUS.md](unifydesk-app/docs/platform/MODULE-STATUS.md) and [PRODUCTION-READINESS.md](unifydesk-app/docs/platform/PRODUCTION-READINESS.md).

## Local development

Requirements depend on the path you run, but the primary stack expects Java 25, Node 24+, Docker and a local PostgreSQL/Redis/Kafka environment.

Start from the application directory:

```bash
cd unifydesk-app
cp .env.example .env
```

Populate development-only values in `.env`. Never commit secrets.

For the Compose path:

```bash
docker compose up --build
```

For the local k3s path, review [unifydesk-app/k8s/README.md](unifydesk-app/k8s/README.md) and use `unifydesk-app/k8s.sh`. The checked-in Kubernetes topology is a development/small-host baseline, not the final production architecture.

## Verification commands

The commands below are the repository's standard verification paths. Their presence here is not a claim that they executed successfully for the current branch.

Backend:

```bash
docker compose -f unifydesk-app/compose.test.yml up -d --wait
cd unifydesk-app/backend
./mvnw -B spotless:check test
./mvnw -B clean test
./mvnw -B package -DskipTests
```

Frontend:

```bash
cd unifydesk-app/frontend
npm ci
npm test
npm run build
npm run lint
npm run format:check
```

Email-provider service:

```bash
cd email-provider
./mvnw -B spotless:check test
```

Website:

```bash
cd unifydesk-website
npm ci
npm run build
```

When finished with the application integration-test dependencies:

```bash
docker compose -f unifydesk-app/compose.test.yml down
```

Verification results must use the evidence terminology defined in [platform/README.md](unifydesk-app/docs/platform/README.md). A workflow that terminates without executing steps is not a test PASS.

## Security and production configuration

Development defaults are not production approval. Production must explicitly provide and verify, at minimum:

- JWT signing keys and secure cookie settings;
- exact CORS/realtime origins;
- proxy/TLS/forwarded-header behavior;
- provider and OAuth credentials;
- channel-credential encryption keys;
- managed-service credentials and webhook secrets;
- database/Redis/Kafka production endpoints and durability;
- secret rotation and backup/restore procedures.

Normal production Flyway startup should explicitly use `FLYWAY_BASELINE_ON_MIGRATE=false`. See [PRODUCTION-READINESS.md](unifydesk-app/docs/platform/PRODUCTION-READINESS.md) for the current baseline finding and required follow-up.

## Documentation

Platform entry point:

- [unifydesk-app/docs/platform/README.md](unifydesk-app/docs/platform/README.md)

Key platform references:

- [System overview](unifydesk-app/docs/platform/SYSTEM-OVERVIEW.md)
- [Module status](unifydesk-app/docs/platform/MODULE-STATUS.md)
- [Production readiness](unifydesk-app/docs/platform/PRODUCTION-READINESS.md)
- [Deployment architecture](unifydesk-app/docs/platform/DEPLOYMENT-ARCHITECTURE.md)
- [Release process](unifydesk-app/docs/platform/RELEASE-PROCESS.md)
- [Team workflow](unifydesk-app/docs/platform/TEAM-WORKFLOW.md)
- [Risk register](unifydesk-app/docs/platform/RISK-REGISTER.md)
- [Go-live checklist](unifydesk-app/docs/platform/GO-LIVE-CHECKLIST.md)

Feature-specific documentation remains under `unifydesk-app/docs/`; historical QA reports should be preserved as evidence for the exact SHA/round they reviewed.
