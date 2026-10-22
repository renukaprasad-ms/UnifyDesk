# UnifyDesk Email — independent product architecture

**Decision (2026-10-09):** `email-provider/` is the foundation of **UnifyDesk Email**, a standalone customer-facing product under the UnifyDesk brand. It is **not** an embedded microservice belonging to `unifydesk-app/`, and must operate for customers who have never used UnifyDesk App. UnifyDesk App is exactly one API customer.

## Product boundary

- **UnifyDesk App:** marketing/inbox/contacts/broadcast workflows. Remains a separate product with its own tenant/workspace model and product entitlements. It uses a securely stored **UnifyDesk Email project API key** to send permitted bulk email. It must not read or write the email product's private database directly.
- **UnifyDesk Email:** first-party email sending as a product. Own tenant organizations, projects, verified sender domains, API keys, security, send APIs, tracking, limits, reputation, usage ledger, billing, webhooks, operator console and infrastructure.
- **UnifyDesk Mail (gated development):** business mailboxes, inbound SMTP, durable storage and a webmail console are implemented in source. Mail has separate mailbox grants and delivery controls. IMAP and the remaining release features are still pending; see [current local implementation and QA](MAIL_LOCAL_IMPLEMENTATION_AND_QA.md). Account federation is optional; independent Email accounts remain supported.

Avoid confusion: the email product **can be deployed as multiple services internally**; "product" describes independent ownership and external contracts, not necessarily a single process or a separate source repository.

Possible hostnames (not yet provisioned): `email.unifydesk.in` (marketing site/console) and `api.email.unifydesk.in` (API). SMTP/MX hostnames require separate DNS and infrastructure review.

## Standalone runtime

```
Customer or UnifyDesk App backend
   │ public, versioned HTTPS API + scoped project API key
   ▼
UnifyDesk Email product API (auth, project authorization, billing/quota gate)
   │ PostgreSQL: organizations, projects, domains, API keys, send records
   │ Redis: atomic quotas, rate limits, abuse holds
   │ Kafka: durable outbox and delivery/feedback events
   ▼
Delivery worker ──> DKIM/MIME ──> isolated Postfix nodes / IP pools
   │                                         │
   └──────── per-recipient status <── DSN/log feedback <── remote MX servers
```

No shared database tables, no implicit company ID trust across products, no direct connection from the browser to an internal token-authenticated endpoint.

## First-party external API (R1 partial implementation, draft)

- **Implemented in source:** `POST /v1/emails`: bounded send; `Idempotency-Key` required for retries.
- **Implemented in source:** `POST /v1/email-batches`: async bulk job up to 1000 recipients and 100-recipient chunks; individual email submissions; basic per-chunk status.
- `GET /v1/emails/{id}` and `GET /v1/email-batches/{id}`: project-scoped status.
- **Not yet implemented:** `GET /v1/email-batches/{id}/recipients`: paginated per-recipient outcomes. Existing message events can be polled by message ID.
- **Implemented in source:** `POST /v1/domains`, `GET /v1/domains`, `POST /v1/domains/{id}/verify`: project/domain-scoped authorization.
- **Implemented in source:** `GET /v1/reputation`, `GET /v1/emails/{id}/events`, and `/v1/validations/jobs`: project-scoped metrics/events/validation. **Not yet implemented:** invoice-grade usage ledger.
- **Not yet implemented:** `POST /v1/webhook-endpoints`: signed, replay-protected async event delivery.
- Product **console**: organization/project creation, domain management, API keys (create/revoke/rotate; secret shown once), quotas, suppressions, audit logs, usage/billing, mail quality and support.

Authentication: HTTPS, high-entropy random project API keys; only hashed or keyed-hash secret material stored at rest; short lookup prefix and full constant-time verification. Keys are scoped to organization and project with expiry, rotation, revocation and granular actions. Tenant/project inferred from the verified key; **never** trust a caller-supplied `companyId` to authorize an operation. Admin and user login to product console use proper user identity/roles; API keys are not human login tokens. Apply request size, concurrency, per-project/hour/day and per-sending-domain caps with abuse suspension. Require domain ownership, verified sender identity, and marketing consent/unsubscribe guardrails. Marketing and transactional mail must have distinct IP/reputation pools.

## Existing internal-token filter — NOT a customer API mechanism

The `InternalApiTokenFilter` in draft PR #347 is a temporary private-service barrier. **It is not suitable for use as a third-party/public product API** because one global bearer token does not establish project/tenant identity or fine-grained authorization. Public API endpoints must remain disabled/inaccessible until project-scoped API key validation is implemented; a private mesh token may remain for worker-to-worker service endpoints.

## Integration from UnifyDesk App

- UnifyDesk Email creates an isolated `UnifyDesk App` organization/project (or an explicitly linked project).
- UnifyDesk App backend stores the project key server-side in secret storage; never in React/mobile or a workspace-config GET response.
- Broadcast launch verifies the App's existing tenant, consent, subscription and PAYG reservation first.
- App backend submits bounded, idempotent jobs to public UnifyDesk Email API.
- Email product independently checks sender domain, abuse policy, quotas and final suppression.
- Email product signs delivery webhooks; App verifies signatures, tenant/project mapping, replays and status transitions and reconciles billing.
- Choose one definitive billable-event and idempotency rule; do not charge twice across the products. App can charge its own customer for orchestration while Email charges the App organization/project at provider rates.

**SES remains the default shipping provider** in UnifyDesk App until UnifyDesk Email has independent product API, security, quotas, operational infrastructure and live deliverability validation. The new product should not depend on SES to function once its own mail nodes are ready.

## Implementation milestones

1. **Foundations:** organization/project identity, invitation/login, admin roles, hashed project API keys, audit, independent product billing and secure console.
2. **API:** versioned scoped sends, batch jobs, status, verified domains, signed outgoing webhooks and SDK documentation.
3. **Safety:** hard fail-closed tenant/IP/account budgets, suppression and unsubscribe, abuse investigation, rate controls, Postfix UNKNOWN reconciliation, complaint/bounce resilience, deliverability dashboards.
4. **Infrastructure:** real mail nodes, static IP/PTR/EHLO, SPF/DKIM/DMARC, TLS, isolated IP pools, IP warm-up, inbound feedback, runbooks and reliability tests.
5. **App connector:** treat UnifyDesk Email like a third-party provider using API credentials, provider selection, events/metering and operational rollback.
6. **Mail development:** standalone business mailbox hosting and webmail are a separate, gated milestone. Local source and QA do not imply public availability.

## Existing scope

The repository contains the delivery engine, sender-domain verification, DKIM,
validation, feedback, scoped public APIs, operator-provisioned organizations,
projects, console accounts, project API keys, bounded batches, signed outgoing
webhooks and a usage ledger. The independent console and gated hosted-mail
features are present in source. Public self-service onboarding, independent
payment collection/invoices and production acceptance remain outstanding.
Refer to the [current local QA report](MAIL_LOCAL_IMPLEMENTATION_AND_QA.md)
for verified work rather than inferring production readiness from this document.

## Scope boundary for PR #347

PR #347 contains private service hardening, a safety patch for ambiguous SMTP handoff, rate-limit scaffolding and UnifyDesk branding. It is not a standalone product launch PR. No GitHub Actions changes are required by this decision.
