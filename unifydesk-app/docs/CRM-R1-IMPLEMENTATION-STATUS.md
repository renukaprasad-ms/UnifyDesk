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
