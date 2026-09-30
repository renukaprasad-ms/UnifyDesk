# Contacts & Audience Integrations

## Inbox

Round 1 integration seam:
ContactIdentityLookup(companyId, identityType, normalizedValue)

Inbox remains independently valid for unknown senders.

Do not add Contact foreign keys into Inbox message/conversation tables in B1/B2.

Future Inbox side panel:
- resolve participant identity -> contactId
- GET Contact detail/activity
- action “Create contact” from unknown participant

## Broadcast

Current Broadcast uses channel-specific broadcast_groups and group members.

Round-1 compatibility:
- existing group flows unchanged
- Lead adds List/Segment source support without deleting group support
- BroadcastMessage/Run stores audience source/snapshot references
- BroadcastRecipient stores immutable destination + optional contact/identity provenance
- snapshot generated at run preparation
- delivery rechecks current eligibility

Do not replace the entire Broadcast execution engine.

## Managed Email

Provider/domain/sender topology remains Managed Email-owned.

Adapter consumes provider events:
- unsubscribe -> Contact preference event when recipient resolves
- hard bounce/complaint -> Contact suppression when recipient resolves

Contacts APIs never expose SES tenant/configuration-set concepts.

## Managed SMS

Contacts supplies E.164 identity and generic preference/suppression.

Managed SMS retains:
- DLT profile
- PE/Header
- template
- MSG91 provider/runtime concerns

Provider opt-out/DND can feed Contacts suppression through adapter.

## Meta WhatsApp

Contacts PHONE identity is provider-neutral.

Meta WABA/phone_number_id/wa_id/BSUID remain channel runtime data. A WhatsApp identity resolver may associate a Meta participant with a Contact using normalized customer phone or a future EXTERNAL identity namespace.

Future WhatsApp Broadcast consumes Contacts audience snapshot + eligibility.

## Ad Center

Round 1 only defines future activation seam.

Later adapter owns:
- Meta custom audience / Google Customer Match provider audience ID
- provider sync state/cursor
- provider-specific normalization/hashing
- provider consent flags
- add/remove reconciliation
- error diagnostics

Contacts owns source audience/version and first-party identity/consent facts.

## Billing / entitlements

Existing keys:
- CONTACT_LIMIT
- SEGMENT_LIMIT

Round-1 B1 may add:
- CONTACT_CUSTOM_FIELD_LIMIT
- CONTACT_LIST_LIMIT
- CONTACT_IMPORT_ROWS_LIMIT

Defaults fail closed/controlled according to existing catalog semantics. No commercial plan names.

B2 enforces SEGMENT_LIMIT for create and import-row limit for commit.

## Storage

Reuse:
- CONTACT_DATA
- EXPORT

Import, error report and export retention release storage accounting idempotently.

## Async jobs

Extend AsyncJobType with:
- CONTACT_IMPORT
- CONTACT_EXPORT
- CONTACT_BULK
- CONTACT_SEGMENT_COUNT
- CONTACT_DELETE_PURGE
- CONTACT_AUDIENCE_SNAPSHOT if needed by implementation

Use current claim/recovery/realtime progress behavior.

## Realtime

Import/export/bulk/count progress uses existing async.job.progress.

Do not add a new websocket protocol for Contacts.

## Notifications

No mandatory user notification is required for every Contact edit. Long jobs may later emit completion notifications, but AsyncJob progress is sufficient for Round 1.

## Company deletion

Contacts must hook into existing company/entity purge lifecycle. Lead integration ensures purge registration rather than inventing a second company-cleanup process.
