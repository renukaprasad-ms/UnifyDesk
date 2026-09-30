# Contacts & Audience Round-1 Tasks and Ownership

Status: FROZEN
Architecture Lead: Product Manager / Lead Architect
Frozen worker base SHA: f31f4f950cc257d4d1edd947a5038a3447599f85
Architecture PR: #198
Architecture parent before docs: b81f68c9d162c5778042f338afbdfe561d164506

All implementation workers branch from EXACTLY:

f31f4f950cc257d4d1edd947a5038a3447599f85

Do not branch from later moving dev.

## Branches

B1:
feature/contacts-core-r1

B2:
feature/contacts-audience-processing-r1

F1:
feature/contacts-audience-ui-r1

QA/SEC after Lead integration:
review/contacts-audience-security-qa-r1

## Migration allocation

Current pre-module Flyway head at architecture freeze: V79.

Reserved:
- B1: V80__contacts_core.sql
- B2: V81__contacts_audience_processing.sql
- Lead integration only: V82__contacts_audience_integration.sql

No worker may invent a different migration number.

## Shared contract rule

B1/B2/F1 work in parallel.

B2 must compile/test without importing B1 Java classes. Where B2 needs core Contact data, it consumes the frozen database contract through B2-owned SQL/JdbcTemplate projections/interfaces and mocks on its branch.

F1 works from API-CONTRACT.md and typed local interfaces/mocks. It must not wait for backend implementation.

## B1 ownership — Contacts/Core Control Plane

Mission:
- Contact CRUD/search
- multiple identities and normalization
- tags
- custom fields
- static Lists
- explicit merge
- permissions/RBAC keys
- Contact limit/list/custom-field entitlements
- core domain tests

New backend package ownership:
unifydesk-app/backend/src/main/java/com/messaging/contacts/core/**

Tests:
unifydesk-app/backend/src/test/java/com/messaging/contacts/core/**

Migration:
V80__contacts_core.sql

B1 may modify only these shared files unless Lead expands ownership:
- CompanyPermissionKeys.java
- frontend-independent permission catalog/bootstrap files required to register new backend permission keys
- SystemEntitlementCatalogInitializer.java
- pom.xml only if a phone normalization library is truly required and no suitable dependency already exists
- Entity/company purge registration only if existing architecture requires core-owned hook and no shared-file conflict exists; otherwise request Lead integration

B1 must not modify:
- Broadcast execution
- Inbox persistence/runtime
- Ads
- Managed Email/SMS/WhatsApp implementation
- frontend
- B2 packages/migration
- frozen Contacts docs

Entitlements:
reuse CONTACT_LIMIT.
Add only if absent:
- CONTACT_CUSTOM_FIELD_LIMIT
- CONTACT_LIST_LIMIT
- CONTACT_IMPORT_ROWS_LIMIT
Do not add plan names.

Permissions:
- contact.read
- contact.create
- contact.modify
- contact.delete
- contact.import
- contact.export
- contact.consent.read
- contact.consent.modify

Default-role policy for Round 1:
- OWNER: existing owner/full semantics
- MANAGER: read/create/modify/import/export/consent read+modify; delete allowed only if existing manager semantics already permit destructive customer-data actions, otherwise Lead integration keeps delete owner/custom-role only
- MEMBER: contact.read and contact.consent.read only
B1 must preserve least privilege and document any conflict with current IAM bootstrap.

## B2 ownership — Audience/Data Processing

Mission:
- Segments/filter compiler
- preferences/consent event history
- suppressions
- eligibility service
- CSV import/export
- bulk operations
- Contacts-domain activity events/read aggregation adapters
- audience source resolver and snapshot tables/workers
- async job/storage integration
- performance-sensitive processing

New package ownership:
unifydesk-app/backend/src/main/java/com/messaging/contacts/audience/**
unifydesk-app/backend/src/main/java/com/messaging/contacts/processing/**
unifydesk-app/backend/src/main/java/com/messaging/contacts/consent/**
unifydesk-app/backend/src/main/java/com/messaging/contacts/integration/** only for provider-neutral B2-owned read/event adapters

Tests under matching contacts packages.

Migration:
V81__contacts_audience_processing.sql

Shared files B2 may modify:
- AsyncJobType.java
- minimal async-worker registration/configuration files
- storage enums/config only if necessary to reuse existing CONTACT_DATA/EXPORT; do not duplicate quota
- provider event integration adapters only when they can be isolated without modifying provider-owned runtime files. Otherwise create Lead integration request.

B2 must not modify:
- B1 core entities/repositories/services
- Broadcast execution/shared entities
- Inbox entities
- Ads
- frontend
- V80/V82
- frozen docs

B2 must use exact B1 SQL/table contract from DATA-MODEL.md through its own runtime projection.

## F1 ownership

Mission:
- Contacts home
- Contact detail
- identities/tags/lists/custom fields
- consent/suppression
- Lists
- Segments visual builder
- CSV import wizard
- import/export progress/results
- bulk UX
- responsive navigation/routes
- API client/types/tests

Frontend ownership:
unifydesk-app/frontend/src/features/contacts/**
unifydesk-app/frontend/src/pages/contacts/**
new contact-specific components under components/contacts/**

F1 may modify:
- AppRoutes.tsx
- AppSidebar.tsx
- MainLayout.tsx only if Contacts context mode truly requires it
- frontend IAM permission constants
- shared UI primitives only for backward-compatible tiny additions

F1 must not modify backend or Ads pages/components.

## Lead integration ownership

Lead owns:
- V82
- cross-worker FK/constraints
- Broadcast List/Segment source integration
- audience snapshot references on BroadcastMessage/Run/Recipient
- eligibility recheck integration in Broadcast delivery
- tiny Inbox identity lookup glue if necessary
- provider event hook glue that requires Managed Email/SMS/WhatsApp-owned files
- company deletion/purge shared registration when workers cannot safely own it
- resolving shared-file conflicts

## Round-1 acceptance

B1, B2 and F1 each:
- compile/test independently from frozen base
- honor exact contracts
- commit
- open PR
- do not merge
- report branch, final SHA, changed files, tests, deviations/integration requests

Lead then integrates and freezes an exact SHA for QA.

QA does not start from f31f4f950cc257d4d1edd947a5038a3447599f85; QA starts from the later exact integrated SHA published by Lead.
