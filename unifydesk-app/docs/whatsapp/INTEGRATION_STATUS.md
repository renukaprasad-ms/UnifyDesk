# Meta WhatsApp Integration Status

Updated: 2026-09-29

## Round 0

Architecture branch:
`docs/whatsapp-architecture-r0`

Frozen Round-1 worker base:
`cd5961362ddba0eda45d0ee1a881e93bab2f214c`

## Round 1 integrated

B1:
- branch: `feature/whatsapp-control-plane-r1`
- final SHA: `d8b98df2a6b1ea277081b7bde179dbe8f8b60781`
- integration PR: #177

B2:
- branch: `feature/whatsapp-runtime-r1`
- final SHA: `5ab5f71a06f6d605a02b9211c8d47b31b51aeefe`
- integration PR: #178

F1:
- branch: `feature/whatsapp-ui-r1`
- final SHA: `35c954504bf9dea3c981b9f5d34308ac0321db26`
- integration PR: #179

Architecture/orchestration docs:
- integration PR: #180

Final integration:
- branch: `integration/whatsapp-r1`
- PR to dev: #181
- exact integrated QA base SHA: `cd41f5b4dc3389e2b6213ee176558d70010827d7`

## Integrated migrations

- V76 — Meta WhatsApp control plane
- V77 — Meta WhatsApp runtime
- V78 — Lead-owned Meta WhatsApp cross-module constraints

Current dev had no V76-V78 collision before integration.

## QA / Security

QA branch:
`review/whatsapp-security-qa-r1`

QA MUST branch from:
`cd41f5b4dc3389e2b6213ee176558d70010827d7`

Status:
READY TO START

Prompt:
`SECURITY_QA_CHAT_PROMPT.md`

Detailed tasks:
`QA-SEC-PLAN.md`

## Verification status

Completed by L1 review:
- [x] B1 ownership/contract review
- [x] B2 ownership/contract review
- [x] F1 ownership/contract review
- [x] B1/B2 frozen SQL and credential seam checked
- [x] F1/B1/B2 HTTP shape parity checked
- [x] migration number collision checked
- [x] B1 integrated
- [x] B2 integrated
- [x] F1 integrated
- [x] V78 integrated
- [x] runtime configuration wired
- [x] exact QA SHA frozen
- [x] QA prompt activated

Still owned by QA/SEC:
- [ ] clean Flyway migration executed through V78
- [ ] backend Spotless/full tests/package executed
- [ ] frontend format/lint/tests/build executed
- [ ] integrated concurrency/fault/security tests
- [ ] real Meta Embedded Signup v4/provider smoke
- [ ] QA disposition

## CI note

The observed B2 GitHub Actions run failed before any job step started:
- runner_id = 0
- steps = []

That is repository runner/infrastructure behavior, not evidence that the implementation tests failed. It is also not a pass. QA must execute the actual suites.

## Branch cleanup after QA base freeze

The Round-1 worker branches and temporary architecture/integration branch are no longer needed for active development once their SHAs are recorded here. They may be deleted from the remote after confirming `dev` contains PR #181.

Keep the QA branch until QA/remediation is complete.

## Integration invariants

- no worker implementation depends on another unmerged worker branch;
- B2 uses the frozen B1 SQL/credential seam rather than B1 implementation imports;
- F1 consumes frozen HTTP contracts;
- no Meta Ads work is part of this integration;
- Embedded Signup v4 is the target;
- signed webhook durable boundary is present;
- existing unified Inbox/Media are reused;
- backend owns template-window enforcement;
- ambiguous sends are not blindly replayed.
