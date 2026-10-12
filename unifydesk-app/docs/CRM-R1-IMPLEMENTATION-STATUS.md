# UnifyDesk CRM R1 — implementation status

Branch: `feature/crm-navigation-r1` (branched from `dev` commit `3762bcf`).
This file is a work-in-progress implementation ledger, not a release approval.

## Implemented in the CRM branch

- CRM primary navigation, contextual secondary sidebar, overview landing page, and compatibility with existing `/contacts` routes.
- `/crm/leads` route and first editable Leads screen.
- `V108__crm_leads_r1.sql`: company-scoped lead projection over the existing canonical `contacts` record, optimistic version column, bounded stage history, and PostgreSQL indexes.
- `LeadController` and `LeadService`: company-authorized list, summary, read, enroll, stage transitions and history; server-side stage selection, bounded paging and audit events.
- `crmLeadsApi.ts`: frontend API wrapper and typed lead contracts.
- No separate people table, no marketing consent mutations, no new cloud service or message broker.

## Security / behavioral notes

- Existing `contact.read` gates lead reads; `contact.modify` gates enrollment/stage transitions. Independent `crm.*` permissions are planned but not implemented yet.
- Server SQL always scopes lead and contact by `company_id`; clients receive Snowflake identifiers as strings.
- Stage changes require the previously read optimistic `version`; stale edits return HTTP 409.
- The current Create Lead picker displays up to 100 current workspace Contacts. Search-based selection, account association, and create-from-new-contact are later UX improvements.
- Lists and Segments remain on the old paths and render inside the CRM contextual navigation. Dedicated `/crm/contacts/*` aliases are not yet implemented.

## Still needed before claiming R1 complete

1. Implement CRM Account/Companies and follow-up Task domains; add Customer 360 data from Contacts/Inbox.
2. CRM overview live KPI cards and task widgets.
3. Add dedicated CRM permission keys, role defaults, feature entitlements, tenant purge/merge lifecycle, and seed/default grant tests.
4. Enhance Leads with owner assignment, source, follow-up scheduling, contact search, and direct create-from-contact workflow.
5. Validate migration V108 on fresh and existing PostgreSQL, Java compile/Spotless/unit/security tests, frontend lint/typecheck/test/build, and live tenant-boundary QA.
6. Correct and test inactive-contact/merge/purge behavior across CRM references.
7. Independently review concurrency, pagination, invalid inputs, access control and workspace-epoch stale-response handling.

**Important:** The changes are committed as code, but an executable build/test run has not yet been verified. Do not merge into `dev` or deploy as release-ready without QA.


## Increment 2 — CRM Companies and Tasks (source implemented)

- Flyway `V109__crm_accounts_tasks_r1.sql`: customer-account records and linked canonical Contacts using composite (company_id, id) foreign keys; tasks with assignees, priorities, UTC instants, statuses, optimistic versions and bounded query indexes.
- `CrmWorkService` / `CrmWorkController`: list/create Companies, link contact to customer Company, list company contacts, list/create Tasks, update Task status.
- `CrmCompaniesPage` and `CrmTasksPage` plus typed `crmWorkApi` and CRM secondary navigation routes.
- Company purge drains CRM tables before the existing Contacts purge to avoid leaking tenant data and to respect foreign keys.
- No extra service, infrastructure, marketing consent modification or `dev` change.

### Remaining quality gates / limitations

- **Not yet build verified**: Java compiler/formatter, frontend typecheck, Postgres migration and QA have not run; committed source is not deployment approval.
- Interim authorization uses existing `contact.read` / `contact.modify` permissions; replace with seeded granular `crm.account.*`, `crm.task.*`, `crm.lead.*` keys and ensure role defaults.
- No full account editor, contact-linking UI, task assignee selector, due-time picker, notifications scheduler or Customer 360 panels yet.
- User-deletion purge requires auditing because tasks currently reference assignee users with a restrictive FK; do not ship before evaluating user lifecycle.
- Validate concurrent company duplicate normalization (database uniqueness), task status version conflict, tenant-crosslink rejection, invalid dates, merged contacts and sensitive log handling in real PostgreSQL.
- Expand account contact-linking UX and task relation selection after endpoint QA.


## Increment 3 — Contact 360 (source implemented)

- Added a bounded Contact 360 backend projection at `GET /api/v1/companies/{companyId}/crm/contacts/{contactId}/overview`. It returns canonical lead stage/version, up to 20 linked customer companies and up to 20 related tasks with explicit more-results indicators.
- Added `DELETE /crm/accounts/{accountId}/contacts/{contactId}` for workspace-scoped company unlinking.
- Added `ContactCrmPanel` on the existing Contact Detail page, using a component key on contact ID to isolate contact-switch requests and the existing workspace epoch guard for company switching.
- Contact Detail now supports lead enrollment/stage updates, link/unlink customer companies, and create contact-bound follow-up tasks; the existing Contact Activity feed and channel consent panels remain the source of truth.
- No migration required for this increment; it uses the earlier V108 and V109 CRM schemas.

### Verification / follow-up

- Source changes committed to `feature/crm-navigation-r1`; Java/TypeScript compilation, Postgres migration tests and end-to-end QA still **not executed**.
- Customer-company selection currently loads a bounded first page of 50. Add searchable cursored picker before production usage with many accounts.
- Contact overview intentionally caps accounts and tasks; full record history should use dedicated cursor endpoints.
- Dedicated CRM permissions, cross-contact-merge tests, deletion of users referenced as task assignees, and all R1 QA gates remain outstanding.


## Increment 3 — Contact 360 panel

- Added bounded `GET /api/v1/companies/{companyId}/crm/contacts/{contactId}/overview`, sharing existing workspace authorization and checking the canonical contact is ACTIVE. Response includes lead stage/version, up to 20 linked customer companies, and up to 20 follow-up tasks with overflow indicators.
- Added `DELETE /api/v1/companies/{companyId}/crm/accounts/{accountId}/contacts/{contactId}`; POST linking existed. Mutations require Contact Modify permission and company-scoped IDs.
- Added `ContactCrmPanel` on existing `/contacts/:contactId` screens. Users can enroll a lead, change stage, associate/unlink a customer company and create a contact-linked follow-up task.
- Existing Contacts communication activity, channel preferences and suppressions remain in their original panels. No cross-service Inbox/Broadcast message copying.
- Contact 360 widget remounts on workspace/company/Contact change; async updates verify workspace epoch and component lifetime.
- Future: searchable customer-company selector instead of first-page selection, task status editing from contact panel, end-to-end Contact timeline aggregation, independent tests for tenant-switch races, and permission dedicated CRM grants.

**QA is not yet complete:** No verified JVM compiler/Spotless, frontend typecheck/build, PostgreSQL Flyway or runtime tests for this CRM branch.


## Increment 3 — Contact 360 integration

- Contact Detail renders the consolidated `ContactCrmPanel`, keyed by company/contact, without replacing existing Contact, consent, suppression, or activity features.
- New company-authorized, bounded `GET /api/v1/companies/{companyId}/crm/contacts/{contactId}/overview` projects CRM lead stage, linked customer-account records and follow-up tasks. Company ID and active Contact are checked before reads; lists return up to 20 entities each with overflow flags.
- Contact 360 allows turning an existing Contact into a Lead, changing stage with a version, linking/unlinking customer Companies, creating a follow-up for the Contact and changing task status with a version.
- `DELETE /crm/accounts/{accountId}/contacts/{contactId}` unlinks a customer business from a Contact without touching the Contact or its marketing consent.
- Existing Contacts activity remains the owner of Inbox/Broadcast activity rather than copying email or broadcast events into new CRM storage. The existing Contact Detail timeline continues to show these events separately.

### Release blockers still applicable

- No executable frontend or backend build, migration, E2E, or permission integration QA has been run.
- Dedicated CRM permissions, user-deletion lifecycle, company/contact associations on full CRM pages, deeper timeline integration, refresh after contact mutation, rate/entitlement checks, and user-facing pagination beyond the latest 20 Contact 360 entries remain future work.
- Must validate optimistic concurrency, workspace switch during in-flight mutations, account association on merged/deleted Contacts, PostgreSQL FK behavior, and cross-tenant access before releasing.

**Integration note (Oct 10):** Parallel changes on this same branch had already added `ContactCrmPanel`. The duplicate `CrmContact360Panel` was removed and the existing consolidated panel retained. Review the branch history for all contributors before QA.

## Increment 4 — static security hardening (2026-10-10)

- Removed duplicate Contact 360 record, service methods and overlapping controller endpoints caused by parallel branch edits (Java source blocker).
- Added granular `crm.lead.read/modify`, `crm.account.read/modify` and `crm.task.read/modify` permission keys to the system catalog/role defaults, controllers, client routes and CRM pages. Owner retains catalog-based access, Manager gets read/write, Member gets read-only. Existing Contact consent permissions remain separate.
- Contact 360 currently requires **all three CRM read permissions plus contact.read** to avoid leaking unrelated CRM resources in a combined response; users lacking any required read grant do not see the combined panel.
- Read-only source inspection confirmed CRM tasks hold assignee IDs referencing soft-purged `users` rows; user purge anonymizes the user record rather than physically deleting it. A future business rule is needed to reassign/cancel tasks assigned to purged users.
- Source review confirmed workspace purge drains CRM tasks, links, accounts, lead stage events and leads before Contacts are removed. This still requires actual migrated PostgreSQL FK testing.
- **Build/test state: NOT VERIFIED.** No access to a checked-out private repo build environment or running PostgreSQL from this connector session. Controller duplication was resolved by source inspection, not by a successful Java compilation. Fresh/upgrade migration tests, Java/Node compilation, IAM integration checks and multi-tenant QA remain release blockers.

## Independent merge-readiness review — 2026-10-10

- Repaired triple-defined `Contact360` DTO and repeated API functions in `crmWorkApi.ts`.
- Static assertions passed 31/31 for duplicate declarations, routes, granular permission names, schema composite references, and optimistic update predicates. These are **source assertions only**, not compiler, database, or integration tests.
- Tightened authorization: CRM Leads and contact-bearing Tasks require `contact.read`; associating/disassociating customer Accounts with people requires `contact.read`; task association with a customer Account requires `crm.account.read`.
- **Merge blocker:** CRM has no `ContactMergeExtension` to reconcile source lead, linked Companies and Tasks during Contact merge. Without one, sales relationships can become stranded on the merged source Contact.
- **Merge blocker:** Cannot run Java 25 Maven project, repository frontend dependency-validated build, or PostgreSQL migrations from the available isolated environment. GitHub Actions reported failures, and logs were unavailable.
- **Merge blocker:** CRM V108/V109 upgrade migrations and fresh-database install require execution; do not infer success from SQL inspection.
- **Merge blocker:** contact lifecycle, tenant epoch race, and user/task assignee retirement require executable regression coverage.

PR #377 must remain draft; no merge to dev until the above issues are addressed and validated.
