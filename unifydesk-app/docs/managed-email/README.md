# UnifyDesk Managed Email

This folder is the coordination and architecture workspace for the UnifyDesk-managed email broadcast initiative.

## Product goal

UnifyDesk already supports customer-managed broadcast providers such as Amazon SES, SendGrid, Azure Email and MSG91. Managed Email adds a zero-infrastructure path for customers who do not want to understand or operate those providers.

Two onboarding paths must be supported:

1. **Customer-owned domain**
   - Customer enters a domain they control.
   - UnifyDesk provisions the required Amazon SES resources in the UnifyDesk AWS account.
   - UnifyDesk returns the DNS records the customer must add.
   - UnifyDesk verifies the records and activates one or more sender addresses such as `sales@example.com`.

2. **UnifyDesk-hosted subdomain**
   - Customer does not own a domain.
   - UnifyDesk allocates a safe unique subdomain such as `xyz.unifydesk.in`.
   - UnifyDesk owns the DNS and SES setup.
   - The customer chooses an approved local-part such as `offers@xyz.unifydesk.in`.
   - The customer never receives AWS credentials.

The existing Broadcast engine remains authoritative. Managed Email must extend the current `BroadcastSenderConnection -> BroadcastSender -> BroadcastEmailTransport` model and use `BroadcastSenderManagementMode.MESSAGING_MANAGED`; it must not create a second broadcast engine.

## Team

Five chats work as one team:

- Lead / Architect
- Backend A — Managed Email Control Plane
- Backend B — Managed Email Delivery & Events
- Frontend
- Security & QA

Read `TEAM_WORKFLOW.md` before starting any implementation.

## Shared documents

- `ARCHITECTURE.md` — owned by Lead.
- `TASKS.md` — owned by Lead; source of implementation scope.
- `DECISIONS.md` — architecture decision log, owned by Lead.
- `INTEGRATION_STATUS.md` — merge/integration state, owned by Lead.
- `SECURITY_QA_FEEDBACK.md` — owned by Security & QA during review rounds.

Role-specific prompts are stored in this folder so each chat can be started with the exact same operating rules.
