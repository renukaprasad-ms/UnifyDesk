# WhatsApp Architecture Decisions

Status: FROZEN FOR ROUND 1

## ADR-01 — Embedded Signup v4 only

Decision: Round 1 targets Meta Embedded Signup v4. Remove `sessionInfoVersion: '3'` from the launch payload and use a v4 Facebook Login for Business configuration.

Reason: v2/v3 new-customer onboarding retirement is imminent (2026-10-15).

## ADR-02 — Fresh signup session at user gesture

Decision: replace page-load GET state creation with POST `/signup-session` called immediately from Connect/Reconnect. The returned state is one-time and short-lived.

Reason: opening the page must not consume most of the anti-replay TTL.

## ADR-03 — Provider IDs are untrusted hints

Decision: FINISH waba/account/phone IDs are validated against the exchanged token and provider graph before any ownership assignment.

Reason: cross-company/provider-ID tampering must fail closed.

## ADR-04 — Recoverable provisioning, not one request transaction

Decision: persist a local PENDING connection, encrypted token/PIN and provisioning state before registration/subscription external side effects; reconcile each step.

Reason: current provider-side success can be lost if local persistence fails.

## ADR-05 — Keep generic channel status; add Meta detail state

Decision: do not expand ChannelConnectionStatus for Meta-only lifecycle. Store detailed Meta connection/auth/registration/subscription health separately.

Reason: avoids cross-provider enum churn and lets F1 show actionable WhatsApp state.

## ADR-06 — Durable signed webhook inbox before 200

Decision: signature-verified, size-bounded webhook events are inserted into a B2 durable table before returning 200. Processing is asynchronous.

Reason: fast acknowledgement without losing messages during Kafka/provider/DB worker disruption.

## ADR-07 — Do not store unauthenticated raw webhook bodies

Decision: verify exact-byte HMAC and size before durable payload storage. Existing general webhook audit may receive safe metadata after verification.

Reason: prevents unauthenticated storage amplification and customer-PII retention abuse.

## ADR-08 — Existing Inbox is canonical

Decision: WhatsApp messages are persisted to InboxConversation/InboxMessage/InboxParticipant. Provider detail tables supplement, never replace, Inbox.

Reason: keeps read state, realtime, notification and storage accounting unified.

## ADR-09 — B2 owns provider-aware Inbox adaptation

Decision: B2 may modify InboxController/InboxQueryService/repositories/types necessary to remove email-only assumptions and enforce channel-specific permissions.

Reason: current Inbox hard-codes EMAIL_READ/EMAIL_MODIFY and EmailFolder; inbound persistence alone would not make a safe usable WhatsApp Inbox.

## ADR-10 — Round-1 media is image + PDF/document

Decision: reuse existing Media subsystem. Round 1 supports local validator formats already present: JPEG, PNG, WebP and PDF. Other incoming media becomes a safe unsupported placeholder unless validation is deliberately expanded.

Reason: prevents a second storage path and bounds security work.

## ADR-11 — Templates: sync/send first

Decision: list/sync approved templates and send them. Defer template create/edit/delete to a later product round.

Reason: template sending is necessary for production replies outside the service window; mutation is not necessary to prove core channel reliability.

## ADR-12 — Backend owns customer-service-window enforcement

Decision: F1 guides the user but B2 authoritatively enforces free-form eligibility using trusted last inbound activity plus provider errors.

Reason: client time/UI state cannot be authorization.

## ADR-13 — Ambiguous send is UNKNOWN

Decision: do not automatically retry a send whose outcome is ambiguous after network timeout/reset.

Reason: WhatsApp send API does not provide a client idempotency key that makes blind replay safe. Local clientRequestId only dedupes UnifyDesk requests.

## ADR-14 — Account-model forward compatibility

Decision: store legacy WABA plus optional WhatsApp Account/Messaging Account identifiers and version the provider account model.

Reason: Meta's 2026 account model is changing; hardcoding WABA as the only identity creates an avoidable migration.

## ADR-15 — Coexistence feature-gated

Decision: model coexistence status/capability but keep customer-facing enablement off until real-account QA proves current semantics.

Reason: provider eligibility/routing is account dependent and undergoing 2026 changes.

## ADR-16 — One worker base, SQL seam across B1/B2

Decision: B1 and B2 branch from one immutable SHA. B2 reads B1 control-plane fields through documented SQL/JdbcTemplate at runtime, mocks that projection in its branch tests, and never imports B1 classes.

Reason: both backend branches can compile/test independently and merge cleanly.

## ADR-17 — Migration ownership

Decision:
- B1: V76__meta_whatsapp_control_plane.sql
- B2: V77__meta_whatsapp_runtime.sql
- L1 after integration: V78__meta_whatsapp_cross_module_constraints.sql if needed.

B2 V77 must migrate independently from the frozen worker base and cannot FK to V76-only tables.

## ADR-18 — No Meta Ads work

Decision: Ads is out of scope. Shared Meta app settings may be inspected, but no Ads code is redesigned or changed by WhatsApp workers.
