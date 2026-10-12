# CRM R1 QA and UX follow-up

Branch: `fix/crm-r1-qa-ux` based on merged CRM R1 (`9c978ec5ceb721d7bc1ef845227b9b96ce0cff34`).

## Implemented in this increment

- Search-based creation of Leads using canonical workspace-scoped Contacts search, with 300 ms debounce, active-only results, capped page, and stale response protection by workspace epoch plus effect cleanup. The previous first-100 Contacts dropdown was inadequate for large workspaces.
- Customer Company name duplicate insert now yields an explicit HTTP 409 CRM domain error rather than an unhandled database exception.

## Verified from repository source

- Existing Contacts API supports `q`, `status`, and `size`; CRM frontend does not need a new contact-search endpoint.
- Existing CRM company normalization uniqueness is enforced per workspace by a PostgreSQL unique index.
- R1 introduced V108/V109 and isolated permission keys; these must be verified on running PostgreSQL before production deploy.

## Release gates

- Full backend Java 25/Spotless tests, PostgreSQL fresh/upgrade Flyway tests and frontend Node 24 lint/build/tests cannot be executed in this tool environment: repository content is available through GitHub connector but direct git clone is unavailable; current Java runtime is 21, and Postgres/Docker aren't installed.
- Verify Contact merge transfers lead history, account memberships and task references atomically.
- Verify CRM permissions, workspace-switch stale response behavior, Contacts consent regression, and company/user purge under actual integration tests.
- This is a follow-up QA increment, not proof of full end-to-end correctness. Do not represent it as fully tested based only on source inspection.

## R1 UX completion update (October 10, 2026)

- **Company profiles:** Dedicated `/crm/companies/:accountId` route, workspace-scoped profile read/edit (name, website, notes, optional owner), optimistic version checks, active-member owner validation, bounded linked-Contact summaries and association management; IDs remain strings in the frontend.
- **Task management:** Create/update title, details, due time, priority, optional customer Company association and active-workspace assignee; server-side status and assignee filtering; permission-gated member chooser and customer-company links.
- **Overview/Contact 360:** Live lead totals/qualified/converted counts, recent follow-ups, most recent lead-stage history in the existing Contact panel.
- **Consistency:** Stale response protection for lead/task listings, Contact 360 and company profile reads; preserving existing owner when `user.read` is unavailable; explicit JDBC `Timestamp.from(Instant)` binding and typed nullable PostgreSQL filter placeholders.
- **Database tests added (committed, not executed):** target/source Contact merge behavior, lead event preservation, account-link deduplication, task relinking, nullable query filters, and account optimistic version/tenant isolation.
- Static source assertions: **26/26 passed** across endpoint authorization, UI/API linkage, version predicates and query construction. This is not a substitute for compiler, live database or runtime tests.

### Remaining release gates and planned CRM scope

- Executable Java 25 Maven + Spotless/test/package, Node 24 frontend build/test/lint/format, fresh/upgrade PostgreSQL migration testing, and end-to-end multitenancy/consent regression tests remain unverified in this environment.
- Task notification reminders and reassignment after employee removal are **not implemented**; task due dates and assignments are persisted but no scheduler sends CRM reminders.
- Contact 360 reuses the existing Contacts activity timeline rather than duplicating Inbox or Broadcast messages; a unified multi-channel CRM activity feed, Deals/pipelines and reporting are future releases.
- Do not deploy to production until the above runtime gates pass.
