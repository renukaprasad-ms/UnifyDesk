# Contacts & Audience Performance

Target design sizes:
- 10k contacts: routine tenant
- 100k contacts: large tenant
- 1m contacts: schema must remain viable

Round 1 is not promising instant 1m-contact bulk operations; it must avoid architectural dead ends.

## Pagination

Use keyset pagination for Contacts, List members, Segment results, Activity and snapshot workers.

Avoid OFFSET for high-cardinality data.

Stable order examples:
- updated_at DESC, id DESC
- id ASC for workers

## Search

V1 search supports bounded normalized patterns over:
- display/name fields
- exact/prefix email
- exact/prefix E.164 phone
- organization

Do not implement unbounded contains across every custom field.

A query shorter than the configured minimum prefix must not trigger expensive wildcard scans.

## Index rules

Every high-cardinality index starts with company_id or a parent key that is already tenant-scoped.

Important:
- identities: company + type + normalized
- contact listing sorts
- list memberships: list/contact
- tags memberships
- custom typed values by field/value/contact
- segment current metadata
- preference/suppression lookup
- snapshot members
- import/export jobs

## Import

Streaming parser.
Default DB insert/update batch: 500.
Hard configurable ceiling: 2,000.
Commit in bounded transactions; do not hold one transaction for a 1m-row file.

Preload exact identity matches per batch, not one query per row.

Use database uniqueness as the final concurrency guard.

## Export

Stream ResultSet/page chunks directly to output object/file. Never collect all rows into memory.

CSV row writer has bounded field/cell sizes.

## Segment compilation

Compiler emits parameterized SQL with EXISTS/JOINs that can use indexes.

Cost classifier:
- LOW: builtins/identity/tag/list/simple custom field
- MEDIUM: multiple indexed predicates
- HIGH: large OR trees/non-prefix text

HIGH exact count defaults async.

No arbitrary SQL.

## Snapshot

Snapshot evaluation:
- keyset candidate query
- batch load identities/preferences/suppressions
- batch eligibility
- batch insert snapshot members
- update progress cursor transactionally

Memory bound is O(batch size).

## Counts

Lists maintain transactional cached member_count with periodic reconciliation.

Segments cache exact count + filter hash + timestamp. Cache is advisory; send snapshot always evaluates the current frozen filter definition/version.

## Bulk

Small <=500 explicit IDs may be synchronous for tag/list operations.
Anything selection-based, destructive, or larger is a job.

## Transactions

- no network/storage provider calls inside long DB transaction
- contact merge is one bounded DB transaction
- import/export perform storage access outside DB locks
- snapshot batches have bounded transaction size
- SKIP LOCKED/lease patterns may be reused for job workers

## Concurrency

Required tests:
- duplicate identity creation
- same import idempotency key
- same list membership add
- tag assignment
- preference event replay
- suppression event replay
- merge races
- snapshot worker duplicate claim
- delete vs bulk update

## Million-contact readiness

Before production claims 1m-contact SLA, load tests must verify real DB plans. V1 architecture intentionally leaves room for:
- partitioning by company/hash
- read replicas
- materialized segment membership
- dedicated analytics/event stores

None is introduced prematurely.
