# UnifyDesk Managed SMS

Status: Round-0 architecture frozen for implementation planning  
Customer-facing name: **UnifyDesk Managed SMS**  
Initial provider: MSG91 SMS  
Initial jurisdiction: India / DLT-regulated A2P SMS

## Product goal

Managed SMS lets a non-technical company send compliant SMS broadcasts without connecting or learning MSG91. The company remains the Principal Entity (PE) for its own business communication, completes the legally required DLT actions, and gives UnifyDesk the approved identifiers needed to operate the sending path.

The customer journey is:

1. Choose **Use UnifyDesk Managed SMS**.
2. Register/verify the business Principal Entity on a DLT platform.
3. Complete the required PE–Telemarketer chain declaration.
4. Add approved Headers/Sender IDs.
5. Add approved Content Templates and their DLT Template IDs.
6. Complete CTA/consent requirements when applicable.
7. UnifyDesk maps the approved PE/Header/Template combination into platform-owned MSG91 infrastructure.
8. Customer chooses an approved managed sender and template in Broadcast.
9. The existing Broadcast run/outbox/Kafka/batch/recipient engine performs delivery.

Existing CUSTOMER_MANAGED MSG91 remains unchanged.

## Release-blocking feasibility decision

**Conditional YES.** MSG91 publishes a B2B-platform model in which each client completes DLT registration and the platform can use the client's DLT-approved Header and PE ID from the MSG91 account whose AuthKey performs the send, or pass PE_ID in supported APIs. TRAI separately requires a valid PE–TM chain and rejects traffic whose registered chain is absent or does not match.

Production therefore requires all of the following:

- the customer is the registered PE;
- the customer's PE/Header/Content Template combination is active;
- the PE–TM chain used by UnifyDesk/MSG91 is registered and active;
- UnifyDesk's exact role in that chain is confirmed with MSG91/access-provider onboarding before production;
- each tenant is isolated in MSG91 using a child/subaccount/client where commercially available, or an explicitly approved multi-PE master-account arrangement;
- UnifyDesk never exposes platform or child-account credentials;
- every send is bound server-side to one company, PE, Header, exact approved template version, and provider binding.

A single undifferentiated shared MSG91 account is **not** an acceptable production architecture unless MSG91 confirms the commercial/account arrangement and the actual DLT chain used for every customer. The preferred model is one MSG91 child account/client per UnifyDesk company under a master/subaccount arrangement, with master billing retained by UnifyDesk.

## Architecture documents

- RESEARCH-DLT-MSG91.md — current regulatory/provider findings and unresolved external checks.
- ARCHITECTURE.md — control plane, delivery plane, events, metering and lifecycle.
- DECISIONS.md — frozen architecture decisions.
- DATA-MODEL.md — table/state/ownership contract.
- API-CONTRACT.md — frozen HTTP contract for B1/B2/F1.
- SECURITY.md — threat model and invariants.
- QA-SEC-PLAN.md — integrated review plan.
- TASKS.md — exact worker ownership and frozen worker SHA.
- INTEGRATION_STATUS.md — branch/round status.
- BACKEND_B1_CHAT_PROMPT.md — B1 paste-ready prompt.
- BACKEND_B2_CHAT_PROMPT.md — B2 paste-ready prompt.
- UX_CHAT_PROMPT.md — UX1 paste-ready prompt.
- FRONTEND_CHAT_PROMPT.md — F1 paste-ready prompt.
- SECURITY_QA_CHAT_PROMPT.md — QA/SEC paste-ready prompt.

## Existing architecture reused

Managed SMS is a compliance/policy/runtime layer around existing Broadcast:

- BroadcastSenderConnection
- BroadcastSender
- BroadcastSenderManagementMode.MESSAGING_MANAGED
- BroadcastSenderProvider.MSG91_SMS
- BroadcastSmsTransport / BroadcastSmsTransportRegistry
- BroadcastMessage / BroadcastRun
- BroadcastOutbox + Kafka preparation
- BroadcastBatch / BroadcastRecipient
- retry, recovery, pause/cancel and statistics
- UsageBillingService / usage_events
- Redis-backed distributed limiting patterns

No second SMS campaign engine is permitted.

## Non-negotiable release invariants

1. A managed SMS never accepts arbitrary customer text as the source of truth.
2. A managed send uses an ACTIVE company-owned PE, Header and exact approved Content Template version.
3. Runtime values may replace only declared template variables and must satisfy variable type/length rules.
4. PE/Header/Template/provider-binding ownership is always checked by company; raw IDs are never authorization.
5. Managed MSG91 credentials are platform secrets and never appear in customer APIs, UI, logs or setup sessions.
6. Every recipient send carries a collision-resistant UnifyDesk correlation value for DLR reconciliation.
7. Provider acceptance and final delivery are separate states.
8. Billing is committed once on provider acceptance; duplicate callbacks never duplicate billing.
9. An ambiguous provider outcome is UNKNOWN and is not blindly retried.
10. Platform, tenant and campaign safety limits are distributed across backend pods.
11. A single tenant can be suspended before it exhausts the shared provider wallet/reputation.
12. Compliance records referenced by historical runs are archived/versioned, not destructively reused.
13. CUSTOMER_MANAGED MSG91 behavior remains backward compatible.
14. Third Amendment 2026 changes are tracked by effective date; as of 2026-09-27 the amendment was notified 2026-09-18 but its staged commencement had not yet occurred.
