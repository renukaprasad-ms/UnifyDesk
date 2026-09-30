# Contacts & Audience Integration Contracts

## 1. Inbox

### Current state

Inbox persists conversation participants independently.

### Round-1 integration

L1 owns a thin `ContactIdentityResolver` seam after B1 integration.

Input:
- companyId
- EMAIL/PHONE
- raw/normalized destination

Output:
- contactId
- displayName
- primary channel identities
- tags summary if needed

Rules:

- same-company only;
- active/archived visibility follows caller permission;
- no hard FK;
- no automatic contact creation;
- no consent mutation from inbound traffic.

Frontend Inbox side panel is later integration work unless L1 explicitly includes a minimal safe hook after worker integration.

## 2. Broadcast

### Existing state

Broadcast groups contain raw destinations.

Runs snapshot group rows into `broadcast_recipients`.

### Contacts future-facing source

A broadcast draft can later select:

- static Contact list;
- dynamic Contact segment;
- explicit Contact selection.

At send preparation, Broadcast requests a Contacts audience snapshot.

Contacts returns immutable eligible destinations plus exclusion counts.

Broadcast materializes those into its existing run/recipient model.

### Why send-preparation snapshot

Scheduling can be days ahead.

Using list membership from schedule-creation time could ignore legitimate consent/list changes before delivery.

Using a live segment per delivery batch makes one campaign non-reproducible and risks sending to contacts added mid-run.

Therefore freeze when the scheduled run actually enters preparation.

### Legacy compatibility

Do not delete/migrate all existing `broadcast_groups` in Round 1.

Do not change existing customer behavior until Contacts audience integration has full regression tests.

## 3. Managed Email

Provider delivery feedback may update central EMAIL suppressions:

- hard bounce;
- complaint;
- invalid destination;
- unsubscribe where authoritative.

Contacts never exposes SES terminology.

Managed Email metering/reputation remains provider/module owned.

## 4. Managed SMS

Use central PHONE normalization and SMS preference/suppression.

Managed SMS retains:

- DLT/PE;
- headers/senders;
- templates;
- MSG91 provider details;
- rate/metering.

## 5. WhatsApp

Use PHONE identity.

Central preferences supply MARKETING consent state.

WhatsApp runtime retains:

- Meta auth/assets;
- template/session policy;
- provider send states;
- media.

Future WhatsApp broadcast may consume Contacts audience snapshots.

## 6. Ad Center

No Round-1 provider synchronization.

Future adapter:

- requests a Contacts activation snapshot;
- applies consent eligibility required by product/provider;
- normalizes provider fields;
- hashes at the boundary when required;
- creates/updates `ad_audiences`;
- tracks provider IDs/sync status outside Contacts;
- reconciles add/remove changes.

A central audience should be reusable by multiple ad accounts without putting provider IDs on the segment.

Google Customer Match and Meta Custom Audiences evolve independently, so the adapter is versioned/provider-owned.

## 7. Billing / entitlements

B1 uses EntitlementService:

- CONTACT_LIMIT;
- new custom-field/list limit keys if added.

B2 uses:

- SEGMENT_LIMIT;
- import/export row limits if added.

No plan names in domain logic.

## 8. Storage

Imports:
- CONTACT_DATA storage usage.

Exports:
- EXPORT storage usage.

Use existing company storage locking/idempotency.

## 9. Async jobs

Extend AsyncJobType with:

- CONTACT_IMPORT
- CONTACT_EXPORT
- CONTACT_BULK
- CONTACT_SEGMENT_COUNT
- CONTACT_PURGE if separate from lifecycle is truly needed.

Prefer reuse of existing `AsyncJobService` claim/recovery/progress.

## 10. Realtime

Existing async progress event is sufficient for import/export/bulk.

Contact/list/segment mutations may emit a compact invalidation hint but correctness uses API refetch.

## 11. Notifications

Round 1 need not send an email/push for every import/export.

UI gets async progress.

A future product choice can create an in-app "Import completed" notification using the Notification event recorder, but Contacts workers must not couple directly to SMTP/Web Push.

## 12. Lifecycle

L1 integrates Contacts company purge into `EntityPurgeService` or a dedicated cleanup service seam using bounded batches.

A million-contact workspace must not rely on one cascade transaction.

## 13. Platform Admin

Platform admins with existing workspace access can use the same company-scoped services.

No separate "global contacts search" is created in Round 1.

## 14. Event contracts

Compact event payload example:

```json
{
  "companyId":"1",
  "contactId":"2",
  "eventType":"contact.preference.changed.v1",
  "occurredAt":"...",
  "version":5,
  "change":{"channel":"EMAIL","purpose":"MARKETING","state":"OPTED_OUT"}
}
```

Never put entire notes/custom field JSON/raw CSV row/access tokens into integration event payloads.

## 15. Integration order

1. merge B1;
2. merge B2;
3. resolve cross-worker FKs/constraints in V96;
4. merge F1;
5. add permission/default-role/bootstrap glue;
6. add lifecycle cleanup;
7. add minimal Inbox resolver;
8. add Broadcast adapter only if its frozen acceptance tests can preserve legacy behavior;
9. leave Ad Center untouched except documented interfaces;
10. run full migrations/tests;
11. freeze integrated QA SHA.
