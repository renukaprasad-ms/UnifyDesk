# Contacts & Audience Performance

## 1. Scale targets

Architecture must remain viable for:

- 10,000 contacts/company;
- 100,000 contacts/company;
- 1,000,000 contacts/large company.

Round 1 does not promise identical latency at all sizes.

## 2. Core API targets

Under normal indexed conditions:

- exact identity resolve: index lookup, no table scan;
- contact first page: bounded keyset query;
- list/tag member page: bounded keyset query;
- detail: bounded number of queries;
- no N+1 identity/tag/list loads in table pages.

Do not return every contact list/tag/identity object in table summaries.

## 3. Pagination

Use keyset/cursor for:

- contacts;
- list members;
- segment members;
- activity;
- consent history;
- import errors;
- export/bulk internal iteration.

Offset may remain on small configuration collections if bounded, but not million-row member data.

## 4. Search

Exact normalized email/phone:
- B-tree.

Name/free text:
- normalized columns or PostgreSQL text/trigram where confirmed.

Avoid `lower(column) like '%q%'` full scans at million scale without an index strategy.

Minimum search length and rate limit may apply.

## 5. Import

Memory: O(batch size), not O(file rows).

Use:

- streaming CSV parser;
- set-based normalization/identity lookup;
- batch inserts/updates;
- chunk commits;
- prepared statements;
- checkpointing;
- async worker.

Target initial practical throughput should be measured, not guessed. QA records rows/sec at 10k and 100k fixtures and extrapolates bottlenecks.

## 6. Contact limits under concurrency

Avoid count(*) per inserted row.

Maintain/derive tenant usage with a locked company-scoped counter or efficient indexed count at operation boundary.

Bulk import reserves/controls capacity in batches and stops safely if limit is reached.

## 7. List/tag counts

Do not `count(*)` every membership for every index row.

Maintain denormalized member_count/contact_count transactionally with reconciliation or compute asynchronously.

Unique membership insert determines whether count increments.

## 8. Segment evaluation

Never:

- load contacts into Java and filter;
- emit raw SQL from client;
- materialize all segment members just to show a page.

SQL compiler + indexes.

Count can be expensive; cache/version and async refresh.

Member enumeration uses keyset.

## 9. Audience snapshot

Snapshot is async-capable and chunked.

Pipeline:

- enumerate source contacts by keyset;
- resolve selected identities set-based;
- join preference/suppression data;
- insert snapshot members in batches;
- update counters/checkpoint.

Memory bounded.

Snapshot members are immutable after READY.

## 10. Export

Stream:
- DB cursor/keyset pages;
- CSV writer;
- private object output.

Do not build a full CSV string/byte array in memory.

## 11. Bulk jobs

Process 250-2000 contacts/chunk depending on operation/query.

Each chunk:
- stable keyset;
- bounded transaction;
- idempotent mutation;
- progress/checkpoint.

Avoid million-row single transactions.

## 12. Custom JSONB

Bound payload size.

GIN can help containment/equality.

Range sort/filter on arbitrary JSON paths may be slower; do not create indexes per user field automatically.

Future large tenants can use generated/indexed promoted custom fields if product evidence justifies it.

## 13. Activity

Contact detail shows a bounded recent timeline.

No unbounded joins over all Inbox/Broadcast event tables.

Cross-module references are paginated.

## 14. Purge

Company/contact purge is batch-based.

Never cascade-delete a million-contact workspace inside one application transaction if it can be drained incrementally.

## 15. Async worker claiming

Reuse SKIP LOCKED/lease patterns where available.

Multiple pods should process different jobs/chunks without duplicate effects.

## 16. Metrics

At minimum:

- contact CRUD latency/errors;
- identity conflicts;
- search latency/result count;
- segment compile/evaluate/count latency;
- import rows/sec/errors/job age;
- export rows/sec/bytes/job age;
- bulk rows/sec/job age;
- audience snapshot matched/eligible/excluded/latency;
- consent/suppression changes;
- worker reclaim count;
- DB query timeout count.

No raw PII labels.

## 17. QA data sizes

Automated/focused QA should create manageable fixtures for CI plus dedicated larger runs where environment permits.

Required recorded scenarios:

- 10k contacts;
- 100k import/export/segment;
- 1M schema/query-plan validation or generated DB fixture where infrastructure permits.

For 1M, if full load test cannot run, QA must report NOT EXECUTED rather than claim success.

Use EXPLAIN/ANALYZE for representative hot queries in a non-production test DB.

## 18. Backpressure

Rate-limit expensive user-triggered jobs per company.

Do not allow 100 simultaneous full exports/segment counts to starve messaging runtime.

Worker concurrency/config must be separate from Broadcast/notification critical workers where necessary.
