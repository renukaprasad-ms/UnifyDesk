# Managed Email Architecture Decisions

Status: Frozen for Round 1  
Date: 2026-09-26

## ADR-001 — Reuse the existing Broadcast engine

Decision:
Managed Email is a sender/control-plane extension of existing Broadcast, not a new sending engine.

Use:
- BroadcastSenderConnection
- BroadcastSender
- BroadcastRun
- BroadcastOutbox
- Kafka preparation
- BroadcastBatch
- BroadcastRecipient
- BroadcastEmailTransportRegistry

Reason:
The current engine already solves persistent groups, snapshotting, outbox reliability, bounded batches, retry/UNKNOWN semantics, distributed workers and statistics. Duplicating it would create two delivery systems and inconsistent behavior.

## ADR-002 — Use MESSAGING_MANAGED on existing AMAZON_SES connections

Decision:
A Managed Email company has one BroadcastSenderConnection:
- provider AMAZON_SES
- managementMode MESSAGING_MANAGED
- no customer AWS credentials
- configured Managed Email region

Customer-managed SES remains CUSTOMER_MANAGED.

Reason:
The enum and data model were explicitly created for this split.

## ADR-003 — Map one UnifyDesk company to one SES tenant

Decision:
One company maps to one SES tenant.

Reason:
Current SES tenant management gives the clearest provider-side attribution, tenant resource association, sending status/reputation controls and tenant suppression boundary.

Non-goal:
An SES tenant is not treated as a separate AWS account. Platform-wide account health, quota and abuse controls remain mandatory.

## ADR-004 — One SES configuration set per company

Decision:
One company/tenant gets one configuration set.

Reason:
- deterministic event attribution;
- tenant resource association;
- future IP pool assignment;
- company-level operational controls;
- avoids a giant shared configuration set becoming a difficult lifecycle boundary.

## ADR-005 — One SES domain identity per Managed Email domain

Decision:
Every customer-owned domain and every hosted UnifyDesk subdomain is created as its own SES domain identity using Easy DKIM.

Do not create an SES identity for every sender email address.

Reason:
A verified domain supports multiple From addresses while allowing UnifyDesk to keep sender ownership locally.

## ADR-006 — Easy DKIM is required

Decision:
Managed Email requires successful SES domain verification and DKIM before a domain is ACTIVE.

Reason:
It provides a simple current SES-managed signing model and the primary DMARC alignment path for MVP.

## ADR-007 — Custom MAIL FROM is deferred

Decision:
No custom MAIL FROM in MVP.

Reason:
Default SES MAIL FROM provides SPF authentication. Custom MAIL FROM adds MX/TXT records, fallback semantics and onboarding complexity without being necessary for MVP authentication.

Future:
Add as an optional deliverability feature if operational data justifies it.

## ADR-008 — Own-domain DMARC is recommended, not required

Decision:
Detect/assess DMARC best-effort and warn when absent/weak. Do not block domain activation solely because DMARC is missing.

Never change a customer's DMARC record.

Reason:
Easy DKIM already supplies an aligned authentication path; requiring DMARC on day one would increase onboarding friction and risk overwriting an existing policy.

Hosted UnifyDesk subdomains receive platform-managed DMARC.

## ADR-009 — Single SES region for MVP

Decision:
Managed Email uses one configured SES region.

Reason:
SES identities, quotas, event resources and related behavior are regional. Multi-region would multiply identity/DNS/event/failover complexity before it is needed.

Future:
Multi-region is a deliberate migration, not automatic failover.

## ADR-010 — Shared IPs for MVP

Decision:
Use SES shared sending infrastructure initially.

Reason:
Dedicated IPs require adequate volume and reputation management. They are not a universal deliverability improvement.

Future:
Configuration-set-to-sending-pool mapping supports high-volume/premium tiers.

## ADR-011 — SNS HTTPS for provider events in Round 1

Decision:
SES configuration-set events publish to a shared platform SNS topic consumed by one authenticated HTTPS endpoint.

Reason:
- supported directly by SES;
- no customer callback;
- no additional SQS SDK dependency for B2;
- signed SNS envelopes;
- SNS retry behavior;
- normalized event processing can later move behind SQS without changing domain logic.

Security:
Signature, cert URL, topic ARN, timestamp/replay and idempotency checks are mandatory.

## ADR-012 — Application suppression remains product truth

Decision:
UnifyDesk application suppression determines product send eligibility.

SES tenant suppression is enabled for BOUNCE and COMPLAINT as provider defense-in-depth.

Reason:
Application suppression also represents unsubscribe, admin block, invalid address and future compliance cases that are not equivalent to SES provider suppression.

## ADR-013 — Provider acceptance is the billing boundary

Decision:
A Managed Email message is billable when SES accepts the provider request.

Delivery success is not required.

Reason:
AWS/provider cost and platform work are incurred at acceptance; asynchronous bounce/complaint does not undo the send.

UNKNOWN is reserved until acceptance can be reconciled.

## ADR-014 — Usage uses reservation + idempotent ledger

Decision:
Reserve quota before provider submission, commit on acceptance, release on definite pre-acceptance failure, retain reservation on UNKNOWN, and reconcile later.

Reason:
This prevents concurrent pods from overshooting monthly limits and prevents duplicate callbacks/retries from double billing.

## ADR-015 — Add a hierarchical managed rate limiter

Decision:
The current connection-scoped BroadcastRateLimiter is retained for existing providers but is not sufficient for Managed Email.

Managed sends enforce atomically:
- account/region rate;
- company/tenant rate;
- optional plan/tier rate;
- optional run/campaign rate;
plus hourly/daily/ramp caps.

Reason:
All MESSAGING_MANAGED companies share UnifyDesk's SES account quota.

## ADR-016 — Hosted subdomains are immutable reservations

Decision:
A hosted subdomain is never reassigned after release.

Reason:
Email addresses can remain in contact lists, old messages and reputation systems indefinitely. Reassignment creates impersonation, stale-link and reputation risk.

Company rename does not rename an existing subdomain.

## ADR-017 — Hosted subdomains always include an opaque random suffix

Decision:
Use readable normalized company prefix plus a persisted secure random suffix.

Example:
abc-technologies-k7m4q2.unifydesk.in

Reason:
- duplicates are naturally handled;
- less race/collision complexity;
- avoids raw company IDs;
- reduces obvious name squatting;
- company display-name changes do not affect identity.

## ADR-018 — Core lifecycle state must be relational

Decision:
Tenant/domain/provisioning/usage/event core states are columns/enums/tables, not only provider_config_json.

Reason:
Authorization, recovery, indexes, invariants and operations must be queryable and enforceable.

Provider-specific diagnostics may use JSON as a secondary field.

## ADR-019 — External calls never run inside long DB transactions

Decision:
Provisioning APIs persist desired state + durable operation, commit, then workers call SES/DNS.

Reason:
Avoids locks around network latency and makes retries/crash recovery explicit.

## ADR-020 — Do not expose AWS internals in customer APIs

Decision:
Customer-facing responses do not return:
- SES tenant name/id;
- configuration-set name;
- AWS role;
- AWS account ID;
- platform quota details;
- AWS credentials.

DNS records are returned because the customer must configure them, but are labeled as UnifyDesk verification/authentication records.

## ADR-021 — Keep richer SES analytics outside BroadcastRecipient enum in Round 1

Decision:
Do not add DELIVERED/OPENED/CLICKED/COMPLAINED to the core recipient state machine in Round 1.

Store an immutable event ledger and recipient delivery projection instead.

Reason:
Open/click are not mutually exclusive delivery states, events can be out of order, and changing the existing stats/state machine would cause broad coupling. Hard bounce can still update the existing BOUNCED state where appropriate.

## ADR-022 — Separate managed send context from provider identity

Decision:
Keep exactly one AMAZON_SES BroadcastEmailTransport registration.

Inside it, resolve a SES send context based on management mode:
- CUSTOMER_MANAGED: existing customer credential client;
- MESSAGING_MANAGED: platform workload client + tenant/configuration-set context.

Reason:
The registry keys by provider, so registering a second AMAZON_SES transport is incorrect. A focused send-context strategy preserves SOLID design without a giant provider switch.

## ADR-023 — Platform workload credentials only for Managed Email

Decision:
Managed Email never stores customer AWS secrets.

Provisioning and sending use platform workload identity/IAM roles with least privilege.

Prefer separate provisioning and sending roles where deployment supports it.

## ADR-024 — Customer-owned domain claims can be released, hosted names cannot

Decision:
A customer-owned domain may later be claimed by another company only after:
- previous claim is fully released/deleted;
- provider association is removed;
- new claimant performs fresh DNS verification.

Hosted UnifyDesk subdomains are never reused.

Reason:
Real domain ownership can legitimately transfer, while platform-hosted addresses are persistent platform identifiers.

## ADR-025 — Architecture contracts, not implementation order, coordinate Round 1

Decision:
B1, B2 and F1 start from the same baseline and frozen docs.

They must not wait for each other's code.

Shared DB/API contracts are defined here/API-CONTRACT/DATA-MODEL. Shared hot source files have one owner in TASKS.md.

Reason:
This minimizes merge conflicts and allows true parallel implementation.
