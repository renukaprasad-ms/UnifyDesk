# UnifyDesk Notification System — Round 0

Status: architecture/contracts frozen for Round 1 after the contract commit is published  
Architecture branch: `docs/notification-architecture-r0`  
Application baseline: `dev @ c0c50579f2f823a264eefb525a48abb1b6120198`  
Research date: 2026-09-27

## Purpose

This directory freezes the product, data, API, event, delivery, security and performance contracts for the UnifyDesk platform notification subsystem.

Round 1 implements two end-to-end source events:

1. `COMPANY_CREATED` — durable in-app welcome plus exactly-once logical welcome-email intent for the creator/owner.
2. `INBOX_ACTIVITY` — durable, coalesced in-app activity; optional/coalesced browser push; optional smart email digest. It MUST NOT send one email per inbound message.

The platform is intentionally broader than those two producers so later modules can add billing, security, campaign, approval, integration and quota events without creating new delivery stacks.

## Non-goals for Round 1

- customer marketing/broadcast delivery;
- customer-supplied notification templates;
- mobile native push, SMS, WhatsApp or Slack delivery;
- arbitrary external webhook notifications;
- changing existing OTP semantics;
- replacing the existing Inbox, Broadcast or Managed Email engines;
- guaranteeing exactly-once effects at SMTP/browser push providers, which those external protocols do not provide.

## Frozen architectural shape

```text
business transaction
  -> notification_events row (transactional event/outbox; unique event_key)
  -> outbox publisher
  -> existing priority Kafka
  -> B1 materializer
       -> user_notifications (durable in-app projection)
       -> unread counter
       -> notification_delivery_intents
       -> existing RealtimeService after commit (best-effort hint)
  -> B2 durable delivery workers
       -> email immediate/delayed
       -> digest aggregation/sending
       -> web-push subscriptions/sending
```

PostgreSQL is the source of truth. Kafka is transport, Redis is coordination/cooldown/realtime infrastructure, and WebSocket/browser push are delivery hints/channels. A Redis/Kafka/WebSocket outage must never erase already-committed notification history.

## Core invariants

- IDs exposed over HTTP/JSON are strings.
- Every company-scoped read/write is constrained by current user + active company membership + current required permission.
- Notification content is server-defined from a closed type registry. Browser requests cannot inject arbitrary notification text, HTML, URLs or recipient IDs.
- All deep links are same-origin relative routes from the registry.
- Event recording occurs inside the originating database transaction where practical.
- `notification_events.event_key` is the first durable dedupe boundary.
- Materialization has a durable per-event/per-recipient uniqueness boundary.
- In-app unread counters change in the same transaction as read-state changes.
- Provider calls never occur while holding a database row lock/transaction open.
- Optional email/browser delivery respects preference, quiet hours, cooldown, relevance and membership/permission at send time.
- Mandatory security/account email can override user channel preferences and quiet hours, but still uses anti-abuse/provider safety controls.
- Realtime is a hint. Reconnect/window-focus recovery re-fetches durable state.
- Round-1 workers do not import one another's unmerged Java/TypeScript classes.

## Documents

- [RESEARCH.md](RESEARCH.md) — repository findings and 2026 primary-source research.
- [PRODUCT-POLICY.md](PRODUCT-POLICY.md) — taxonomy/default channel policy and anti-fatigue defaults.
- [ARCHITECTURE.md](ARCHITECTURE.md) — runtime topology, flow and transaction boundaries.
- [DECISIONS.md](DECISIONS.md) — frozen ADR-style decisions.
- [DATA-MODEL.md](DATA-MODEL.md) — tables, constraints, indexes, retention and state machines.
- [EVENT-CONTRACT.md](EVENT-CONTRACT.md) — durable event/Kafka/realtime contracts.
- [API-CONTRACT.md](API-CONTRACT.md) — exact HTTP contracts consumed by F1.
- [DELIVERY-POLICY.md](DELIVERY-POLICY.md) — email/push/digest/retry/quiet-hours semantics.
- [SECURITY.md](SECURITY.md) — threat model and release controls.
- [PERFORMANCE.md](PERFORMANCE.md) — hot paths, complexity and growth plan.
- [QA-SEC-PLAN.md](QA-SEC-PLAN.md) — future integrated review plan.
- `TASKS.md` and worker prompts — committed one manifest commit after the frozen worker base so they can name that immutable SHA exactly.
- `INTEGRATION_STATUS.md` — integration/QA handoff ledger.

## Worker-base convention

To avoid the impossible self-reference of a commit containing its own SHA, this project uses the same convention as Managed Email/Managed SMS:

1. commit all frozen architecture and contract documents;
2. that commit becomes the immutable Round-1 worker base;
3. commit `TASKS.md`, `INTEGRATION_STATUS.md` and worker chat prompts one commit later, naming the exact worker-base SHA;
4. B1/B2/F1 branch from the worker-base SHA but read their assignment prompt from the architecture branch.

No application source is changed in Round 0.
