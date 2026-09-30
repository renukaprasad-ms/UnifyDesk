# Meta WhatsApp Round-2 Remediation Contract

Status: FROZEN FOR ROUND 2  
Round-2 worker base SHA: `fac085504dce38ac4c8f6f04e2f454773bdb811e`

This contract is a targeted extension of the frozen Round-1 contracts. It exists only to resolve the findings in `SECURITY_QA_FEEDBACK.md`.

Round-2 MUST NOT expand product scope into WhatsApp Broadcast, template authoring, Meta Ads, Flows/catalog or coexistence.

## 1. WA-R1-001 — webhook structural acceptance

After exact-byte HMAC verification, the webhook normalizer/controller MUST reject a structurally unsupported WhatsApp callback.

Required:
- root JSON object;
- `object == "whatsapp_business_account"`;
- non-empty `entry` array;
- every processed entry is an object;
- each accepted entry has a non-empty `changes` array;
- each accepted change is an object with non-blank `field` and object `value`.

A correctly signed callback that violates the supported WhatsApp envelope returns HTTP 400 and creates zero durable event rows.

Unknown but structurally valid WhatsApp change fields may still normalize to an `UNKNOWN` durable event and be ignored asynchronously.

## 2. WA-R1-002 — durable delivery state projection

The existing Inbox message JSON gains one backward-compatible nullable field:

`whatsappStatus`

Allowed values:
- PENDING
- ACCEPTED
- SENT
- DELIVERED
- READ
- FAILED
- UNKNOWN
- null

Rules:
- populated from `meta_whatsapp_message_runtime.detailed_status` for WhatsApp outbound messages;
- null for non-WhatsApp messages unless a future provider-specific contract defines otherwise;
- Inbox detail/messages and realtime snapshot return the same durable value;
- fetch statuses in batch for a page/detail/snapshot; no per-message N+1 query.

The existing generic `status` field remains unchanged for compatibility.

Realtime event stays:

`inbox.whatsapp.status.changed`

Payload:
```json
{
  "connectionId":"123",
  "conversationId":"123",
  "messageId":"123"
}
```

F1 reacts to that event by refreshing the affected message/snapshot and rendering `whatsappStatus`. Browser reload/navigation must recover the same status from HTTP without relying on local send-result memory.

## 3. WA-R1-006 — granular-scope intersection

The selected WABA/account is authorized only if it is present in the target IDs for EVERY required granular permission.

Required permissions remain:
- `whatsapp_business_management`
- `whatsapp_business_messaging`

Algorithm:
1. union duplicate target lists within each required scope;
2. require every required scope to have a target set;
3. intersect the sets;
4. selectable WABAs are only the intersection.

No union-across-required-scopes authorization.

## 4. WA-R1-003 — distributed per-phone outbound limiter

B2 adds a WhatsApp-specific distributed limiter using the existing Redis infrastructure.

Required properties with safe defaults:
- `outboundMaxPerSecond`: configurable, default 10
- `outboundRateWindow`: default 1s
- `providerRetryAfterFallback`: default 5s
- `pendingOutcomeTimeout`: default 2m

Key scope must include provider phone number:
`unifydesk:whatsapp:rate:phone:<phoneNumberId>`

Rules:
- acquire immediately before Meta message submission;
- Redis error/unavailable => fail closed for outbound provider protection;
- local limiter rejection produces a stable safe rate-limit error and MUST NOT submit to Meta;
- provider HTTP 429 parses `Retry-After` where present;
- 429 imposes a distributed cooldown for that phone for Retry-After or fallback duration;
- no immediate automatic resubmission of the user message.

L1 may wire env/YAML after integration. B2 owns the runtime property fields and limiter implementation/tests.

## 5. WA-R1-004 — inbound media idempotency

B2 migration V79 adds a provider-media reservation table.

Required logical columns:
- id
- connection_id
- inbox_message_id
- provider_media_id
- reserved_media_id
- state
- created_at
- updated_at
- completed_at

Constraint:
- unique(connection_id, provider_media_id)

States:
- RESERVED
- INGESTED
- LINKED
- FAILED

Required sequence:
1. canonical Inbox message exists;
2. reserve/find one durable row for (connection, provider_media_id), including a fixed `reserved_media_id`;
3. download/validate provider media;
4. ingest through an idempotent MediaService path that uses that reserved media ID/deterministic object key;
5. persist reservation INGESTED;
6. link same media ID to same message;
7. persist LINKED;
8. mark webhook processed.

Crash/retry behavior:
- if Media already exists ACTIVE for reserved_media_id, reuse it;
- if storage/object write repeats, it targets the same deterministic object key;
- retry never generates another Media ID for the same provider media;
- exactly one Media row/object and one message link after convergence.

## 6. WA-R1-005 — stale PENDING -> UNKNOWN

A locally reserved outbound message must not remain PENDING forever.

Required:
- no automatic provider replay;
- a PENDING runtime row older than `pendingOutcomeTimeout` is atomically changed to UNKNOWN;
- recovery runs on a scheduler and also may occur lazily when an existing clientRequestId is read;
- existing clientRequestId returns UNKNOWN after recovery;
- providerMessageId remains null when acceptance was never durably recorded;
- user can see UNKNOWN after reload through the durable Inbox status projection.

Add an index in V79 suitable for bounded stale-PENDING recovery.

## 7. WA-R1-007 — provider timestamp ordering

If both current and incoming provider timestamps are known, an incoming event older than the current provider timestamp MUST NOT apply, regardless of current rank.

Examples:
- newer ACCEPTED + older FAILED => keep ACCEPTED;
- newer SENT + older FAILED => keep SENT;
- newer DELIVERED/READ + older FAILED => keep successful state;
- newer FAILED after ACCEPTED may apply under existing failure rules.

Equal timestamp duplicate/non-advancing state remains a no-op.

## 8. WA-R1-008 — Meta URL button variables

Frontend template component type gains provider button structure:

```ts
buttons?: Array<{
  type?: string
  text?: string
  url?: string
}>
```

For `BUTTONS` components:
- inspect each provider button object;
- only variable-bearing URL buttons create a user parameter input under the current Round-1 backend policy;
- parameter order matches provider button order;
- BODY/HEADER behavior remains unchanged;
- request `parameters.buttons` length/order matches B2 template validation.

## 9. Migration ownership

B1: no migration.  
B2: `V79__meta_whatsapp_round2_runtime_hardening.sql`.  
F1: no migration.  
L1: V80 only if an integration-only cross-module constraint becomes necessary.

## 10. Worker independence

B1/B2/F1 all branch from `fac085504dce38ac4c8f6f04e2f454773bdb811e`.

B1 and F1 must not import B2 implementation.

F1 implements the frozen optional `whatsappStatus` JSON field even though its worker base does not yet contain the backend implementation.

B2 must not edit frontend.

## 11. Release criteria after Round 2

All WA-R1-001 through WA-R1-008 must be fixed and regression-covered.

Round-2 QA must still execute:
- backend Spotless + full/clean tests/package;
- frontend format/lint/tests/build;
- clean Flyway through V79/V80;
- representative V75/V78 -> latest upgrade;
- Redis limiter integration/concurrency test;
- media fault-injection retry test;
- stale-PENDING recovery test;
- real Meta provider checklist where credentials permit.
