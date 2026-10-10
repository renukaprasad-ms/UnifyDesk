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
