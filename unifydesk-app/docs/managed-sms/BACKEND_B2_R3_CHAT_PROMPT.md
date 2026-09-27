You are B2 — Backend Runtime Engineer for UnifyDesk Managed SMS Round 3.

Repository: renukaprasad-ms/messaging
Application: unifydesk-app/
Branch: fix/managed-sms-cta-parity-r3
EXACT frozen Round-3 base SHA: ffb6894673f473dc7be80b5870129804807bb36c

Create your branch from exactly that SHA.
Do not branch from later moving dev.

Before coding read:
- docs/managed-sms/SECURITY_QA_FEEDBACK.md
- docs/managed-sms/ROUND3_TASKS.md
- docs/managed-sms/ROUND2_TASKS.md
- docs/managed-sms/SECURITY.md

Round 3 is intentionally tiny.

You own ONLY the residual MS-SMS-R1-003 CTA host/port parity defect.

Problem:
B1 preview correctly rejects non-default ports in HOST_ALLOWLIST mode, but B2 runtime currently accepts them.

Required behavior:
- HOST_ALLOWLIST https://example.com => allow
- HOST_ALLOWLIST https://example.com:443 => allow
- HOST_ALLOWLIST https://example.com:8443 => reject
- HOST_ALLOWLIST http://example.com:80 with requireHttps=false => allow
- HOST_ALLOWLIST http://example.com:8080 => reject
- EXACT_VALUES behavior remains unchanged; an explicitly approved exact URL containing a non-default port can still match exactly.

Read the exact semantics and test matrix in ROUND3_TASKS.md and implement them exactly.

You may modify only:
- backend/src/main/java/com/messaging/managedsms/runtime/ManagedSmsTemplateService.java
- backend/src/test/java/com/messaging/managedsms/runtime/**
- the existing QA CTA parity test only to add coverage, never to weaken the failing assertion.

Do not:
- modify B1 control code;
- add a migration;
- change application.yaml/.env.example;
- modify frontend;
- reopen external provider blockers;
- broaden the CTA contract with allowedPorts.

Make the existing QA parity test pass by fixing production code.

Run the focused CTA/runtime tests and backend Spotless/tests if the environment permits.

Commit everything to your branch and report:
- branch
- final commit SHA
- exact files changed
- MS-SMS-R1-003 fix summary
- tests run/results
- any blocker.

Do not merge.
