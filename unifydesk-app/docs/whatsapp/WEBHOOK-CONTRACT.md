# Meta WhatsApp Webhook Contract

Status: FROZEN FOR ROUND 1

## Public endpoints

GET and POST:
`/api/v1/webhooks/meta/whatsapp`

These endpoints are provider endpoints, not authenticated company APIs.

## GET verification

Inputs:
- hub.mode
- hub.verify_token
- hub.challenge

Success only when:
- mode == subscribe
- configured verify token exists
- supplied token constant-time matches

Return exact challenge with 200.

Failure:
- 403
- no secret/token echo
- safe audit metadata only

## POST receive

### Request acceptance order

1. Enforce configured maximum body bytes before trusted parsing/storage.
2. Read exact request bytes.
3. Require X-Hub-Signature-256 with sha256= prefix.
4. Compute HMAC-SHA256 using Meta app secret.
5. Constant-time compare decoded digest.
6. Parse JSON with bounded parser constraints.
7. Require supported WhatsApp object/change envelope.
8. Normalize one or more durable event rows.
9. Insert rows idempotently.
10. Commit.
11. Return 200 EVENT_RECEIVED.

No media download, Graph API call, Inbox write or member notification occurs before step 11.

### HTTP result

- 200: signed event durably recorded or duplicate already recorded
- 400: signed but malformed/unsupported structural request that should not be retried
- 401: missing/invalid signature
- 413: body exceeds hard limit
- 503: durable DB handoff unavailable; provider should retry

## Event extraction

Recognized Round-1 effects:
- inbound messages
- message statuses
- template status/account updates needed for cache/health
- unknown signed effect -> bounded IGNORED event/metric, no tenant mutation

A single callback can contain multiple entries/changes/messages/statuses. Each logical effect receives its own event_key.

## Dedupe

Message:
`msg:<phoneNumberId>:<providerMessageId>`

Status:
`status:<phoneNumberId>:<providerMessageId>:<status>:<providerTimestamp>`

If timestamp is missing, use a canonical safe tuple/hash that includes enough provider identity to avoid collapsing genuinely distinct effects.

Duplicate insert must still return 200.

## Tenant resolution

Processing worker resolves:
- metadata.phone_number_id -> local SenderIdentity.externalId
- SenderIdentity.connection -> META_WHATSAPP ChannelConnection
- B1 runtime projection -> expected provider account identifiers and usability state

Zero matches:
- IGNORED_UNKNOWN_PHONE
- no tenant creation

More than one active-company match:
- security/data-integrity failure
- DEAD/alert
- no mutation

## Inbound message mapping

TEXT:
- body_text = provider text
- snippet = bounded text
- status RECEIVED
- read=false

IMAGE/DOCUMENT:
- create canonical Inbox message first with bounded caption/type preview
- worker fetches authenticated media metadata/content
- validate/store in existing Media
- attach/map media id
- media failure must not duplicate the message on retry

AUDIO/VIDEO/LOCATION/CONTACTS/REACTION/SYSTEM/UNKNOWN:
- Round 1 persists safe placeholder text/type metadata so the conversation is not silently lost
- no rich interaction UI guarantee

Reply/context:
- map provider context.message_id to local referenced InboxMessage when present
- missing reference is tolerated; do not fail whole inbound message

## Status mapping

Provider statuses:
- sent -> SENT rank
- delivered -> DELIVERED
- read -> READ
- failed -> FAILED with safe provider diagnostics

Never regress a higher successful rank due to older event.

## Auditing

After authentication, the controller may record:
- callback ID
- event counts
- signature valid boolean
- bounded event IDs
- processing handoff result

Do not record full unsigned raw body.

## Operational replay

Ops replay targets durable event ID/state. Replay reuses the same idempotent processing path and never fabricates a new event key.
