# End-to-End Flow

Baseline: `dev@1a39af74156e21ae5d4d7e7f4e0c32ddc0851daf`.

## Flow A — canonical Contact audience to Broadcast

Current backend source:

`Contact -> Contact List/Segment -> AudienceSourceResolver -> AudienceSnapshotService -> contact_audience_snapshot_members -> BroadcastPreparationChunkService -> BroadcastRecipient -> provider transport`

What works:
- List/Segment company ownership is checked.
- source version/filter hash is captured.
- Contact identity is canonicalized.
- eligibility is evaluated in batches.
- execution recipient rows retain `contact_id` and `contact_identity_id`.
- send-time Contact eligibility is rechecked.

Where the chain breaks:
- the customer Broadcast UI never invokes this path;
- there is no unified pre-send recipient-resolution preview with exclusion reasons;
- only eligible members are materialized, so exclusions are not represented as a reproducible execution decision set;
- scheduled drafts do not freeze the audience seen at schedule time.

## Flow B — current customer-visible Broadcast

Current frontend:

`Broadcast Group -> add/import BroadcastGroupMember -> draft -> sender -> send/schedule -> run -> recipient -> provider`

This works as a standalone Broadcast product, but recipients are maintained independently from Contacts. CSV/member imports do not create canonical Contacts. This is the principal UX/domain duplication R1 must remove from the normal path.

Legacy group data can remain readable/compatible while canonical Contacts becomes the normal creation path.

## Flow C — delivery events

### Managed Email

`SES/SNS -> SnsMessageVerifier -> ManagedEmailProviderEventProcessor -> ownership/correlation check -> idempotent event ledger -> provider delivery projection -> suppression/reputation/usage effects`

Hard bounce can transition main Broadcast recipient state to BOUNCED. Delivery/open/click are provider-specific projections rather than first-class main Broadcast recipient states.

### Managed SMS

`MSG91 callback -> account-token verification -> correlation/request-id check -> durable provider event -> SKIP LOCKED event worker -> delivery projection -> usage -> UNKNOWN-to-ACCEPTED recovery`

Delivered/failed/rejected details live in Managed SMS projection.

### Direct/customer-connected providers

Provider acceptance is implemented. Final provider callback reconciliation is not consistently implemented across Amazon SES/SendGrid/Azure/MSG91 customer-managed transports. The product must say “accepted/sent” truthfully and not fabricate delivered/read status.

## Flow D — reply to Inbox

### Email

`provider sync/push -> normalized InboxConversation/InboxMessage/InboxParticipant`

### WhatsApp

`Meta webhook -> trusted tenant resolution -> durable webhook event -> worker -> MetaWhatsAppInboxPersistenceService -> InboxConversation/InboxMessage/participant`

WhatsApp replies and provider statuses are strongly modeled.

### Break

The Inbox participant is not resolved/persisted as a canonical Contact association.

Desired R1:

`canonical Contact -> BroadcastRecipient(contactId) -> outbound -> inbound reply -> normalized identity lookup -> Inbox conversation contact association -> Contact detail/inbox navigation`

If no exact canonical Contact exists, Inbox may remain an unlinked participant. R1 must not silently create duplicate Contacts on every inbound message.

## Flow E — Notifications

`Inbound Inbox -> InboxMessagePostProcessor -> NotificationEventRecorder -> notification materialization/delivery`

This chain exists.

Broadcast completion/failure notification types are registered but not emitted by current Broadcast finalization/failure paths.

## Flow F — Workspace switching

Backend calls are company-scoped.

Frontend normal path:
`WorkspaceSwitcher -> selectedCompanyId -> module effects/refetches`

Redux Inbox/Broadcast collections carry scope markers, but component-local requests/realtime work are not uniformly guarded by a synchronous tenant epoch. R1 must apply one reusable rule: every async result/mutation/realtime callback must prove it still belongs to the same workspace generation before it mutates visible state.
