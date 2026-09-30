# F1 Prompt — Contacts & Audience Frontend Round 1

You are F1 for UnifyDesk Contacts & Audience Management.

Repository:
renukaprasad-ms/messaging

Application:
unifydesk-app/

Your EXACT frozen worker base SHA:
f31f4f950cc257d4d1edd947a5038a3447599f85

Your branch:
feature/contacts-audience-ui-r1

Create your branch from EXACTLY that SHA.
Do NOT branch from later moving dev.
Do NOT wait for B1/B2.

Read current dev docs:
- contacts-audience/README.md
- ARCHITECTURE.md
- API-CONTRACT.md
- SEGMENTATION.md
- CONSENT-SUPPRESSION.md
- IMPORT-EXPORT.md
- SECURITY.md
- TASKS.md

Also inspect existing UnifyDesk:
- AppSidebar
- MainLayout
- AppRoutes
- Broadcast workspace
- Company pages
- Notifications job progress behavior
- existing form/table/modal responsive patterns

## Mission

Implement the complete Round-1 Contacts/Audience UX against the frozen API contract.

Own:
- Contacts home/table
- Contact detail
- create/edit Contact
- identities
- tags
- custom fields rendering/edit
- Lists
- List members
- Segments filter builder
- Segment preview/count
- consent/preferences
- suppressions display/manual controls
- CSV import wizard
- import progress/result/error download
- export UX
- bulk selection/actions
- responsive navigation/routes

## Ownership

Create:
unifydesk-app/frontend/src/features/contacts/**
unifydesk-app/frontend/src/pages/contacts/**
unifydesk-app/frontend/src/components/contacts/**

May modify:
- AppRoutes.tsx
- AppSidebar.tsx
- frontend IAM permissions constants
- MainLayout.tsx only if needed for a Contacts contextual sidebar
- tiny shared UI primitives only if backward-compatible

Do NOT modify:
- backend
- Ads pages/components
- Broadcast implementation
- unrelated Company/Inbox logic

## Navigation

Add first-class “Contacts” entry in main workspace navigation.

Recommended routes:
- /contacts
- /contacts/:contactId
- /contacts/lists
- /contacts/lists/:listId
- /contacts/segments
- /contacts/segments/:segmentId
- /contacts/imports
- /contacts/imports/:importId

Use CONTACT_READ permission for route visibility.

Do not overload Ad Center “Audiences”; Contacts/Audiences is the first-party customer database, while Ad Center audiences are provider advertising objects.

## Contacts table

Requirements:
- search
- keyset/load-more or cursor-aware pagination
- sortable supported fields only
- filters
- responsive columns
- bulk selection
- empty/loading/error states
- no rendering huge data sets in DOM
- URL-state where useful

Rows show:
name/display name
primary email
primary phone
tags summary
organization
consent/channel summary
updated/last activity where available

## Contact detail

Sections:
- profile
- identities
- tags
- Lists
- custom fields
- channel preferences
- suppressions
- activity

Do not display provider secrets/SES/MSG91/Meta internals.

Merged Contact response redirects or shows target safely according to API contract.

## Lists

- create/edit/archive
- member count
- members table
- add/remove small sets
- bulk membership actions delegated to bulk API
- never fetch all members to client for large List

## Segments

Build visual AST editor for documented V1 predicates/operators.

UI must:
- prevent invalid type/operator combos
- support nested AND/OR up to server limits
- show validation errors from backend
- preview sample
- show count state EXACT/CACHED/PENDING
- avoid client-generated SQL
- preserve server normalized filter when saving

## Import wizard

Steps:
1. upload CSV
2. preview headers/sample
3. map fields
4. choose duplicate strategy
5. optional target List
6. explicit consent mapping/evidence only when user provides it
7. review
8. commit
9. progress/results

UX requirements:
- never imply importing addresses equals consent
- show invalid-row/error-report count
- async progress via existing AsyncJob/realtime if available; polling fallback bounded
- retry/reopen existing idempotent import where API returns it
- downloadable error report only through backend-authorized link

Do not add XLSX in Round 1.

## Consent/suppression UX

Clearly distinguish:
- Email marketing preference
- SMS marketing preference
- WhatsApp marketing preference
- Suppression reason

Do not merge into one “Subscribed” toggle.

Sensitive changes require explicit action and display source/timestamp when available.

## Bulk actions

Support UI for:
- add/remove tag
- add/remove List
- update custom field
- delete
- export

For select-all-across-filter, send selection descriptor to backend. Never enumerate a million Contact IDs in browser.

## API/types

Create contactsApi and strict types matching API-CONTRACT.md.

Use existing ApiResponse/apiClient conventions.

Because backend is being built in parallel:
- mock network layer in tests
- no temporary backend dependency
- do not silently change contract to match assumptions

## Permissions

Add frontend constants:
CONTACT_READ/CREATE/MODIFY/DELETE/IMPORT/EXPORT/CONSENT_READ/CONSENT_MODIFY

Buttons/actions must be hidden/disabled correctly, but frontend remains UX only; backend authorizes.

## Responsive/design

Match UnifyDesk blue/white existing design.
Must work desktop/tablet/mobile.
Reuse current spacing, buttons, forms, tables, drawers/modals and loading patterns.
Do not introduce a disconnected design system.

## Accessibility

- labels for form controls
- keyboard navigation in filter builder/import wizard
- focus management for dialogs
- status/progress text not color-only
- table bulk selection accessible

## Required tests

At minimum:
- Contacts route permission
- table cursor progression
- search/filter request
- create/edit form validation
- identity UI
- list membership UI
- segment AST builder serialization
- invalid operator prevented
- preview count states
- import wizard mapping
- consent warning/copy
- async import progress
- bulk selection descriptor
- mobile navigation/basic rendering
- foreign/not-found safe UI
- merged Contact handling

Run:
cd unifydesk-app/frontend
npm ci
npm run format:check
npm run lint
npm test
npm run build

## Forbidden

No backend edits.
No Ads feature edits.
No provider-specific audience sync.
No XLSX.
No full CRM/deals.
No huge in-browser filtering.

## Completion

Commit.
Open PR to dev.
Do not merge.

Report:
- branch
- final SHA
- PR
- files changed
- routes/navigation
- screens completed
- API contract coverage
- tests/build/lint/format
- responsive/accessibility notes
- integration requests/deviations
