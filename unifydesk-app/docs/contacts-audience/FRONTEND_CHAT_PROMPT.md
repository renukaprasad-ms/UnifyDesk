# Prompt — F1 Frontend Contacts & Audience UX

You are F1 for UnifyDesk Contacts & Audience Management Round 1.

Repository:
`renukaprasad-ms/messaging`

Application:
`unifydesk-app/`

Docs:
`unifydesk-app/docs/contacts-audience/`

Your branch:
`feature/contacts-audience-ui-r1`

Your EXACT frozen base SHA:
`__ROUND1_BASE_SHA__`

Create your branch from exactly that SHA.

Do NOT branch from moving dev.
Do NOT merge.
Do NOT modify backend/Flyway.
Do NOT work on Ad Center.

The prompt may be stored on a later docs metadata commit. Your implementation branch still starts from the exact frozen SHA above.

## Read first

Read completely:

- README.md
- ARCHITECTURE.md
- DECISIONS.md
- API-CONTRACT.md
- IMPORT-EXPORT.md
- SEGMENTATION.md
- CONSENT-SUPPRESSION.md
- CONTACT-MERGE.md
- SECURITY.md
- PERFORMANCE.md
- TASKS.md
- TEAM_WORKFLOW.md

Inspect current:

- frontend/src/routes/AppRoutes.tsx
- frontend/src/components/navigation/AppSidebar.tsx
- frontend/src/features/iam/**
- existing asyncJob API/progress UX
- Broadcast table/filter patterns
- Notifications loading/error conventions
- responsive layouts
- shared form/dialog/table components and CSS conventions

## Mission

Build the complete Round-1 Contacts & Audience customer UX against the frozen APIs, independently of B1/B2 implementation.

Use mocked API fixtures/tests where backend is not on your branch.

## Exact ownership

New:

- `frontend/src/features/contacts/**`
- `frontend/src/components/contacts/**`
- `frontend/src/pages/contacts/**`
- colocated tests/fixtures

Existing files you may modify:

- `frontend/src/routes/AppRoutes.tsx`
- `frontend/src/components/navigation/AppSidebar.tsx`
- `frontend/src/features/iam/permissions.ts`
- minimal existing shared styling/types only when necessary and backward compatible

Do not refactor unrelated Inbox/Broadcast/Ads pages.

## Navigation

Add a first-class **Contacts** workspace entry, permission-gated by `contacts.read`.

Recommended routes:

- `/contacts`
- `/contacts/:contactId`
- `/contacts/lists`
- `/contacts/segments`
- `/contacts/imports`
- optional `/contacts/settings` for tags/custom fields if UX remains coherent

Do not put Contacts under Ad Center.

## Permissions

Mirror exact frozen strings:

- contacts.read
- contacts.create
- contacts.modify
- contacts.delete
- contacts.import
- contacts.export
- contacts.consent.manage
- audiences.read
- audiences.create
- audiences.modify
- audiences.delete

Controls must reflect permission, but backend 403 remains authoritative.

Do not show destructive/export/consent actions merely because the user can read Contacts.

## Contacts table

Required:

- cursor-based pagination/infinite-next behavior;
- search;
- whitelisted filters;
- status;
- owner;
- country;
- tag;
- list;
- stable loading/empty/error states;
- selectable rows;
- bulk-action toolbar;
- primary email/phone summary;
- tags summary;
- last activity;
- responsive layout.

Do not assume page numbers or total count when API does not provide one.

A search change resets cursor.

Ignore stale requests when filters/search change quickly.

## Create/edit contact

Support:

- standard fields;
- multiple emails;
- multiple phones;
- labels;
- primary identity;
- owner;
- notes;
- typed custom fields;
- validation errors;
- version conflict.

Do not imply that adding an email/phone opts the person into marketing.

Identity conflict UX:

- explain exact duplicate conflict safely;
- link/open conflicting contact only when API returns an authorized contact ID;
- offer manual merge only with required permissions;
- never expose a foreign tenant.

## Contact detail

Show sections:

- profile;
- emails/phones;
- custom fields;
- tags;
- list memberships;
- channel marketing preferences;
- active suppressions;
- recent activity refs;
- metadata/source;
- archive/delete/merge actions when allowed.

Consent/suppression status must be explicit and not color-only.

Do not expose sensitive evidence JSON by default.

## Merge UX

Explicit confirmation:

- target survives;
- source becomes merged;
- tags/lists/identities union;
- target values win and missing target values may be filled;
- consent is not newly granted.

Require source/target preview and version.

Handle conflict/concurrent update with safe refetch.

No fuzzy "auto merge" UI.

## Lists

Page:

- list name/description;
- member count;
- create/edit/archive;
- member browse;
- add/remove small membership;
- launch bulk membership action for large selection.

Text must state list membership does not automatically subscribe a contact.

## Segments

Provide a rule builder based on the frozen AST.

Requirements:

- AND/OR groups;
- supported field/operator combinations only;
- custom fields from definitions;
- tags/lists;
- consent/suppression predicates;
- depth/clause UX guard before server error;
- preview sample/count;
- stale count indicator;
- save/version conflict;
- member browse.

Do not send raw SQL/expression text.

Do not build provider-specific Meta/Google segment settings.

## Consent/preferences

For EMAIL/SMS/WHATSAPP MARKETING:

- show UNKNOWN / OPTED_IN / OPTED_OUT;
- show safe source/time summary where returned;
- authorized explicit change;
- confirmation for opt-in;
- explain list membership is unrelated;
- explain suppression can still make an opted-in identity ineligible.

Do not present UnifyDesk as legal advice or claim that one toggle guarantees regulatory compliance.

## Suppressions

Show active suppression reason safely.

Manual suppress/release only when API + permission allow.

Do not allow release UI for a server-marked non-releasable/provider policy state if contract returns one.

Hard bounce/complaint should not look like a normal preference toggle.

## Import wizard

Flow:

1. upload CSV;
2. preview;
3. map columns;
4. choose duplicate mode;
5. choose default phone country if needed;
6. optional target list;
7. explicitly map consent columns if applicable;
8. confirm;
9. async progress;
10. summary/error report.

CSV only.

Do not advertise XLS/XLSX.

Safety copy:

- importing an address does not subscribe it;
- identity conflicts are skipped/error, not silently merged.

Handle:
- malformed file;
- too many columns;
- invalid phone region;
- contact limit;
- cancellation;
- completed-with-errors;
- expired error artifact.

Use existing async job realtime/poll/focus recovery patterns.

## Export

Permission: contacts.export.

Selection:
- current filtered set where supported;
- list;
- segment;
- selected contacts.

Choose fields.

Async progress/status.

Download/access button calls authenticated access endpoint at click time.

Do not persist signed URLs in localStorage.

Warn that CSV values are spreadsheet-sanitized when needed.

## Bulk actions

Support frozen operations appropriate to permissions:

- tag;
- list;
- field update;
- archive;
- delete;
- export.

Large selections create async job.

Confirmation must show selection description and operation, not pretend the browser holds every ID.

## Jobs/progress

Reuse existing async-job API/types where possible.

Correctness cannot depend only on WebSocket.

On reconnect/focus/status page:
- refetch durable job/import/export state.

Do not show completed before server terminal state.

## Error model

Map frozen safe error codes to actionable UX.

Never display:
- stack traces;
- SQL;
- raw object keys;
- signed URL tokens;
- hidden consent evidence;
- raw backend error payloads.

Preserve useful distinctions:
- identity conflict;
- limit reached;
- segment too complex;
- invalid mapping;
- version conflict;
- access denied;
- artifact expired.

## Accessibility

- keyboard-operable rule builder;
- labelled fields;
- focus management in dialogs;
- table selection accessible;
- no color-only consent/suppression states;
- destructive confirmations;
- screen-reader progress/status text.

## Responsive behavior

Must work on:

- desktop;
- tablet;
- narrow mobile.

On mobile:
- table may become cards/scroll-safe rows;
- filter drawer;
- detail sections stack;
- rule builder remains usable;
- bulk toolbar does not cover content.

## Independence

Build API clients/types from API-CONTRACT.md.

Use mocks/fixtures.

Do not wait for B1/B2.

Do not invent alternate field names because mocks are easier.

If a frozen API is insufficient, report exact L1 contract issue.

## Required tests

At minimum:

- permission-gated nav/routes;
- contacts list cursor behavior;
- search stale-request handling;
- create/edit validation;
- multiple identities/primary;
- identity conflict;
- version conflict;
- contact detail;
- merge confirmation/conflict;
- tags/lists;
- segment rule builder validation/preview;
- consent copy/state;
- suppression state;
- CSV import mapping/progress/error;
- export progress/access;
- bulk async job;
- permission-disabled destructive actions;
- loading/empty/error;
- basic keyboard/accessibility;
- mobile layout assertions where practical.

Run:

```bash
cd unifydesk-app/frontend
npm ci
npm run format:check
npm run lint
npm test
npm run build
```

## Forbidden

Do not modify:

- backend
- Flyway
- Ad Center implementation
- Broadcast runtime
- WhatsApp runtime
- architecture docs
- secrets/deployment config

## PR requirements

Open a PR to dev.

Include:

- frozen base SHA;
- routes/pages;
- permissions;
- API mock contract;
- tests/build results;
- accessibility/responsive notes;
- any API contract gap.

## Final report

Report:

- branch
- final SHA
- PR
- files changed
- routes/UX completed
- tests/build
- contract deviation/integration request
- blocker

Do not merge.
