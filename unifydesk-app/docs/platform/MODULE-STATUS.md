# Module Status

Baseline: `dev@d9717f93b3e10a308da9636750a4df6926bc6016`

## Status semantics

- **PLANNING** — requirements/design only.
- **IMPLEMENTING** — implementation is actively incomplete.
- **SOURCE_REVIEWED** — current implementation exists and was inspected; this does not imply tests ran.
- **EXECUTION_BLOCKED** — required executable verification could not run.
- **QA_PENDING** — implementation exists but needs a current frozen-SHA QA cycle.
- **EXTERNAL_VERIFICATION_PENDING** — live provider/environment verification remains.
- **RELEASE_CANDIDATE** — current implementation plus required executable/security/provider evidence is assembled for release review.
- **PRODUCTION_READY** — all applicable release gates, operations and rollback/DR evidence are satisfied.

No major module is classified PRODUCTION_READY in this baseline.

## Current status matrix

| Module | Implementation | Automated tests at current baseline | Migration state | Security / QA | External provider | Production operations | Overall |
|---|---|---|---|---|---|---|---|
| Authentication & sessions | SOURCE REVIEWED | EXECUTION BLOCKED | schema present from core lineage | QA_PENDING on current head | SMTP/OTP delivery not revalidated here | production cookie/proxy settings must be proven | QA_PENDING |
| Companies / workspaces / IAM / RBAC | SOURCE REVIEWED | EXECUTION BLOCKED | current schema present | QA_PENDING | n/a | admin audit/operational review needed | QA_PENDING |
| Billing & entitlements | SOURCE REVIEWED | EXECUTION BLOCKED | current schema present | QA_PENDING | no live payment-provider evidence in this baseline | catalog/subscription operations need release validation | QA_PENDING |
| Channels / connections | SOURCE REVIEWED | EXECUTION BLOCKED | migrations through mailbox/provider evolution present | QA_PENDING | Gmail/Microsoft/IMAP-SMTP live checks pending | credential rotation/webhook runbooks need proof | EXTERNAL_VERIFICATION_PENDING |
| Inbox | SOURCE REVIEWED | EXECUTION BLOCKED | migrations through mailbox/WhatsApp integration present | QA_PENDING | mailbox/WhatsApp end-to-end pending | realtime/provider recovery needs staging evidence | QA_PENDING |
| Broadcast | SOURCE REVIEWED | EXECUTION BLOCKED | V15–V24 + later integrations present | QA_PENDING | SES/SendGrid/Azure/MSG91 live delivery pending | capacity/retry/DLT/load proof pending | EXTERNAL_VERIFICATION_PENDING |
| Email templates | SOURCE REVIEWED | EXECUTION BLOCKED | V25–V33 present | QA_PENDING | provider-template sync where applicable pending | asset/provider lifecycle validation pending | QA_PENDING |
| Media / storage | SOURCE REVIEWED | EXECUTION BLOCKED | storage/lifecycle migrations present | QA_PENDING | S3/Supabase live path depends on deployment | backup/quota/reconciliation operations need proof | QA_PENDING |
| Managed Email | SOURCE REVIEWED | EXECUTION BLOCKED | V56–V60 present; current migration execution not proven | historical multi-round QA exists; current executable gates pending | SES + DNS/Cloudflare live validation pending | disabled by default; production credentials/alerts/runbook pending | EXTERNAL_VERIFICATION_PENDING |
| Managed SMS | SOURCE REVIEWED | EXECUTION BLOCKED | V62–V66 present; current migration execution not proven | historical QA/remediation exists | MSG91 + DLT topology/API/webhook validation pending | disabled by default; wallet/rate/incident operations pending | EXTERNAL_VERIFICATION_PENDING |
| Meta WhatsApp | SOURCE REVIEWED | EXECUTION BLOCKED | V76–V79 present; executable migration matrix blocked historically | final historical source review found no known implementation blocker | real Meta WABA/phone/signup/webhook/send verification pending | production credential/revocation/throughput runbook pending | EXTERNAL_VERIFICATION_PENDING |
| Notifications | SOURCE REVIEWED | EXECUTION BLOCKED | V70–V75 present | current status doc stale; fresh QA evidence needed | email/Web Push delivery live verification pending | worker/digest/push/realtime operational proof pending | QA_PENDING |
| Contacts & Audience | SOURCE REVIEWED | EXECUTION BLOCKED | V80–V83 present | Round-2 QA + Round-3 fixes exist; current dev has later import changes | n/a for core | 10k/100k/load/crash/recovery evidence pending | QA_PENDING |
| Ad Center / Google Ads / Meta Ads | SOURCE REVIEWED | EXECUTION BLOCKED | V34–V55 and V61 present | current-dev authoritative QA summary absent | real provider account/campaign/insight tests pending | provider quotas, failure recovery, cost controls need proof | EXTERNAL_VERIFICATION_PENDING |
| Async jobs | SOURCE REVIEWED | EXECUTION BLOCKED | job schema exists | QA_PENDING | n/a | queue recovery/backlog/DLT operations need load evidence | QA_PENDING |
| Kafka | SOURCE REVIEWED | EXECUTION BLOCKED | n/a | configuration reviewed | n/a | current K8s is single broker, RF=1, no persistent volume | EXECUTION_BLOCKED |
| Redis | SOURCE REVIEWED | EXECUTION BLOCKED | n/a | feature-level Redis tests exist historically | n/a | current K8s Redis has no persistent volume/HA | EXECUTION_BLOCKED |
| Realtime / WebSocket | SOURCE REVIEWED | EXECUTION BLOCKED | supporting schema/config present | QA_PENDING | browser/proxy path must be exercised | single backend + proxy/TLS behavior needs staging proof | QA_PENDING |
| Storage accounting | SOURCE REVIEWED | EXECUTION BLOCKED | V2 and later lifecycle integration present | QA_PENDING | storage provider-dependent | reconciliation/alerting/backup evidence pending | QA_PENDING |
| Platform administration | SOURCE REVIEWED | EXECUTION BLOCKED | supporting schema present | QA_PENDING | n/a | privileged access/audit release review needed | QA_PENDING |

## Evidence caveat

The repository contains a real test suite and GitHub Actions definitions, but historical reviewed runs include jobs that ended with `runner_id=0` and `steps=[]`. Those results are **EXECUTION BLOCKED**, not test success. The current baseline SHA also has no combined commit status attached through the inspected GitHub status API.

A new release candidate must collect executable evidence against one frozen integrated production-code SHA.
