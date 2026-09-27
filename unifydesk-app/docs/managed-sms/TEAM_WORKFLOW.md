# UnifyDesk Managed SMS — Multi-Chat Workflow

This workflow mirrors the successful Managed Email process while adapting ownership for India DLT and SMS-specific UX.

## Round 0 — Lead / Architecture

The Lead works first and is the only role allowed to freeze shared architecture and contracts.

The Lead must:

- inspect current Broadcast SMS/backend/frontend/provider code;
- inspect `unifydesk-app/docs/managed-email/` for coordination patterns;
- research current official TRAI/DLT and MSG91 requirements;
- verify the feasibility and prerequisites of using a platform-owned MSG91 account for multiple customer entities;
- define the customer onboarding model;
- freeze data model, API contracts, runtime delivery contract, compliance rules, security boundaries and exact file ownership;
- write worker prompts so B1, B2, UX1 and F1 can begin without waiting on one another.

Implementation starts only after Round-0 docs are merged into `dev`.

## Round 1 — Parallel implementation/design

All implementation branches must be created from the same frozen base SHA published by the Lead.

### B1 — Backend Control Plane & Compliance

Suggested branch:

`feature/managed-sms-control-plane`

Owns the managed-SMS company/compliance control plane assigned by Lead.

Must not edit B2 runtime/event code or frontend code.

### B2 — Backend Delivery, Events, Metering & Safety

Suggested branch:

`feature/managed-sms-delivery-events`

Owns managed runtime sending, webhook/event processing, metering, provider/shared-account safeguards and runtime tests assigned by Lead.

Must not edit B1 control-plane ownership or frontend code.

### UX1 — UI/UX Designer

Suggested branch:

`design/managed-sms-ux`

Owns UX/design documentation and design assets/specs only unless the Lead explicitly assigns isolated non-code artifacts.

UX1 should complete the primary journey/spec early, but F1 must not be blocked waiting for UX1: the Lead’s frozen API/flow contract is sufficient to begin implementation. UX improvements can be applied through a later review pass without changing backend contracts.

### F1 — Frontend Engineer

Suggested branch:

`feature/managed-sms-ui`

Owns frontend implementation and tests.

F1 consumes the frozen API contract and Lead-defined journey. F1 must not modify backend source.

## Integration

Recommended integration order:

1. B1
2. B2
3. F1
4. UX1 design review findings applied by F1 or Lead as appropriate
5. Lead integration review

The Lead owns cross-worker glue and conflict resolution.

Workers must not silently change frozen shared contracts or edit another worker’s owned files.

## QA / Security review

QA/SEC begins only after Round-1 implementation is integrated into one candidate.

Suggested branch:

`review/managed-sms-security-qa-r1`

QA/SEC should review at least:

- company/tenant isolation;
- IDOR and authorization across DLT profiles, headers and templates;
- inability to inject another customer’s PE/header/template identifiers;
- arbitrary raw SMS bypass around approved templates;
- MSG91 credential secrecy;
- webhook authenticity/replay/idempotency;
- template-variable validation;
- DLT/compliance state bypasses;
- DND/consent/opt-out behavior applicable to the chosen message classes;
- shared MSG91 rate/balance exhaustion;
- per-company quota and billing correctness;
- duplicate/ambiguous-send behavior;
- abuse/spam controls;
- admin/manual verification privilege boundaries;
- logs/metrics privacy;
- end-to-end happy, pending, rejected, suspended and provider-failure flows.

QA/SEC records findings in a Lead-defined feedback file. It does not redesign architecture during the review.

## Round 2+

The Lead triages every QA/SEC finding and creates exact non-overlapping remediation tasks for the appropriate worker.

Repeat integration and QA/SEC until release criteria are satisfied.

## Conflict prevention

- Lead is sole writer of shared frozen architecture/task/decision docs during each planning round.
- B1/B2 get separate package/migration ownership wherever possible.
- If both backend workers need one existing hot file, Lead assigns exactly one writer; the other consumes an interface/contract.
- F1 is sole frontend-code owner.
- UX1 owns design/UX specs, not application source.
- QA/SEC is sole writer of the security/QA findings document during review.
- No worker merges its own PR unless explicitly instructed.
