# Prompt — B1 Backend Contacts Core / Control Plane

You are B1 for UnifyDesk Contacts & Audience Management Round 1.

Repository:
`renukaprasad-ms/messaging`

Application:
`unifydesk-app/`

Docs:
`unifydesk-app/docs/contacts-audience/`

Your branch:
`feature/contacts-core-r1`

Your EXACT frozen base SHA:
`__ROUND1_BASE_SHA__`

Create your branch from exactly that SHA.

Do NOT branch from moving dev.
Do NOT merge your branch.
Do NOT cherry-pick B2/F1.

The prompt may live on a later docs metadata commit; that does not change the branch base.

## Read first

Read completely:

- README.md
- RESEARCH.md
- ARCHITECTURE.md
- DECISIONS.md
- DATA-MODEL.md
- API-CONTRACT.md
- CONTACT-MERGE.md
- SECURITY.md
- PERFORMANCE.md
- INTEGRATIONS.md
- TASKS.md
- TEAM_WORKFLOW.md

Then inspect current:
- CompanyAuthorizationService
- CompanyPermissionKeys
- company IAM/default role bootstrapping
- EntitlementService
- SystemEntitlementCatalogInitializer
- common ID/response/error conventions
- lifecycle patterns
- existing tests/package conventions

## Mission

Implement the Contacts Core only:

- canonical contact profiles;
- multiple identities;
- normalization/validation;
- contact CRUD/search/detail;
- typed custom fields;
- tags;
- static lists/membership;
- merge;
- archive/delete/restore;
- core activity references;
- permission integration;
- plan limit integration for B1-owned limits.

## Exact ownership

New backend package:

`backend/src/main/java/com/messaging/contacts/core/**`

Matching tests:

`backend/src/test/java/com/messaging/contacts/core/**`

You own new controllers/services/entities/repositories/DTOs within that package.

Shared existing files you MAY modify only when required:

- `backend/src/main/java/com/messaging/company/iam/CompanyPermissionKeys.java`
- company default-role/IAM bootstrap files needed to register frozen Contacts permissions
- `backend/src/main/java/com/messaging/billing/bootstrap/SystemEntitlementCatalogInitializer.java` only for B1-owned new list/custom-field limits if actually required

Do not modify plan names/prices.

If a shared hot-file change conflicts with ongoing work, report it for L1 integration rather than broad refactor.

## Migrations

Your allocation only:

- `V90__contacts_core.sql`
- `V91__contacts_lists_tags_custom_fields.sql`
- `V92__contacts_core_hardening.sql`

You may use fewer.

Do NOT create V93+.

Your migrations must work from the frozen base without B2.

## Frozen data contract

Implement B1-owned tables exactly per DATA-MODEL.md, preserving the B2 SQL seam.

At minimum:

- contact_records
- contact_identities
- contact_custom_field_definitions
- contact_tags
- contact_tag_memberships
- contact_lists
- contact_list_memberships
- contact_merge_audit
- contact_activity_refs

Do not rename frozen B2 seam columns silently.

## Normalization

Email:

- trim;
- Unicode normalize;
- lower-case domain and mailbox for matching;
- preserve dot/plus semantics;
- validate;
- retain raw/display.

Phone:

Use a proven parser/library if one is already appropriate or add the minimal well-maintained dependency required.

Normalize valid numbers to E.164.

National input requires explicit/default country.

Do not implement hand-written country dialing logic.

## Identity concurrency

Database constraints are authoritative.

Test concurrent same-email/same-phone claims.

Primary identity changes must be concurrency safe.

One active normalized identity per company/type.

## Contact limits

Use EntitlementService `CONTACT_LIMIT`.

Enforce under concurrency.

Do not `count(*)` every row insert during a large future import.

Provide a core service/JDBC seam L1/B2 can use for capacity reservation/checking.

Do not invent plan names.

## Custom fields

Implement definitions and bounded JSONB values.

Types:

- TEXT
- NUMBER
- BOOLEAN
- DATE
- DATETIME
- SINGLE_SELECT
- MULTI_SELECT

Validate against active definition/options.

Hard cap serialized customFields size.

No EAV value table.

## Tags/lists

- normalized unique names;
- idempotent memberships;
- company scope;
- denormalized counts transactionally safe;
- reconciliation helper/test if needed;
- list membership never changes consent.

## Merge

Implement CONTACT-MERGE.md exactly.

- lock in deterministic order;
- TARGET_WINS_FILL_NULLS;
- identity union;
- third-contact conflict abort;
- tag/list union;
- merge audit;
- source MERGED tombstone;
- no consent implementation on B1 branch.

For B2-owned consent/suppression rows that will exist only after integration, expose/report an integration seam rather than importing B2 code.

## Delete / restore

Soft delete + retention metadata.

Restore checks identity conflicts and contact limit.

Do not cascade historical Broadcast/Inbox data.

Do not implement final physical purge loop; L1 integrates lifecycle after B1/B2.

## API

Implement B1 API portions in API-CONTRACT.md.

Use opaque cursor/keyset pagination for large collections.

Mass assignment forbidden.

Company ID from path/authorized context.

String IDs in responses.

Optimistic version conflict.

## Permissions

Implement frozen keys:

- contacts.read
- contacts.create
- contacts.modify
- contacts.delete
- audiences.read
- audiences.create
- audiences.modify
- audiences.delete

B2 owns enforcement of:
- contacts.import
- contacts.export
- contacts.consent.manage

But shared permission catalog may contain all keys if B1 owns the hot file. Coordinate only through frozen strings; do not wait for B2.

Recommended default-role behavior should follow existing product conventions. Do not silently grant destructive/export permissions to ordinary Member if that differs from current IAM patterns.

## Security

Must test:

- tenant isolation;
- foreign IDs;
- identity enumeration;
- cross-company duplicate values;
- mass assignment;
- merge IDOR;
- optimistic concurrency;
- invalid custom fields;
- owner assignment;
- large notes/customFields;
- log redaction.

## Performance

- exact identity indexes;
- company/status/id keyset;
- no N+1 table list;
- bounded detail;
- count-safe memberships;
- million-row compatible index design.

## Forbidden

Do not implement/modify:

- `com.messaging.contacts.audience/**`
- B2 migrations V93-V95
- import/export
- segments
- consent/suppression
- audience snapshots
- Broadcast runtime
- Inbox runtime
- Ad Center
- frontend
- unrelated refactors

If another owner's file is required, report an integration request.

## Required tests

At minimum:

- contact CRUD
- multiple identities
- normalization
- cross-company same identity
- same-company conflict
- concurrent identity claim
- primary identity
- cursor pagination
- search
- optimistic version
- custom field types/limits
- tag/list membership/count
- merge normal/conflict/concurrent
- archive/delete/restore
- entitlement limit
- permissions/foreign IDs
- clean B1 migration from frozen base

Run:

```bash
cd unifydesk-app/backend
./mvnw spotless:check
./mvnw test
./mvnw package -DskipTests
```

## PR requirements

Open a PR to dev.

PR body includes:

- frozen base SHA;
- migration numbers;
- API endpoints;
- test results;
- security/performance notes;
- any contract deviation;
- L1 integration requests.

## Final report

Report:

- branch
- final commit SHA
- PR number/link
- exact files changed
- migrations used
- APIs implemented
- tests/checks
- contract deviations/integration requests
- blocker

Do not merge.
