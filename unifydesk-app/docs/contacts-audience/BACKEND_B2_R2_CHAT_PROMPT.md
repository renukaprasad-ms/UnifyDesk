# B2 Prompt — Contacts Audience/Data Processing Round-2 Remediation

You are B2 for targeted Contacts/Audience Round-2 remediation.

Repository:
`renukaprasad-ms/messaging`

Application:
`unifydesk-app/`

Your EXACT frozen Round-2 base SHA is:

`f6a04c4596240d0c43bde6ef83c66619abbd9a02`

Create:

`fix/contacts-audience-processing-r2`

from EXACTLY that SHA.

Do NOT branch from moving `dev`.
Do NOT create V83.
Do NOT modify Lead-owned integration adapters, Broadcast runtime, F1, Ads or provider runtime code unless explicitly listed below.

Read current `dev`:

- `unifydesk-app/docs/contacts-audience/SECURITY_QA_FEEDBACK.md`
- `ROUND2_TASKS.md`
- `SECURITY.md`
- `CONSENT-SUPPRESSION.md`
- `IMPORT-EXPORT.md`
- `API-CONTRACT.md`

Your findings are:

- `CA-R1-001`
- `CA-R1-002`
- `CA-R1-003`
- `CA-R1-006`
- `CA-R1-009`

You also own the runtime/service-validation portion of:

- `CA-R1-008`

Lead owns the matching database constraint in V83.

## CA-R1-001 — Bulk DELETE permission

Current bulk creation authorizes all operations using `contact.modify`.

Fix authorization by operation.

Required behavior:

- `DELETE` requires `contact.delete`.
- non-destructive bulk mutation continues to require `contact.modify`.
- a Manager/default role with MODIFY and without DELETE cannot queue bulk deletion.
- custom roles follow the same backend authorization.
- frontend permission hiding is not relied upon.

Do not call `ContactService.delete` as a substitute for endpoint authorization; the request must be rejected before the destructive job is queued.

## CA-R1-002 — Reauthorize async jobs at execution

User-initiated async jobs must revalidate current company membership/permission when a worker claims or reclaims them.

Build one explicit authorization policy/service for Contacts async jobs rather than scattering permission checks through every worker.

At minimum cover:

- CONTACT_IMPORT -> current `contact.import`
- CONTACT_EXPORT -> current `contact.export`
- CONTACT_BULK -> inspect stored operation:
  - DELETE -> `contact.delete`
  - other mutations -> `contact.modify`
- CONTACT_SEGMENT_COUNT or other user-triggered jobs -> the same current permission required by their initiating API

Important lifecycle exception:

`CONTACT_DELETE_PURGE` is a system follow-up to an already-authorized deletion and must finish even if the original user's permission is later revoked. Do not strand deleted Contact PII because an actor lost access after deletion was accepted.

Likewise do not incorrectly apply user authorization to a system-owned internal operation that has no initiating user capability.

The reauthorization decision must be deterministic and testable.

If authorization is revoked:
- fail the user job safely before mutation/export publication;
- do not leak whether a foreign resource exists;
- preserve an auditable stable job failure reason.

Recovery/reclaim must execute the same authorization check.

## CA-R1-003 — Import consent authority

An import without consent mappings requires only `contact.import`.

An import with one or more consent mappings requires BOTH:

- `contact.import`
- `contact.consent.modify`

Enforce this:
1. at commit/queue time;
2. again when the async worker executes/reclaims the job.

Do not infer consent authority merely from IMPORT permission.

Use the stored import mapping to determine whether the queued operation includes consent changes.

Add tests for:
- import-only role + no consent mapping -> allowed;
- import-only role + consent mapping -> denied;
- import + consent.modify -> allowed;
- consent permission revoked after queue -> worker fails before preference mutation.

## CA-R1-006 — Bulk idempotency request binding

The idempotency key must identify one normalized logical bulk request.

Current behavior returns an existing operation for the same key even when selection or operation differs.

Fix without adding unnecessary schema if the existing canonical stored JSON is sufficient.

Required behavior:

- same company + same key + same canonical selection + same canonical operation -> return original operation;
- same key + different selection -> conflict;
- same key + different operation -> conflict;
- concurrent same-key/same-request converges;
- concurrent same-key/different-request does not silently pick one as success.

Use a stable error code such as `CONTACT_BULK_IDEMPOTENCY_CONFLICT`.

Do not compare raw client JSON formatting. Compare normalized domain/canonical serialization.

## CA-R1-009 — Preference source provenance

Public/user preference mutation must not let the caller claim trusted/internal provenance.

The public endpoint must not persist caller-selected values such as:

- IMPORT
- API
- MANAGED_EMAIL_EVENT
- MANAGED_SMS_EVENT
- META_WHATSAPP
- SYSTEM_MIGRATION

For user-facing preference changes:
- server owns ActorType.USER;
- server owns the source trust class;
- use `USER_UI` or the specifically documented safe user-origin source.

Internal/provider/import adapters may continue using trusted sources through explicit internal methods.

Prefer rejecting a non-user public source with a stable validation error rather than silently misrepresenting provenance.

Add tests over every non-user source enum.

## CA-R1-008 — service identity/contact/channel invariant

Before writing identity-scoped preference or suppression:

- Contact must exist in the same company and be active where required;
- identity must belong to that Contact in that company;
- identity must be active;
- EMAIL channel requires EMAIL identity;
- SMS and WHATSAPP require PHONE identity.

Use the existing provider-neutral `ContactCoreReadPort` seam rather than importing B1 repository implementation classes.

A null identityId remains valid for a Contact-level preference/suppression where the frozen model allows it.

Foreign-company and foreign-within-company identity IDs must fail safely without becoming an enumeration oracle.

Lead will add the complementary relational invariant in V83.

## Code/system-design quality

Use one clear authorization-policy layer for async job revalidation.

Do not create circular dependencies between B1 and B2.

Do not perform network calls while holding DB locks.

Keep async authorization O(1) database/resource metadata lookups per claimed job, not per imported row/contact.

Do not introduce per-row permission checks.

Keep code readable, typed and production-grade.

## Required tests

Add focused tests for all acceptance conditions above, including:

- bulk DELETE permission bypass regression;
- async permission revoked after queue;
- recovery/reclaim authorization;
- consent import dual permission;
- bulk idempotency same/different payload;
- public source spoofing;
- identity belongs to different Contact;
- identity belongs to different company;
- channel/identity mismatch;
- valid Contact-level null identity.

Run when environment permits:

`./mvnw spotless:check`
`./mvnw test`
`./mvnw clean test`
`./mvnw package -DskipTests`

If execution is unavailable, report `EXECUTION BLOCKED`.

## Completion

Commit.
Open PR to `dev`.
Do not merge.

Report:
- branch
- final SHA
- PR
- files changed
- finding-by-finding fix
- authorization mapping
- idempotency behavior
- consent/source integrity
- identity validation
- tests/commands
- blockers/integration requests

Start now.
