# WhatsApp Template and Conversation Policy

Status: FROZEN FOR ROUND 1

## Backend authority

The frontend may display whether free-form messaging appears available, but only the backend decides if a send is allowed.

## Customer-service session

Round-1 local projection records:
- connection_id
- customer_key
- last_customer_message_at

Default configured eligibility horizon: 24 hours from trusted inbound customer activity, consistent with current WhatsApp customer-service behavior.

Do not confuse:
- messaging eligibility
with
- current Meta pricing/free-entry conversation rules.

Pricing can change without changing the architecture.

## Free-form send

Allowed only when:
- active usable connection;
- current company owns conversation;
- customer-service session still valid;
- message type is supported.

If expired:
- reject with WHATSAPP_TEMPLATE_REQUIRED
- never silently convert the text to a template.

## Templates

Round 1 supports:
- list/sync
- local cache
- approved template send
- language/category/components/variables

Round 1 does NOT provide template create/edit/delete UI/API.

## Template cache

Unique key:
(connection_id, name, language)

Send requires:
- local status APPROVED;
- components compatible with supplied parameters;
- cache not older than configured send freshness limit.

If stale:
- perform bounded refresh outside long DB transaction, or
- fail closed with actionable error.

## Variable validation

Backend validates:
- expected component indexes/types;
- parameter counts;
- bounded string length;
- media header ownership if used;
- no arbitrary raw JSON passthrough from browser.

## Categories

Current provider categories include:
- MARKETING
- UTILITY
- AUTHENTICATION

UnifyDesk displays provider category/status but does not invent approval.

## Opt-in

UnifyDesk must not treat template approval as recipient consent.

For business-initiated/template messages the customer is responsible for lawful WhatsApp opt-in/consent and Meta policy compliance. Future Broadcast integration must add consent/suppression governance before campaign use.

Round 1 Inbox replies do not provide a scraping/import-to-WhatsApp broadcast bypass.

## Provider rejection

Provider policy rejection maps to stable safe error and may mark cached template stale/action-required. Do not expose provider internal raw body.

## QA tests

- free-form allowed inside trusted session
- free-form blocked outside
- client cannot fake lastCustomerMessageAt
- foreign template ID rejected
- rejected/paused/non-approved template rejected
- stale template cache handled
- wrong variable count/type rejected
- duplicate send clientRequestId does not send twice
