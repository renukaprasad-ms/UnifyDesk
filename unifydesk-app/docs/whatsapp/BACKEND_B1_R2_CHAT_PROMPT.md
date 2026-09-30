# B1 Prompt — Meta WhatsApp Round 2

You are B1 for UnifyDesk Meta WhatsApp Round-2 remediation.

Your EXACT worker base SHA:
`fac085504dce38ac4c8f6f04e2f454773bdb811e`

Your branch:
`fix/whatsapp-control-plane-r2`

Create your branch from exactly that SHA. Do not branch from later moving `dev`.

Read from the current `dev` docs before coding:
- `unifydesk-app/docs/whatsapp/SECURITY_QA_FEEDBACK.md`
- `unifydesk-app/docs/whatsapp/ROUND2_CONTRACT.md`
- `unifydesk-app/docs/whatsapp/ROUND2_TASKS.md`
- original SECURITY/API/ARCHITECTURE contracts as needed.

Your ONLY Round-2 finding is:

`WA-R1-006 — WABA authorization uses the union of granular-scope targets instead of requiring the selected asset in every required scope`

## Required fix

In the control-plane token inspection/asset-selection flow:
- keep top-level required-scope validation;
- build a target set separately for each required granular scope;
- union duplicate entries for the same scope;
- require both `whatsapp_business_management` and `whatsapp_business_messaging` target sets;
- compute their intersection;
- only WABAs in that intersection are selectable/acceptable;
- a WABA present in only one required scope must fail with the existing safe asset-not-granted behavior;
- multiple valid intersected WABAs must continue requiring explicit selection.

Do not weaken or delete the QA regression test:
`MetaWhatsAppRound1ControlPlaneQaTests.selectedWabaMustBeGrantedByEveryRequiredGranularScope`

Add focused B1 production tests for:
1. same WABA in both scopes -> allowed;
2. only management -> rejected;
3. only messaging -> rejected;
4. duplicate scope entries union correctly before intersection;
5. multiple targets where only one ID intersects;
6. requested WABA outside the intersection -> rejected.

## Ownership

You may modify:
- `MetaWhatsAppConnectionService.java`
- directly related B1 connection tests
- tiny B1 helper code if useful

Do NOT modify:
- B2 runtime/webhook/Inbox/Media code
- frontend
- Flyway
- Ads
- Broadcast
- frozen docs

Run:
```bash
cd unifydesk-app/backend
./mvnw spotless:check
./mvnw test
./mvnw clean test
./mvnw package -DskipTests
```

Commit everything and report:
- branch
- final SHA
- files changed
- WA-R1-006 implementation
- tests/checks/results
- any blocker/deviation

Do not merge.
