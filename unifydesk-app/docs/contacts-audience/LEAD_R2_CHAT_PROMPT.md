# Lead Prompt — Contacts & Audience Round-2 Integration Remediation

You are the Lead engineer for the Contacts/Audience Round-2 integration fixes.

Repository:
`renukaprasad-ms/messaging`

Application:
`unifydesk-app/`

Your EXACT frozen Round-2 base SHA is:

`f6a04c4596240d0c43bde6ef83c66619abbd9a02`

Create:

`fix/contacts-audience-integration-r2`

from EXACTLY that SHA.

Do NOT branch from moving `dev`.
Do NOT cherry-pick B1/B2/F1.
Work independently on Lead-owned fixes; final integration happens after all four PRs are reviewed.

Read current `dev`:

- `unifydesk-app/docs/contacts-audience/SECURITY_QA_FEEDBACK.md`
- `ROUND2_TASKS.md`
- architecture/security/performance/import/merge/integration docs

Your findings are:

- `CA-R1-004`
- `CA-R1-005`
- `CA-R1-007`
- database portion of `CA-R1-008`
- `CA-R1-012`

Your only allowed Round-2 migration is:

`V83__contacts_audience_round2_security.sql`

Do not rewrite V80/V81/V82.

## CA-R1-004 — import mutation/checkpoint crash atomicity

Current import flow can commit Contact mutations before durable import cursor/outcome advancement.

Fix the crash window without placing storage/network reads inside a long DB transaction.

Recommended production shape:

1. worker streams/parses a bounded batch outside the DB transaction;
2. pass the fully parsed/validated batch to a dedicated transaction boundary;
3. inside ONE bounded database transaction:
   - apply Contact mutations;
   - persist row failures/outcomes;
   - advance durable import cursor/counters/checkpoint;
4. commit;
5. update non-authoritative async progress after commit.

Existing mutation adapter transactions should join the outer batch transaction rather than commit earlier.

Do not make the entire CSV one transaction.

Required crash-injection acceptance:
- crash/rollback after mutation logic but before checkpoint commit leaves neither mutation nor cursor advancement committed;
- crash after committed batch reclaims from the next durable cursor;
- SKIP/MERGE_EXACT/FAIL produce the same final Contacts/outcomes as uninterrupted execution;
- no successful first execution becomes a duplicate failure solely because of replay.

Keep object storage access outside the transactional mutation/checkpoint section.

## CA-R1-005 — company purge merged Contact self-FK

V80 merge tombstones reference target Contacts with a restrictive self-FK. Bounded unordered physical company deletion can try to delete a target before its merged sources.

Fix this in a forward-safe way.

Use V83 if changing the relational delete behavior.

Acceptable approaches include:
- recreate merge self-FK with privacy/company-purge-safe `ON DELETE SET NULL`, if semantics remain correct;
- or explicitly clear/dependency-order merge references in bounded cleanup before physical Contact deletion.

Do not leave an unbounded recursive/topological in-memory purge.

Add DB-backed tests with merged pairs spread across multiple purge batches.

Company deletion must eventually reach PURGED without FK retry loops and must leave no Contact PII.

## CA-R1-007 — bulk UPDATE_FIELD / DELETE transaction/query behavior

Current Lead integration adapter performs per-Contact field writes and destructive Contact workflows under a broad batch transaction.

Fix without weakening correctness.

UPDATE_FIELD:
- add/use true bounded batch repository operations for custom-field value mutation;
- avoid delete+upsert query pairs per Contact when one batch mutation can express the same semantics;
- preserve type validation before mutation.

DELETE:
- do not hold one transaction across hundreds/thousands of independent Contact delete workflows;
- each deletion or a deliberately small chunk must have bounded transactional scope;
- deletion must still queue/guarantee privacy purge correctly;
- worker recovery/idempotency must remain safe.

Tag/List operations that already have batch primitives should stay batched.

Do not optimize by bypassing permission, optimistic state or deletion lifecycle invariants.

Provide query-count/transaction-scope tests or the strongest locally executable evidence available.

## CA-R1-008 — database Contact/identity invariant

B2 Round-2 owns service validation.

Lead adds the complementary database invariant in V83.

For identity-scoped current preferences/suppressions, the database must not permit:

Contact A + identity belonging to Contact B.

A practical design is:

- add a referenced unique key/index on `contact_identities(company_id, contact_id, id)`;
- add composite FKs where nullable semantics permit:
  `(company_id, contact_id, identity_id)`
  -> `contact_identities(company_id, contact_id, id)`.

Preserve suppression tombstones where contact_id/identity_id become null.

Do not break privacy purge.

Do not create an FK that prevents legitimate historical event retention.

Channel compatibility remains a service invariant; do not encode EMAIL/SMS/WHATSAPP channel logic with brittle SQL hacks.

## CA-R1-012 — one exact QA base everywhere

Round-1 docs contained both the old `be6de61...` base and final `f6a04c...` base.

As part of Round-2 integration:
- remove stale QA-base instructions from Contacts/Audience status/handoff docs;
- after B1/B2/F1/Lead R2 integration, write ONE exact final R2 QA implementation SHA everywhere;
- do not use moving `dev`;
- add a simple Lead release-freeze checklist/consistency verification before QA handoff.

The final R2 QA SHA will NOT be known until all remediation PRs are merged.

Do not pre-fill a guessed SHA.

## Migration / regression review

V83 must be verified against:

- clean V1 -> V83
- representative V79 -> V83
- existing V80/V81/V82 data
- legacy Broadcast rows
- Contacts with merge tombstones
- preferences/suppressions with nullable identity/contact tombstones
- privacy deletion
- company deletion

## Engineering quality

Keep:
- bounded batches
- O(batch-size) memory
- explicit transaction boundaries
- deterministic retry behavior
- tenant-safe constraints
- readable SQL
- no unnecessary framework/new queue/cache
- no network/storage call while holding DB locks

Every new index/constraint must have an explicit query/invariant reason.

## Required tests/checks

Backend when environment permits:

`./mvnw spotless:check`
`./mvnw test`
`./mvnw clean test`
`./mvnw package -DskipTests`

PostgreSQL:
- V79 -> V83
- clean -> V83
- merged-contact multi-batch company purge
- identity/contact mismatch rejection
- tombstone null behavior
- import crash/rollback/reclaim
- bulk UPDATE_FIELD/DELETE transaction behavior

If unavailable, mark `EXECUTION BLOCKED`.

## Completion

Commit.
Open PR to `dev`.
Do not merge.

Report:
- branch
- final SHA
- PR
- V83 changes
- import transaction/checkpoint design
- purge/self-FK fix
- bulk performance fix
- DB identity invariant
- tests/commands
- blockers
- any integration instruction for B1/B2/F1 merge

Start now.
