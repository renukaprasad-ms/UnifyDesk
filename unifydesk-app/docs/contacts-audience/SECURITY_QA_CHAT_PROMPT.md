# QA/SEC Prompt — Contacts & Audience Round 1

Status: READY FOR QA/SEC
Lead-frozen integrated code SHA:
`be6de61a7c46db4785457f3f9bbb0d622f026101`

You are QA/SEC for UnifyDesk Contacts & Audience Management.

Repository:
`renukaprasad-ms/messaging`

Application:
`unifydesk-app/`

Review branch:
`review/contacts-audience-security-qa-r1`

Architecture/worker base for reference only:
`f31f4f950cc257d4d1edd947a5038a3447599f85`

Your EXACT integrated QA base is:

`be6de61a7c46db4785457f3f9bbb0d622f026101`

Create the review branch from EXACTLY that SHA.

Do NOT branch from later moving `dev`.
Do NOT rebase onto later Ads or unrelated changes.

The documentation manifest may be newer than the QA base. Read the latest Contacts/Audience QA documents from `dev`, but review production code exactly at the frozen SHA above.

## Integrated lineage

- B1 PR #202 final reviewed head: `4c86c66607721c85b5e3f75d4b27bddae96dc421`
- B2 PR #201 final head: `3c37f504fb5d256154f6c9f045567e514695a37a`
- F1 PR #200 final head: `e9b4d7b7fdae1efd23b3ed53962a2882b12b9787`
- Lead integration PR #203: merged
- V82: `V82__contacts_audience_integration.sql`
- Exact integrated QA code base: `be6de61a7c46db4785457f3f9bbb0d622f026101`

## Read first

- `unifydesk-app/docs/contacts-audience/ARCHITECTURE.md`
- `DATA-MODEL.md`
- `API-CONTRACT.md`
- `SECURITY.md`
- `PERFORMANCE.md`
- `DECISIONS.md`
- `SEGMENTATION.md`
- `CONSENT-SUPPRESSION.md`
- `IMPORT-EXPORT.md`
- `CONTACT-MERGE.md`
- `INTEGRATIONS.md`
- `QA-SEC-PLAN.md`
- `INTEGRATION_STATUS.md`
- `TASKS.md`

## Mission

Perform an adversarial production verification of the complete integrated Round-1 Contacts/Audience implementation.

This is NOT an implementation chat.

Do not silently fix production defects.

You may add review-only tests/tooling on the QA branch.

Write all findings to:

`unifydesk-app/docs/contacts-audience/SECURITY_QA_FEEDBACK.md`

Finding IDs:

`CA-R1-001`, `CA-R1-002`, ...

Each finding must contain:

- severity: Critical / High / Medium / Low
- ownership: B1 / B2 / F1 / Lead
- violated invariant or contract
- exact source/runtime evidence
- reproduction
- security/correctness/performance impact
- expected behavior
- precise acceptance condition

Do not create findings for stylistic preferences unless they have a concrete production impact.

## Engineering-review standard

Review this as production multi-tenant SaaS infrastructure.

Explicitly examine:

- time complexity;
- space complexity;
- SQL/query complexity;
- indexes/query plans;
- N+1 behavior;
- keyset pagination correctness;
- bounded memory;
- transaction duration;
- lock ordering;
- concurrency;
- idempotency;
- retry/recovery semantics;
- failure transitions;
- tenant isolation;
- PII lifecycle;
- code readability and maintainability where they affect correctness.

Flag unbounded whole-tenant materialization, O(n²) paths, unsafe OFFSET scans, per-row query/write loops where batching is required, or long external calls inside DB lock/transaction boundaries.

## Required verification

Execute `QA-SEC-PLAN.md` completely.

### Security / tenancy

Verify:

- Contact IDOR/cross-company access
- identities
- tags
- custom fields
- Lists/members
- Segments
- imports
- exports/downloads
- bulk operations
- preferences
- suppressions
- merge
- activity
- audience snapshot access
- Broadcast Contact provenance
- signed URLs
- permissions/default role behavior
- mass assignment
- cursor tampering
- API IDs remain JS-safe strings at public boundaries
- PII is not exposed through logs/errors

A foreign resource ID must not become an existence oracle.

### Contact/identity

Verify:

- conservative email normalization
- IDNA email domain handling
- E.164 phone handling
- ambiguous national phone rejection without country context
- multiple identities
- primary identity uniqueness
- exact duplicate race
- same identity in different tenants
- deleted/merged mutation behavior
- optimistic Contact version conflict

### Tags / Lists / custom fields

Verify:

- tenant-scoped normalized uniqueness
- membership race/idempotency
- List member-count correctness
- archived behavior
- typed custom values
- `SINGLE_SELECT` and `MULTI_SELECT`
- select option validation
- version behavior
- indexes/query behavior

### Segments

Verify the public frozen contract:

- `filterVersion: 1`
- documented AST
- no exposure of internal SQL/Java serialization

Test:

- max depth
- max predicate count
- max IN values
- max serialized bytes
- type/operator matrix
- malicious field names
- SQL injection payloads
- unavoidable company predicate
- active Contact predicate
- custom field type resolution
- keyset paging
- exact/cached/pending counts
- optimistic Segment version
- source-version drift

Review representative SQL/EXPLAIN behavior.

### Consent / suppression / eligibility

Verify:

- projection + append-only event atomicity
- idempotency replay
- actor spoof rejection
- occurredAt bounds
- OPTED_OUT precedence
- active suppression precedence
- non-liftable suppression policy
- HMAC suppression tombstones
- Contact deletion with multiple identities
- re-import of suppressed identity
- explicit Contact merge: OPTED_OUT > UNKNOWN > OPTED_IN
- suppression survival through merge

### Import

Adversarially test:

- oversized object
- size mismatch
- malformed CSV
- excessive columns
- oversized cells
- control characters
- invalid encoding
- invalid email/phone
- duplicate strategies
- mapping validation
- custom-field mapping
- explicit consent evidence mapping
- idempotency: same key/same request
- same key/different checksum or mapping
- worker crash/reclaim
- error report
- storage reservation/accounting
- expiry/private file behavior

Do not load a full large file into memory just to test it.

### Export

Verify:

- CONTACTS / LIST / SEGMENT sources
- tenant authorization
- field allow-list
- streaming/bounded memory
- CSV formula-injection neutralization
- short-lived signed URL
- expired export
- guessed/foreign export ID
- permission reauthorization
- artifact cleanup

### Bulk

Verify:

- IDS <= 500 synchronous/bounded semantics
- LIST/SEGMENT/FILTER selections
- large/destructive async processing
- duplicate idempotency
- partial failure reporting
- cross-company IDs
- crash/recovery
- selection is not expanded into an unbounded in-memory list

### Contact delete / privacy purge

Verify:

- DELETE immediately blocks query/send
- `CONTACT_DELETE_PURGE` job is actually claimed/executed
- profile/custom fields/identities are purged
- active suppressions are converted to keyed HMAC tombstones before identity deletion
- contact-wide suppression with multiple same-channel identities protects every relevant identity
- replay/retry remains idempotent
- historical Broadcast recipient destination remains immutable for Contact-level deletion
- no plaintext identity remains in suppression tombstone

### Company purge

Verify Lead integration:

- Contacts import/export private objects are physically deleted before metadata removal
- cleanup is bounded/retryable
- Contacts PII/data is removed in FK-safe order
- no Contacts FK prevents company lifecycle completion
- storage/provider failures cause retry rather than silent data loss
- existing company lifecycle behavior is not regressed

### Broadcast integration

Legacy group Broadcast MUST remain unchanged.

Then verify LIST and SEGMENT audience sources:

- source belongs to company
- source version/filter hash frozen
- snapshot generated at actual launch/materialization
- candidate and eligible counts are correct
- snapshot processing is keyset/batched
- destination is selected deterministically
- duplicate destination cannot create duplicate send
- BroadcastRecipient stores immutable destination plus Contact/identity/snapshot provenance
- List/Segment mutation during snapshot cannot silently change the audience
- source drift reaches a terminal failure rather than leaving run stuck PREPARING
- retries use the same snapshot
- Contact edits do not rewrite historical recipient destination
- post-snapshot OPT_OUT blocks provider submission
- post-snapshot suppression blocks provider submission
- identity replacement/change blocks provider submission rather than silently sending to a different address
- eligibility recheck happens before every managed/existing provider call
- statistics remain internally consistent for skipped recipients

WhatsApp Broadcast execution remains intentionally out of Round-1 and must not be invented during QA.

### Inbox / activity

Verify:

- ContactIdentityLookup is company-scoped
- same email/phone in different tenants resolves correctly
- unknown Inbox sender remains valid
- no hard FK from Inbox persistence to Contacts
- Contact activity returns normalized bounded summaries
- Broadcast activity adapter contains no copied message body/provider webhook payload
- activity cursor ordering is stable

### Merge

Test concurrency and semantics:

- deterministic source/target locking
- reversed merge race
- stale versions
- merge vs identity create
- tag/List union
- typed custom-field strategy
- consent reconciliation
- suppression survival
- history is not rewritten as if it originally belonged to target

### Migrations

Mandatory PostgreSQL verification:

- clean V1 -> V82
- representative V79 -> V82
- V80/V81/V82 ordering
- indexes
- unique constraints
- FK behavior
- privacy-purge FK behavior
- legacy Broadcast rows remain valid after V82
- new audience Broadcast rows satisfy V82 checks

### Performance / scale

Use representative DB-backed tests where feasible:

- 10k Contacts
- 100k Contacts
- EXPLAIN/query-plan review for million-contact viability

Review:

- Contact search
- identity lookup
- List members
- Segment evaluation
- import batching
- export streaming
- bulk
- audience snapshot
- Broadcast materialization
- activity timeline

Do not claim a 1M production SLA from synthetic unit tests.

## Required commands

Backend:

`cd unifydesk-app/backend`

`./mvnw spotless:check`

`./mvnw test`

`./mvnw clean test`

`./mvnw package -DskipTests`

Frontend:

`cd unifydesk-app/frontend`

`npm ci`

`npm run format:check`

`npm run lint`

`npm test`

`npm run build`

## Current CI infrastructure warning

At Lead freeze time, GitHub Actions for PR #203 and the worker PRs reported failures but their affected jobs contained **zero executed workflow steps**.

Therefore:

- do NOT call those CI failures product-code failures without evidence;
- do NOT call the checks PASS either;
- run the commands locally/on an available runner if possible;
- otherwise mark them `EXECUTION BLOCKED` with evidence.

This warning does not excuse source-level compilation errors or contract defects found during review.

## Execution evidence classification

For every important verification area distinguish:

- `SOURCE/CONTRACT REVIEWED`
- `LOCAL/MOCK VERIFIED`
- `DB INTEGRATION VERIFIED`
- `REAL PROVIDER VERIFIED`
- `EXECUTION BLOCKED`

Never report an unexecuted check as PASS.

Real Google Customer Match / Meta Custom Audience activation is not implemented in Round-1 and must not block the Round-1 Contacts release.

## Final disposition

Conclude with exactly one:

`RELEASE-READY`

or

`NOT RELEASE-READY`

Commit only:

- `SECURITY_QA_FEEDBACK.md`
- review-only QA tests/tooling when useful

Do NOT silently modify production implementation.

Do NOT merge the QA branch.

Report:

- review branch
- exact QA base SHA
- final review SHA
- files added/changed
- commands actually executed
- migration results
- security results
- concurrency results
- performance/query-plan results
- findings by severity/owner
- regressions
- execution blockers
- final disposition
