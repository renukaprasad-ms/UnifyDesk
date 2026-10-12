# UnifyDesk Mail — release gate and priority order

Status: **not ready to merge to dev or expose production SMTP** (October 9, 2026).

## Active delivery priority

Finish UnifyDesk Mail core, including reliable inbound receipt, secure storage, Inbox, replies/compose, outgoing mailbox submission, attachments and operational recovery, **before** expanding the separate mail-specific IAM implementation.

The IAM target remains non-negotiable for GA: parity with UnifyDesk App's identity and RBAC model, MFA, account recovery, independent mailbox permissions, delegation, revocation and audited administrative actions. This is a tracked later phase, not a reason to block development of the existing core on isolated branches. **Do not ship GA until IAM passes.**

## PR integration dependency chain

1. PR #362 `feature/unifydesk-mail-foundation-r1` → `dev`: PostgreSQL mail schema and staging endpoints.
2. PR #364 `feature/unifydesk-mail-dns-inbound-r2` → feature R1 until merged: DNS ownership and durable inbound quarantine. After R1 merges, rebase or retarget cleanly.
3. PR #365 `feature/unifydesk-mail-scan-inbox-r3` → feature R2 until merged: scanning, ACL, Inbox API.
4. PR #366 `feature/unifydesk-mail-console-r4` → feature R3 until merged: feature-gated React UI plus security verification changes. Do not squash/merge out of order.

No production feature flags are enabled, no customer MX changed and SES stays the shipping managed-email provider.

## Present blocker: GitHub Actions checks did not execute

The GitHub workflow `Checks` on PR #362 failed in six jobs. Workflow run `37923035809` attempt 2 failed again on October 9, 2026. Available job records show no executed steps and empty runner name. **This does not prove source tests failed, nor does it prove a billing cause**. Inspect the run details banner and repository Actions/billing/runners settings; this connector cannot diagnose the exact provider-side failure from absent job logs.

Until CI execution is available, the developer may run `bash email-provider/tests/mail-verify.sh` on a local or existing build host (JDK 25, Node 24, correct PostgreSQL/Kafka/Redis test dependencies), without adding a paid CI resource. Passing this script is only unit/build evidence; it is not production acceptance.

## Mandatory merge/production gates

### Merge gate — each stacked PR
- Successful Java 25 compilation, Spotless checks, and relevant unit tests; Node 24 React builds with hosted-mail flag both on and off.
- Real PostgreSQL 16+ fresh migrations V1–V16 and upgrade testing from an existing V13, V14 and V15 database. Verify FKs, unique constraints, rollback, idempotent migrations.
- Independent review of tenant/user ACLs, API filters, idempotency, concurrent quota control, DNS token rotation and scanner behavior. No unapproved review blockers.
- Replay, failure and bounded-load tests. Target measurable CPU/RAM/disk budgets on the current mail node; keep service feature flags off in dev until end-to-end tests are signed off.
- GitHub PRs remain drafts until these checks pass. **Do not bypass failing required checks.**

### Product launch gate — after core features
- Public SMTP receiver, MX provisioning, policy enforcement, relay protection, transport security, bounced mail and operator observability.
- MIME parser and safe text/HTML rendering with remote-image blocking, attachment scanning/download, replies/compose, drafts/sent folders, authenticated submission, spam feedback.
- Quarantine cleanups, storage quota release, orphan reconciliation, encrypted off-node backups and restore drills.
- SPF/DKIM/DMARC, PTR and IP warmup/deliverability on dedicated mail hostname and mail-server public IP. Cloudflare Tunnel is for HTTP interfaces, not MX/port-25 SMTP.
- IAM parity with UnifyDesk App before GA; mailbox privacy always requires explicit delegated access and audit logging.
- Gradual internal/early-access release with traffic caps and fallback to existing SES sending.

## Cost discipline

No new managed cloud service by default. Reuse existing Spring Boot process, PostgreSQL and available node storage (persistent encrypted volume). Scanner daemon RAM consumption and message volume must be measured; self-hosting has operational costs even when software is free. Keep message objects out of relational DB and index paginated metadata. Retention, backups and recovery are mandatory for hosted mail.

## Truth in status

**Committed is not tested. Tested is not deployed. Deployed is not deliverable.**
Report each gate independently. Never call the current R1–R4 stack production-ready or merged based solely on source review.
