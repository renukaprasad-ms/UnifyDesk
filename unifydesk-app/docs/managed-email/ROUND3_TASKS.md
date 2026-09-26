# Managed Email Round 3 Tasks

**Architecture owner:** Lead / Architect
**Source:** Round-2 QA/SEC retest on `review/managed-email-security-qa-r2`
**Round-2 tested SHA:** `0dd63e9f4951277144bcbe1b9c45a50d4b6fbdd6`

Round 3 is intentionally narrow. Only substantive residual code defects are reopened.

## Residual implementation defects

1. ME-SEC-001 — invalid Java syntax in AmazonSesBroadcastEmailTransport.
2. ME-SEC-002 — persisted sender validation checks only domain equality; malformed same-domain mailbox syntax can still pass.
3. ME-SEC-004 — event local processing is resumable, but provider suppression has no durable completion/checkpoint.
4. ME-SEC-005 — SES provider snapshot uses a persistence timestamp generated after GetAccount, leaving an observation/send race.

Findings ME-SEC-003, 006, 007, 008, 009 and 010 remain in QA verification only. No new production work is assigned for them unless Round-3 retest shows a real defect.

---

# B1 — Provider snapshot timestamp correctness

**Branch:** `fix/managed-email-provider-snapshot-r3`

Own only the provider observation-time half of ME-SEC-005.

Requirements:

- Capture one immutable provider observation timestamp at or immediately before the SES GetAccount call that produced MaxSendRate, Max24HourSend and SentLast24Hours.
- Return that exact timestamp in ManagedEmailSesGateway.TenantSnapshot.
- Persist that exact timestamp as the managed provider snapshot time.
- Synchronize BroadcastSenderConnection.lastValidatedAt from that provider observation timestamp rather than from a later Instant.now().
- Ensure retries cannot make an old quota observation appear newer than it actually is.
- Do not redesign the provider budget algorithm owned by B2.
- Add tests proving the persisted connection snapshot timestamp exactly matches the provider observation time supplied by the gateway.
- Add a concurrency-oriented regression fixture proving a send committed after provider observation is counted by B2's local "since snapshot" logic after integration.

B1 may modify only:
- managedemail/control/aws/**
- managedemail/control/provisioning/**
- managedemail/control/entity/** only if necessary
- relevant B1 tests

B1 must not modify:
- Broadcast delivery/transport files
- managedemail/delivery/**
- managedemail/events/**
- managedemail/runtime/**
- migrations
- frontend
- architecture docs

No new migration is expected.

---

# B2 — Final runtime/event correctness

**Branch:** `fix/managed-email-runtime-r3`

Own ME-SEC-001, residual ME-SEC-002, residual ME-SEC-004, and integration consumption of B1's exact snapshot timestamp contract.

## A. Build blocker

Fix the invalid Java display-name sanitization in AmazonSesBroadcastEmailTransport.

Use valid Java string escaping.

Add/retain a focused test around display names containing quotes.

## B. Strict persisted mailbox validation

ManagedEmailAddressPolicy must validate the entire persisted mailbox, not only extract a domain.

Requirements:

- one mailbox only;
- valid local-part syntax;
- reject consecutive dots;
- reject leading/trailing dot;
- reject illegal control/whitespace;
- reject display-name wrappers in persisted raw fromAddress;
- validate address using a strict parser compatible with existing sender creation policy;
- normalize/extract domain only after full mailbox validation;
- exact normalized domain equality to bound ManagedEmailDomain.

Tests:
- valid offers@example.com passes;
- ceo@amazon.com fails against example.com;
- bad..local@example.com fails;
- .bad@example.com fails;
- bad.@example.com fails;
- malformed quoted/display-name form fails;
- malformed Unicode/control forms fail;
- zero SES invocation for every invalid persisted sender case.

## C. Durable provider suppression completion

For hard bounce/complaint, do not allow processed_at to represent complete event workflow while SES tenant suppression remains unscheduled/uncompleted.

Implement a durable provider-effect checkpoint/outbox state.

Acceptable design:
- persist required provider suppression task/status in the same local transaction as event local effects;
- local event can be marked local-processing complete only when the provider task is durably scheduled;
- a retry/scheduler claims pending provider suppression work and calls SES;
- success marks provider effect completed;
- transient failure remains retryable;
- duplicate SNS delivery or worker retry is idempotent;
- no duplicate local suppression or billing;
- permanent provider errors are recorded with bounded retry/dead-letter/operator-visible state.

Do not perform an external SES call inside the long local DB transaction.

Add tests:
- local commit succeeds, process crashes before SES suppression call, retry worker later completes it;
- SES suppression fails transiently then succeeds;
- duplicate provider-effect worker execution is idempotent;
- duplicate SNS MessageId does not double-create provider-effect work;
- processed/completion fields have unambiguous semantics.

## D. Snapshot timestamp consumption

Consume B1's exact provider observation timestamp through the existing connection/runtime projection.

Do not create a second timestamp.

Provider budget lower-bound calculations must use the provider observation instant persisted by B1.

B2 may modify only:
- AmazonSesBroadcastEmailTransport.java
- managedemail/delivery/**
- managedemail/events/**
- managedemail/runtime/**
- related backend tests

B2 must not modify:
- managedemail/control/**
- pom.xml
- application.yaml
- .env.example
- migrations
- frontend
- architecture docs

---

# F1

No Round-3 production work assigned.

Frontend privacy changes from Round 2 remain in QA retest scope only.

Do not create a new F1 branch unless Lead reopens a frontend defect after Round-3 QA.

---

# Lead integration

After B1 and B2 PRs are ready:

1. verify zero overlapping production files;
2. merge B1 first;
3. overlay/rebase B2 only if GitHub reports stale-base conflict;
4. verify B2 uses the exact provider observation timestamp produced by B1;
5. run/review backend build/test commands;
6. freeze one Round-3 QA SHA;
7. QA/SEC retests all ME-SEC-001..010, but with special attention to 001/002/004/005.

Workers must not merge their own PRs.
