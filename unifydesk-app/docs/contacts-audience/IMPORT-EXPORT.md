# Import / Export Architecture

## V1 import: CSV

Lifecycle:
DRAFT_UPLOAD -> UPLOADED -> PREVIEWED -> QUEUED -> RUNNING -> COMPLETED
Failure states:
VALIDATION_FAILED | FAILED | EXPIRED

## Upload

Contacts uses existing storage provider/quota/accounting through a Contacts storage adapter.

New StorageUsageType values are not needed:
- CONTACT_DATA for import/error artifacts
- EXPORT for generated exports

Because MediaValidationService currently accepts media/document MIME types only, Contacts does not add text/csv to that generic validator.

## Preview

Preview reads a bounded prefix:
- file metadata
- detected delimiter/encoding policy
- headers
- sample rows
- validation counts estimate
- possible field mappings

No Contact mutation occurs during preview.

## Mapping

Mapping may target:
- built-in properties
- EMAIL identity
- PHONE identity
- tag names
- custom field IDs
- marketing consent columns only when explicit evidence/source mapping is provided

Unmapped columns can be ignored or mapped to pre-created custom fields. Worker does not auto-create an unbounded number of custom fields from CSV headers.

## Duplicate strategy

SKIP:
- existing exact identity -> no mutation

MERGE_EXACT:
- incoming exact identities resolving to one Contact -> merge mapped fields according to import rules
- identities resolving to different Contacts -> row conflict/error; no automatic Contact-to-Contact merge

FAIL:
- any duplicate match fails that row

## Import idempotency

Import commit requires idempotencyKey.
Unique(company_id, idempotency_key).

The same key + same content checksum/mapping hash returns the existing operation.
Same key + different checksum/mapping returns 409.

Within processing, durable row/batch progress prevents a reclaimed worker from blindly duplicating mutations. Database uniqueness remains the final guard.

## Validation

Email:
- syntactic + normalization only in core import
- do not claim mailbox deliverability validation without a dedicated service

Phone:
- parse to E.164 with explicit/default country context
- invalid/ambiguous row goes to error report

Constraints:
- max bytes
- max rows
- max columns
- max cell length
- UTF-8 policy with BOM support
- bounded parser errors

## Error report

CSV columns:
- rowNumber
- errorCode
- safeMessage
- optionally sanitized original identifiers needed to fix row

The report is private, temporary, storage-accounted and downloadable through an authorized signed URL.

## Export

Sources:
- all Contacts under current filters
- List
- Segment

Exports are always async when source is not trivially small.

Generated CSV:
- applies CSV formula-injection neutralization
- uses current authorized field selection
- excludes internal IDs unless explicitly part of product contract
- excludes hidden provider/audit internals

Signed URL expires quickly. Export object itself has a retention expiry and cleanup.

## XLSX

Deferred. Add only after:
- parser dependency review
- zip-bomb protection
- formula/macro policy
- memory/streaming benchmarks
- QA abuse tests
