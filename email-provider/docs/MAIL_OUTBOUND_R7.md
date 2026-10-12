# UnifyDesk Mail — R7 mailbox SMTP delivery

**State: DRAFT implementation, source committed but untested. Do not merge, deploy, enable or change MX records until QA and security sign-off.**

R7 stacks on R6 (draft PR #368), then R5 #367, R4 #366, R3 #365, R2 #364, R1 #362. These PRs have not been merged to `dev`. The first PR's GitHub Actions runner setup remains a known blocker. R7 does **not** modify UnifyDesk App or its shipping AWS SES managed-email route.

## What R7 adds

- Versioned V18 migration:
  - `email_mail_domains.outbound_enabled=false` is an explicit operator-only safety gate. DNS ownership verification is **insufficient** to authorize SMTP sending.
  - A per-recipient `email_mail_outbound_recipients` ledger with tenant-composite foreign keys, attempt counts, leased claims, retry timestamps and Postfix handoff outcomes.
  - Independent SMTP states: `PENDING`, `PROCESSING`, `DEFERRED`, `HANDED_OFF`, `UNKNOWN`, `FAILED`.
  - Parent intent status reflects aggregate recipient state, never claims final inbox delivery.
- `MailComposeService.hold` requires BOTH `EMAIL_MAIL_SUBMISSION_ENABLED` and `EMAIL_MAIL_OUTBOUND_ENABLED`, plus an active authorized user/mailbox/domain and an approved verified sending domain with an active verified DKIM key. It atomically persists one immutable recipient row per address inside the same transaction as the held draft and intent.
- A separate bounded worker (4 recipient claims per poll) rechecks mailbox, organization, **author account and explicit mailbox grant**, verified domain, enabled sending project and DKIM status before contacting SMTP.
- The worker uses existing DKIM encryption/signing code and a **dedicated local Postfix listener**. It builds plain-text UTF-8 MIME with a stable Message-ID derived from intent ID and no Bcc disclosure; its envelope sender is the mailbox's verified canonical address.
- Redis Lua per-recipient admission limits: maximum 20 handoffs per mailbox per hour and 100 per organization per 24 hours, atomically enforced; on Redis failure, fail closed. These are conservative launch defaults.
- Deterministic handling of SMTP outcomes:
  - `HANDED_OFF`: Postfix explicitly accepted responsibility (usually SMTP 250); **does not mean delivered to recipient inbox**.
  - `DEFERRED`: an explicit retryable failure before or at a known negative SMTP result, with exponential backoff and at most 3 attempts.
  - `UNKNOWN`: ambiguous post-DATA connection/error, unexpected SMTP exception, or a worker crash with a stale lease; **NEVER automatically retry**.
  - `FAILED`: explicit permanent SMTP failure or retries exhausted.
  - Recovery marks stale in-flight claims UNKNOWN after 180 seconds, so an uncertain handoff cannot automatically duplicate a message.
- User+mailbox ACL-scoped outgoing history and recipient status APIs. Console UI displays handoff counts, pending/failed/unknown status, and an optional gated **Submit for sending** action only for a previously saved unchanged draft.

## Endpoints and feature flags

- `POST /console/v1/mail/mailboxes/{mailboxId}/drafts/{draftId}/hold` requires a session and `Idempotency-Key`; returns an initial HELD durable intent. **Only with the approved R7 worker enabled can it progress to actual Postfix handoff.**
- `GET /console/v1/mail/mailboxes/{mailboxId}/outgoing` lists at most 50 of the requesting user's own outgoing intents.
- `GET /console/v1/mail/mailboxes/{mailboxId}/outgoing/{intentId}/recipients` shows max 20 recipients of the same owning user, including SMTP handoff state.

All remain off in committed configuration:

```env
EMAIL_MAIL_COMPOSE_ENABLED=false
EMAIL_MAIL_SUBMISSION_ENABLED=false
EMAIL_MAIL_OUTBOUND_ENABLED=false
EMAIL_MAIL_OUTBOUND_INTERVAL_MS=5000
EMAIL_MAIL_OUTBOUND_SMTP_HOST=127.0.0.1
EMAIL_MAIL_OUTBOUND_SMTP_PORT=0
EMAIL_MAIL_OUTBOUND_EHLO_HOST=
VITE_MAIL_SEND_UI_ENABLED=false
```

The mail UI also requires `VITE_MAIL_UI_ENABLED=true`, `VITE_MAIL_COMPOSE_UI_ENABLED=true` and verified user mailbox activation. `EMAIL_MAIL_OUTBOUND_SMTP_PORT=0` is intentionally invalid when the transport flag is enabled; configure a real **different Postfix instance/listener** only in isolated testing.

**A separate SMTP port does not itself isolate mail reputation.** Postfix's mail listener must use an independently configured outbound transport/IP pool separated from marketing/bulk (which remains on the old listener); static public IP and matching PTR/HELO, DNS SPF/DKIM/DMARC, bounce handling and reputation warmup are required for release. Cloudflare Pages/Tunnel are for HTTPS, not public SMTP delivery or MX.

**Provisioning sequence after independent QA only:**
1. Validate DNS ownership of a mail domain, add/verify the corresponding `sending_domains` record associated with an ACTIVE email-provider project in the same organization, and verify DKIM key deployment. Reuse existing project DKIM machinery but do not reuse marketing API credentials or marketing rate limits.
2. Configure and test a separate trusted loopback Postfix submission listener and actual **separate outbound transport/IP reputation**.
3. Operator-approved migration/policy sets the exact tenant's `email_mail_domains.outbound_enabled` to true, with audit logging and security review; R7 intentionally exposes **no public toggle** for this.
4. Validate RAM/CPU, Redis, Postgres, Postfix logs, rate limits, bounce handling, fake SMTP failures and retention. Then enable server and UI gates in isolated staging.
5. Keep SES as the UnifyDesk App production sending provider until deliverability QA is complete.

## Time/space complexity and cost

- MIME construction/signing is O(n) time/memory with **n bounded by 64 KiB source body**; each recipient is processed sequentially. There are at most 20 recipients per submission. This is not yet a streaming SMTP encoder, but bounded size prevents unbounded heap usage.
- DB reads are indexed by mailbox/intent/recipient; claiming a recipient uses `FOR UPDATE SKIP LOCKED` and an attempt limit, without holding a DB transaction during SMTP network I/O.
- Redis limiter is O(1) commands and fail-closed.
- Existing Spring Boot, PostgreSQL, Redis and local Postfix are reused; no new managed cloud messaging/queue service. A second logical SMTP listener can share the host but separate outbound IPs/backup/monitoring still have real operating costs.
- R7 outbound content remains in the author-owned draft while sent status lives in a separate intent/recipient ledger. It does **not** yet create a complete RFC 5322 Sent-folder archive with immutable stored MIME.

## Mandatory QA (NOT executed)

1. JDK 25 Maven Spotless/compile/test; Node 24 React build with all mail flags on/off; Flyway V1–V18 cold and V17→V18 upgrade tests on PostgreSQL, including tenant-composite FK enforcement.
2. Gate matrix: any disabled flag, pending/suspended organization or domain, missing project, unverified DKIM, mailbox/grant revocation after queue creation, suspended user, wrong mailbox ID and cross-org attempts **must not hand off SMTP**.
3. Parallel HOLD with same/different idempotency keys, per-recipient uniqueness, concurrent worker claims, retry backoff and attempt caps. Test Redis outage and quota caps without unmetered sending.
4. Local fake SMTP server: 250 handoff, 4xx pre-DATA, 5xx permanent failure, socket timeout before DATA, timeout **after DATA**, worker crash after DATA before database commit, Postgres outage on result persistence. Verify stale processing transitions to UNKNOWN without retry.
5. DKIM verification over real MIME using external testing verifier; encoded Unicode headers and nested Bcc recipient disclosure tests.
6. Verify Postfix separate relay/IP routing and that marketing traffic cannot use the business-mail relay, and business-mail traffic cannot inherit marketing rate budgets or API keys.
7. Verify end-to-end recipient delivery separately via DSN/webhooks/feedback (not implemented by R7), bounce handling, anti-abuse, delayed queues, operator reconciliation, key rotation, error metrics, rate limits and monitoring.
8. R1–R6 unsatisfied CI/migration/security and persistent-storage/anti-spam gates remain blocking. No one may call R7 production ready solely because the code was committed.

## Later phases

Full MIME Sent archive, delivery/bounce tracking, attachments, public inbound MX and authenticated SMTP submission for third-party mail clients, recovery/backups, plus IAM parity with UnifyDesk App (SSO, MFA, custom roles, delegation and audit logs) before public release.
