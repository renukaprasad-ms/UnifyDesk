# B1 Prompt — Contacts Core Round 1

You are B1 for UnifyDesk Contacts & Audience Management.

Repository:
renukaprasad-ms/messaging

Application:
unifydesk-app/

Your EXACT frozen worker base SHA:
f31f4f950cc257d4d1edd947a5038a3447599f85

Your branch:
feature/contacts-core-r1

Create the branch from EXACTLY that SHA.
Do NOT branch from later moving dev.

Before coding, read the current dev documentation:
- unifydesk-app/docs/contacts-audience/README.md
- ARCHITECTURE.md
- DATA-MODEL.md
- API-CONTRACT.md
- SECURITY.md
- PERFORMANCE.md
- DECISIONS.md
- CONTACT-MERGE.md
- INTEGRATIONS.md
- TASKS.md

The docs are authoritative. If current dev has later unrelated Ads work, do not merge or copy it into your branch.

## Mission

Implement the provider-neutral Contacts core:
- contact CRUD/search
- multi-identity model
- exact identity dedupe
- email normalization
- E.164 phone normalization
- tags
- custom field definitions/typed values
- static Lists/list membership
- explicit merge
- Contacts RBAC keys
- core entitlements
- core deletion state/purge hook contract

Do not implement Segments, import/export workers, consent/suppression, audience snapshots, Broadcast integration or frontend.

## Package ownership

Create production code under:
unifydesk-app/backend/src/main/java/com/messaging/contacts/core/**

Create tests under:
unifydesk-app/backend/src/test/java/com/messaging/contacts/core/**

Migration you own:
unifydesk-app/backend/src/main/resources/db/migration/V80__contacts_core.sql

No other migration number is permitted.

## Shared files allowed

You may modify:
- CompanyPermissionKeys.java
- the existing IAM permission-definition/bootstrap mechanism needed to make new Contacts permissions assignable
- SystemEntitlementCatalogInitializer.java
- pom.xml only if a production-grade phone parsing dependency is required and not already present
- directly related IAM/entitlement tests

Do not modify any unlisted existing hot file without reporting an integration request.

## Frozen domain rules

Contact:
- internal opaque ID + company_id
- ACTIVE | MERGED | DELETED
- email is NOT primary key
- multiple EMAIL/PHONE identities supported

Identity uniqueness:
- exact normalized active identity is unique within company/type/namespace
- never globally unique across companies
- different companies may store the same email/phone

Email:
- trim
- validate
- IDNA-normalize domain
- lowercase canonical matching representation
- preserve display/original value
- never Gmail-dot/plus rewrite

Phone:
- E.164 canonical
- explicit country context required when national format is ambiguous
- use a vetted parser; do not hand-roll international dialing logic

Creation/update:
- supplied identities matching one Contact may update that Contact only under explicit API operation semantics
- identities matching multiple Contacts -> 409 CONTACT_IDENTITY_CONFLICT
- never fuzzy-auto-merge

Primary identity:
- max one active primary EMAIL per Contact
- max one active primary PHONE per Contact
- database/service concurrency safe

## Custom fields

Implement typed sparse values exactly from DATA-MODEL.md:
TEXT, NUMBER, BOOLEAN, DATE, DATETIME, SINGLE_SELECT, MULTI_SELECT.

Do not store all values in one unindexed JSON blob.

Enforce:
- company field ownership
- definition type
- select options
- one typed value per Contact/field
- field-definition entitlement
- archived definition safety

## Lists/tags

Lists are static Contact collections.
Tag/list names are tenant-scoped normalized unique under the documented active/archival policy.
Membership additions are idempotent/concurrency-safe.
Maintain List member_count transactionally; provide reconciliation-capable repository/service method for later jobs/tests.

## Merge

Implement CONTACT-MERGE.md exactly:
- explicit only
- deterministic source/target locking order
- optimistic version guard
- union identities/tags/lists
- typed custom field conflict strategy
- source becomes MERGED -> target
- append merge audit
- no historical Broadcast/Inbox rewrite
- no network calls

B1 does not own preference/suppression tables. Define merge extension seam/integration request so Lead/B2 can reconcile those after integration without creating a circular compile dependency.

## API

Implement the B1-owned portions of API-CONTRACT.md:
- Contacts
- identities
- tags
- custom fields
- Lists
- merge

Use keyset/cursor pagination for Contacts and List members.
Do not expose huge Page/offset scans for high-cardinality collections.

Every endpoint:
- current authenticated user
- CompanyAuthorizationService
- company-scoped resource query
- safe foreign-ID behavior

Do not bind request entities directly to JPA.

## Permissions

Add:
contact.read
contact.create
contact.modify
contact.delete
contact.import
contact.export
contact.consent.read
contact.consent.modify

Apply TASKS.md default-role policy conservatively.
Do not accidentally give MEMBER delete/modify/import/export.

## Entitlements

Reuse:
CONTACT_LIMIT

Add catalog definitions if absent:
CONTACT_CUSTOM_FIELD_LIMIT
CONTACT_LIST_LIMIT
CONTACT_IMPORT_ROWS_LIMIT

B1 enforces CONTACT_LIMIT, custom-field and list limits where it owns creation.
B2 later consumes import rows limit.

No pricing-plan names.

## Security tests required

At minimum:
- foreign company Contact ID -> safe not found
- foreign identity/tag/list/custom field
- missing permission
- mass-assignment attempts
- duplicate concurrent identity create
- same identity in two companies allowed
- malicious/oversized search query bounded
- cursor tampering fails safely
- deleted/merged Contact mutation rejected
- no raw PII in logs introduced by your code

## Functional/concurrency tests

- multiple emails/phones
- primary identity uniqueness
- IDN email domain
- email case normalization
- E.164 cases
- ambiguous phone rejected without country context
- duplicate list/tag names
- concurrent list membership add
- list count correctness
- typed custom fields
- archived definitions
- merge success
- merge reversed concurrent order/no deadlock
- stale merge version
- merge identity conflict
- merge source tombstone

## Performance

- no N+1 identity/custom field fetch in Contact table
- keyset query
- batch repository methods for B2 future use
- all hot indexes from DATA-MODEL.md
- no loading entire tenant

## Required checks

cd unifydesk-app/backend
./mvnw spotless:check
./mvnw test
./mvnw clean test
./mvnw package -DskipTests

Also validate clean Flyway through V80 and representative V79 -> V80 if local PostgreSQL tooling exists.

Do not weaken tests because the environment is inconvenient. Report EXECUTION BLOCKED separately when infrastructure is unavailable.

## Forbidden scope

Do NOT modify:
- Broadcast execution/entity/service files
- Inbox entity/runtime files
- Managed Email/SMS/WhatsApp implementations
- Ads
- frontend
- V81/V82
- contacts-audience frozen docs

## Completion

Commit everything.
Open a PR to dev.
Do not merge.

Report:
- branch
- final SHA
- PR
- files changed
- migration
- API implemented
- normalization/dedupe behavior
- merge behavior
- permissions/entitlements
- tests/checks
- any blocked test
- every integration request/deviation
