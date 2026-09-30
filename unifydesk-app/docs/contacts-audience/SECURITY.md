# Contacts & Audience Security

## 1. Threat model

Contacts stores high-value customer PII at large scale.

Primary risks:

- cross-company IDOR;
- unauthorized export;
- mass scraping/enumeration;
- malicious imports;
- CSV injection;
- consent tampering;
- over-broad bulk operations;
- identity collision/merge corruption;
- PII leakage in logs/events/errors;
- stale signed URLs;
- expensive segment DoS;
- quota bypass under concurrency.

## 2. Tenant isolation

Every repository/query must bind company ID from the authorized route/service context.

Never:
- load by contactId and authorize after returning DTO;
- accept companyId from body as authoritative;
- use global identity lookup for UI/API operations.

Foreign IDs return safe 404 or generic forbidden according to existing conventions without confirming existence.

## 3. Authorization

Use CompanyAuthorizationService + frozen permission keys.

Read does not imply:
- export;
- import;
- delete;
- consent mutation.

Bulk jobs derive required permission from operation type.

Artifact access rechecks authorization at access time.

## 4. Mass assignment

DTOs expose only allowed fields.

Server derives:

- companyId;
- actor user;
- createdBy/updatedBy;
- status transitions;
- merge linkage;
- consent event audit IDs;
- suppression source for internal provider signals.

Reject unknown dangerous fields where deserialization configuration permits.

## 5. Identity security

Exact identity uniqueness is company-scoped.

Do not expose global "does this email exist?" endpoints.

Search is authenticated, tenant-scoped, rate limited and page limited.

Conflict responses do not reveal foreign tenant data.

## 6. Import security

- CSV only;
- byte limit;
- row/column/field limit;
- private storage;
- generated object keys;
- no executable macros/archive extraction;
- bounded parser;
- validation before contact mutation;
- no formula execution server-side;
- no HTML rendering of raw cells without escaping;
- sanitized error preview;
- retention expiry;
- checksum/idempotency;
- rate limits.

If future XLSX is added, it requires a separate zip-bomb/entity-expansion/security review.

## 7. Export security

- separate contacts.export permission;
- async private artifact;
- short signed URL;
- authorization checked before URL generation;
- no long-lived public URL;
- CSV formula neutralization;
- no secrets/internal audit evidence by default;
- audit export request/access;
- rate/row limits;
- artifact expiry.

## 8. Consent integrity

Consent events are append-only through the domain service.

Authenticated user cannot forge:
- provider source;
- system source;
- actor ID.

Import/API opt-in requires explicit evidence/source mapping.

Reimport does not silently clear opt-out/suppression.

## 9. Suppression integrity

Provider/system adapters use internal authenticated service calls.

Manual release requires contacts.consent.manage.

Hard-bounce/complaint release policies are explicit.

## 10. Bulk-operation safety

- frozen selection definition/version;
- hard request caps;
- async for large selection;
- company scope baked into selection;
- permission recheck at start/claim where practical;
- idempotency;
- chunk transactions;
- cancellation;
- counts and audit.

Never accept a raw SQL filter or millions of IDs in one JSON body.

## 11. Segment DoS/injection

- closed AST;
- bound parameters;
- depth/clause caps;
- no regex;
- no raw field names;
- timeout/bounded sync preview;
- async expensive counts;
- page caps;
- metric query latency.

## 12. Log redaction

Do not log:

- raw CSV rows;
- complete email/phone lists;
- notes;
- custom field JSON;
- consent evidence blobs;
- signed URLs;
- object keys if they are access-sensitive;
- export contents.

Logs may use:
- company/contact/import/job IDs;
- masked destination;
- counts;
- error codes;
- provider-independent reason.

## 13. API validation

Validate:

- names/notes length;
- ISO country;
- timezone;
- language format;
- custom field type/size;
- email;
- phone;
- tag/list/segment names;
- cursor;
- versions;
- idempotency-key length/charset;
- JSON complexity.

## 14. CSRF

Use existing application CSRF/session behavior for mutations.

Do not create an unauthenticated upload completion endpoint.

## 15. Rate limiting

Apply existing/general rate limiting where present and add Contacts-local guards where needed:

- contact search;
- identity conflict attempts;
- import create/start;
- export create/access;
- segment preview/count;
- bulk create;
- merge.

Limits are per user/company and must not become global denial-of-service between tenants.

## 16. PII in realtime/events

Realtime/event payloads carry IDs and safe invalidation/change codes.

Frontend refetches authorized detail.

Do not broadcast contact profile payload to every workspace session by default.

## 17. Soft delete and privacy

Deleted data is hidden from ordinary APIs/export.

Purge is bounded.

Any retained suppression fingerprint must be one-way and purpose-limited.

Historical campaign rows should not be rewritten in a way that corrupts reporting.

## 18. Storage security

Private bucket/object.

Validate company ownership before object read/delete.

Short signed URL expiry.

Avoid user-provided object keys.

Account storage usage idempotently.

## 19. Concurrency

Database unique constraints are authoritative for:

- identities;
- memberships;
- idempotency;
- current preferences;
- snapshots.

Service locks must have deterministic order for merge/primary identity changes.

Entitlement enforcement must withstand concurrent creates/imports.

## 20. QA abuse matrix

QA must cover:

- foreign contact/list/tag/segment/import/export/bulk/snapshot IDs;
- permission combinations;
- platform-admin workspace access;
- search enumeration;
- merge IDOR;
- custom field mass assignment;
- CSV XSS/formula strings;
- malicious huge CSV;
- cursor tampering;
- segment injection;
- consent actor/source forgery;
- suppression release;
- revoked permission before export access;
- delete/restore conflict;
- cross-company identical email/phone;
- signed URL expiry/replay;
- log inspection for PII.
