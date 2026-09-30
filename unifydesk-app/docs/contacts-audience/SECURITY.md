# Contacts & Audience Security

## Primary threat

This module stores customer PII. A tenant-isolation failure is release-blocking.

## Authorization invariants

- Every company API resolves current user server-side.
- Every endpoint calls CompanyAuthorizationService for the exact company.
- Every resource load includes company_id in the query or validates company ownership before use.
- Foreign IDs return the same safe not-found behavior as nonexistent IDs.
- Bulk jobs persist company_id/user_id and revalidate authorization/policy when execution begins if privileges may have changed.
- Frontend permission hiding is never authorization.

New permission keys:
- contact.read
- contact.create
- contact.modify
- contact.delete
- contact.import
- contact.export
- contact.consent.read
- contact.consent.modify

Owner gets implicit full access through existing owner semantics. Manager/member defaults are Lead-frozen in TASKS.md; workers must not broaden defaults casually.

## IDOR/cross-tenant tests

Mandatory for:
- contact
- identity
- tag
- custom field
- list
- segment
- import
- export
- bulk operation
- preference
- suppression
- merge
- activity
- signed download

## Mass assignment

DTOs enumerate accepted fields. Never bind request JSON directly to JPA entities.

Server-owned fields include:
- companyId
- IDs
- status where lifecycle-owned
- created/updated/deleted timestamps
- merge target
- actor IDs/types
- provider/source trust class
- counts
- audit metadata

## Import security

- CSV only in V1
- size/row/column limits
- stream parser, bounded cell length
- reject NUL/control abuse
- normalize headers
- no path-controlled storage keys
- content checksum
- idempotent commit
- formula injection handling on exported/error CSV
- preview must not execute spreadsheet formulas
- failure rows must not include unrelated tenant data
- logs exclude row PII by default

If server-side multipart upload is used, enforce hard byte limits before parsing. Preferred production path is signed private-object upload with post-upload size/checksum validation through the Contacts storage adapter.

## CSV formula injection

Any exported cell whose first non-whitespace character is one of:
= + - @
must be neutralized using a documented safe CSV strategy. Preserve original database value; sanitize only the file representation.

## Export security

- CONTACT_EXPORT permission required
- source List/Segment re-resolved by company
- export object private
- short-lived signed download
- expiry and deletion worker
- no permanent public URL
- audit event records requester/source/row count
- download endpoint reauthorizes, not just possession of export ID

## PII/logging

Do not log:
- raw imported row
- full email/phone
- notes/custom-field values
- consent evidence payload
- export signed URLs
- storage credentials

Operational logs use company ID, contact ID, job ID, stable error codes and redacted identity fingerprints when necessary.

## Enumeration/rate limits

Search and identity lookup require company permission and are rate-limited using existing platform primitives. Do not expose global identity-existence APIs.

## Cursor safety

Cursor contains only server-generated sort keys/filter hash/version and is validated. Foreign/modified cursor fails safely. Cursors must not permit bypassing company/filter predicates.

## Consent integrity

- actor fields server-owned
- immutable preference/suppression event rows
- idempotency keys on provider/import/API writes
- current projection update and event append in one DB transaction
- occurredAt from untrusted clients is bounded/validated; createdAt remains server time

## Suppression deletion safety

Hard opt-out/complaint tombstones can survive Contact privacy purge as keyed HMAC fingerprints. No plaintext identity remains in the tombstone. Reimport checks the fingerprint before enabling delivery.

## Merge safety

- deterministic row locking
- company-scoped loads
- optimistic version guard
- suppression cannot be lost
- no identity may become double-owned

## File lifecycle

Contact-data/export temporary objects have TTL and cleanup jobs. Storage accounting is released idempotently on cleanup/failure.

## Audit

Security-significant operations record:
- contact delete
- merge
- export requested/downloaded
- consent change
- suppression change
- bulk delete
- import committed
- custom field definition change

Reuse existing audit infrastructure when available; do not create a second global audit subsystem.

## Encryption

Use platform/database/storage encryption-at-rest. Do not invent application-level per-field encryption in Round 1 unless existing platform policy already mandates it. Secrets used for suppression HMAC are application secret material and never stored with data.
