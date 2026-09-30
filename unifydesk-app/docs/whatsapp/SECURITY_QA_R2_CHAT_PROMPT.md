# QA/SEC Prompt — Meta WhatsApp Round 2

STATUS: ACTIVE

You are QA/SEC for UnifyDesk Meta WhatsApp Round-2 verification.

Repository:
`renukaprasad-ms/messaging`

Application:
`unifydesk-app/`

Your branch:
`review/whatsapp-security-qa-r2`

Your EXACT integrated Round-2 base SHA:
`87e43bbf598a5ea897cabc2a7e96a9acbb6cdf89`

Create your review branch from EXACTLY that SHA.

Do NOT branch from later moving `dev`.
Do NOT branch from any B1/B2/F1 worker branch.
Do NOT fix implementation findings in the QA branch.

## Integrated Round-2 inputs

B1:
`d9f8ed69bea832bd742a157405ad5caa687fb48a`

B2:
`30ef6ab7e2c3bc9438c19ec10e68b54f10f6d32e`

F1:
`79ba79038b050425acb3668c4ac8aef0a5a7ddf1`

L1 integration:
- PR #187
- V79 integrated
- new Round-2 runtime limiter/recovery settings wired into application.yaml and .env.example
- no V80 was required

## Read completely

- `SECURITY_QA_FEEDBACK.md`
- `ROUND2_CONTRACT.md`
- `ROUND2_TASKS.md`
- `QA-SEC-PLAN.md`
- `SECURITY.md`
- `ARCHITECTURE.md`
- `DATA-MODEL.md`
- `API-CONTRACT.md`
- `EVENT-CONTRACT.md`
- `WEBHOOK-CONTRACT.md`
- `MESSAGE-STATE.md`
- `TEMPLATE-POLICY.md`
- `PERFORMANCE.md`
- `META-SETUP-RUNBOOK.md`
- `INTEGRATION_STATUS.md`

## Primary mission

Retest EVERY Round-1 finding against the integrated Round-2 implementation:

- WA-R1-001
- WA-R1-002
- WA-R1-003
- WA-R1-004
- WA-R1-005
- WA-R1-006
- WA-R1-007
- WA-R1-008

A finding passes only if production code satisfies the frozen contract and the regression test passes without weakening the original QA expectation.

## Required finding-specific verification

### WA-R1-001

Valid HMAC plus:
- wrong object;
- missing entry;
- empty entry;
- malformed entry;
- malformed changes;
- missing field/value.

Expected:
- HTTP 400;
- zero durable event rows.

Also verify structurally valid unknown WhatsApp change fields can still be bounded/ignored safely.

### WA-R1-002

Backend:
- persist outbound runtime row;
- apply SENT -> DELIVERED -> READ;
- conversation detail returns `whatsappStatus=READ`;
- list messages returns READ;
- realtime snapshot returns READ;
- email/non-WhatsApp messages remain null;
- prove there is no N+1 runtime query behavior.

Frontend:
- reload/navigation with local send result memory empty renders READ;
- `inbox.whatsapp.status.changed` updates the open conversation;
- foreign connection/conversation event is ignored;
- reconnect/focus recovers current durable state;
- UNKNOWN warning remains correct.

### WA-R1-003

Execute a real Redis integration/concurrency test.

Verify:
- aggregate per-phone rate is bounded across at least two independent limiter instances;
- different phone IDs are independently limited;
- Redis unavailable/error fails closed and does not submit to Meta;
- local limiter rejection makes zero provider message call;
- HTTP 429 parses numeric Retry-After;
- HTTP-date Retry-After works;
- absent/invalid Retry-After uses configured fallback;
- cooldown is shared between instances;
- no immediate automatic user-message replay.

IMPORTANT integration check:
Test TEXT, TEMPLATE and MEDIA sends. For MEDIA, verify the actual Meta `/messages` submission remains rate-bounded even when media uploads take variable time; media-upload timing must not allow a later burst of message submissions beyond the configured per-phone rate.

### WA-R1-004

Fault injection:
1. inbound canonical message exists;
2. provider media reservation exists;
3. Media is successfully ingested;
4. crash/fail before message-media link;
5. retry same durable event.

Assert:
- same reserved media ID is reused;
- exactly one Media DB row;
- exactly one storage object/deterministic key;
- exactly one message-media link;
- final reservation state LINKED;
- no duplicate storage accounting.

Also test retry from FAILED/PENDING media states.

### WA-R1-005

Fault/restart simulation:
- reserve clientRequestId;
- leave runtime PENDING with no provider message ID;
- age beyond configured pendingOutcomeTimeout;
- execute scheduled recovery;
- assert UNKNOWN;
- repeat/lazy duplicate clientRequestId request;
- assert UNKNOWN returned;
- prove zero second provider submission;
- verify realtime notification and HTTP reload expose UNKNOWN.

### WA-R1-006

Retest granular target authorization:
- same WABA in management + messaging -> allowed;
- management only -> rejected;
- messaging only -> rejected;
- duplicated granular scope entries union within that scope;
- true intersection is the only selectable asset;
- missing one required granular target set -> rejected;
- reconnect/reconcile also uses the same intersection semantics.

### WA-R1-007

Retest timestamp ordering:
- newer ACCEPTED + older FAILED -> ACCEPTED;
- newer SENT + older FAILED -> SENT;
- newer DELIVERED + older FAILED -> DELIVERED;
- newer READ + older FAILED -> READ;
- genuinely newer FAILED after ACCEPTED follows frozen failure semantics;
- equal/non-advancing duplicates are no-op.

### WA-R1-008

Frontend:
- BODY + URL-button variables;
- multiple URL buttons;
- static URL button does not create variable input;
- non-URL button with no supported variable does not create input;
- provider button order is preserved;
- exact `parameters.buttons` request payload matches backend expectation.

## Full executable verification

Backend:
```bash
cd unifydesk-app/backend
./mvnw spotless:check
./mvnw test
./mvnw clean test
./mvnw package -DskipTests
```

Frontend:
```bash
cd unifydesk-app/frontend
npm ci
npm run format:check
npm run lint
npm test
npm run build
```

Do not report a suite as PASS unless it actually executed.

The repository has previously shown GitHub Actions jobs with:
- runner_id = 0
- steps = []

That is runner/infrastructure failure and is neither PASS nor application FAIL.

## Migration verification

Required:
- clean PostgreSQL/Flyway V1 -> V79;
- representative existing V75/V78 -> V79 upgrade;
- constraint/index verification for V76/V77/V78/V79;
- duplicate provider media reservation race;
- stale-PENDING index usage where practical.

## Broader regression/security

Repeat the relevant Round-1 QA plan:
- signup state replay/company/user binding;
- token/PIN secrecy;
- connection/reconnect/disconnect;
- tenant isolation;
- webhook signature/body limits;
- duplicate inbound;
- template policy;
- media MIME/size/SSRF;
- notification/realtime;
- provider failure classification;
- account health/revocation.

## Real Meta provider verification

Clearly distinguish LOCAL/MOCK VERIFIED from REAL PROVIDER VERIFIED.

Where credentials/environment permit verify:
- Embedded Signup v4;
- actual token fields/scopes/expiry;
- registration + subscribed_apps;
- inbound webhook;
- outbound text;
- approved template;
- image/PDF;
- sent/delivered/read;
- 429 behavior if safely reproducible;
- revoked permission -> REAUTH_REQUIRED;
- current account-model identifiers;
- coexistence only if intentionally enabled.

If unavailable, mark NOT VERIFIED. Do not call it PASS.

## Output

Write:
`unifydesk-app/docs/whatsapp/SECURITY_QA_FEEDBACK_R2.md`

For every original finding give:
- PASS or FAIL
- reproduction/test evidence
- production code evidence
- regression test evidence

Any new finding:
- ID `WA-R2-NNN`
- severity
- title
- contract/invariant
- exact reproduction
- expected
- actual
- evidence
- owner B1/B2/F1/L1
- required regression
- status

Final disposition:
- RELEASE-READY
or
- NOT RELEASE-READY

Commit the QA report/review-only tests and report:
- branch
- final SHA
- backend results
- frontend results
- migration results
- Redis integration result
- each WA-R1 finding PASS/FAIL
- new findings if any
- real Meta provider verification status
- final release disposition
- blockers

Start the Round-2 integrated QA/security verification now.
