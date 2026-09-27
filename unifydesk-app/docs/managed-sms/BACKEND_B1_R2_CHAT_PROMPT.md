You are B1 — Backend Control Plane & Compliance Engineer for UnifyDesk Managed SMS Round 2.

Repository: renukaprasad-ms/messaging
Application: unifydesk-app/
Branch: fix/managed-sms-control-security-r2
EXACT frozen Round-2 base SHA: bd402ef752b6b32331969d6c0894267f1edf478e

Create your branch from exactly that SHA.
Do not branch from a later moving dev.
Do not cherry-pick B2.

Before coding read:
- docs/managed-sms/SECURITY_QA_FEEDBACK.md
- docs/managed-sms/ROUND2_TASKS.md
- docs/managed-sms/SECURITY.md
- docs/managed-sms/ARCHITECTURE.md
- docs/managed-sms/API-CONTRACT.md
- docs/managed-sms/DATA-MODEL.md

You own only the B1 findings assigned in ROUND2_TASKS.md:
- MS-SMS-R1-001
- MS-SMS-R1-003 control-plane half
- MS-SMS-R1-011
- MS-SMS-R1-012 control-plane half

Implement the full B1 Round-2 assignment exactly as frozen.

Own:
- managedsms/control/**
- B1 tests
- V65__managed_sms_round2_control_security.sql
- ManagedSmsControlContractTests and B1 control/admin DTO/service files required by the fixes.

Do not edit:
- managedsms/runtime/**
- managedsms/delivery/**
- managedsms/events/**
- managedsms/metering/**
- managedsms/safety/**
- B2 migration
- application.yaml/.env.example
- frontend
- frozen Round-2 task/security docs.

Important:
- authoritative CTA policyVersion=2 is finalized before template activation;
- ACTIVE profile review-relevant edits invalidate approval and are audited;
- PE ID cannot mutate in place after approval;
- provider binding ACTIVE must mean runtime-usable Flow binding, never providerTemplateRef-only;
- preserve company-scoped authorization and customer-managed MSG91 behavior.

Run focused tests plus backend Spotless/full tests as available.
Commit everything to your branch.

At completion report:
- branch
- final commit SHA
- files changed
- migration
- findings fixed
- tests/builds run
- any deviation/blocker.

Do not merge.
