# UnifyDesk Mail R3 — security scanning, activation, mailbox reads

**Status: Source-level implementation in a draft PR, NOT production ready.**
This PR stacks on `feature/unifydesk-mail-dns-inbound-r2` (PR #364), which stacks on the R1 foundation (PR #362).

## Low-cost system design

- Reuse the existing Spring Boot 4 backend, PostgreSQL, Redis and the single-node persistent MIME spool.
- Use **self-hosted** clamd (ClamAV) and Rspamd, not usage-billed cloud scan APIs.
- Both scanners must be available on the mail node's **private loopback interface**. These daemons consume additional memory/CPU and may require a larger node if capacity is insufficient. Do not create a new paid cloud service by default.
- Raw MIME is stored only once per delivery (plus a transient spool temp file). The DB holds size, SHA-256 and an opaque object filename.
- ClamAV streams in 8KiB blocks (O(n) I/O, O(1) application message buffer). Rspamd sends the stored MIME as bounded streaming HTTP (O(n) I/O, O(1) application message buffer) with 64KiB maximum response.
- Scanner jobs use `FOR UPDATE SKIP LOCKED` row claims, UUID fencing tokens and bounded four-job polls. The scanner **never** holds a database transaction open during network or filesystem scanning.
- Database indexes cover mailbox ACLs, pending/stale scanner leases and the descending mailbox message cursor.

## Enabled services and safety gates

All three flags remain **false** in `application.yml` and `.env.example`:

```env
EMAIL_MAIL_INBOUND_ENABLED=false
EMAIL_MAIL_SCANNER_ENABLED=false
EMAIL_MAIL_ACTIVATION_ENABLED=false
EMAIL_MAIL_INBOUND_SPOOL_PATH=
EMAIL_MAIL_INBOUND_MAX_BYTES=10485760
EMAIL_MAIL_CLAMD_HOST=127.0.0.1
EMAIL_MAIL_CLAMD_PORT=3310
EMAIL_MAIL_RSPAMD_URL=http://127.0.0.1:11333/checkv2
EMAIL_MAIL_SCAN_INTERVAL_MS=5000
```

Scanner enablement requires inbound spool enablement and a writable **persistent encrypted** volume; it must not point to `emptyDir`, `/tmp`, or a container root filesystem. Mailbox activation additionally requires its explicit feature gate and scanner gate. The private SMTP intake endpoint also rejects requests when the scanner feature flag is disabled.

**Do not turn on these flags until live ClamAV and Rspamd checks, encrypted storage, recovery, operational alerting and independent QA pass.** Neither Kubernetes scanner deployments nor public SMTP/MX listeners are added in this PR.

ClamAV's `StreamMaxLength` must be at least the configured inbound maximum. Rspamd HTTP must be loopback/private only. Neither port is to be exposed publicly; enforce network isolation and authentication at the MTA boundary. ClamAV detects known malware but cannot guarantee all malicious content will be detected; sanitized rendering, spoofing defenses and layered security remain necessary.

## Scanner lifecycle

1. `POST /internal/v1/mail/inbound` stages a size-limited raw MIME file and inserts `PENDING_SCAN` in V15. The endpoint is private and requires the existing internal service bearer.
2. The scan worker atomically claims a single job and marks `SCANNING` with a lease token.
3. ClamAV must produce a verified `stream: OK`; discovered malware becomes `QUARANTINED` and never enters a mailbox. SHA-256 and size are rechecked as part of the ClamAV stream.
4. Rspamd must return a complete, non-skipped JSON classification. `no action` with score below threshold goes to INBOX. Spam actions go to SPAM. Reject/discard goes to QUARANTINED. Unknown results, scanner failures and timeouts cause bounded retries and eventual quarantine.
5. Read only bounded RFC 5322 summary fields (From, Subject, Message-ID) after security checks. This does **not** render HTML, decode arbitrary MIME, or execute attachments.
6. In a database transaction verify the mailbox, domain and organization are still active. Update the claimed job and insert the Inbox/Spam row, protected by `UNIQUE(inbound_job_id)`. A stale scanner cannot publish because its claim token no longer matches.
7. Lease expiry, scanner failure and crashes do not automatically expose unscanned mail. After five failures jobs are quarantined; stranded sixth leases are also quarantined.

**Important:** quarantine entries retain their reserved storage bytes and require an operator-controlled retention/cleanup/reconciliation workflow, not blind deletion. Objects left after uncertain PostgreSQL commits must be reconciled against durable intake records. The code does not yet implement that complete cleanup policy.

## Controlled mailbox access and API

Requires authenticated email-console **user** sessions, not developer/project API keys.

- `POST /console/v1/mail/mailboxes/{mailboxId}/activate` with JSON `{"userId":"123"}`: OWNER/ADMIN only; requires verified domain, the exact same organization, active user and enabled security gates. Creates explicit `email_mailbox_access` record. Send Snowflake IDs as strings to avoid JavaScript integer precision loss.
- `GET /console/v1/mail/mailboxes/{mailboxId}/messages?folder=INBOX&beforeId=...&limit=30`: returns up to 100 message summaries for that authenticated user **only**; SPAM is also supported. Cursor uses descending Snowflake ID.
- `GET /console/v1/mail/mailboxes/{mailboxId}/messages/{messageId}/raw`: streams the original RFC 5322 bytes as an **attachment** (not inline HTML), with `nosniff` and `no-store`.

The SQL uses organization+mailbox+user grants, not just the mailbox ID. A workspace/project API key has no access to mailbox content; organization admins do not receive implicit read permissions. At this stage an administrator can assign the first owner while activating a mailbox. Separate safe access revocation/transfer workflows are needed before general availability.

## QA and production blockers

**No executable tests or deployment validation occurred in the GitHub connector session.**

- Compile and run tests with `cd email-provider/backend && ./mvnw -B test`; format/check with `./mvnw -B spotless:apply` and `./mvnw -B spotless:check` on the feature branch.
- Flyway upgrade V14→V15→V16 on real PostgreSQL, and cold migrations V1→V16.
- Confirm scanner protocol framing with a live ClamAV instance, known EICAR fixture in isolated QA, clean MIME and simulated timeouts; verify Rspamd `/checkv2` classifications.
- Verify no unscanned or malformed message is ever visible, and exact-once promotion under concurrent scanner restarts and DB failover.
- Verify tenant and user ACL isolation, disabled/suspended mailbox behavior, stolen session and unverified domain attempts, raw download content headers, and the full authenticated console flow.
- Verify spool volume permissions, disk-full behavior, backups and restore, scanner daemon memory sizing, degraded service handling, and no public `/internal/v1/` routing.
- **Before real mail hosting:** public SMTP gateway/MX, DKIM/SPF/DMARC, MTA recipient validation, protected relay, outbound mailbox submission, real MIME parser + XSS-safe HTML viewing, comprehensive mailbox CRUD, anti-abuse controls, cleanup/reconciliation and monitoring are still required.

Do not change UnifyDesk App managed-email SES, production flags, Kubernetes deployments or customer MX records as part of this PR.
