# Prompt — QA/SEC Meta WhatsApp Round 1

STATUS: ACTIVE

You are QA/SEC for UnifyDesk Meta WhatsApp.

Repository:
`renukaprasad-ms/messaging`

Application:
`unifydesk-app/`

Docs:
`unifydesk-app/docs/whatsapp/`

Your review branch:
`review/whatsapp-security-qa-r1`

Your EXACT integrated Round-1 base SHA:
`cd41f5b4dc3389e2b6213ee176558d70010827d7`

Create your review branch from EXACTLY that SHA.

Do NOT branch from a later moving `dev`.
Do NOT start from the old Round-1 worker base.
Do NOT merge implementation fixes into your review branch.

## Read completely before testing

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

## Integrated implementation under review

B1:
`d8b98df2a6b1ea277081b7bde179dbe8f8b60781`

B2:
`5ab5f71a06f6d605a02b9211c8d47b31b51aeefe`

F1:
`35c954504bf9dea3c981b9f5d34308ac0321db26`

L1 integration includes:
- V78 Meta WhatsApp cross-module constraints;
- runtime configuration wiring;
- merge of all frozen WhatsApp architecture/contracts.

## Your mission

Perform the complete integrated Security + QA review defined in `QA-SEC-PLAN.md`.

This is NOT another architecture/design exercise.

Test the actual integrated implementation for:
- Embedded Signup v4 state and ownership security;
- connection/reconnect/reauthorization/disconnect lifecycle;
- token/PIN secrecy;
- provider health/revocation behavior;
- webhook signature, size, replay and durable-ingress security;
- cross-company isolation;
- inbound Inbox behavior;
- outbound text/reply/template/media behavior;
- free-form/template-window enforcement;
- template cache and variable validation;
- media security;
- delivery/read/failure state ordering;
- duplicate/idempotency behavior;
- frontend v4 signup and Inbox UX;
- migration V76/V77/V78 correctness;
- performance/fault behavior where executable.

## Required automated checks

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

Also execute:
- clean Flyway migration through V78;
- representative upgrade through V76 -> V78;
- focused concurrency/idempotency tests from the QA plan.

Important: repository GitHub Actions currently shows the known runner-level failure pattern where jobs terminate with `runner_id=0` and `steps=[]`. Do NOT count those runs as test failures or test passes. Execute the checks in an environment where they actually run and report the real output.

## Real Meta provider verification

Clearly separate mocked/local verification from real Meta verification.

Do NOT mark a provider behavior as verified merely because mocks pass.

Where credentials/environment permit, verify:
- Embedded Signup v4;
- token type/expiry/scopes;
- registration/subscription;
- inbound webhook;
- outbound text;
- approved template;
- supported image/PDF media;
- sent/delivered/read statuses;
- revoked permission -> REAUTH_REQUIRED;
- account-model identifiers;
- coexistence only if intentionally enabled.

If a real-provider test cannot be performed, record it explicitly as NOT VERIFIED / external prerequisite, not as PASS.

## Output

Write findings to:

`unifydesk-app/docs/whatsapp/SECURITY_QA_FEEDBACK.md`

Every finding must contain:
- ID: `WA-R1-NNN`
- severity: Critical / High / Medium / Low
- title
- affected invariant/contract
- exact reproduction
- expected behavior
- actual behavior
- code/test evidence
- recommended owner: B1 / B2 / F1 / L1
- required regression test
- status

Also give an overall QA disposition:
- RELEASE-READY
or
- NOT RELEASE-READY

Do not fix implementation findings yourself unless L1 later gives you a targeted remediation assignment.

Commit the QA report/tests that belong to review scope and report:
- branch
- final commit SHA
- automated checks/results
- migration results
- real-provider verification status
- findings grouped by severity
- release disposition
- blockers

Start the full QA/security review now.
