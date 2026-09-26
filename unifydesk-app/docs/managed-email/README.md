# UnifyDesk Managed Email

This folder is the architecture and coordination workspace for UnifyDesk Managed Email.

## Product goal

UnifyDesk already supports customer-managed Amazon SES, SendGrid, Azure Email and MSG91 Broadcast providers. Managed Email adds a zero-provider-knowledge path where UnifyDesk owns the sending infrastructure.

Two onboarding paths are supported:

1. Customer-owned domain — customer enters a domain, UnifyDesk provisions its managed email resources, returns only the DNS records the customer must publish, verifies them, then allows addresses such as sales@example.com.
2. UnifyDesk-hosted subdomain — UnifyDesk allocates a safe immutable domain such as abc-technologies-k7m4q2.unifydesk.in, manages DNS and email authentication automatically, then allows approved sender addresses under it.

Managed Email reuses the existing Broadcast execution engine and BroadcastSenderManagementMode.MESSAGING_MANAGED. It does not create a second campaign, recipient, outbox, batching, Kafka, retry or statistics engine.

## Team and workflow

Five chats work as one team:

- Lead / Architect
- Backend A / B1 — Control Plane
- Backend B / B2 — Delivery, Events & Metering
- Frontend / F1
- Security & QA

Read TEAM_WORKFLOW.md before implementation. Role-specific chat prompts remain in this folder and are subordinate to the frozen architecture/contracts in TASKS.md.

## Frozen architecture documents

- ARCHITECTURE.md — complete production architecture and lifecycle.
- RESEARCH-AWS-SES.md — current official SES research and source links.
- DECISIONS.md — accepted architecture decisions.
- DATA-MODEL.md — relational model, states, constraints and migration boundaries.
- API-CONTRACT.md — frozen customer/backend contract for parallel work.
- SECURITY.md — threat model, IAM/event trust, tenant isolation and abuse controls.
- QA-SEC-PLAN.md — post-integration review plan.
- TASKS.md — exact Round-1 ownership and forbidden-file boundaries.
- INTEGRATION_STATUS.md — round/branch/PR status.
- SECURITY_QA_FEEDBACK.md — QA/SEC findings in the review round.

## Existing architecture reused

The current repo already contains the important seams:

- BroadcastSenderConnection
- BroadcastSender
- BroadcastSenderManagementMode.CUSTOMER_MANAGED
- BroadcastSenderManagementMode.MESSAGING_MANAGED
- BroadcastEmailTransport / Registry
- AmazonSesBroadcastEmailTransport
- BroadcastLaunchService
- BroadcastDeliveryService
- Broadcast outbox/Kafka/batches/recipients/retries
- BroadcastRateLimiter
- BroadcastSuppression
- BroadcastStats
- ChannelConnection / SenderIdentity
- CompanyAuthorizationService
- EntitlementService

## Non-negotiable release principles

1. Managed senders require the company, managed tenant, connection, domain and sender to all be eligible.
2. Every company-scoped lookup includes company ownership. Raw IDs are never authorization.
3. Customers never receive UnifyDesk AWS credentials or SES tenant/configuration-set internals.
4. Managed From addresses are constructed server-side under an ACTIVE verified domain.
5. Provider acceptance is the technical billing boundary; later bounce/complaint does not undo an accepted send.
6. Application suppression is checked before provider submission; SES tenant suppression is defense-in-depth for bounce/complaint.
7. One abusive tenant must be containable before it threatens the shared account.
8. External provisioning is idempotent/retryable and no long DB transaction wraps SES/DNS calls.
9. Hosted subdomain reservations are immutable and never reused.
10. Round-1 workers obey exact ownership in TASKS.md.

## Shared prerequisite

SES Tenant Management launched after the repository's former AWS SDK line. The Lead architecture branch updates the AWS SDK dependency before worker branching so B1 tenant APIs and B2 tenant-qualified SendEmail use the same SDK surface.

Implementation starts only after this Lead architecture branch is merged into dev, per TEAM_WORKFLOW.md.
