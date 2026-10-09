# UnifyDesk Mail — R1–R9 Consolidation and Release QA

Audit date: **2026-10-09**. Source branch: `fix/unifydesk-mail-r1-r9-qa-consolidation`.
Base: `feature/unifydesk-mail-dsn-recovery-r9` (PR #371). **No merges into dev.**

## Release decision: BLOCKED

The entire mailbox stack remains in drafts: R1 #362, R2 #364, R3 #365,
R4 #366, R5 #367, R6 #368, R7 #369, R8 #370, R9 #371.
None has an independently verified compile, full test suite, migration run,
live SMTP handshake, real DSN correlation, persistent-volume recovery test,
penetration test, or authorizing code review. Do **not** enable
`EMAIL_MAIL_*`/Vite mail flags or move production MX/DNS. Existing UnifyDesk App
SES must remain unchanged.

### CI scheduler blocker (confirmed via GitHub run metadata)

- [Checks workflow attempt 2](https://github.com/renukaprasad-ms/UnifyDesk/actions/runs/37923035809) on PR #362: **six jobs all failed within ~2 seconds; all have empty steps and empty runner name**.
- A retry did not resolve it. The new standalone [Mail QA workflow](https://github.com/renukaprasad-ms/UnifyDesk/actions/runs/37941860300) also triggered on PR #372: its backend and frontend jobs both failed in 3–5 seconds with **zero steps and no runner**. This independently confirms the pre-execution scheduler/account issue persists. This is **not evidence of a code test failure or success**. Possible settings/actions entitlement/billing/runner-availability causes require inspecting the GitHub Actions run banner and the repository/owner Actions settings. We have not verified the exact account-level reason.
- The executing environment available for this audit has Java 21 and Node 22, **not** required Java 25 and Node 24, no PostgreSQL executable or Docker daemon, and no outbound networking/private repository checkout. It cannot run Maven dependencies or the real database here.
- **Action for repository owner:** Open the linked run in GitHub and check the workflow banner, Settings → Actions → General, account/organization Billing & Licensing → Actions usage/spending limit, and runner availability. Resolve the GitHub-side issue. Do not bypass required checks.

### Corrections committed on this QA branch (source reviewed, NOT runtime tested)

| ID | Severity | Finding and correction |
|---|---|---|
| MAIL-QA-001 | HIGH | Feedback-only startup depended on an outbound-only bounce-verifier bean. It now loads when **either** outbound or feedback is enabled. |
| MAIL-QA-002 | HIGH | Outgoing archive required the inbound feature flag to create `MailSpoolStore`. It now initializes for **inbound OR outbound**, permitting SMTP archive storage without activating inbound endpoints. A persistent secured path remains mandatory. |
| MAIL-QA-003 | HIGH | Mailbox read authorization previously omitted current domain and organization status. The access gate now requires verified domain and active organization. |
| MAIL-QA-004 | MED/HIGH | Outgoing message/recipient history previously omitted domain and organization state in its read gate. Both routes now reject suspended domain or organization state. |
| MAIL-QA-005 | HIGH | Existing repo-wide Checks failed before running and cannot validate stacked PRs. Added `.github/workflows/mail-qa.yml` to build mail backend, PostgreSQL migrations and frontend feature flag variants separately after runners work. |
| MAIL-QA-006 | HIGH | No automated **real PostgreSQL** schema upgrade gate for V14–V20. Added `MailPostgresMigrationTests` that migrates fresh V1→V20 and V13/V16/V19→V20 using disposable isolated schemas. |
| MAIL-QA-007 | HIGH | Ambiguous SMTP acceptance needs protocol-level regression evidence; prior tests only checked mapping. Added a local in-process fake SMTP test for 250 DATA acceptance, explicit RCPT 450, and connection lost after DATA (UNKNOWN). |
| MAIL-QA-008 | LOW | Reply control was also shown on Sent/Spam messages, although the backend only accepts a scanned inbound reply target. It now only appears for Inbox items. |

All corrections remain in **one new QA branch**; previous draft PR branches were deliberately not rewritten.

### Source checks performed

- Inspected existing GitHub Actions metadata and workflow YAML.
- Inspected all PostgreSQL Flyway SQL migrations V1–V20 and principal mailbox service/controller APIs. No real SQL execution was available, so migration validity is **unconfirmed**.
- Checked 29 mailbox Java source files for references to missing internal `com.messaging.emailprovider` classes: **no missing internal import targets found**. This is a static path check, not compilation.
- Verified `InternalApiTokenFilter` handles `/internal/v1/mail/...` and excludes only the earlier marketing feedback route. The newer mail feedback endpoint uses its own separate secret as a second requirement.
- Inspected MIME parsing behavior against Apache James Mime4j's published API. **Dependency downloading and executable MIME tests were unavailable.**
- Reviewed tenant scope, sender authorization, security feature gates, quota locks, SMTP uncertainty, archive pointer integrity, and bounce suppression from source. These checks do not replace race/failure integration tests.

## Automated QA added — how to execute

On the GitHub PR (once Actions runners can start), the **Mail QA (R1–R9)**
workflow executes:

1. **Backend:** JDK 25, Maven Spotless check, `clean test`, packaging.
2. **Database:** a fresh PostgreSQL 16 service container with
   `MAIL_QA_PG_URL=jdbc:postgresql://localhost:5432/mail_qa` and ephemeral
   schemas for V1→V20, V13→V20, V16→V20 and V19→V20. It validates the schema
   history and key mail tables. These tests intentionally skip if the
   disposable DB variable is absent, but the CI workflow supplies it.
3. **Frontend:** Node 24 builds with mail UI/compose/send flags **all off** and
   **all on**.

Alternately on your isolated developer host after installing the required
toolchain, run `bash email-provider/tests/mail-verify.sh`. For database tests
also set `MAIL_QA_PG_URL`, `MAIL_QA_PG_USER`, and `MAIL_QA_PG_PASSWORD`
to a **throwaway local** PostgreSQL database you control.

The workflow reuses GitHub's standard runners and a temporary PostgreSQL
container; it does **not** provision an additional production database or
paid cloud product. GitHub runner usage can still count toward account
limits; a local QA host avoids extra CI expenditure but requires secure
credential isolation.

## Remaining gates (still BLOCKED, not implemented by this QA branch)

**Build/format:** Full Maven dependency resolution, Spotless formatting of
R1–R9 files, all Java tests, Vite build, and PostgreSQL migration tests must
run and pass; remediate every failure and preserve exact logs.

**Security:** Independently verify cross-tenant/domain/user permissions,
revocation mid-download and mid-send, session isolation, spoofed/ambiguous
DSNs, HMAC key rotation, send suppressions, double-submit idempotency, and
marketing-vs-mailbox relay separation.

**I/O and recovery:** Run real local Postfix 250/450/550/timeout-after-DATA
tests; clamd/Rspamd validation with malware fixtures; concurrent Inbox/Spam
promotion; disk-full and fsync semantics on the actual k3s PVC; encrypted
off-node backups and restore; orphan reconciliation without data loss.

**Product:** Public inbound MX/SMTP, MTA bounce pipe and trusted source
validation, attachment handling, bounce abuse/suppression operations, true
recipient delivery evidence, observability alerts, backup policy and data
retention. Full IAM parity with UnifyDesk App before general availability.

## Integration plan once all gates pass

1. Fix GitHub Actions account/runner scheduling; run mail-only QA on this
   consolidation branch. Apply formatter and correct Java, SQL, UI, and security
   failures discovered by execution.
2. Obtain independent QA and a consolidated green build plus upgrade test log.
3. Freeze the reviewed QA SHA, then integrate R1→R9 sequentially or squash a
   reviewed rebased consolidation PR into `dev` with traceable commit history.
   Do **not** merge a failing/untested branch merely to simplify PR cleanup.
4. Keep all hosted-mail runtime flags off on dev initially; enable only
   isolated internal staging after SMTP/volume/network QA.
5. Validate SES and the existing UnifyDesk App were unaffected; perform
   production MX/sending rollout only after dedicated release authorization.

**Summary:** consolidation, targeted source-level fixes, and executable CI /
integration-test specifications were committed. **Actual runtime and
database test passes have not been obtained**, and no merge or deployment
was performed.
