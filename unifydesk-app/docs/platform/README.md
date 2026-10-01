# UnifyDesk Platform Documentation

This directory is the platform-level source of truth for UnifyDesk.

Baseline audit date: 2026-10-01  
Audited `dev` SHA: `d9717f93b3e10a308da9636750a4df6926bc6016`

## Authority order

When documents disagree, use this order:

1. implementation and runtime configuration at the exact audited SHA;
2. this platform documentation layer;
3. current feature contracts and architecture documents linked below;
4. historical QA reports and round-specific handoff documents;
5. older READMEs, prompts, plans and status files.

Historical QA evidence must not be rewritten to look current. A historical report describes what was reviewed at its frozen SHA, not necessarily the current `dev` state.

## Evidence language

Platform and future feature reviews use these terms:

- **SOURCE REVIEWED** — implementation/configuration was inspected.
- **UNIT TEST EXECUTED** — unit test command actually ran for the stated SHA.
- **INTEGRATION TEST EXECUTED** — integration test command actually ran.
- **MIGRATION TEST EXECUTED** — Flyway/database migration path actually ran.
- **PROVIDER TEST EXECUTED** — a live provider test actually ran.
- **LOAD TEST EXECUTED** — load/concurrency test actually ran.
- **EXECUTION BLOCKED** — required executable verification could not run.
- **BLOCKED EXTERNAL** — an external provider, approval, credential, compliance or environment dependency blocks verification/release.

A workflow definition, test source, mock, code review, or GitHub job with zero executed steps is not executable test evidence.

## Platform documents

- [System overview](SYSTEM-OVERVIEW.md) — current architecture and major user flows.
- [Module status](MODULE-STATUS.md) — module-by-module implementation and verification state.
- [Production readiness](PRODUCTION-READINESS.md) — release-stage assessment and hardening requirements.
- [Deployment architecture](DEPLOYMENT-ARCHITECTURE.md) — current K8s/dependency topology and gaps.
- [Release process](RELEASE-PROCESS.md) — required dev-to-main, tagging, verification and rollback process.
- [Team workflow](TEAM-WORKFLOW.md) — permanent Main/L1/B1/B2/F1/SEC-QA operating model.
- [Decisions](DECISIONS.md) — platform-level decisions established by this baseline.
- [Risk register](RISK-REGISTER.md) — P0/P1/P2 follow-up work.
- [Go-live checklist](GO-LIVE-CHECKLIST.md) — evidence-driven release gates.

## Feature documentation

Feature documents remain authoritative for detailed contracts when they match implementation:

- Managed Email: [../managed-email/README.md](../managed-email/README.md)
- Managed SMS: [../managed-sms/README.md](../managed-sms/README.md)
- Notifications: [../notification/README.md](../notification/README.md)
- Meta WhatsApp: [../whatsapp/README.md](../whatsapp/README.md)
- Contacts & Audience: [../contacts-audience/README.md](../contacts-audience/README.md)
- Ads: [../ad-center-google-ads.md](../ad-center-google-ads.md) and [../ad-center-gap-analysis-and-roadmap.md](../ad-center-gap-analysis-and-roadmap.md)
- Gmail realtime: [../gmail-realtime-pubsub.md](../gmail-realtime-pubsub.md)
- Storage accounting: [../storage-accounting.md](../storage-accounting.md)

## Documentation classification at this baseline

### CURRENT SOURCE OF TRUTH

- implementation under `unifydesk-app/backend` and `unifydesk-app/frontend`;
- Flyway migrations through `V83__contacts_audience_round2_security.sql`;
- `unifydesk-app/.env.example`, `backend/src/main/resources/application.yaml`;
- current Kubernetes manifests under `unifydesk-app/k8s`;
- this platform documentation layer;
- feature contracts that have not been superseded by later implementation.

### HISTORICAL QA EVIDENCE

Round-specific `SECURITY_QA_*`, QA feedback, frozen-SHA and remediation documents remain valuable evidence for the SHA they name. They are not automatically current release evidence.

Examples include Contacts Round-1/Round-2 reports, WhatsApp Round-2/Round-3 reports, Managed Email QA rounds, and Managed SMS QA rounds.

### STALE / SUPERSEDED DOCUMENTATION

Verified examples at the audited SHA:

- root `README.md` described the old “Messaging” product, V1–V3 era, and stated SMS was not implemented;
- `docs/notification/INTEGRATION_STATUS.md` still says Round 0 / workers ready to start although Notification implementation and later remediation are merged;
- `docs/managed-email/INTEGRATION_STATUS.md` still presents Round-2 planning although Round-3 work was later merged;
- `docs/managed-sms/INTEGRATION_STATUS.md` stops at the Round-1/next-QA stage although later remediation exists;
- `k8s/README.md` says three backend replicas and a 1 GiB backend limit while the current manifest uses one backend replica and a 2 GiB limit;
- Contacts status/QA files remain valid lineage evidence but predate the latest import UX/savepoint changes at the audited `dev` SHA.

Do not delete historical QA documents merely because their status section is stale. Prefer a current platform summary plus explicit historical labels.
