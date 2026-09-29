# Meta WhatsApp Round-2 Remediation Tasks

Status: READY  
Exact worker base SHA: `fac085504dce38ac4c8f6f04e2f454773bdb811e`

Source findings:
`SECURITY_QA_FEEDBACK.md`

Frozen remediation contract:
`ROUND2_CONTRACT.md`

## Branches

- B1: `fix/whatsapp-control-plane-r2`
- B2: `fix/whatsapp-runtime-r2`
- F1: `fix/whatsapp-ui-r2`
- QA/SEC later: `review/whatsapp-security-qa-r2` from L1's new integrated SHA

All three implementation workers branch from exactly `fac085504dce38ac4c8f6f04e2f454773bdb811e`.

Do not branch from later moving `dev`.

## Finding ownership

| Finding | Severity | Owner |
|---|---|---|
| WA-R1-001 | High | B2 |
| WA-R1-002 | High | B2 backend + F1 frontend |
| WA-R1-003 | Medium | B2, L1 config wiring after integration if needed |
| WA-R1-004 | Medium | B2 |
| WA-R1-005 | Medium | B2 |
| WA-R1-006 | High | B1 |
| WA-R1-007 | Medium | B2 |
| WA-R1-008 | Medium | F1 |

## B1 — Control-plane authorization hardening

Branch:
`fix/whatsapp-control-plane-r2`

Only finding:
`WA-R1-006`

Required:
- replace union-across-required-scopes asset authorization with per-scope union + intersection;
- selected WABA must be in every required granular permission target set;
- preserve existing top-level scope validation;
- preserve multi-WABA selection behavior;
- keep QA test `MetaWhatsAppRound1ControlPlaneQaTests` passing because production code is fixed, not because test expectation is weakened;
- add focused production tests for both scopes/intersection cases.

Primary ownership:
- `MetaWhatsAppConnectionService.java`
- B1 connection tests
- QA test may be moved/refactored only without weakening it.

Forbidden:
- webhook/runtime
- Inbox DTO/query
- frontend
- migrations
- Ads/Broadcast

## B2 — Runtime/security/reliability hardening

Branch:
`fix/whatsapp-runtime-r2`

Findings:
- WA-R1-001
- WA-R1-002 backend half
- WA-R1-003
- WA-R1-004
- WA-R1-005
- WA-R1-007

Required:
1. strict signed WhatsApp webhook envelope validation;
2. durable `whatsappStatus` projection in Inbox message/detail/snapshot without N+1;
3. distributed Redis per-phone limiter + fail-closed Redis behavior + provider Retry-After cooldown;
4. durable inbound media reservation + reserved Media ID idempotent ingest;
5. stale outbound PENDING recovery to UNKNOWN, no provider replay;
6. provider timestamp monotonic state fix.

Migration:
`V79__meta_whatsapp_round2_runtime_hardening.sql`

B2 may modify:
- `channel/meta/runtime/**`
- `MetaWhatsAppWebhookController.java`
- `InboxMessageResponse.java`
- `InboxQueryService.java`
- focused Inbox repositories/helpers
- `MediaService.java` and focused Media repository/service helpers necessary for reserved-ID idempotent ingest
- B2/runtime/Inbox/Media tests
- V79

B2 must not modify:
- B1 control-plane implementation
- frontend
- Ads/Broadcast
- frozen Round-1 architecture contracts
- Round-2 contract/task docs

## F1 — Delivery-state + template UI hardening

Branch:
`fix/whatsapp-ui-r2`

Findings:
- WA-R1-002 frontend half
- WA-R1-008

Required:
- add optional Inbox message `whatsappStatus` field;
- use durable status before generic SENT fallback;
- subscribe to `inbox.whatsapp.status.changed`;
- scope event by selected connection/conversation;
- refresh the affected message/snapshot/detail so DELIVERED/READ/FAILED/UNKNOWN update live;
- reload/navigation/focus behavior recovers durable status through HTTP;
- preserve UNKNOWN warning;
- extend template provider component typing with buttons;
- derive URL-button variables from `component.buttons[*].url` in provider order;
- submit `parameters.buttons` exactly in backend-expected order;
- keep QA frontend contract test and add integration coverage.

F1 must not modify backend/Flyway/Ads/Broadcast.

## L1 integration responsibilities

After workers finish:
1. review all diffs/findings one-by-one;
2. integrate B1;
3. integrate B2;
4. wire new B2 runtime limiter/recovery configuration to YAML/env if B2 only added property defaults;
5. integrate F1;
6. add V80 only if truly required;
7. execute/inspect integration checks available;
8. freeze one immutable Round-2 QA SHA;
9. activate `SECURITY_QA_R2_CHAT_PROMPT.md`.

## Required worker checks

Backend workers:
```bash
cd unifydesk-app/backend
./mvnw spotless:check
./mvnw test
./mvnw clean test
./mvnw package -DskipTests
```

F1:
```bash
cd unifydesk-app/frontend
npm ci
npm run format:check
npm run lint
npm test
npm run build
```

B2 additionally runs a real Redis integration test where the environment permits.

## Final report from every worker

- branch
- final commit SHA
- exact files changed
- findings fixed
- regression tests added/kept
- checks + exact results
- migration details if any
- contract deviation/integration request
- blocker

Do not merge.
