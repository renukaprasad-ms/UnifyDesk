# UnifyDesk Mail R2 — ownership DNS and private inbound quarantine

Status: **development-only draft**. This is not a working public MX or webmail product. Never enable inbound while malware/spam scanning, persistent storage, security review, and runtime tests are missing.

## Architectural decisions

- Reuse the existing Spring Boot mail backend, console-session authentication, PostgreSQL, and DNS resolver. No new paid cloud managed service.
- Keep existing SES, Postfix outbound queue, and UnifyDesk App integrations unchanged.
- Mail domains are scoped to the email product's **organization**, not the app company's broadcast project; mailboxes inherit organization identity.
- The challenge is a cryptographically random 32-byte value; persist **only SHA-256**, not plaintext.
- Staging a domain creates an ownership TXT challenge but does not verify MX, change customer DNS, activate mailboxes, or expose port 25.
- Verifying ownership sets mail domain status VERIFIED. All mailboxes remain DISABLED until a separate security-reviewed activation workflow.

## DNS ownership workflow (console session, manage role required)

- POST /console/v1/mail/domains — body \`{"domain":"example.com"}\`. Response includes domain ID, one-time TXT record name \`_unifydesk-mail.example.com\` and value \`ud-mail-verification=<random>\`.
- POST /console/v1/mail/domains/{id}/challenge — rotate an unverified domain's lost challenge; previous proof becomes invalid.
- POST /console/v1/mail/domains/{id}/verify — resolve TXT through the existing DNS resolver, compare hashed token and conditionally update the original pending domain. A stale or rotated proof cannot pass. DNS resolution occurs **outside** the DB transaction.
- GET /console/v1/mail/domains — bounded list; no token disclosure.
- POST /console/v1/mail/domains/{id}/mailboxes — create **disabled** mailbox metadata only.

A verified domain does not imply correct MX routing, DKIM/SPF/DMARC, static SMTP IP or readiness to accept Internet mail.

## Private inbound intake (feature-gated OFF)

Enable only on a private network after placing **persistent storage** at the configured path, installing an SMTP adapter, spam/virus scanners, backups and admission/disk alerts, and passing operator QA.

Config:
- \`EMAIL_MAIL_INBOUND_ENABLED=false\` (default).
- \`EMAIL_MAIL_INBOUND_SPOOL_PATH=\` (must be an existing writable persistent directory when enabled).
- \`EMAIL_MAIL_INBOUND_MAX_BYTES=10485760\` (10 MiB upper bound).

When enabled, the private relay endpoint is:

\`\`\`http
POST /internal/v1/mail/inbound
Authorization: Bearer <internal service token>
Content-Type: message/rfc822
X-UnifyDesk-Envelope-To: support@example.com
X-UnifyDesk-Delivery-Key: relay-session-unique-key

<raw RFC 5322 MIME bytes>
\`\`\`

The existing internal API token filter guards this route. **Never expose /internal/v1/ via Cloudflare, public Ingress, or the React console.** The upstream SMTP adapter (not implemented) is solely responsible for validated envelope recipients, stable delivery IDs, abuse limits, and trustworthy streaming. Header From/To MUST NOT be treated as recipient routing authority.

Inbound flow:
1. Normalize the SMTP envelope recipient and reject unknown/disabled/unverified mailboxes before disk I/O.
2. Stream MIME in **8 KiB chunks**, enforce a 10 MiB hard limit, hash while writing; fsync the file, atomic-rename it and fsync the containing directory. Temporary files are deleted on known failures.
3. Start a PostgreSQL transaction and lock only the recipient mailbox. Recheck active organization, verified domain, active mailbox.
4. Check unique (mailbox_id, delivery_key). A replay with the same bytes returns the existing job; a different payload is a conflict.
5. Reserve the bounded per-mailbox storage quota and insert a durable \`email_mail_inbound_jobs\` row, both in one transaction.
6. Return HTTP 202 with state \`PENDING_SCAN\`. **No inbox reader consumes this table yet**.

Recovery contract: if database commit is ambiguous, retain the MIME object rather than risking lost mail. Crash after file fsync but before DB commit may leave an orphan \`.eml\` file. Operator reconciliation must cross-check objects against the database and only reclaim objects after a conservative age and retention window; never blindly delete on database error. A later worker must scan MIME/attachments, quarantine or reject dangerous content, then promote safe messages to Inbox idempotently. Quota debit reversal must be transactional when a job is rejected or purged.

### Complexity and cost

- MIME I/O: O(n) sequential bytes of work for an n-byte message, O(8192) byte buffer in Java heap, O(n) disk for original MIME; **no MIME BLOB inside PostgreSQL**.
- Recipient and idempotency lookups: PostgreSQL B-tree point lookups O(log N); one mailbox row lock serializes per-mailbox quota reservation, rather than a global lock.
- Domain/mailbox lists are bounded at 100 results; full pagination/search will need cursor indexes as accounts grow.
- Use an existing local-path persistent volume only for the initial single-node staging topology. This consumes your own disk space rather than a new paid cloud storage product.
- **A local PVC is not replicated or backed up automatically** and does not provide high availability. Do not advertise hosted mail as production ready until disaster recovery and storage integrity are proven.
- A per-mailbox 1 GiB quota does not protect shared disk capacity. Implement node-level disk headroom budgets, alerts, age-based quarantine cleanup, and backup restore tests before admitting real traffic.

### Required tests before enable/merge

- Fresh and upgraded Flyway V1–V15 on PostgreSQL, including unique constraints, FKs and migrations from V14.
- Console role and tenant isolation, concurrent registration limits, denied cross-org verification, DNS timeouts, malformed TXT and challenge rotation races.
- MIME overflow/empty stream, invalid envelope, duplicate keys, concurrent mailbox claims, quota rollback, disk-full, crash in each persistence step, and Postgres commit-unknown recovery.
- SMTP relay auth, prevention of open relay, transport TLS, size limits, scanner failures, spam/malware attachments and queue alerting.
- Local filesystem atomic rename/fsync with your **actual** PVC provisioner; \`emptyDir\`, container root or \`/tmp\` is not durable.
- Continuous monitoring of inbound job backlog and disk usage. If the scanner is offline, upstream admission must be suspended before disk exhaustion.

Do not change MX records to UnifyDesk yet. Public SMTP on port 25 requires its own network path, DNS and IP reputation plan; Cloudflare Pages/Tunnel for HTTP does not replace inbound SMTP.
