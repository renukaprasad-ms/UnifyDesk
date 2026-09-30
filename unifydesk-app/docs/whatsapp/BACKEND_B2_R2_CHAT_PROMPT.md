# B2 Prompt — Meta WhatsApp Round 2

You are B2 for UnifyDesk Meta WhatsApp Round-2 remediation.

Your EXACT worker base SHA:
`fac085504dce38ac4c8f6f04e2f454773bdb811e`

Your branch:
`fix/whatsapp-runtime-r2`

Create your branch from exactly that SHA. Do not branch from later moving `dev`.

Read completely from current `dev`:
- `SECURITY_QA_FEEDBACK.md`
- `ROUND2_CONTRACT.md`
- `ROUND2_TASKS.md`
- `WEBHOOK-CONTRACT.md`
- `MESSAGE-STATE.md`
- `PERFORMANCE.md`
- `SECURITY.md`

Your findings:
- WA-R1-001
- WA-R1-002 backend half
- WA-R1-003
- WA-R1-004
- WA-R1-005
- WA-R1-007

Do not work on WA-R1-006 or WA-R1-008.

## WA-R1-001

Reject a validly signed but structurally unsupported callback:
- wrong object;
- missing/empty entry;
- malformed entry/change;
- missing field/value.

Return 400 and insert zero events.

Keep structurally valid unknown WhatsApp fields as bounded UNKNOWN events if appropriate.

The QA test must pass because production validation is fixed.

## WA-R1-002 backend

Extend existing `InboxMessageResponse` with nullable:

`whatsappStatus`

Allowed detailed runtime values:
PENDING, ACCEPTED, SENT, DELIVERED, READ, FAILED, UNKNOWN.

Populate in:
- list messages;
- conversation detail;
- realtime snapshot.

Requirements:
- batch fetch runtime state by Inbox message IDs;
- no N+1;
- non-WhatsApp messages remain null;
- generic Inbox `status` semantics remain unchanged;
- current realtime event `inbox.whatsapp.status.changed` remains valid.

Add backend tests proving reload/detail/snapshot returns READ after delivered/read webhook progression.

## WA-R1-003

Implement distributed per-phone outbound limiting using existing Redis infrastructure.

Use the frozen Round-2 properties/defaults and phone-scoped key.

Required:
- acquire immediately before Meta message submission;
- Redis failure = fail closed;
- limiter rejection = no provider call;
- parse provider 429 Retry-After;
- impose distributed phone cooldown;
- no blind automatic message replay.

Reuse repository Redis services/patterns instead of adding a second limiter platform.

Add:
- focused unit tests;
- Redis integration/concurrency test where environment permits;
- 429/Retry-After test.

## WA-R1-004

Add V79 provider-media durable reservation.

Reserve a fixed media ID before storage. Add an idempotent trusted MediaService ingest method that accepts/reuses that reserved ID and deterministic object key.

Fault sequence must converge:
- crash after Media ingest but before message link;
- retry;
- same reserved media reused;
- exactly one Media row/object and one message-media link.

Do not create an alternate storage subsystem.

## WA-R1-005

Add bounded stale-PENDING recovery:
- configurable default 2m;
- atomically PENDING -> UNKNOWN;
- scheduler + safe lazy recovery allowed;
- no provider resubmission;
- duplicate clientRequestId returns UNKNOWN after recovery;
- durable Inbox projection exposes UNKNOWN.

Add recovery index to V79 and fault/restart regression tests.

## WA-R1-007

Fix timestamp monotonicity:
- if incoming provider timestamp is older than current known provider timestamp, reject it regardless of rank;
- keep existing terminal-state protections;
- equal/non-advancing duplicate remains no-op.

Keep QA contract test and add production coverage.

## Migration

Create:
`V79__meta_whatsapp_round2_runtime_hardening.sql`

Do not create V80.

## Ownership

You may modify:
- Meta WhatsApp runtime package
- webhook controller
- InboxMessageResponse / InboxQueryService / focused repository/helper code
- MediaService / MediaRepository only as required for reserved-ID idempotent trusted ingest
- B2 tests
- V79

Do NOT modify:
- B1 connection/control-plane files
- frontend
- Ads/Broadcast
- Round-2 docs

You may add runtime property fields with safe defaults. L1 will wire new env/YAML values after integration if necessary.

Run:
```bash
cd unifydesk-app/backend
./mvnw spotless:check
./mvnw test
./mvnw clean test
./mvnw package -DskipTests
```

Also run Redis integration tests when an executable Redis is available.

Commit and report:
- branch
- final SHA
- exact files changed
- each finding and its fix
- V79 details
- tests/checks
- Redis integration result
- config fields L1 must wire
- blockers/deviations

Do not merge.
