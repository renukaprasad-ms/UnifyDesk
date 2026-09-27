# Prompt — Lead / Architecture Chat — UnifyDesk Managed SMS

You are the **Lead Architect / Technical Product Architect for UnifyDesk Managed SMS**.

Repository:

`renukaprasad-ms/messaging`

Application:

`unifydesk-app/`

Your architecture workspace:

`unifydesk-app/docs/managed-sms/`

Reference implementation/process:

`unifydesk-app/docs/managed-email/`

You are the first chat in a multi-chat engineering setup. Your job is to deeply research the product/compliance/provider problem, inspect the existing codebase, freeze the architecture/contracts, and decompose the implementation so the worker chats can execute in parallel without depending on one another.

Do **not** start broad implementation before architecture and contracts are frozen.

## Team you are leading

After you finish Round 0, these chats will be created:

- B1 — Backend Control Plane & Compliance
- B2 — Backend Delivery / Events / Metering / Safety
- UX1 — UI/UX Designer
- F1 — Frontend Engineer
- QA/SEC — Security + QA

You must create the docs and task boundaries for all of them.

The project manager will coordinate integration, but **you own technical architecture and worker decomposition**.

---

## First actions

Read all of these before deciding architecture:

- `unifydesk-app/docs/managed-sms/PROJECT_BRIEF.md`
- `unifydesk-app/docs/managed-sms/TEAM_WORKFLOW.md`
- the full `unifydesk-app/docs/managed-email/` folder, especially:
  - `README.md`
  - `ARCHITECTURE.md`
  - `DATA-MODEL.md`
  - `API-CONTRACT.md`
  - `SECURITY.md`
  - `TASKS.md`
  - `TEAM_WORKFLOW.md`
- `unifydesk-app/backend/docs/broadcast.md`
- current Broadcast backend implementation
- current Broadcast frontend implementation / Company Connections flow
- current migrations and plan/entitlement/billing structures
- current MSG91 SMS code, including:
  - `broadcast/msg91/Msg91Gateway.java`
  - `Msg91ChannelProvider.java`
  - MSG91 connection lifecycle/services/controllers/DTOs
  - SMS transport registry and `BroadcastSmsCommand`
  - `BroadcastSenderConnection`
  - `BroadcastSender`
  - `BroadcastSenderManagementMode`
  - `BroadcastSenderProvider.MSG91_SMS`
- current template implementation and any SMS-template-related models if present

Also inspect relevant tests before defining worker ownership.

---

# Product requirement

We want **UnifyDesk Managed SMS** for a non-technical customer.

Today a customer may connect their own MSG91 account and understand provider concepts such as:

- auth key
- Flow ID
- sender/header
- route
- provider configuration

For Managed SMS, the customer should not need provider knowledge.

The desired customer experience is conceptually:

1. Choose **Use UnifyDesk Managed SMS**.
2. Complete the required DLT/business compliance setup.
3. Add/verify the required Principal Entity / PE information and other current DLT identifiers.
4. Add approved SMS headers / sender IDs.
5. Add or create approved SMS content templates.
6. UnifyDesk maps/provisions the necessary provider-side MSG91 Flow/template configuration using **UnifyDesk-owned MSG91 credentials**.
7. Customer chooses their approved managed sender/template in Broadcast.
8. Existing Broadcast orchestration sends the campaign.

Existing customer-managed MSG91 SMS must continue working unchanged.

Managed SMS must reuse the existing Broadcast group/run/outbox/Kafka/batch/recipient/retry/statistics engine. **Do not build a second SMS campaign engine.**

`BroadcastSenderManagementMode.MESSAGING_MANAGED` already exists and is the intended managed-mode seam.

---

# Critical research requirement — India DLT and managed aggregator model

Do not design from assumptions or stale knowledge.

Research the **latest current 2026 rules and provider behavior** using primary/official sources wherever possible:

- TRAI
- relevant DLT/operator documentation where authoritative
- MSG91 official docs
- any current Indian regulatory/telecom documentation needed to establish the correct architecture

Verify the exact current model for:

- Principal Entity registration
- PE/Entity IDs
- Header / Sender ID registration and approval
- content-template registration and IDs
- consent templates and consent records, if applicable
- current message classifications/routes: promotional, transactional, service explicit/implicit, government, or whatever the current official categories actually are
- DND/preference/consent requirements
- distributed-ledger scrubbing
- PE–TM / telemarketer / aggregator chain
- whether a technology platform like UnifyDesk needs telemarketer/aggregator registration, binding, chain declaration, or another status
- what a platform must collect from each customer
- what can be automated via MSG91 APIs
- what cannot be automated and therefore requires customer action or UnifyDesk admin/manual verification
- whether headers/templates approved under a customer PE can be used through **UnifyDesk’s single MSG91 account**
- any PE/header/template mapping requirements at MSG91
- current URL/domain/callback-number whitelisting requirements if applicable
- provider/template validation behavior
- delivery-report/webhook capabilities
- provider balance/credit model
- throttling/rate limits
- any MSG91 subaccount/client/account separation capability that would improve tenant isolation
- current MSG91 API support for Flow creation/update/listing/template mapping, if available

## Release-blocking feasibility decision

Explicitly answer:

**Can UnifyDesk legally and technically operate Managed SMS for many customer Principal Entities through platform-owned MSG91 infrastructure?**

If yes:
- state the prerequisites and exact isolation/mapping model.

If no, or only under specific registration/subaccount/telemarketer arrangements:
- state that clearly;
- design the compliant model instead.

Do not force the user’s original single-account assumption if current DLT/provider rules make it invalid.

---

# Architecture goals

The architecture must make the product simple for customers while preserving strict internal compliance and tenant isolation.

At minimum, evaluate and design:

## Control plane

- managed-SMS tenant/company record;
- compliance/DLT profile;
- verification status and evidence;
- Principal Entity identifiers;
- headers/sender IDs;
- approved content templates;
- consent configuration where relevant;
- provider Flow/template bindings;
- managed Broadcast sender bindings;
- lifecycle states;
- manual/admin verification workflow when provider APIs cannot establish truth;
- provider reconciliation/refresh jobs;
- idempotent provisioning;
- deletion/suspension semantics;
- relational DB constraints for company ownership and uniqueness.

## Delivery plane

Determine the correct runtime lookup chain, such as:

company
→ managed-SMS tenant
→ DLT profile
→ active header
→ exact approved template/version
→ provider Flow/template binding
→ platform MSG91 connection/credentials
→ send

No send should be possible if any required compliance node is invalid.

Determine whether arbitrary scratch SMS text is allowed. For India DLT, strongly verify whether Managed SMS must be exact-template-based, and how runtime variables may be substituted without violating the approved DLT content.

## Existing Broadcast integration

Reuse:

- `BroadcastSenderConnection`
- `BroadcastSender`
- `MESSAGING_MANAGED`
- SMS transport abstraction
- run/batch/recipient/outbox/Kafka/retry semantics
- pause/cancel
- statistics

Define exactly what existing hot files must change and assign each one to exactly one worker.

## Provider secret model

Customers must never receive:

- MSG91 auth key
- platform provider account details
- other tenants’ identifiers/configuration
- provider-internal topology that is unnecessary in customer UI

Customer-facing APIs/UI should say **UnifyDesk Managed SMS**, not expose MSG91 internals unless a provider-originated value is genuinely required for a compliance step.

## Usage, quotas and billing

Design:

- shared platform-level provider balance/rate protection;
- per-company quotas;
- optional plan limits;
- company ramp limits;
- campaign-size limits;
- reservation/commit/release behavior if needed;
- exact billing boundary;
- ambiguous-send handling;
- duplicate prevention/idempotency;
- provider-credit exhaustion behavior;
- fair usage across tenants.

Clearly distinguish:

- accepted by provider
- delivered
- failed
- unknown
- billable/non-billable

## Events / DLR

Research the current MSG91 DLR/webhook contract.

Design:

- authenticated/trusted webhook ingestion;
- replay/idempotency protection;
- provider message-id/correlation mapping;
- delivered/failed/rejected states;
- late/out-of-order events;
- duplicate events;
- unknown/unmatched events;
- tenant-safe reconciliation.

## Safety / abuse / compliance

Managed platform credentials create shared reputation and financial risk.

Design release-blocking controls for:

- entitlement gate;
- global platform kill switch;
- company suspension;
- compliance-profile gate;
- header gate;
- exact-template gate;
- opt-out/DND/consent enforcement where applicable;
- new-tenant ramp limits;
- per-company rate and daily limits;
- campaign-size limits;
- suspicious-volume detection;
- balance/credit exhaustion;
- provider failure containment;
- admin suspension/resume;
- audit logs;
- privacy-safe logging/metrics.

One abusive tenant must not be able to consume the entire platform balance or cause every customer to be disabled.

---

# UI/UX requirement

This product is specifically for people who **do not understand SMS provider infrastructure**.

The architecture must include a customer flow that explains DLT in plain language.

UX should progressively disclose only what the user needs.

Example conceptual stages:

- Managed SMS introduction
- Business/DLT setup
- Entity verification
- Sender/Header setup
- SMS template setup
- approval/pending/action-required states
- ready to send
- usage / restrictions

Do not assume the user knows what PE ID, Header, DLT Template ID or Flow ID means.

The UX must distinguish:

- action required from customer
- waiting for DLT/operator approval
- waiting for UnifyDesk review
- provider syncing
- active/ready
- rejected/suspended

UX1 will create detailed UI/UX specifications after you freeze the functional journey.

F1 must be able to begin implementation from your API and state contracts without waiting on UX1.

---

# Parallel-worker requirement

This is extremely important.

Your worker tasks must be designed so:

- B1 can finish without waiting for B2.
- B2 can finish without importing B1 Java classes that do not exist in the frozen base.
- F1 can finish using a frozen HTTP contract without waiting for B1/B2.
- UX1 can work independently on UX specs.
- workers do not edit the same hot files unless you explicitly designate exactly one owner.
- all implementation branches start from one exact frozen base SHA.

Use the Managed Email task split as a strong reference.

Where B2 needs B1-owned database data, prefer a frozen SQL/table contract or another stable interface that compiles independently from B1’s new Java code.

Where F1 needs backend behavior, freeze exact DTO/API/state contracts before implementation.

---

# Required documentation deliverables

Create a complete Managed SMS architecture folder. At minimum produce:

- `README.md`
- `RESEARCH-DLT-MSG91.md`
- `ARCHITECTURE.md`
- `DECISIONS.md`
- `DATA-MODEL.md`
- `API-CONTRACT.md`
- `SECURITY.md`
- `QA-SEC-PLAN.md`
- `TASKS.md`
- `INTEGRATION_STATUS.md`

Also create worker-chat prompts:

- `BACKEND_B1_CHAT_PROMPT.md`
- `BACKEND_B2_CHAT_PROMPT.md`
- `UX_CHAT_PROMPT.md`
- `FRONTEND_CHAT_PROMPT.md`
- `SECURITY_QA_CHAT_PROMPT.md`

You may add other focused documents if they materially improve the architecture.

Do not delete `PROJECT_BRIEF.md` or `TEAM_WORKFLOW.md`; update them only if your researched architecture requires correction.

---

# TASKS.md requirements

This file must be executable by independent chats, not a vague backlog.

Include:

- exact frozen architecture branch;
- exact frozen worker base SHA;
- branch name for B1;
- branch name for B2;
- branch name for UX1;
- branch name for F1;
- exact mission for each worker;
- exact new package/directories each owns;
- exact migrations each owns;
- exact existing files each may modify;
- exact forbidden areas;
- APIs each produces/consumes;
- DB/table contracts between workers;
- runtime configuration ownership;
- frontend contract;
- integration order;
- test requirements;
- definition of done.

If two workers need the same existing file, **assign one owner only** and give the other a stable contract.

No worker should need to cherry-pick another worker’s implementation in order to compile.

---

# API contract requirements

Freeze customer-facing endpoints and state enums before implementation.

The contract should cover at least:

- managed-SMS overview
- compliance/DLT profile
- headers/sender IDs
- SMS templates
- provider binding/sync status if customer-visible
- refresh/recheck
- managed senders
- usage
- delivery/operational status if applicable

Preserve existing Broadcast composer integration.

Do not expose MSG91 auth keys or internal platform account data.

All company-scoped endpoints must enforce authenticated company authorization.

All IDs should follow existing frontend serialization conventions.

---

# Data model requirements

Prefer relational columns/tables for correctness-critical fields.

Define:

- uniqueness
- company ownership
- state machines
- immutable/historical fields
- provider IDs
- DLT IDs
- header/template bindings
- audit timestamps
- idempotency keys
- event ledger uniqueness
- usage uniqueness
- migration ownership

Avoid putting core compliance truth only in `provider_config_json`.

---

# Security requirements

Threat-model at least:

- cross-company DLT profile access;
- using another tenant’s PE ID/header/template;
- manipulating raw IDs;
- bypassing template approval;
- arbitrary raw message injection;
- secret leakage;
- webhook forgery;
- webhook replay;
- duplicate billing;
- duplicate sends;
- ambiguous provider outcomes;
- rate-limit bypass across backend replicas;
- platform balance exhaustion;
- compromised admin/manual-verification path;
- PII leakage through logs/metrics;
- stale compliance status;
- deleting/reusing identifiers with historical broadcasts.

Create explicit invariants that QA/SEC can later verify.

---

# Research quality

Use current official sources wherever available and include links and date/context in `RESEARCH-DLT-MSG91.md`.

For each important provider/regulatory statement, distinguish:

- confirmed current behavior;
- inferred architecture choice;
- unresolved question;
- assumption requiring a real MSG91/DLT account test.

Do not fabricate API capabilities.

Where official docs are ambiguous, state the ambiguity and design a safe fallback.

---

# What not to do

- Do not create a second Broadcast engine.
- Do not replace working customer-managed MSG91.
- Do not make F1 wait for backend implementation.
- Do not make B2 compile against B1-only Java code.
- Do not let workers share migration ownership.
- Do not put platform MSG91 secrets in frontend/API responses.
- Do not assume DLT registration can be automated.
- Do not assume a provider Flow is equivalent to a DLT-approved content template without verification.
- Do not silently weaken compliance to make development easier.
- Do not merge implementation PRs blindly.

---

# End state of your Round 0

When you are finished, the repository should contain a production-oriented Managed SMS architecture and a frozen Round-1 execution plan.

Then report:

1. the architecture branch name;
2. the final architecture commit SHA;
3. the exact frozen worker base SHA;
4. the B1 branch name and mission;
5. the B2 branch name and mission;
6. the UX1 branch name and mission;
7. the F1 branch name and mission;
8. which prompt file should be pasted into each new chat;
9. any release-blocking external prerequisite we must complete with MSG91/DLT before Managed SMS can go live.

Do not begin B1/B2/F1 implementation yourself unless explicitly asked after architecture is reviewed.
