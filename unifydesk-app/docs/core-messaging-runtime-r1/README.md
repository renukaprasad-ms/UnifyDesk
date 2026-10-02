# Core Messaging E2E Runtime & Provider Verification R1

## Purpose

This round verifies the executable UnifyDesk messaging journey against an exact frozen production-source baseline. It is a verification + narrow-remediation initiative, not a feature expansion round.

Canonical journey:

`Workspace -> Contacts import -> Consent/Preferences -> Lists/Segments -> Broadcast creation -> Audience preview -> Audience freeze -> Send/Schedule -> Provider -> Delivery events -> Notifications -> Inbox -> Contact linkage`

## Frozen audit baseline

- Repository: `renukaprasad-ms/UnifyDesk`
- Branch inspected live before audit: `dev`
- Exact frozen SHA: `850ce7ad2874d20070067d46d8604ddc2bdd2c99`
- Commit: `test(contacts): keep segment conditions after preview`
- Audit branch: `review/core-messaging-runtime-provider-verification-r1`

Do not substitute moving `dev` for this SHA when reproducing the evidence in this directory.

## Verification vocabulary

Every item is classified as one of:

- **PASS** — executable evidence proved the stated behavior.
- **FAIL** — executable evidence or current source proves the required behavior is violated.
- **BLOCKED** — an external/provider/environment dependency prevents execution.
- **NOT EXECUTED** — the verification has not actually been run.

Source review is supporting evidence only. It is not promoted to runtime/provider PASS.

## Current high-level disposition

- Previous Core Messaging R1 source remediation: accepted by independent SEC/QA at `353069f922040be922ec353961386fb962d8d187`.
- Post-QA production changes reviewed on the frozen baseline:
  - Managed Email post-acceptance reconciliation now passes the matched permanent-bounce provider event into suppression feedback.
  - Segment HTTP wire model/mapper use Jackson 3 `tools.jackson.*` types.
  - Inbox -> Contacts prefill is present.
  - Segment Preview uses the server-normalized AST without replacing editor-visible filter state.
- Exact frozen SHA has no GitHub Actions workflow run returned by GitHub at audit time: current executable CI result is **NOT EXECUTED**.
- Real SES/MSG91/Meta provider execution was not available through this repository audit and remains **NOT EXECUTED/BLOCKED pending runtime assets**.
- Meta customer Embedded Signup remains separately **BLOCKED BY META** per the initiative handoff.
- Gmail/Microsoft/IMAP-SMTP/SMTP are historical `BroadcastSenderProvider` values and are intentionally rejected by the modern Broadcast sender contract. Provider-4 regression against those mailbox providers is therefore a current source/scope mismatch, not a credential-only blocker.

## Documents

- `RUNTIME-BASELINE.md` — exact source/runtime baseline and post-QA deltas.
- `E2E-VERIFICATION-MATRIX.md` — executable verification plan and current classification.
- `PROVIDER-READINESS.md` — provider prerequisites, assets and blockers.
- `STATE-TRANSITIONS.md` — actual Broadcast/provider durable states.
- `TEST-DATA.md` — deterministic fixtures and cleanup.
- `EXECUTION-RESULTS.md` — evidence actually obtained in this audit.
- `REMEDIATION-PLAN.md` — narrow follow-up decisions; no manufactured worker tasks.

## Stale PR housekeeping

The following stale PRs were inspected against modern `dev` and closed as superseded, not merged:

- #197 — superseded Contacts/Audience architecture.
- #218 — superseded Contacts import mapping work.
- #244 — superseded original Core Messaging QA; final remediation verification was #248 and was merged.
