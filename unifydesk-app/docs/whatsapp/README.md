# UnifyDesk Meta WhatsApp — Architecture Workspace

Status: ROUND-0 ARCHITECTURE FROZEN FOR IMPLEMENTATION  
Architecture branch: `docs/whatsapp-architecture-r0`  
Application baseline under this branch: `dev @ 6daf9d89ac3b2c58b794c0b82dc2917d5737caa7`

This workspace freezes the production architecture for the existing Meta WhatsApp integration before implementation workers begin.

## Read first

- `PROJECT_BRIEF.md`
- `TEAM_WORKFLOW.md`
- `RESEARCH-META-WHATSAPP.md`
- `ARCHITECTURE.md`
- `DECISIONS.md`
- `DATA-MODEL.md`
- `API-CONTRACT.md`
- `EVENT-CONTRACT.md`
- `WEBHOOK-CONTRACT.md`
- `MESSAGE-STATE.md`
- `TEMPLATE-POLICY.md`
- `SECURITY.md`
- `PERFORMANCE.md`
- `META-SETUP-RUNBOOK.md`
- `QA-SEC-PLAN.md`

The worker manifest/prompts are committed after the immutable worker base so they can name that SHA exactly.

## Architecture summary

Round 1 uses the current monolith and reuses:
- generic ChannelConnection/SenderIdentity/ProviderCredential;
- unified Inbox;
- existing private Media;
- Redis/Kafka/realtime;
- notification post-processors.

The target flow is:

```
Embedded Signup v4
  -> one-time company/user-bound state
  -> server token/asset validation
  -> recoverable local provisioning
  -> registration + app subscription
  -> cached live health

Meta signed webhook
  -> byte cap + HMAC
  -> durable signed-event inbox
  -> async worker
  -> unified Inbox/status/media/realtime

Agent send
  -> WhatsApp authorization + ownership
  -> service-window/template policy
  -> idempotency reservation
  -> Meta send
  -> provider message ID
  -> status webhook reconciliation
```

## Round-1 scope

Included:
- connect/reconnect/disconnect;
- Embedded Signup v4;
- health/token/revocation lifecycle;
- durable inbound text;
- image/document media;
- text/reply outbound;
- approved template sync/send;
- delivery/read/failure state;
- Inbox + notification + realtime integration.

Feature-gated/deferred:
- Meta Ads;
- template authoring;
- WhatsApp Broadcast transport;
- rich contacts/location/reaction/audio/video UI;
- coexistence until real-account verification;
- full username/BSUID product UX.

## Team

- L1 — Lead Architect / Integration Lead
- B1 — Backend Connection / Control Plane
- B2 — Backend Messaging / Webhook / Delivery
- F1 — Frontend + WhatsApp UX
- QA/SEC — starts only after L1 publishes one integrated immutable SHA

## Current 2026 provider requirement

The architecture targets Meta Embedded Signup v4 and does not freeze the current frontend's `sessionInfoVersion: '3'`. Meta's 2026 account-model changes are handled by storing legacy WABA compatibility plus optional new account identifiers.

See `RESEARCH-META-WHATSAPP.md` and `META-SETUP-RUNBOOK.md`.

## Scope guard

No Meta Ads implementation belongs in this project.
