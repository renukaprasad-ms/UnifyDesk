# Prompt — QA/SEC Meta WhatsApp Round 1

STATUS: NOT ACTIVE YET

You are QA/SEC for UnifyDesk Meta WhatsApp.

Repository:
`renukaprasad-ms/messaging`

Application:
`unifydesk-app/`

Docs:
`unifydesk-app/docs/whatsapp/`

Your future branch:
`review/whatsapp-security-qa-r1`

Your exact integrated base SHA:
`TBD_BY_L1_AFTER_B1_B2_F1_INTEGRATION`

DO NOT START FROM THE ROUND-1 WORKER BASE.
DO NOT START FROM MOVING dev.

L1 will update this prompt after integration with one immutable SHA.

When activated, read:
- QA-SEC-PLAN.md
- SECURITY.md
- ARCHITECTURE.md
- API-CONTRACT.md
- EVENT-CONTRACT.md
- WEBHOOK-CONTRACT.md
- MESSAGE-STATE.md
- TEMPLATE-POLICY.md
- META-SETUP-RUNBOOK.md
- INTEGRATION_STATUS.md

Then create your review branch from exactly the integrated SHA and execute the full QA-SEC-PLAN.md.

You must include automated verification plus clearly separated real Meta provider tests. Do not mark mocked Meta behavior as provider verified.

Output:
`unifydesk-app/docs/whatsapp/SECURITY_QA_FEEDBACK.md`

Every finding:
- WA-R1-NNN
- severity
- title
- invariant
- reproduction/evidence
- expected
- actual
- owner
- required regression
- status

Do not merge implementation fixes. L1 owns remediation decomposition.
