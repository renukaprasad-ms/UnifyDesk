# F1 Prompt — Contacts & Audience Frontend Round-2 Remediation

You are F1 for targeted Contacts/Audience Round-2 remediation.

Repository:
`renukaprasad-ms/messaging`

Application:
`unifydesk-app/`

Your EXACT frozen Round-2 base SHA is:

`f6a04c4596240d0c43bde6ef83c66619abbd9a02`

Create:

`fix/contacts-audience-ui-r2`

from EXACTLY that SHA.

Do NOT branch from moving `dev`.
Do NOT modify backend code.
Do NOT redesign Contacts UI.

Read current `dev`:

- `unifydesk-app/docs/contacts-audience/SECURITY_QA_FEEDBACK.md`
- `ROUND2_TASKS.md`
- `API-CONTRACT.md`

Your ONLY findings are:

- `CA-R1-011`
- `CA-R1-013`

## CA-R1-011 — default export request

Current default Contacts export sends:

- displayName
- primaryEmail
- primaryPhone
- organization

The backend frozen allow-list does not currently support primaryEmail/primaryPhone.

This remediation round does NOT widen the backend export contract.

Align the frontend request to the supported backend allow-list.

The normal Contacts export control must submit only supported fields and must successfully create an export against the frozen API.

Keep public ID types as strings.

Do not introduce client-side export generation.

Do not fetch all Contacts to manufacture email/phone columns.

If product wants identity export later, that is a separate backend/API enhancement with efficient server-side querying and privacy review.

## CA-R1-013 — unsupported DELETED filter

Remove the unsupported DELETED Contact status option from the normal Contacts search UI.

Do not send a backend-invalid filter merely because the enum exists internally.

Preserve supported ACTIVE/default and MERGED behavior according to the frozen API.

Ensure URL/query state containing an unsupported status fails/normalizes safely rather than producing a confusing repeated backend error.

## Tests

Add focused frontend/API tests proving:

- clicking normal Export Contacts generates the exact supported field list;
- export no longer requests primaryEmail/primaryPhone;
- DELETED is not offered in the status selector;
- unsupported stale URL status is safely handled;
- existing search, cursor paging, bulk selection and responsive behavior are not regressed.

## Performance / code quality

No new full-audience browser state.
No client-side CSV generation.
No unbounded ID collection.
No new polling loops.

Keep the change small, strongly typed, accessible and production-grade.

Run:

`npm ci`
`npm run format:check`
`npm run lint`
`npm test`
`npm run build`

If unavailable, report `EXECUTION BLOCKED`.

## Completion

Commit.
Open PR to `dev`.
Do not merge.

Report:
- branch
- final SHA
- PR
- files changed
- export request before/after
- status filter behavior
- tests/commands
- blockers

Start now.
