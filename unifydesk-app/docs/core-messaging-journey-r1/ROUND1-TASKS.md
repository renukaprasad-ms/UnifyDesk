# Round 1 Tasks

Audit base: `dev@1a39af74156e21ae5d4d7e7f4e0c32ddc0851daf`.

Status: L1 PLANNING ONLY. Main must review/release worker prompts. Do not start implementation from this document until the frozen worker base is published.

## Worker decision

All three workers are justified:

- **B1 — Broadcast/Audience/Channel execution integration**
- **B2 — Provider feedback, Inbox Contact association, notification integration**
- **F1 — coherent customer journey + tenant-safe frontend**

The boundaries are domain-oriented and designed to avoid editing the same high-conflict services.

## B1 owned findings

### CMJ-R1-001 — canonical audience becomes normal Broadcast backend path
- Preserve legacy group compatibility.
- Define/list canonical List/Segment/individual Contact audience APIs required by F1.
- Do not copy Contacts into permanent Broadcast group membership.
- Add bounded individual Contact selector only if frozen contract requires it.

### CMJ-R1-002 — recipient resolution preview
- Add preview service/API using canonical Contact eligibility.
- Return selected/eligible/excluded reason counts and bounded samples.
- No second eligibility rules engine.
- Ensure query/memory behavior remains bounded.

### CMJ-R1-003 — schedule/send freeze semantics
- Freeze canonical audience/source descriptor when send or schedule is confirmed.
- Scheduled execution must reuse the frozen audience rather than resolving a later List/Segment membership.
- Preserve send-time consent/suppression/identity safety recheck.
- Preserve idempotent run launch and crash recovery.

### CMJ-R1-005 — WhatsApp Broadcast execution
- Integrate Broadcast with existing Meta WhatsApp connection/template/runtime policies.
- Marketing Broadcast uses approved/fresh templates.
- Reuse Broadcast batches and Meta distributed per-phone rate limiter/cooldown.
- Stable per-recipient request/correlation identity.
- Preserve UNKNOWN semantics for ambiguous provider outcomes.
- Do not route Broadcast through an Inbox conversation requirement.

### CMJ-R1-007 — backend channel/sender readiness
- Provide a common product-level readiness response/capability contract.
- Adapt existing Managed Email, Managed SMS, direct Broadcast sender and WhatsApp health.
- Do not leak credentials/provider internals.
- Do not remove admin diagnostics.

### CMJ-R1-010 — truthful delivery capability
- Surface provider capability flags so frontend distinguishes acceptance from delivery/read.
- Do not implement speculative direct-provider webhooks merely for parity.

## B1 forbidden scope

- no Inbox Contact-link implementation;
- no provider feedback suppression bridge;
- no notification materializer redesign;
- no Ads Center;
- no new providers;
- no full removal of legacy BroadcastGroup schema;
- no frontend implementation.

## B2 owned findings

### CMJ-R1-004 — canonical suppression convergence
- Bridge trusted hard-bounce/complaint/provider opt-out feedback to canonical Contact suppression when Contact identity provenance exists.
- Preserve legacy destination suppression during migration/compatibility.
- Idempotent, company-scoped, no weaker-state overwrite.
- Reuse Contacts suppression policy/services where possible; do not duplicate compliance logic.

### CMJ-R1-006 — Inbox canonical Contact association
- Add a durable/read-efficient participant-to-Contact association or projection using `ContactIdentityLookup`.
- Email canonical matching; WhatsApp phone -> E.164 canonical matching.
- No implicit duplicate Contact creation.
- Expose matched Contact in Inbox API.
- Add Contact-side read adapter for related Inbox activity/conversations.
- Keep unknown senders valid.

### CMJ-R1-009 — Broadcast terminal notifications
- Emit durable/idempotent Broadcast completed/failed source events into existing Notification platform.
- Do not expand notification product scope.

### Delivery/event consistency
- Review Managed Email/SMS/WhatsApp event paths touched by the above changes.
- Preserve webhook authentication, dedupe, monotonicity and company correlation.
- Where a provider-specific delivery projection can enrich Contact activity safely, use a read adapter rather than copying provider payloads into Contacts.

## B2 forbidden scope

- no Broadcast audience preview/freeze engine;
- no WhatsApp Broadcast send orchestration;
- no frontend implementation;
- no new provider webhook integration unless required to preserve an existing provider path;
- no Ads Center.

## F1 owned findings

### CMJ-R1-001 UX — Contacts/Audience selection
- Replace legacy Broadcast Group/member management as the primary new Broadcast flow.
- Select Contact List, Segment, bounded Contacts.
- Keep legacy history/compatibility accessible if required.
- Add List/Segment -> Create Broadcast entry points where low-conflict.

### CMJ-R1-002 UX — eligibility preview
- Show selected/eligible/excluded and reason counts.
- Block send when no eligible recipients.
- Safe samples/actionable reasons.

### CMJ-R1-007 UX — channel/sender readiness
- Product-level Email/SMS/WhatsApp readiness.
- “UnifyDesk Managed Email” abstraction.
- Managed SMS DLT/header/template actions.
- WhatsApp connection/template actions.
- Distinguish accepted/sent vs delivered/read based on capabilities.

### CMJ-R1-008 — tenant epoch hardening
- Introduce/reuse a shared workspace generation token.
- Protect Broadcast async requests, delayed timers, realtime callbacks and mutations.
- A -> B -> A old work must remain stale.
- Apply to new journey UI and any touched Inbox Contact-link state.

### CMJ-R1-006 UX — Contact/Inbox linkage
- Matched Inbox participant -> Contact navigation.
- Contact -> related Inbox navigation.
- Explicit “Create/Add Contact” for unmatched participant; never silent creation.

### Mobile/responsive
- Ensure audience selector, preview, compose, confirmation and progress are usable on mobile.

## F1 forbidden scope

- no backend contract invention;
- no provider credential internals in normal UI;
- no Ads Center;
- no new design system;
- no removal of provider-required external setup.

## Shared acceptance requirements

- same exact frozen worker base;
- no rebase onto moving `dev`;
- PR target `dev`;
- workers do not merge own PRs;
- production-quality readable code;
- tenant isolation;
- bounded memory/query behavior;
- explicit idempotency;
- no provider success fabrication;
- focused tests plus relevant existing regression suites.

## Required tests

B1:
- List/Segment/Contacts preview reason counts and invariant;
- schedule freeze then mutate source, execution remains frozen;
- send-time opt-out/suppression/identity change -> SKIPPED;
- duplicate send/schedule retry -> one execution;
- 2k/5k/10k bounded snapshot/claim tests;
- WhatsApp template Broadcast happy/failure/429/ambiguous/idempotency tests;
- foreign audience/sender ownership.

B2:
- provider feedback -> canonical suppression idempotency/company isolation;
- duplicate/out-of-order feedback;
- Inbox email/WhatsApp exact Contact match;
- unmatched/ambiguous no auto-create;
- foreign-company identity cannot link;
- Broadcast completed/failed notification dedupe;
- Contact activity read adapter bounded queries.

F1:
- canonical List/Segment selection;
- exclusion preview;
- channel readiness states;
- A -> B -> A stale request/realtime tests;
- Contact <-> Inbox navigation;
- mobile component behavior;
- accepted vs delivered wording/capability rendering.

## Verification debt

Current CI at the audit SHA did not execute. Worker PR status must not call tests PASS unless commands actually run. L1/SEC-QA must record source and executable evidence separately.
