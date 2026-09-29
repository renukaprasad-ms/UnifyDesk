# QA/SEC Prompt — Meta WhatsApp Round 3 Final

STATUS: ACTIVE

You are QA/SEC for the final UnifyDesk Meta WhatsApp verification.

Repository:
`renukaprasad-ms/messaging`

Application:
`unifydesk-app/`

Your review branch:
`review/whatsapp-security-qa-r3`

Your EXACT final integrated base SHA:
`8738d9f7daafb878552eef70f1730ed6d8332a6c`

Create your review branch from EXACTLY that SHA.

Do NOT branch from later moving `dev`.
Do NOT modify implementation code in the QA branch.

## Read first

- `SECURITY_QA_FEEDBACK_R2.md`
- `ROUND3_TASKS.md`
- `ROUND2_CONTRACT.md`
- `QA-SEC-PLAN.md`
- `SECURITY.md`
- `PERFORMANCE.md`
- `MESSAGE-STATE.md`
- `INTEGRATION_STATUS.md`

## Final integrated inputs

Round-3 B2:
`3a7f3f094289d8adf9c68ada3d9be49905d6779f`

L1 integration:
- PR #191
- no new migration
- final implementation candidate SHA `8738d9f7daafb878552eef70f1730ed6d8332a6c`

## Primary mission

Retest the only residual finding:

`WA-R1-003`

Round-2 QA already passed:
- WA-R1-001
- WA-R1-002
- WA-R1-004
- WA-R1-005
- WA-R1-006
- WA-R1-007
- WA-R1-008

Do not reopen those unless the Round-3 change caused a regression.

## WA-R1-003 required verification

Verify production ordering:

TEXT:
`reserve -> requirePermit -> sendText`

TEMPLATE:
`reserve -> requirePermit -> sendTemplate`

MEDIA:
`reserve -> read media -> uploadMedia -> persist media link -> requirePermit -> sendMedia`

For MEDIA:
- permit is acquired after upload, immediately before actual Meta message submission;
- a slow upload cannot consume a stale earlier rate window;
- local rate rejection after upload causes zero `sendMedia`;
- Redis/limiter infrastructure failure after upload causes zero `sendMedia`;
- both failures become durable FAILED, not PENDING or UNKNOWN;
- safe error code is preserved;
- realtime status update is published;
- duplicate same clientRequestId returns the existing FAILED result;
- duplicate request performs zero second upload and zero second send;
- provider 429 after sendMedia still imposes shared Retry-After cooldown.

Keep and run:
`MetaWhatsAppRound2RateLimitQaTests.mediaPermitIsAcquiredAfterUploadImmediatelyBeforeMessageSubmission`

Also run the Round-3 focused rate-limit tests.

## Executable verification

Backend:
```bash
cd unifydesk-app/backend
./mvnw spotless:check
./mvnw test
./mvnw clean test
./mvnw package -DskipTests
```

Frontend final regression:
```bash
cd unifydesk-app/frontend
npm ci
npm run format:check
npm run lint
npm test
npm run build
```

Migration:
- clean Flyway V1 -> V79;
- representative V75/V78 -> V79 upgrade.

Redis:
- multi-instance same-phone aggregate limiter;
- independent different-phone limiter;
- shared cooldown;
- Redis outage fail-closed;
- MEDIA delayed-upload ordering.

Broader regression:
- rerun the previous focused WA-R1 tests;
- confirm durable whatsappStatus;
- webhook structural validation;
- granular-scope intersection;
- media inbound idempotency;
- stale PENDING -> UNKNOWN;
- timestamp ordering;
- URL-button variables.

## CI caveat

Previous GitHub Actions jobs repeatedly failed before any step started:
- `runner_id=0`
- `steps=[]`

Treat that as infrastructure failure only.

Do not call unexecuted suites PASS.

If runners still fail, report EXECUTION BLOCKED separately from source/contract verification.

## Real Meta provider verification

Clearly separate:
- LOCAL/MOCK VERIFIED
- REAL META VERIFIED
- NOT VERIFIED

Where credentials permit, verify:
- Embedded Signup v4;
- registration/subscription;
- inbound webhook;
- text/template/media send;
- sent/delivered/read status;
- Retry-After behavior if safely reproducible;
- revoked permission;
- current account model identifiers.

Do not claim real-provider verification from mocks.

## Output

Write:
`unifydesk-app/docs/whatsapp/SECURITY_QA_FEEDBACK_R3_FINAL.md`

Report:
- WA-R1-003 PASS or FAIL with exact evidence;
- any regression in previously passed findings;
- backend results;
- frontend results;
- Flyway results;
- Redis results;
- real Meta provider status;
- any new finding as `WA-R3-NNN`;
- final disposition.

Final disposition:
- `RELEASE-READY`
or
- `NOT RELEASE-READY`

Commit the QA report/review-only tests and report:
- branch
- final SHA
- tests/results
- WA-R1-003 result
- regressions
- new findings
- real-provider status
- final disposition
- blockers

Start final QA now.
