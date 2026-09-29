# Meta WhatsApp Integration Status

Updated: 2026-09-29

## Round 1

Integrated QA base:
`cd41f5b4dc3389e2b6213ee176558d70010827d7`

Round-1 QA branch:
`review/whatsapp-security-qa-r1`

Round-1 QA final SHA:
`abc42d95cacd98d904ea338c6fd7aff0f9652777`

QA report:
`SECURITY_QA_FEEDBACK.md`

Disposition:
**NOT RELEASE-READY**

Round-1 QA report/tests were merged into dev by PR #182.

## Open Round-1 findings

High:
- WA-R1-001 — B2 — unsupported signed webhook envelope accepted
- WA-R1-002 — B2 + F1 — durable detailed delivery state not recoverable/rendered after reload
- WA-R1-006 — B1 — required granular-scope target sets incorrectly unioned

Medium:
- WA-R1-003 — B2 — no distributed per-phone limiter / Retry-After cooldown
- WA-R1-004 — B2 — inbound media duplicate window across crash
- WA-R1-005 — B2 — provider-accepted send can remain permanently PENDING
- WA-R1-007 — B2 — older FAILED can overwrite newer ACCEPTED
- WA-R1-008 — F1 — URL-button template variables not derived

## Round 2

Exact immutable worker base:
`fac085504dce38ac4c8f6f04e2f454773bdb811e`

This base includes:
- full Round-1 integrated implementation;
- L1 V78/config integration;
- Round-1 QA findings;
- Round-1 QA contract/regression tests.

Round-2 docs are committed later on dev and therefore are not part of the worker base. Workers must read the current dev copies before branching/coding.

### B1

Branch:
`fix/whatsapp-control-plane-r2`

Prompt:
`BACKEND_B1_R2_CHAT_PROMPT.md`

Finding:
- WA-R1-006

Status:
READY TO START

### B2

Branch:
`fix/whatsapp-runtime-r2`

Prompt:
`BACKEND_B2_R2_CHAT_PROMPT.md`

Findings:
- WA-R1-001
- WA-R1-002 backend
- WA-R1-003
- WA-R1-004
- WA-R1-005
- WA-R1-007

Migration:
`V79__meta_whatsapp_round2_runtime_hardening.sql`

Status:
READY TO START

### F1

Branch:
`fix/whatsapp-ui-r2`

Prompt:
`FRONTEND_R2_CHAT_PROMPT.md`

Findings:
- WA-R1-002 frontend
- WA-R1-008

Status:
READY TO START

## Frozen Round-2 seams

See:
- `ROUND2_CONTRACT.md`
- `ROUND2_TASKS.md`

Important:
- Inbox message JSON gets nullable `whatsappStatus`;
- B2 returns it in HTTP detail/messages/realtime snapshot;
- F1 consumes it and subscribes to `inbox.whatsapp.status.changed`;
- B1 granular target authorization uses required-scope intersection;
- B2 owns V79 media reservation + stale-PENDING recovery;
- B2 owns distributed Redis per-phone limiter;
- no Broadcast/Ads scope expansion.

## L1 after workers complete

- [ ] review B1 final SHA/diff
- [ ] review B2 final SHA/diff
- [ ] review F1 final SHA/diff
- [ ] integrate B1
- [ ] integrate B2
- [ ] wire B2 runtime config if required
- [ ] integrate F1
- [ ] V80 only if an integration-only constraint is required
- [ ] freeze exact Round-2 QA SHA
- [ ] activate SECURITY_QA_R2_CHAT_PROMPT.md
- [ ] run Round-2 QA

## Round-2 QA

Future branch:
`review/whatsapp-security-qa-r2`

Prompt:
`SECURITY_QA_R2_CHAT_PROMPT.md`

Status:
BLOCKED UNTIL L1 INTEGRATION

Round-2 QA must execute the previously unavailable backend/frontend/Flyway suites and distinguish real Meta provider verification from mocks.
