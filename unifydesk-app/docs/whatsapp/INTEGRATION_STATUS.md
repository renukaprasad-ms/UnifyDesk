# Meta WhatsApp Integration Status

Updated: 2026-09-29

## Round 1 QA

Integrated QA base:
`cd41f5b4dc3389e2b6213ee176558d70010827d7`

QA branch:
`review/whatsapp-security-qa-r1`

QA final SHA:
`abc42d95cacd98d904ea338c6fd7aff0f9652777`

Disposition:
NOT RELEASE-READY

Round-1 findings:
- WA-R1-001 — High — B2
- WA-R1-002 — High — B2 + F1
- WA-R1-003 — Medium — B2
- WA-R1-004 — Medium — B2
- WA-R1-005 — Medium — B2
- WA-R1-006 — High — B1
- WA-R1-007 — Medium — B2
- WA-R1-008 — Medium — F1

## Round 2 workers

Frozen worker base:
`fac085504dce38ac4c8f6f04e2f454773bdb811e`

B1:
- branch: `fix/whatsapp-control-plane-r2`
- final SHA: `d9f8ed69bea832bd742a157405ad5caa687fb48a`
- finding: WA-R1-006
- integration PR: #184

B2:
- branch: `fix/whatsapp-runtime-r2`
- final SHA: `30ef6ab7e2c3bc9438c19ec10e68b54f10f6d32e`
- findings: WA-R1-001, WA-R1-002 backend, WA-R1-003, WA-R1-004, WA-R1-005, WA-R1-007
- migration: V79
- integration PR: #185

F1:
- branch: `fix/whatsapp-ui-r2`
- final SHA: `79ba79038b050425acb3668c4ac8aef0a5a7ddf1`
- findings: WA-R1-002 frontend, WA-R1-008
- integration PR: #186

## L1 Round-2 integration

Temporary integration branch:
`integration/whatsapp-r2`

Final integration PR:
#187

Exact integrated Round-2 QA base:
`87e43bbf598a5ea897cabc2a7e96a9acbb6cdf89`

L1 additions:
- wired META_WHATSAPP_OUTBOUND_MAX_PER_SECOND
- wired META_WHATSAPP_OUTBOUND_RATE_WINDOW
- wired META_WHATSAPP_PROVIDER_RETRY_AFTER_FALLBACK
- wired META_WHATSAPP_PENDING_OUTCOME_TIMEOUT
- no V80 was required

Integrated migrations:
- V76 control plane
- V77 runtime
- V78 cross-module constraints
- V79 Round-2 runtime hardening

## Source-review status

L1 source review confirmed intended remediation exists for:
- granular-scope intersection;
- webhook structural validation;
- durable Inbox whatsappStatus projection;
- WhatsApp status realtime consumption;
- Redis per-phone limiter/cooldown;
- Retry-After parsing;
- provider-media reservation + fixed media ID ingest;
- stale PENDING -> UNKNOWN recovery;
- provider timestamp monotonicity;
- Meta URL-button template variable extraction.

This is NOT a replacement for executable QA.

Observed GitHub Actions on the B2 head again failed at runner allocation with `runner_id=0` and no steps, so no automated PASS is claimed by L1.

## Round-2 QA

Branch:
`review/whatsapp-security-qa-r2`

Exact base:
`87e43bbf598a5ea897cabc2a7e96a9acbb6cdf89`

Prompt:
`SECURITY_QA_R2_CHAT_PROMPT.md`

Status:
READY TO START

QA must retest WA-R1-001 through WA-R1-008, execute backend/frontend/Flyway/Redis/fault-injection checks, and separate real Meta provider verification from mocks.

## Branch cleanup

Once the exact QA base above is confirmed in dev, the following Round-2 implementation branches are no longer needed for active development:
- `fix/whatsapp-control-plane-r2`
- `fix/whatsapp-runtime-r2`
- `fix/whatsapp-ui-r2`
- `integration/whatsapp-r2`

Keep `review/whatsapp-security-qa-r2` until QA/remediation is complete.
