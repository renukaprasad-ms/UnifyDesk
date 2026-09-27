You are B2 — Backend Delivery / Events / Metering / Safety Engineer for UnifyDesk Managed SMS Round 2.

Repository: renukaprasad-ms/messaging
Application: unifydesk-app/
Branch: fix/managed-sms-runtime-security-r2
EXACT frozen Round-2 base SHA: bd402ef752b6b32331969d6c0894267f1edf478e

Create your branch from exactly that SHA.
Do not branch from a later moving dev.
Do not cherry-pick B1 and do not import B1 Java classes.

Before coding read:
- docs/managed-sms/SECURITY_QA_FEEDBACK.md
- docs/managed-sms/ROUND2_TASKS.md
- docs/managed-sms/SECURITY.md
- docs/managed-sms/ARCHITECTURE.md
- docs/managed-sms/API-CONTRACT.md
- docs/managed-sms/DATA-MODEL.md

You own the B2 Round-2 findings assigned in ROUND2_TASKS.md:
- MS-SMS-R1-002
- MS-SMS-R1-003 runtime half
- MS-SMS-R1-004
- MS-SMS-R1-005
- MS-SMS-R1-006
- MS-SMS-R1-007 runtime safety
- MS-SMS-R1-008
- MS-SMS-R1-009
- MS-SMS-R1-010
- MS-SMS-R1-012 runtime half
- MS-SMS-R1-013

Implement the full assignment exactly as frozen.

Own:
- managedsms/runtime/**
- managedsms/delivery/**
- managedsms/events/**
- managedsms/metering/**
- managedsms/safety/**
- corresponding tests
- V66__managed_sms_round2_runtime_security.sql
- exact Broadcast runtime hot files needed for tuple ownership/campaign cap
- backend application.yaml
- unifydesk-app/.env.example

Do not edit:
- managedsms/control/**
- V65
- frontend
- frozen Round-2 task/security docs.

Critical requirements:
- no fake positive provider snapshot; use provider observation abstraction and fail closed when unavailable;
- CTA policyVersion=2 enforced on actual runtime values;
- delivery rechecks live freshness before every provider call;
- webhook ts replay window enforced without Instant.now fallback;
- exact duplicate callbacks remain idempotent;
- every billing/state mutation binds company+run+recipient(+attempt) before billing;
- child mode can never fall back to master AuthKey;
- shared-master mode requires explicit opt-in;
- provider messages never leak into customer fields;
- global/account/company/run rates are distinct and atomically enforced;
- absolute campaign cap is enforced;
- SHA-256 approved-body fingerprint is actually verified;
- webhook authentication is provider-account-bound;
- CUSTOMER_MANAGED MSG91 remains unchanged.

Provider capabilities that remain unverified must stay fail-closed and be reported, not invented.

Run focused tests plus backend Spotless/full tests as available.
Commit everything to your branch.

At completion report:
- branch
- final commit SHA
- files changed
- migration
- findings fixed
- tests/builds run
- provider assumptions/external blockers.

Do not merge.
