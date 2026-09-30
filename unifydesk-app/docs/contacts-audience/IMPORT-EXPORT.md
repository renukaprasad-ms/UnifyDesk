# Contacts Import / Export Contract

## 1. Round-1 scope

Supported import format: CSV only.

Supported export format: CSV only.

Both are asynchronous beyond the small bounded preview step.

## 2. Import storage

The user-facing Media API currently accepts image/PDF media and should not be stretched into a generic document-ingestion API.

B2 implements a Contacts-owned private file adapter over the existing storage provider/accounting primitives.

Requirements:

- private object only;
- company-scoped object key;
- random/non-user-controlled server key;
- no public URL;
- declared and actual size bounds;
- SHA-256 checksum;
- retention/expiry;
- storage accounting using CONTACT_DATA;
- delete/release on expiry/cancel/failure where appropriate.

## 3. File validation

Before processing:

- extension/name is advisory only;
- MIME cannot be trusted by itself;
- reject NUL/path traversal filename content;
- reject empty file;
- enforce configured maximum upload bytes;
- parse as bounded text using supported encoding policy;
- reject pathological line/field/header sizes;
- maximum columns: 200;
- duplicate/blank headers are surfaced explicitly;
- delimiter is comma in Round 1;
- UTF-8 is preferred/frozen; optional BOM tolerated.

No ZIP/XLSX parsing in Round 1.

## 4. Preview

Preview reads:

- header;
- bounded number of sample rows;
- rough validation statistics where cheap.

It must not mutate contacts.

Suggested mapping may map common aliases to:

- firstName
- lastName
- displayName
- organizationName
- jobTitle
- countryCode
- timezone
- languageCode
- sourceDetail
- notes
- identity.email
- identity.phone
- custom:<fieldKey>
- consent:<channel>:MARKETING

Suggestions are advisory. Start requires explicit confirmed mapping.

## 5. Mapping rules

A source column maps to at most one scalar target.

Multiple email/phone source columns may map to multiple identities and nominate at most one primary per type.

Unknown custom fields:

- may be rejected; or
- user may explicitly create a new field before start if permission/entitlement allows.

The worker does not silently create hundreds of custom fields from CSV headers.

## 6. Duplicate resolution per row

Normalize mapped identities.

Cases:

### No usable identity

Row is invalid for create/upsert in Round 1 unless future external-ID support is added.

### All matching identities resolve to zero contacts

Create a new contact subject to CONTACT_LIMIT.

### One or more identities all resolve to exactly one same contact

Matched row.

Action depends on duplicate mode.

### Identities resolve to more than one existing contact

`IDENTITY_CONFLICT`.

No automatic merge.

### Duplicate rows within same import

Earlier committed row establishes identity ownership.

Later row follows the same duplicate-mode semantics.

Import idempotency/checkpointing prevents replay from creating duplicate contacts.

## 7. Duplicate modes

### CREATE_ONLY

- unmatched -> create
- matched -> skip with MATCHED_EXISTING
- conflict -> error

### SKIP_MATCHED

Same create behavior, matched rows intentionally skipped without being treated as a job failure.

### UPSERT_MATCHED

- unmatched -> create
- matched -> patch only mapped fields under the frozen import policy
- conflict -> error

Upsert does not:

- clear non-mapped fields;
- clear consent;
- release suppression;
- transfer identity from another contact;
- force merge.

## 8. Consent import

Consent is never inferred from address presence or list membership.

Consent mapping requires explicit source columns/value mapping and a declared evidence/source policy.

Examples:

- "email_opt_in=true" mapped to EMAIL/MARKETING/OPTED_IN;
- "sms_status=unsubscribed" mapped to SMS/MARKETING/OPTED_OUT.

B2 validates accepted truth values/enums.

Re-import cannot overwrite a later/newer opt-out with stale consent evidence without an explicit allowed occurrence timestamp/policy.

## 9. Phone normalization

Import start may specify `defaultCountryCode`.

National phone input without enough regional context is a row error.

Do not guess from company website, UI locale or IP.

## 10. Processing

Worker claims CONTACT_IMPORT jobs using existing AsyncJob recovery pattern.

Batch recommendations:

- 250-1000 rows/chunk depending on query/write plan;
- commit after each chunk;
- checkpoint transactionally with effects;
- never keep complete CSV or complete error list in memory.

Use set-based identity lookup per chunk where possible.

Avoid one DB query per cell/row.

## 11. Error reporting

Each row has at most a bounded number of primary error entries.

Example codes:

- INVALID_EMAIL
- INVALID_PHONE
- PHONE_REGION_REQUIRED
- IDENTITY_CONFLICT
- CUSTOM_FIELD_TYPE_MISMATCH
- UNKNOWN_CUSTOM_FIELD
- INVALID_CONSENT_VALUE
- CONTACT_LIMIT_REACHED
- ROW_TOO_LARGE

Database retention stores bounded safe previews only.

Full downloadable error CSV is streamed to private storage and expires.

## 12. CSV injection

Exported cells beginning with spreadsheet-formula characters after optional leading whitespace must be neutralized.

Danger prefixes include:

- =
- +
- -
- @
- tab/carriage-return variants recognized by common spreadsheet tools

Use a deterministic safe export encoding strategy, such as prefixing a single quote, and document it in the UI.

Do not alter normal stored contact values just to make exports safe.

## 13. Export selection

Supported:

- all active contacts with frozen filter;
- list;
- segment version;
- explicit small contact selection.

Export stores the selection/version at request time.

The worker keyset-streams contacts.

## 14. Export fields

Default safe profile fields may be exported.

Consent history evidence and internal suppression source metadata are excluded unless explicitly supported by a future audited export type.

## 15. Authorization

Create-time authorization is not enough.

- export job creator must have contacts.export;
- status API checks company;
- access URL endpoint rechecks current permission;
- foreign/export IDs return safe 404;
- signed URL lifetime is short;
- URLs are not logged.

## 16. Retention

Recommended default:

- source import file: 7 days after terminal state;
- import error report: 7 days;
- completed export: 24 hours;
- failed/cancelled unused artifacts: purge promptly.

Configuration may change these durations.

DB control/audit rows can be retained longer than file bytes.

## 17. Cancellation

Cancellation is cooperative.

- queued -> cancel before start;
- running -> worker observes cancel at chunk boundary;
- committed chunks remain committed;
- status records partial counts;
- no rollback of millions of already committed changes.

## 18. Tests

At minimum:

- duplicate headers;
- 0-byte;
- oversize;
- huge one-line CSV;
- quoted comma/newline;
- BOM;
- malformed quote;
- invalid UTF-8 policy;
- 200+ columns;
- duplicate same-row email/phone;
- cross-contact identity conflict;
- crash/reclaim at chunk boundary;
- repeated Idempotency-Key;
- cancellation;
- quota failure;
- formula injection export;
- access after permission revoked;
- expired artifact;
- cross-company artifact ID.
