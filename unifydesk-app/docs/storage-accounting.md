# Storage Accounting Architecture

## Goal

`STORAGE_GB` is the company-wide logical storage allowance. It is not the physical size of the
PostgreSQL database or the Supabase bucket.

The storage subsystem accounts data that the company owns and the product intentionally retains:
messages, stored attachments/media, template media, company media, ad creatives, and future
company-owned datasets.

## Design

The storage subsystem has three layers:

1. **Source of truth**
   - `inbox_messages.storage_bytes` for persisted message content.
   - `media.size_bytes` + `media.status` for physically stored objects.
   - Future modules remain the source of truth for their own resources.

2. **Fast aggregate**
   - `company_storage_usage`
   - one row per `(company_id, usage_type)`
   - stores `used_bytes` and `reserved_bytes`
   - used for quota checks and customer-facing usage reads.

3. **Idempotent ledger**
   - `storage_usage_events`
   - immutable deltas with a unique `idempotency_key`
   - protects counters from Kafka retries, HTTP retries, and repeated lifecycle callbacks.

## Responsibilities

### StorageQuotaService

Owns quota decisions.

- resolves the company's `STORAGE_GB` entitlement.
- serializes quota-changing reservations by locking the company row.
- checks `used + reserved + requested <= limit`.
- supports `-1` as unlimited.
- exposes total usage and category breakdown.

### StorageAccountingService

Owns accounting transitions.

- append ledger event.
- apply aggregate delta in the same transaction.
- commit reservations.
- release reservations.
- release used bytes.
- reconcile aggregate buckets.

Feature modules never update `company_storage_usage` directly.

### Module adapters

Feature-specific adapters translate domain resources into storage categories.

Example:

`MediaStorageAccountingAdapter`

- maps `MediaPurpose` to `StorageUsageType`.
- keeps media-specific knowledge out of the generic storage services.

Future WhatsApp/Ads/Contacts modules should use the same pattern.

## Lifecycle invariants

### Pending upload

```
initiate
  -> reserve bytes
  -> media=PENDING
```

The reservation prevents concurrent uploads from bypassing the quota.

### Completed upload

```
PENDING
  -> ACTIVE
  -> reserved -= bytes
  -> used += bytes
```

### Failed / expired upload

```
PENDING
  -> FAILED/DELETED
  -> reserved -= bytes
```

### Deleted active object

```
ACTIVE
  -> DELETED
  -> used -= bytes
```

Physical object deletion is a separate concern. Logical deletion controls quota accounting.

### Persisted inbox message

The message meter calculates deterministic logical bytes from the UTF-8 representation of the
stored message fields plus a small fixed metadata allowance.

Attachments are **not** counted in message bytes. They are counted by the media subsystem only
when the attachment is actually downloaded/stored, which prevents double counting.

## Concurrency

Quota-changing operations obtain a PostgreSQL row lock on the company before reading aggregate
usage and applying a reservation/consumption.

This serializes competing quota allocations for the same company while allowing unrelated
companies to proceed concurrently.

## Idempotency

Resource lifecycle operations use deterministic keys, for example:

```
MEDIA:<mediaId>:reserve
MEDIA:<mediaId>:commit
MEDIA:<mediaId>:release-used
INBOX_MESSAGE:<messageId>:add-used
```

If the same operation is retried, the ledger insert is ignored and the aggregate is not applied
twice.

## Reconciliation

A scheduled reconciliation job processes companies in bounded pages.

It compares the aggregate table against authoritative source data currently supported by:

- inbox messages.
- active/pending media.

Only usage types whose authoritative sources are implemented are reconciled. Future categories
must add their source adapter before being included in reconciliation.

Configuration:

```
STORAGE_RECONCILIATION_CRON=0 30 3 * * *
STORAGE_RECONCILIATION_ZONE=UTC
STORAGE_RECONCILIATION_BATCH_SIZE=100
```

## Adding a new storage-owning module

A new module must:

1. Define or reuse a `StorageUsageType`.
2. Decide the authoritative byte measurement.
3. Use `StorageQuotaService.reserve/consume` before allocating storage.
4. Use `StorageAccountingService` for commit/release lifecycle events.
5. Use stable resource identifiers and deterministic idempotency keys.
6. Add the module's source-of-truth calculation to reconciliation.
7. Never write directly to `company_storage_usage`.

## Customer-facing semantics

- `usedBytes` = committed logical storage.
- `reservedBytes` = capacity temporarily reserved by pending operations.
- `remainingBytes` = limit - used - reserved.
- `usedPercent` uses used + reserved because both consume available quota.
- the UI displays committed usage separately from pending reservations.

## Migration note

This project intentionally uses a single squashed Flyway baseline. The storage schema changes are
therefore added to `V1__initial_schema.sql`.

A database that already recorded the previous V1 checksum must be reset or deliberately
re-baselined before deploying this branch.
