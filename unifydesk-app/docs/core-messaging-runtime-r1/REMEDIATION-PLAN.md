# Remediation Plan

## Principle

Do not manufacture B1/B2/F1 work. Create implementation assignments only when evidence proves a production-source defect that belongs in this narrow round.

## MAIN decision — Provider-4 scope resolved

MAIN selected the scope-correction option.

Provider-4 canonical direct Email regression means the modern supported customer-managed bulk Email providers:

1. Amazon SES
2. SendGrid
3. Azure Communication Services Email
4. MSG91 Email

Gmail, Microsoft, IMAP/SMTP and SMTP mailbox providers are outside Provider-4 verification for this round. No production remediation is required merely to make those historical mailbox enum values launchable as modern Broadcast senders.

The prior `CMR1-AUDIT-001` requirement/scope mismatch is therefore **RESOLVED BY SCOPE DECISION**, not by source modification.

No B1/B2/F1 implementation assignment is created for mailbox-backed Broadcast.

## Current runtime finding status

No production source defect has been proven in the regenerated executable round yet.

Current blocking evidence is environmental:

- exact source SHA is frozen at `f2760fe0a11ad571f984636a65304a64b77459ac`;
- GitHub Actions run `37011044661` reaches attempt 2 but all jobs have `steps = null`;
- this execution session has no authenticated k3s/server channel to prove the deployed backend image/build is the frozen SHA;
- public `api.unifydesk.in` cannot be resolved/reached from this execution environment.

Therefore:

`WORKERS REQUIRED: B2 + F1`

Do not create B1/B2/F1 remediation work until executable runtime evidence demonstrates a genuine source defect.

## CMR1-RUNTIME-001A — Recipient-facing Email marketing unsubscribe action missing

### Severity

**HIGH**

### Required backend contract

Use one canonical recipient Email action above providers.

The action token must be opaque or cryptographically protected and must bind, at minimum, the server-owned subject needed to validate:

- company;
- Contact;
- Contact identity;
- `EMAIL`;
- `MARKETING`;
- action/token version;
- expiry/non-replay identity as appropriate.

Do not use editable URL IDs as authorization.

Recommended narrow shape:

- dedicated recipient-action token service with a dedicated key/configuration, not provider credentials;
- public idempotent unsubscribe POST endpoint;
- optional safe validation/metadata endpoint only if required by the confirmation page;
- source `CONTACT_ACTION`;
- actor type `CONTACT`;
- current preference mutation through `ContactPreferenceService`, not a second unsubscribe table;
- stable idempotency key derived from the action token nonce/identity without logging the raw token.

Repeated action returns success-equivalent behavior.

Tampered/cross-company/cross-Contact/cross-identity tokens must never mutate and should use generic safe responses.

### Email content/transport contract

For canonical Broadcast Email MARKETING sends:

- include a recipient-visible Unsubscribe action in text and HTML content;
- add `List-Unsubscribe`;
- add `List-Unsubscribe-Post: List-Unsubscribe=One-Click` where the provider transport supports the required custom headers;
- keep the core semantic metadata provider-independent;
- adapt at each transport boundary.

Current provider capability review:

- AWS SES v2: project uses SDK 2.54.17; current SES v2 simple `Message` supports custom message headers, so raw MIME is not required merely for these headers.
- SendGrid: Mail Send supports custom headers; current gateway does not populate them.
- Azure Communication Services Email Java: `EmailMessage.setHeaders(Map<String,String>)` exists; current gateway does not populate headers.
- MSG91 Email: current UnifyDesk request is template-based and has no custom unsubscribe metadata. MSG91 may provide a provider-managed unsubscribe link, but that does not replace the canonical UnifyDesk Contact action. Preserve provider-template semantics and expose/verify the canonical action through the narrowest supported provider adaptation; if provider headers cannot carry one-click, classify that transport capability explicitly rather than inventing it.

Do not touch transactional notification/OTP/password-reset/security mail pipelines.

## CMR1-RUNTIME-001B — Preference projection is arrival-order, not event-time ordered

### Severity

**HIGH**

Keep immutable event insertion/idempotency.

Change effective projection mutation so:

1. incoming event is validated and appended once;
2. compare incoming `occurredAt` to the current projection;
3. strictly newer evidence may replace the current projection;
4. strictly older evidence never regresses the current projection;
5. equal-time conflicting evidence follows one explicit deterministic safety ordering (prefer fail-closed semantics such as `OPTED_OUT > UNKNOWN > OPTED_IN` unless existing domain policy proves another ordering);
6. genuinely later valid re-consent remains supported.

Do not equate ingestion time with consent occurrence time.

### Required regression cases

- old import cannot resurrect newer recipient opt-out;
- genuinely later valid re-consent becomes effective;
- duplicate unsubscribe remains idempotent;
- duplicate/import replay remains deterministic;
- projection/event history remains company/identity/channel/purpose scoped;
- Contact merge behavior is reviewed for consistency but not broadened unless tests expose a conflict.

## Worker ownership

### B2

Prompt:

`unifydesk-app/docs/core-messaging-runtime-r1/BACKEND_B2_R1_UNSUBSCRIBE_REMEDIATION_CHAT_PROMPT.md`

Owns CMR1-RUNTIME-001A and CMR1-RUNTIME-001B backend/source contract.

### F1

Prompt:

`unifydesk-app/docs/core-messaging-runtime-r1/FRONTEND_F1_R1_UNSUBSCRIBE_REMEDIATION_CHAT_PROMPT.md`

Owns only the minimal public recipient confirmation/result page.

### B1

**No assignment.**

Both workers must branch production work from:

`f2760fe0a11ad571f984636a65304a64b77459ac`

They must not branch from the evidence PR head, moving `dev`, or each other.

## Environment/provider blockers

These do not justify source changes:

- live `messaging-env` secret values not visible to repository audit;
- real SES account/domain/sender/SNS state not executed;
- MSG91 DLT/header/template/provider assets not executed;
- Meta customer onboarding blocked by Meta;
- test WABA availability not proven.

Classify them as environment/provider blockers or NOT EXECUTED after checking the deployed runtime.

## Next execution sequence

1. Prove the deployed k3s/backend build is exactly `f2760fe0a11ad571f984636a65304a64b77459ac`; CI is already classified BLOCKED because no steps execute.
2. Inspect deployed backend environment variable **names/presence only**, never secret values.
3. Execute Contact import + consent + List/Segment + Preview/freeze tests.
4. Execute Managed Email end-to-end first.
5. Execute Managed SMS end-to-end second.
6. Execute Meta test-WABA outbound/status/inbound path if assets are available.
7. Regression-verify Provider-4 against Amazon SES, SendGrid, Azure Email and MSG91 Email after the Managed Email/SES path.
8. Record every API ID, DB transition and provider event in `EXECUTION-RESULTS.md`.
9. If a genuine narrow source defect appears, create exactly the worker assignment needed, integrate it, freeze the resulting production SHA and hand that exact SHA to independent SEC/QA.

## Independent SEC/QA handoff gate

Do not hand off a runtime PASS until:

- exact SHA is frozen;
- executable matrix results are committed;
- blocked items are explicitly named;
- provider PASS claims contain real provider IDs/events;
- no NOT EXECUTED item is mislabeled PASS;
- any remediation has been integrated and re-frozen.
