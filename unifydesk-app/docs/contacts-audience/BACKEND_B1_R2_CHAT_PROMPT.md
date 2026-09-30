# B1 Prompt — Contacts Core Round-2 Remediation

You are B1 for targeted Contacts/Audience Round-2 remediation.

Repository:
`renukaprasad-ms/messaging`

Application:
`unifydesk-app/`

Your EXACT frozen Round-2 base SHA is:

`f6a04c4596240d0c43bde6ef83c66619abbd9a02`

Create:

`fix/contacts-core-r2`

from EXACTLY that SHA.

Do NOT branch from moving `dev`.
Do NOT create another migration.
Do NOT modify B2, F1, Lead integration, Broadcast, Inbox, Ads or provider runtime code.

Read from current `dev` before coding:

- `unifydesk-app/docs/contacts-audience/SECURITY_QA_FEEDBACK.md`
- `unifydesk-app/docs/contacts-audience/ROUND2_TASKS.md`
- `SECURITY.md`
- `PERFORMANCE.md`
- `API-CONTRACT.md`

Your ONLY Round-2 finding is:

`CA-R1-010`

## Problem

`ContactCursorCodec` currently reads a Contacts cursor signing secret and falls back to the channel credential master key. If neither is configured it generates a random process-local key.

That makes cursors invalid across backend replicas and after restart.

Production pagination signing state must be stable across replicas.

## Required fix

Keep signed/tamper-resistant cursors.

Use stable shared configured secret material only.

Preferred precedence:
1. dedicated Contacts cursor HMAC key when configured;
2. documented existing shared platform secret only if its use is already an accepted platform fallback.

If no stable secret is available, fail configuration/startup clearly.

Do NOT silently generate a new random production key.

Do NOT log secret values.

Do NOT weaken cursor signature validation.

Do NOT fall back to an unsigned/base64-only cursor.

## Configuration quality

Make configuration behavior explicit and easy to operate.

A missing required key should produce a clear startup/configuration failure rather than intermittent pagination failures later.

Avoid hidden static/global mutable state.

## Required tests

At minimum:

1. two codec instances using the same configured key accept each other's cursors;
2. cursor generated before a simulated restart remains valid with the same key;
3. different configured keys reject each other's cursors;
4. tampered cursor is rejected;
5. missing/blank stable secret produces the intended explicit configuration failure;
6. no secret material appears in exception/log output;
7. existing cursor filter/sort binding behavior remains intact.

## Complexity / production design

Cursor encode/decode must remain O(1) relative to Contact dataset size.

Do not add database lookups, cache dependencies or distributed state merely to validate a cursor.

Use small focused code with clear names and no unnecessary abstraction.

## Required checks

From `unifydesk-app/backend` run when environment permits:

`./mvnw spotless:check`
`./mvnw test`
`./mvnw clean test`
`./mvnw package -DskipTests`

If commands cannot execute, report `EXECUTION BLOCKED`; never claim PASS.

## Completion

Commit everything to the existing Round-2 branch.
Open a PR to `dev`.
Do not merge.

Report:
- branch
- final SHA
- PR
- exact files changed
- configuration behavior
- tests added
- commands actually executed
- any blocker or deviation

Start now.
