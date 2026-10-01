# Performance

## Objective

R1 must comfortably process approximately 2,000-10,000 recipients without synchronous HTTP sending or unbounded in-memory materialization.

## Current source behavior

The existing Broadcast engine already has the correct broad shape:
- HTTP only launches/schedules work;
- keyset audience/member paging;
- configurable snapshot batch size;
- bounded recipient rows per chunk;
- bounded batch/recipient claims;
- worker concurrency;
- distributed provider rate limiting;
- incremental stats;
- throttled realtime progress;
- stale claim recovery.

Canonical audience snapshot resolution also pages source candidates and performs batched identity/preference/suppression reads.

## Complexity target

Let `n` be selected Contacts and `b` the configured snapshot/claim batch.

Recipient resolution:
- time: approximately O(n) plus indexed lookups;
- memory: O(b), not O(n);
- durable snapshot storage: O(eligible recipients) plus bounded metadata.

Execution:
- provider calls: O(eligible recipients);
- worker memory: O(workerConcurrency * recipientClaimSize);
- stats reads: O(1) aggregate row;
- realtime events: bounded/throttled, not O(n) websocket messages.

## 2k

Expected:
- no single request loads all Contacts;
- a small number of snapshot pages;
- no N+1 identity/preference queries;
- provider throughput/rate limit dominates send time.

## 5k

Expected:
- same asymptotic behavior;
- DB writes occur in bounded chunks;
- retry rows do not cause full-audience rescans;
- realtime/dashboard reads stay aggregate.

## 10k

Expected:
- no Java/React structure holds all recipient entities simultaneously;
- no giant `IN (...10k...)` request from frontend;
- snapshot and recipient creation remain paged;
- provider throttling and queue pressure are observable;
- partial retries operate only on affected recipients.

## R1 performance requirements

1. Recipient preview count/sample must use bounded/keyset SQL and batched eligibility; it must not serialize 10k Contacts to the frontend.
2. Individual Contact selection, if implemented, has a bounded synchronous ID limit; larger selections become a List/Segment or server-side selection reference.
3. Scheduling freeze materializes asynchronously if required; schedule HTTP latency must not scale linearly with 10k recipients.
4. WhatsApp Broadcast reuses Broadcast batching and the existing Meta distributed limiter rather than spawning unbounded futures.
5. Inbox Contact-link resolution is O(1) indexed identity lookup per inbound participant or batched during sync; it must not scan Contacts.
6. Provider feedback -> canonical suppression uses indexed company/identity lookup and idempotent upsert.
7. No R1 feature may add per-recipient websocket events.

## Required executable measurements

SEC/QA should measure representative 2k/5k/10k runs with:
- snapshot duration;
- recipient rows/sec;
- peak application heap trend;
- DB query count/slow-query evidence;
- batch/claim sizes;
- queue depth;
- provider limiter behavior (mock/local where real provider is unsafe);
- retry/recovery after worker restart.

A 10k source review alone is not performance verification.

## Database indexes to preserve/check

- Contact identity unique/company normalized lookup.
- List membership by list/contact and source paging.
- Segment compiled query indexes appropriate to supported operators.
- Audience snapshot member sequence.
- Broadcast recipient batch/status/next-attempt.
- provider correlation/message ids.
- Inbox company/connection/external conversation.
- new Inbox Contact-link company/contact and conversation indexes.

EXPLAIN/ANALYZE is required for any new preview/link query that is not trivially indexed.
