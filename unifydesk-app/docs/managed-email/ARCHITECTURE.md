# Managed Email Architecture

**Owner:** Lead / Architect  
**Status:** DRAFT — Lead must replace this template before implementation.

## 1. Context and existing system

Document the current Broadcast pipeline and exactly which existing types/services will be reused. At minimum review:

- `BroadcastSenderManagementMode.MESSAGING_MANAGED`
- `BroadcastSenderConnection`
- `BroadcastSender`
- `BroadcastEmailTransportRegistry`
- `AmazonSesBroadcastEmailTransport`
- Broadcast launch/outbox/batch/recipient/retry/state-machine flow
- Company Connections and Broadcast frontend
- existing plan/entitlement/billing/accounting primitives

Managed Email must extend the current engine rather than fork it.

## 2. AWS SES design decision

Research current AWS SES from official AWS sources and document the selected design.

Mandatory topics:

- SES v2 Tenant Management and whether each UnifyDesk company maps 1:1 to an SES tenant.
- Tenant resource associations for identities and configuration sets.
- Tenant-level reputation policy and sending status.
- Tenant-level suppression lists.
- Region strategy and regional limitations.
- Sandbox/production-access implications.
- Account-level quotas versus tenant/application limits.
- Configuration sets and event destinations.
- Bounce, complaint, reject, delivery, open and click event handling.
- Virtual Deliverability Manager if applicable.
- Dedicated IPs / managed dedicated IPs as a future tier, not an MVP assumption.
- Cost implications of tenant management and telemetry.

Do not assume SES tenant isolation removes UnifyDesk's responsibility for account-wide reputation. Document residual account-level risk.

## 3. Sender onboarding models

### 3.1 Customer-owned domain

Define:

- domain normalization and ownership constraints;
- CreateEmailIdentity flow;
- Easy DKIM record generation;
- verification polling/state machine;
- whether custom MAIL FROM is MVP or later;
- SPF/MX records if custom MAIL FROM is enabled;
- DMARC recommendation/validation UX;
- sender local-part rules;
- re-verification, deletion and domain replacement;
- DNS timeout and stale onboarding cleanup;
- idempotency.

### 3.2 UnifyDesk-hosted subdomain

Define:

- slug generation and collision policy;
- reserved/prohibited names;
- whether each customer receives a dedicated SES subdomain identity or safely inherits from a parent identity;
- how SES tenant resource association affects that choice;
- DNS automation for `*.unifydesk.in`;
- sender local-part policy;
- abuse safeguards;
- rename/company-name-change behavior;
- subdomain release/reuse policy.

Never let a tenant claim another tenant's subdomain or From address.

## 4. Control plane

Define the provisioning state machine, e.g.

`REQUESTED -> PROVISIONING -> DNS_PENDING -> VERIFYING -> ACTIVE`

plus failure/suspended/deleting states.

Define APIs, idempotency keys, persistence, retries, scheduled verification and cleanup.

No AWS credential is exposed to customer users.

## 5. Data plane

Define how an active managed sender reaches the existing Broadcast transport.

Specify:

- connection/provider/management-mode invariants;
- SES tenant name;
- identity and configuration set selection;
- provider message ID and recipient correlation;
- retry/UNKNOWN behavior consistent with current Broadcast semantics;
- rate limiting at account + company/tenant + campaign levels;
- pause/suspend behavior.

## 6. Event pipeline

Define authenticated/authorized event ingestion and idempotent reconciliation for:

- SEND/DELIVERY
- BOUNCE
- COMPLAINT
- REJECT
- OPEN
- CLICK

Clarify whether SNS, EventBridge, Firehose or another AWS-supported destination is used and why.

Event processing must never allow one company's event to mutate another company's recipient/suppression state.

## 7. Abuse, compliance and reputation

Define at minimum:

- permission/consent expectations;
- unsubscribe handling;
- company-level suppression;
- hard-bounce and complaint suppression;
- rate/volume limits for new customers;
- warm-up/ramp policy if needed;
- spam/abuse reporting;
- automatic and manual suspension;
- reputation thresholds and alerts;
- platform kill switch;
- risky-domain/local-part restrictions;
- audit log.

## 8. Metering and commercial controls

Define the billable unit and source of truth.

Cover:

- accepted vs delivered billing semantics;
- free/plan allowance;
- hard limits;
- soft alerts;
- retry charging rules;
- failed/rejected message treatment;
- idempotent usage accounting;
- reconciliation.

## 9. Security

Threat model:

- horizontal tenant access;
- sender spoofing;
- SES resource ID substitution;
- DNS verification takeover;
- subdomain squatting;
- forged provider events;
- replayed callbacks;
- privilege escalation;
- leaked AWS credentials;
- SSRF or arbitrary DNS/URL inputs;
- mass-mail abuse;
- broken authorization on sender/domain APIs.

## 10. Data model

List new and changed tables/entities and ownership. Avoid JSON blobs for core lifecycle state when relational fields are required for querying, locking or constraints.

Include uniqueness constraints for tenant/domain/subdomain/sender identity ownership.

## 11. API contract

Document exact endpoints and request/response/error contracts required by Frontend before coding starts.

## 12. Observability

Define metrics/logs/alerts for provisioning, verification, sends, rate-limit decisions, bounces, complaints, tenant suspension, event lag, reconciliation failures and AWS API failures.

Do not log recipient content, secrets or raw credentials unnecessarily.

## 13. Rollout

Define feature flag, admin enablement, migration/backfill, sandbox testing, pilot tenants, rollback and kill-switch behavior.

## 14. Explicit non-goals

Document what is intentionally outside the first release.
