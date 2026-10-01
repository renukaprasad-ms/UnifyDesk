# Canonical R1 Contracts

Baseline: `dev@1a39af74156e21ae5d4d7e7f4e0c32ddc0851daf`.

These are integration contracts. Implementations may use internal models, but must not create new permanent recipient/contact domains.

## 1. Canonical Contact identity

A messaging person is represented by canonical Contact plus one or more active Contact identities.

Required identity key:

`(companyId, identityType, namespace, normalizedValue)`

- EMAIL: canonical validated email.
- PHONE: E.164.
- EXTERNAL: namespaced opaque identifier only where a provider truly requires it.

WhatsApp audience identity uses canonical PHONE/E.164 for customer targeting. Meta phone-number-id is a sender/provider asset, not a Contact identity replacement.

No Inbox or Broadcast flow may auto-create a second Contact when an exact active canonical identity already exists.

## 2. Audience selector

R1 public selector:

```text
AudienceSelector {
  sourceType: CONTACT_LIST | SEGMENT | CONTACTS
  sourceId?: id
  contactIds?: [id]
}
```

Rules:
- CONTACT_LIST and SEGMENT are the primary reusable audience sources.
- CONTACTS is optional/bounded individual selection for coherent UX; it resolves canonical Contacts, not BroadcastGroup members.
- Legacy BroadcastGroup identifiers are compatibility-only and are not exposed as the canonical audience model for new customer journeys.
- every referenced object is resolved under company ownership.

## 3. Source descriptor / version

For every preview/freeze operation:

```text
AudienceSourceDescriptor {
  sourceType
  sourceId/contactSelectionHash
  sourceVersion
  filterVersion?
  filterHash?
  resolvedAt
}
```

List membership/version and Segment version/filter hash are authoritative. R1 must never silently resolve a different source after the user confirms scheduling.

## 4. Recipient resolution preview

Required contract shape:

```text
RecipientResolutionPreview {
  channel
  purpose
  sourceDescriptor
  selectedCount
  eligibleCount
  excludedCount
  exclusions: {
    DUPLICATE
    MISSING_IDENTITY
    INVALID_IDENTITY
    OPTED_OUT
    CONSENT_REQUIRED
    SUPPRESSED
    CHANNEL_INELIGIBLE
  } -> count
  sampleEligible[]
  sampleExcluded[]
  senderReadiness
  computedAt
}
```

Invariant:
`selectedCount = eligibleCount + excludedCount`.

The service must use `ContactEligibilityService` (or its canonical R1 successor) as the eligibility source. Preview must not invent a second rules engine.

Preview is advisory/live. Final safety still requires send-time consent/suppression validation.

## 5. Execution snapshot

Execution snapshot is not a duplicate Contact database. It is immutable evidence of who was selected/resolved for one Broadcast execution.

Required provenance per included recipient:
- companyId
- snapshot/run id
- sequence
- contactId
- contactIdentityId
- normalized destination snapshot
- display snapshot where useful
- source descriptor/version
- resolution decision/version
- created/frozen time

For reproducibility, R1 freeze happens when immediate send is confirmed or when scheduling is confirmed. The frozen source descriptor/snapshot then belongs to that Broadcast execution intent.

Changes after freeze:
- List/Segment membership does not silently add/remove recipients.
- identity/consent/suppression is revalidated at provider-send time.
- if the frozen identity changed/was removed, recipient is SKIPPED with an explicit reason rather than redirected to a new identity without user visibility.

## 6. Sender/channel readiness

Common product contract:

```text
ChannelReadiness {
  channel: EMAIL | SMS | WHATSAPP
  availability: AVAILABLE | UNAVAILABLE
  configuration: NOT_CONFIGURED | ACTION_REQUIRED | READY | DEGRADED | DISCONNECTED
  senders: SenderReadiness[]
  nextAction?
}

SenderReadiness {
  senderId
  label
  addressOrNumber
  state: READY | ACTION_REQUIRED | BLOCKED | DISCONNECTED | TEMPORARILY_UNAVAILABLE
  nextAction?
  capabilities
}
```

Provider-specific diagnostics can exist in admin/internal fields but normal customers should not need AWS/SES or transport internals to understand whether they can send.

Capabilities include truthfulness flags such as:
- providerAcceptance
- deliveryEvents
- readEvents
- inboundReplies
- templateRequired
- freeFormSessionSupported.

## 7. Message composition

Broadcast keeps a shared envelope while channel details remain typed.

Email:
- sender/from
- reply-to
- subject
- bodyText/bodyHtml
- template/version + variables when used
- unsubscribe/compliance behavior.

SMS:
- sender/header
- approved template/version where managed/DLT requires
- rendered body
- segment/character information when available.

WhatsApp:
- connected Meta sender/phone;
- approved template;
- language;
- validated template parameters;
- supported media only where the Broadcast contract explicitly permits it.
- R1 marketing Broadcast must not rely on free-form customer-service-window messages.

## 8. Recipient execution state

Core lifecycle:

`QUEUED/PENDING -> PROCESSING -> ACCEPTED_BY_PROVIDER | RETRY | FAILED | UNKNOWN | SKIPPED`

Provider projections can add:
- DELIVERED
- READ
- BOUNCED
- COMPLAINED
- REJECTED

Rules:
- provider HTTP acceptance is not final delivery;
- direct providers without final callbacks remain “accepted/sent”, not “delivered”;
- terminal/event projections are monotonic;
- out-of-order duplicate events cannot regress delivered/read state.

## 9. Delivery event

Canonical event ingestion contract:

```text
DeliveryEvent {
  provider
  providerAccount/connection
  providerEventId/normalizedDedupKey
  providerMessageId
  correlationId
  companyId (derived/verified, never trusted alone from payload)
  runId
  recipientId
  type
  providerOccurredAt
  receivedAt
}
```

Before mutation:
- authenticate/verify webhook;
- resolve authoritative provider connection;
- verify recipient/run/company/sender correlation;
- dedupe;
- apply monotonic transition/projection.

## 10. Suppression

Canonical long-lived customer eligibility suppression belongs to Contacts.

Provider hard-bounce/complaint feedback must:
1. update provider/event projection idempotently;
2. create/update canonical Contact suppression when an exact canonical Contact identity is known;
3. preserve a compatibility protection for legacy destination-only Broadcast rows during migration;
4. never weaken existing suppression behavior.

## 11. Inbox Contact association

R1 association contract:

```text
InboxParticipantContactLink {
  companyId
  conversationId
  participantAddress
  identityType
  normalizedIdentity
  contactId?
  contactIdentityId?
  resolution: MATCHED | UNMATCHED | AMBIGUOUS
  resolvedAt
}
```

Email lookup is case-insensitive canonical EMAIL matching.
WhatsApp lookup canonicalizes the customer phone to E.164 before PHONE lookup.

Inbound processing may create/update the link, but must not create a Contact implicitly unless the product explicitly invokes canonical Contact upsert with provenance and dedupe.

Inbox DTOs should expose the resolved contact ID where available so UI can navigate to Contact. Contact detail may query linked Inbox conversations through a read adapter.

## 12. Notification events

R1 terminal Broadcast events:
- `broadcast.completed`
- `broadcast.failed`

They must use a durable/idempotent event key tied to Broadcast run and be recorded in the same transaction as the terminal domain transition or via the existing durable outbox/event pattern.
