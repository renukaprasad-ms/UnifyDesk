# UnifyDesk Managed SMS — Project Brief

Status: bootstrap brief for Lead / Architecture research

Repository: `renukaprasad-ms/messaging`  
Application: `unifydesk-app/`  
Reference architecture: `unifydesk-app/docs/managed-email/`

## Product goal

Add **UnifyDesk Managed SMS** for non-technical customers who should not need to understand or connect MSG91 credentials, Flow IDs, routes, provider dashboards, or provider account configuration.

The intended customer experience is:

1. Customer chooses **UnifyDesk Managed SMS**.
2. For India-first SMS, the customer completes the legally required DLT onboarding and supplies or verifies the required business/DLT identifiers, approved headers/sender IDs, and approved message-template information.
3. UnifyDesk validates and stores the customer-scoped compliance configuration.
4. UnifyDesk uses platform-owned MSG91 infrastructure and credentials.
5. UnifyDesk creates or maps the provider-side Flow/template configuration required to send the customer’s approved content.
6. The customer creates/selects a sender and approved SMS template in UnifyDesk.
7. The normal Broadcast workflow sends SMS without exposing MSG91 internals or credentials.

The Lead must verify which parts of DLT/header/template onboarding can actually be automated through current provider/operator APIs and which require customer or operations-team action. Do not assume that DLT registration, header approval, template approval, or provider Flow creation is automatable.

## Existing implementation that must be reused

The repo already has important SMS and Broadcast seams:

- `BroadcastSenderManagementMode.CUSTOMER_MANAGED`
- `BroadcastSenderManagementMode.MESSAGING_MANAGED`
- `BroadcastSenderConnection`
- `BroadcastSender`
- `BroadcastSmsCommand`
- MSG91 SMS provider support
- `Msg91Gateway`
- `POST https://control.msg91.com/api/v5/flow` integration
- provider Flow ID + message variable configuration
- approved sender/header usage through `BroadcastSender.fromAddress`
- Broadcast groups, runs, batches, recipients, outbox/Kafka, retries, pause/cancel and stats

Managed SMS must extend this architecture. It must **not** create a second campaign, recipient, batch, retry, scheduling or statistics engine.

Existing customer-managed SMS/provider setup must continue working unchanged.

## Initial managed provider

MSG91 is the initial managed SMS provider.

The architecture should keep a provider boundary so another managed SMS provider could be added later without redesigning the Broadcast core, but MVP must not over-engineer for hypothetical providers.

## India-first compliance question

The first release is expected to support India DLT-regulated SMS. The Lead must research the current 2026 requirements and determine the exact product truth for:

- Principal Entity / PE registration and identifiers;
- Header / Sender ID registration and ownership;
- content-template registration and provider/template mapping;
- consent templates / consent records where applicable;
- promotional, service, transactional or other current message categories/routes;
- PE–TM / telemarketer or aggregator chain requirements;
- DLT scrubbing requirements;
- URL, domain, callback-number or other content-whitelisting requirements if currently applicable;
- opt-out / DND / preference requirements;
- whether a single UnifyDesk-owned MSG91 account can technically and legally send for multiple customer Principal Entities;
- what identifiers must be passed to MSG91 for each send;
- whether UnifyDesk itself needs any telemarketer/aggregator registration or operator-side relationship for this model.

This question is release-blocking. If the requested shared-account model is not permitted or has prerequisites, the architecture must state that clearly and design the correct compliant model instead of forcing the original assumption.

## Team model

Use a six-role multi-chat team: **Lead + five execution/review chats**.

- Lead / Architecture
- B1 — Backend Control Plane & Compliance
- B2 — Backend Delivery, Events, Metering & Safety
- UX1 — UI/UX Designer
- F1 — Frontend Engineer
- QA/SEC — Security & QA

To avoid merge conflicts, UX1 should primarily own design/UX specifications and review artifacts. F1 is the sole frontend-code owner unless the Lead explicitly assigns non-overlapping frontend files.

## Worker split expected from the Lead

### B1 — Control Plane & Compliance

Likely responsibilities to research and freeze:

- managed-SMS tenant/company setup;
- DLT/PE profile and verification state;
- headers/sender IDs and lifecycle;
- approved SMS templates and provider mappings;
- MSG91 managed-account connection/bootstrap;
- provider Flow/template creation or mapping where supported;
- admin/manual verification workflows where automation is impossible;
- control-plane APIs;
- idempotent provider provisioning;
- persistence/migrations owned by B1.

B1 must not own runtime broadcast delivery, webhook reconciliation or metering.

### B2 — Delivery, Events, Metering & Safety

Likely responsibilities to research and freeze:

- `MESSAGING_MANAGED + MSG91_SMS` runtime delivery;
- selecting the correct company/header/template/Flow binding;
- message-variable validation and exact approved-template enforcement;
- delivery-report/webhook ingestion and recipient reconciliation;
- provider outcome semantics including ambiguous outcomes;
- shared platform credit/balance/rate limits;
- per-company quotas and usage metering;
- billing boundary;
- opt-out/suppression checks;
- anti-abuse and platform kill switches;
- runtime tests.

B2 must not own DLT/header/template onboarding UI or B1 provisioning.

### F1 — Frontend Engineering

Likely responsibilities after Lead freezes contracts:

- “Use UnifyDesk Managed SMS” entry point;
- simple DLT onboarding/status flow;
- PE/header/template setup and verification states;
- managed sender setup;
- SMS template creation/mapping flow;
- managed sender/template selection in Broadcast;
- usage and sendability status;
- safe customer-facing wording that hides MSG91 internals;
- frontend tests.

F1 must not modify backend code.

### UX1 — UI/UX

Own user-journey and design artifacts, not shared backend contracts:

- zero-provider-knowledge onboarding journey;
- progressive disclosure for unfamiliar DLT concepts;
- wireframes / component behavior;
- empty, pending, rejected, suspended and action-required states;
- copy for compliance requirements;
- accessibility/mobile behavior;
- design review of F1 implementation.

### QA/SEC

Starts after B1+B2+F1 are integrated. It must test authorization, tenant isolation, compliance bypasses, provider/webhook trust, abuse controls, billing/usage correctness, template/header mismatches and end-to-end flows.

## Non-negotiable principles

1. Customers never receive UnifyDesk’s MSG91 auth key or provider secrets.
2. Every managed-SMS resource is company-scoped; raw IDs never imply authorization.
3. A customer may only send using an eligible verified company compliance profile, header and approved template mapping.
4. Provider acceptance, final delivery and customer billing semantics must be explicitly separated.
5. Shared platform balance/rate capacity requires platform-level and company-level limiting.
6. One abusive tenant must be containable without disabling every customer.
7. Compliance state must be queryable in relational data where correctness depends on it; do not hide core truth only in opaque JSON.
8. Provisioning and provider calls must be idempotent/retryable and must not hold long database transactions.
9. Existing `CUSTOMER_MANAGED` MSG91 SMS continues to work unchanged.
10. The Lead must freeze contracts and exact file ownership before B1/B2/F1 begin.

## Lead output

The Lead is expected to research, inspect the codebase and then create the full managed-SMS documentation set and worker prompts. This bootstrap brief intentionally does not pre-decide the final architecture.
