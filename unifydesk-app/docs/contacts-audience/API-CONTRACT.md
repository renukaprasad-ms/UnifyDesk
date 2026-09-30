# Contacts & Audience API Contract

Status: Round-1 frozen HTTP contract

Base:
 /api/v1/companies/{companyId}/contacts

All endpoints require an authenticated user and explicit company permission. IDs are decimal strings in JSON, following existing frontend conventions.

## Pagination

Potentially large collections use keyset/cursor pagination.

Response shape:
{
  "items": [...],
  "nextCursor": "opaque-or-null",
  "hasMore": true
}

Cursor is opaque, signed/validated server-side where needed, and tied to the sort/filter shape. Clients must not construct cursors.

Default size 50. Hard maximum 200.

Offset/page is allowed only for small admin metadata collections such as tags/custom-field definitions if existing conventions make it simpler.

## Contact resources

GET /contacts
Query:
- cursor
- size
- q
- status
- tagId
- listId
- createdAfter/createdBefore
- sort = UPDATED_DESC | CREATED_DESC | NAME_ASC

Returns ContactSummary.

POST /contacts
Creates Contact + optional identities/tags/custom fields. Exact identity conflict returns 409 with safe code CONTACT_IDENTITY_CONFLICT.

GET /contacts/{contactId}
Returns ContactDetail.

PATCH /contacts/{contactId}
Partial update. Mass assignment is prohibited: only documented mutable fields are accepted.

DELETE /contacts/{contactId}
202 Accepted when purge is queued; idempotent if already deleted.

POST /contacts/{contactId}/identities
POST /contacts/{contactId}/identities/{identityId}/primary
DELETE /contacts/{contactId}/identities/{identityId}

POST /contacts/merge
Request:
{
  "sourceContactId": "...",
  "targetContactId": "...",
  "sourceVersion": 1,
  "targetVersion": 2,
  "fieldStrategy": {...}
}

## Tags

GET /tags
POST /tags
PATCH /tags/{tagId}
DELETE /tags/{tagId} -> archive

POST /contacts/{contactId}/tags/{tagId}
DELETE /contacts/{contactId}/tags/{tagId}

Bulk tag operations use /bulk-operations rather than huge arrays on the synchronous endpoint.

## Custom fields

GET /custom-fields
POST /custom-fields
PATCH /custom-fields/{fieldId}
DELETE /custom-fields/{fieldId} -> archive

Definition response includes type/options/indexability metadata.

Contact create/update uses:
"customFields": {
  "<fieldId>": <typed JSON value>
}

Unknown/foreign/archived definitions are rejected.

## Lists

GET /lists
POST /lists
GET /lists/{listId}
PATCH /lists/{listId}
DELETE /lists/{listId} -> archive

GET /lists/{listId}/members?cursor=&size=
POST /lists/{listId}/members
Small synchronous request:
{ "contactIds": ["..."] }
Hard max 500 IDs.

DELETE /lists/{listId}/members/{contactId}

Large selection membership changes use bulk operations.

## Segments

GET /segments
POST /segments
GET /segments/{segmentId}
PUT /segments/{segmentId}
DELETE /segments/{segmentId} -> archive

POST /segments/validate
Validates filter AST and returns normalized AST/hash/cost class.

POST /segments/preview
Request:
{
  "filterVersion": 1,
  "filter": {...},
  "sampleSize": 20
}
Response:
{
  "sample": [...],
  "count": 1234,
  "countState": "EXACT|CACHED|PENDING",
  "countComputedAt": "..."
}

POST /segments/{segmentId}/refresh-count
202 AsyncJobResponse when exact count is queued.

## Communication preferences

GET /contacts/{contactId}/preferences

PUT /contacts/{contactId}/preferences/{channel}/{purpose}
Request:
{
  "state": "OPTED_IN|OPTED_OUT|UNKNOWN",
  "identityId": "...|null",
  "source": "USER_UI|IMPORT|API|...",
  "sourceRef": "...|null",
  "occurredAt": "...",
  "reason": "..."
}

The backend supplies authenticated actor identity; callers cannot spoof actor type/user ID.

## Suppressions

GET /contacts/{contactId}/suppressions

POST /contacts/{contactId}/suppressions
Manual/company UI only. Provider-generated suppressions are written through internal adapters.

POST /contacts/{contactId}/suppressions/{suppressionId}/lift

Lifting requires CONTACT_CONSENT_MODIFY and cannot lift immutable provider/legal classes when policy marks them non-liftable.

## Eligibility

Internal-first API; no public bulk eligibility endpoint in Round 1.

Optional UI diagnostic:
GET /contacts/{contactId}/eligibility?channel=EMAIL&purpose=MARKETING&identityId=

Returns safe reason codes, never provider secrets.

## Import

POST /imports
Creates an upload/import intent and returns upload contract or server-upload endpoint depending on storage adapter implementation.

POST /imports/{importId}/preview
Runs bounded validation/sample parsing before commit.

POST /imports/{importId}/commit
Request includes mapping, duplicate strategy, optional targetListId, idempotencyKey.
Returns 202 AsyncJobResponse.

GET /imports/{importId}
GET /imports/{importId}/errors
Errors endpoint returns metadata + temporary signed download when ready.

Import commit is immutable after RUNNING.

## Export

POST /exports
Request:
{
  "sourceType": "CONTACTS|LIST|SEGMENT",
  "sourceId": "...|null",
  "fields": [...],
  "idempotencyKey": "..."
}

Returns 202 AsyncJobResponse/export resource.

GET /exports/{exportId}
GET /exports/{exportId}/download
Download endpoint authorizes every request and returns a short-lived signed URL only while the export is unexpired.

## Bulk operations

POST /bulk-operations
Request:
{
  "selection": {
    "type": "IDS|LIST|SEGMENT|FILTER",
    ...
  },
  "operation": {
    "type": "ADD_TAG|REMOVE_TAG|ADD_TO_LIST|REMOVE_FROM_LIST|UPDATE_FIELD|DELETE",
    ...
  },
  "idempotencyKey": "..."
}

Small ID selections may complete synchronously only if bounded by server threshold; otherwise 202 job.

GET /bulk-operations/{operationId}

## Activity

GET /contacts/{contactId}/activity?cursor=&size=&types=

Returns a normalized timeline assembled from:
- Contacts-domain activity events
- Broadcast recipient/event read adapter
- Inbox read adapter

Response contains safe summaries/links, not copied full message/provider payloads.

## Internal audience contract

Provider-neutral Java/SQL boundary:

resolve(sourceType, sourceId, companyId, channel, purpose, cursor, limit)
-> ordered candidate Contact IDs + sourceVersion/hash

selectIdentity(contactId, channel)
-> identityId + normalizedDestination

evaluateEligibility(...)
-> decision/reason

materializeSnapshot(...)
-> durable snapshot + members

This internal contract is frozen for Lead Broadcast integration.

## Error codes

At minimum:
- CONTACT_NOT_FOUND
- CONTACT_IDENTITY_CONFLICT
- CONTACT_VERSION_CONFLICT
- CONTACT_LIMIT_REACHED
- CONTACT_INVALID_IDENTITY
- CONTACT_FIELD_INVALID
- CONTACT_FIELD_LIMIT_REACHED
- CONTACT_LIST_LIMIT_REACHED
- CONTACT_SEGMENT_LIMIT_REACHED
- CONTACT_SEGMENT_INVALID
- CONTACT_IMPORT_INVALID
- CONTACT_IMPORT_IDEMPOTENCY_CONFLICT
- CONTACT_EXPORT_NOT_READY
- CONTACT_PERMISSION_DENIED

No error reveals whether a foreign-company resource exists.
