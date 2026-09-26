# Managed Email Multi-Chat Workflow

## Ground rule

No implementation chat invents architecture independently. The Lead researches, freezes contracts and task boundaries, then the implementation chats work inside those boundaries.

## Round 0 — Lead research and architecture

Lead must:

1. Inspect current Broadcast backend, Company Connections UI, database model, provider abstractions, billing/plan limits, Kafka/outbox flow and tests.
2. Research current Amazon SES capabilities from official AWS documentation.
3. Complete `ARCHITECTURE.md`, `DECISIONS.md`, and `TASKS.md`.
4. Define API contracts, entity ownership, migration ownership, package boundaries and integration points before implementation begins.
5. Explicitly evaluate Amazon SES native Tenant Management rather than designing tenant isolation from assumptions.
6. Freeze a Round-1 contract version in `TASKS.md`.

Implementation work starts only after Round 0 docs are merged into `dev`.

## Round 1 — Three implementation chats in parallel

All three branches must be created from the same `dev` commit after Lead docs are merged.

### Backend A — Control Plane

Suggested branch: `feature/managed-email-control-plane`

Owns only:
- managed-email account/domain onboarding APIs;
- SES tenant/resource provisioning;
- customer-domain identity creation and DNS verification state;
- UnifyDesk-hosted subdomain allocation;
- sender identity lifecycle;
- managed-email persistence/models/migrations assigned by Lead;
- provisioning idempotency and cleanup.

Must not own:
- broadcast delivery worker;
- SES send transport;
- provider event ingestion;
- frontend.

### Backend B — Delivery, Events & Metering

Suggested branch: `feature/managed-email-delivery-events`

Owns only:
- MESSAGING_MANAGED SES delivery path;
- tenant-aware/configuration-set-aware sending;
- delivery/bounce/complaint/reject reconciliation;
- suppression integration;
- reputation and safety state consumed by sending;
- usage metering and managed-email quota enforcement assigned by Lead;
- delivery/event tests.

Must not own:
- domain onboarding UI/APIs;
- domain/subdomain provisioning except through Lead-defined interfaces;
- frontend.

### Frontend

Suggested branch: `feature/managed-email-ui`

Owns only:
- "Use UnifyDesk Managed Email" entry point;
- choose customer domain vs UnifyDesk subdomain;
- DNS records UI with copy/status/refresh;
- sender-address setup;
- verification/pending/error states;
- managed-email sender selection in Broadcast;
- usage/safety/reputation UX explicitly assigned by Lead;
- frontend tests.

Must not modify backend source files.

## Integration order

Recommended merge order:

1. Backend A
2. Backend B
3. Frontend
4. Lead performs integration review and resolves shared-file glue

Implementation chats must avoid broad refactors unrelated to their task.

## Round 1 review — Security & QA

Security & QA starts only after all Round-1 work is integrated into one reviewable branch or `dev`.

Suggested branch: `review/managed-email-security-qa-r1`

Security & QA:
- does not redesign while testing;
- performs code review, threat modeling, abuse testing, API authorization review, tenant-isolation tests and end-to-end flow testing;
- records every finding in `SECURITY_QA_FEEDBACK.md` with severity, evidence, reproduction and recommended invariant;
- may add tests when useful, but does not silently implement architectural fixes.

## Round 2

Lead reviews `SECURITY_QA_FEEDBACK.md` and updates:
- `ARCHITECTURE.md`
- `DECISIONS.md`
- `TASKS.md`

Lead labels findings as accepted, rejected with reason, or deferred with risk.

Backend A, Backend B and Frontend then create Round-2 fix branches from the integrated base. Security & QA retests afterward.

Repeat until exit criteria are satisfied.

## Conflict prevention

- Lead is the only writer of `ARCHITECTURE.md`, `TASKS.md`, `DECISIONS.md`, and `INTEGRATION_STATUS.md`.
- Security & QA is the only writer of `SECURITY_QA_FEEDBACK.md` during a review round.
- Backend A and Backend B must use separate new packages/files wherever possible.
- If both backend workers need the same existing file changed, Lead assigns exactly one owner in `TASKS.md`; the other worker consumes the resulting contract.
- Frontend never edits backend.
- Each PR must list shared files changed. A shared-file change not assigned in `TASKS.md` is a merge blocker.
- No chat merges its own PR unless explicitly instructed by the user.
