# Contacts & Audience API Contract

Status: FROZEN Round-1 HTTP contract.

Base path:

`/api/v1/companies/{companyId}/contacts`

All IDs are JSON strings.

All endpoints require authenticated company-scoped authorization.

The response envelope follows existing UnifyDesk `ApiResponse`.

Potentially large collections use opaque cursor pagination rather than deep offset pagination.

## 1. Permission keys

Round-1 Contacts permissions:

- `contacts.read`
- `contacts.create`
- `contacts.modify`
- `contacts.delete`
- `contacts.import`
- `contacts.export`
- `contacts.consent.manage`
- `audiences.read`
- `audiences.create`
- `audiences.modify`
- `audiences.delete`

B1 owns backend permission-key definitions/default-role wiring.

F1 mirrors the frozen string constants.

B2 reuses these keys and does not invent alternatives.

## 2. Cursor envelope

Collection payloads use:

```json
{
  "items": [],
  "nextCursor": "opaque-or-null",
  "hasMore": false
}
```

Clients must not parse cursor contents.

Default page size: 50.

Max page size: 200 unless a more restrictive endpoint states otherwise.

Sort choices are endpoint-whitelisted.

## 3. Contact summary

```json
{
  "id": "123",
  "displayName": "Anita Rao",
  "firstName": "Anita",
  "lastName": "Rao",
  "organizationName": "Example Pvt Ltd",
  "jobTitle": "Marketing Manager",
  "countryCode": "IN",
  "timezone": "Asia/Kolkata",
  "languageCode": "en",
  "source": "IMPORT",
  "ownerUserId": "45",
  "status": "ACTIVE",
  "primaryEmail": "anita@example.com",
  "primaryPhone": "+919876543210",
  "tagCount": 3,
  "listCount": 2,
  "lastActivityAt": null,
  "createdAt": "...",
  "updatedAt": "...",
  "version": 4
}
```

Contact detail additionally returns:

- identities
- tags
- lists
- customFields
- channelPreferences
- activeSuppressions
- notes
- sourceDetail

Sensitive audit evidence is not included in normal detail.

## 4. Contacts

### POST /api/v1/companies/{companyId}/contacts

Permission: `contacts.create`

Request:

```json
{
  "firstName": "Anita",
  "lastName": "Rao",
  "displayName": "Anita Rao",
  "organizationName": "Example Pvt Ltd",
  "jobTitle": "Marketing Manager",
  "countryCode": "IN",
  "timezone": "Asia/Kolkata",
  "languageCode": "en",
  "source": "MANUAL",
  "sourceDetail": null,
  "ownerUserId": null,
  "notes": null,
  "identities": [
    {"type": "EMAIL", "value": "anita@example.com", "label": "work", "primary": true},
    {"type": "PHONE", "value": "+919876543210", "label": "mobile", "primary": true}
  ],
  "customFields": {}
}
```

Behavior:

- deterministic normalization;
- conflict with an identity on another active contact => 409 `CONTACT_IDENTITY_CONFLICT`;
- contact entitlement enforced transactionally;
- unknown custom field/type mismatch => 400;
- no implicit consent.

### GET /.../contacts/{contactId}

Permission: `contacts.read`

Foreign/missing contact returns safe 404.

### GET /.../contacts

Permission: `contacts.read`

Supported query:

- `cursor`
- `limit`
- `q`
- `status`
- `ownerUserId`
- `countryCode`
- `tagId`
- `listId`
- `createdAfter/Before`
- `updatedAfter/Before`
- `sort=CREATED_DESC|UPDATED_DESC|NAME_ASC`

`q` searches bounded normalized name/email/phone fields.

### PATCH /.../contacts/{contactId}

Permission: `contacts.modify`

Requires `version` in body or equivalent `If-Match` contract.

Mass assignment is forbidden: only documented fields are patchable.

Identity mutations use dedicated endpoints.

### POST /.../contacts/{contactId}/archive
### POST /.../contacts/{contactId}/restore-archive

Permission: `contacts.modify`

### DELETE /.../contacts/{contactId}

Permission: `contacts.delete`

Soft delete.

### POST /.../contacts/{contactId}/restore

Permission: `contacts.delete`

Restores only an eligible tombstone and fails on identity conflicts.

## 5. Identities

### POST /.../contacts/{contactId}/identities

Permission: `contacts.modify`

Request:

```json
{"type":"EMAIL","value":"other@example.com","label":"personal","primary":false}
```

### PATCH /.../contacts/{contactId}/identities/{identityId}

Patch label/primary only.

Changing destination value is modeled as replace/add+delete to keep consent/suppression provenance clear.

### DELETE /.../contacts/{contactId}/identities/{identityId}

Cannot delete identity if doing so violates an in-flight immutable snapshot—snapshot remains historical, but active profile delete is allowed because snapshot stores its own destination.

Delete does not remove consent/suppression audit.

## 6. Merge

### POST /.../contacts/{targetContactId}/merge

Permission: `contacts.modify`

Request:

```json
{
  "sourceContactId": "456",
  "version": 3,
  "policy": "TARGET_WINS_FILL_NULLS"
}
```

Returns target detail and merge audit ID.

Errors:

- same source/target;
- foreign company;
- source already merged/deleted incompatibly;
- identity conflict involving a third contact;
- concurrent version conflict.

## 7. Custom fields

Base:

`/api/v1/companies/{companyId}/contact-custom-fields`

Permissions:

- read: `contacts.read`
- create/update/archive: `contacts.modify`

Endpoints:

- GET collection
- POST create
- PATCH /{fieldId}
- POST /{fieldId}/archive

Delete is archive-only in Round 1 to preserve existing values/export semantics.

## 8. Tags

Base:

`/api/v1/companies/{companyId}/contact-tags`

Permissions:

- read: `contacts.read`
- mutations: `contacts.modify`

Endpoints:

- GET
- POST
- PATCH /{tagId}
- POST /{tagId}/archive
- POST /contacts/{contactId}/tags/{tagId}
- DELETE /contacts/{contactId}/tags/{tagId}

Bulk tag operation belongs to B2 bulk API.

## 9. Lists

Base:

`/api/v1/companies/{companyId}/contact-lists`

Permissions:

- read: `audiences.read`
- create: `audiences.create`
- update/membership: `audiences.modify`
- archive: `audiences.delete`

Endpoints:

- GET collection
- POST create
- GET /{listId}
- PATCH /{listId}
- POST /{listId}/archive
- GET /{listId}/members?cursor...
- POST /{listId}/members with <=500 contact IDs
- DELETE /{listId}/members/{contactId}

Large membership mutation uses B2 bulk API.

## 10. Segments

Base:

`/api/v1/companies/{companyId}/contact-segments`

Permissions mirror lists.

Endpoints:

### GET /
### POST /
### GET /{segmentId}
### PATCH /{segmentId}
### POST /{segmentId}/archive

Segment request:

```json
{
  "name": "Bengaluru opted-in leads",
  "description": null,
  "definition": {
    "type": "GROUP",
    "operator": "AND",
    "children": [
      {"type":"PREDICATE","field":"countryCode","operator":"EQ","value":"IN"},
      {"type":"PREDICATE","field":"tags","operator":"CONTAINS_ANY","value":["tag-id"]},
      {"type":"PREDICATE","field":"email.marketingConsent","operator":"EQ","value":"OPTED_IN"}
    ]
  }
}
```

### POST /segments/preview

Validates an unsaved definition.

Request optionally asks for sample limit <=100.

Response:

```json
{
  "count": 15342,
  "countFresh": true,
  "sample": [],
  "nextCursor": null
}
```

The server may return `countFresh=false` and an async job reference for expensive counts.

### GET /{segmentId}/members

Cursor-paginated active contacts matching the current version.

## 11. Preferences / consent

Base:

`/api/v1/companies/{companyId}/contacts/{contactId}/preferences`

GET permission: `contacts.read`

Mutation permission: `contacts.consent.manage`

### POST /preferences

Request:

```json
{
  "identityId":"777",
  "channel":"EMAIL",
  "purpose":"MARKETING",
  "state":"OPTED_OUT",
  "source":"USER_REQUEST",
  "sourceRef":"inbox:...",
  "occurredAt":"...",
  "reasonCode":"UNSUBSCRIBE"
}
```

Caller cannot invent arbitrary actor user IDs; server derives authenticated actor.

Import-created opt-in requires explicit mapped consent evidence/source. Merely importing an address never means opt-in.

### GET /consent-history?cursor...

Permission: `contacts.read` or stronger audit permission if later introduced.

Evidence JSON may be redacted from normal users.

## 12. Suppression

Base:

`/api/v1/companies/{companyId}/contacts/{contactId}/suppressions`

GET: `contacts.read`

Manual suppress/release: `contacts.consent.manage`

Provider/system integrations call internal service contracts, not public endpoints with forged source fields.

### POST /suppressions
### POST /suppressions/{suppressionId}/release

## 13. Eligibility

Internal/public-adjacent endpoint for UI estimate:

### POST /api/v1/companies/{companyId}/contact-eligibility/check

Permission: `audiences.read`

Request max 100 identities/contact IDs.

Returns structured decisions.

High-volume delivery systems call the internal B2 service/SQL seam, not this HTTP endpoint per recipient.

## 14. Imports

Base:

`/api/v1/companies/{companyId}/contact-imports`

Permission: `contacts.import`

### POST /uploads

Multipart CSV upload.

Headers:

- required `Idempotency-Key`

Returns import ID + async job/status.

Hard safety checks occur before durable object retention where possible.

### POST /{importId}/preview

Parses bounded sample and returns:

- detected headers;
- sample rows with sensitive values appropriately limited;
- suggested mappings;
- validation warnings.

Preview does not mutate contacts.

### POST /{importId}/start

Request:

```json
{
  "mapping":[
    {"column":"Email","target":"identity.email","primary":true},
    {"column":"First Name","target":"firstName"}
  ],
  "duplicateMode":"UPSERT_MATCHED",
  "defaultCountryCode":"IN",
  "targetListId":"123"
}
```

Mapping becomes immutable when processing starts.

### GET /{importId}
### POST /{importId}/cancel
### GET /{importId}/errors?cursor...
### POST /{importId}/error-report-access

Access URL response is short-lived and re-authorized.

## 15. Exports

Base:

`/api/v1/companies/{companyId}/contact-exports`

Permission: `contacts.export`

### POST /

Request:

```json
{
  "selection":{"type":"SEGMENT","id":"123"},
  "fields":["firstName","lastName","primaryEmail","countryCode","custom:customer_tier"]
}
```

Returns 202 with export + async job.

### GET /{exportId}
### POST /{exportId}/access

Access checks current permission/company every time.

## 16. Bulk operations

Base:

`/api/v1/companies/{companyId}/contact-bulk-jobs`

Permission depends on action:

- update/tag/list/archive: `contacts.modify` and audience permission if list-owned;
- delete: `contacts.delete`;
- export: `contacts.export`.

### POST /

Request:

```json
{
  "operation":"ADD_TAG",
  "selection":{"type":"SEGMENT","id":"123","version":4},
  "payload":{"tagId":"456"}
}
```

Requires Idempotency-Key.

### GET /{bulkJobId}

## 17. Audience snapshots

Internal product API:

`/api/v1/internal/companies/{companyId}/contact-audience-snapshots`

Not exposed as a generic customer endpoint in Round 1.

Request contract:

```json
{
  "selection":{"type":"SEGMENT","id":"123","version":4},
  "channel":"EMAIL",
  "purpose":"MARKETING",
  "idempotencyKey":"broadcast-run:999"
}
```

Result:

- snapshotId
- status
- totalMatched
- eligibleCount
- excludedCount
- exclusionCounts

Consumer iterates eligible snapshot members by keyset through an internal repository/service seam.

## 18. Error codes

At minimum:

- CONTACT_NOT_FOUND
- CONTACT_IDENTITY_CONFLICT
- CONTACT_VERSION_CONFLICT
- CONTACT_LIMIT_REACHED
- CONTACT_INVALID_IDENTITY
- CONTACT_INVALID_PHONE_REGION
- CONTACT_CUSTOM_FIELD_INVALID
- CONTACT_MERGE_CONFLICT
- CONTACT_RESTORE_CONFLICT
- CONTACT_LIST_NOT_FOUND
- CONTACT_SEGMENT_NOT_FOUND
- CONTACT_SEGMENT_INVALID
- CONTACT_SEGMENT_TOO_COMPLEX
- CONTACT_IMPORT_INVALID_FILE
- CONTACT_IMPORT_MAPPING_INVALID
- CONTACT_IMPORT_IDENTITY_CONFLICT
- CONTACT_IMPORT_LIMIT_REACHED
- CONTACT_EXPORT_NOT_READY
- CONTACT_BULK_INVALID_SELECTION
- CONTACT_CONSENT_INVALID_TRANSITION
- CONTACT_FORBIDDEN

Errors must not reveal whether a foreign-company ID exists.
