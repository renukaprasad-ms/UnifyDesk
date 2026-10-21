# UnifyDesk Mail R8 — immutable Sent archive, trusted MTA feedback and operational recovery

**Status:** Source committed in a stacked draft PR. This is not deployment-tested or production-ready. It does not enable public MX, SMTP or customer mail. Existing UnifyDesk App SES sending is unchanged.

## Delivered architecture

1. **Immutable signed Sent archive.** The R7 SMTP worker uses an existing, encrypted persistent mail spool to save one DKIM-signed RFC 5322 MIME image **before** attempting SMTP DATA. PostgreSQL V19 stores only an opaque object key, size and SHA-256. `UNIQUE(intent_id)` selects one authoritative archive under concurrent claims, and all recipient attempts reuse those same bytes. Disk writes are fsynced by `MailSpoolStore`; ambiguous insert failures deliberately retain orphaned files for reconciliation.
2. **Postfix handoff semantics.** A `SENT` folder row is created in the same transaction as the recipient's confirmed `HANDED_OFF` state, protected by `UNIQUE(outbound_intent_id)`. It means **accepted by the local Postfix MTA**, not delivered to a recipient's inbox. If DB persistence fails after 250, the worker's lease eventually becomes `UNKNOWN` and is **not retried** automatically.
3. **Authenticated private delivery feedback.** V19 includes per-recipient remote status and immutable feedback events. `POST /internal/v1/mail/outbound/feedback` requires the existing internal service bearer **and** an independent `X-UnifyDesk-Mail-Feedback-Token` (minimum 32 random characters). The trusted local MTA adapter supplies exact organization, mailbox, intent, recipient IDs, address and stable unique event key. It is the adapter's responsibility to authenticate/parse actual DSN and Postfix-log events before calling the endpoint. Replay uses `UNIQUE(recipient_id,external_event_id)`; row-level locking prevents conflicting concurrent transitions.
4. **Monotonic remote results.** `DEFERRED`, `DELIVERED`, `BOUNCED`, `COMPLAINED` are stored **separately** from SMTP handoff state. A late delivery event cannot erase a hard bounce or complaint. Feedback never triggers an automatic resend. Remote results are allowed only for previously `HANDED_OFF` or `UNKNOWN` recipients.
5. **Sent and status UI.** Authenticated mailbox users with an explicit grant can list `SENT` and download the SHA-256-verified original message as a file. Outgoing history distinguishes local SMTP acceptance, remote delivery, bounces, complaints, failures and unknown outcomes. Unsafe HTML is never rendered.
6. **Monitoring and operator recovery.** Read-only internal `GET /internal/v1/mail/outbound/ops` provides 24-hour handoff/failure/bounce counts and aggregate pending-scan/unknown counts. `GET /internal/v1/mail/outbound/reconciliation` lists at most 100 recent `UNKNOWN` recipient records **without mail content**, for Postfix queue/log inspection. Neither endpoint changes a delivery state.

## Disabled-by-default feature gates

```env
EMAIL_MAIL_INBOUND_ENABLED=false
EMAIL_MAIL_OUTBOUND_ENABLED=false
EMAIL_MAIL_SUBMISSION_ENABLED=false
EMAIL_MAIL_FEEDBACK_ENABLED=false
EMAIL_MAIL_FEEDBACK_TOKEN=
EMAIL_MAIL_OPS_ENABLED=false
VITE_MAIL_SEND_UI_ENABLED=false
```

R8 archive storage reuses `MailSpoolStore` (a bean available only with `EMAIL_MAIL_INBOUND_ENABLED=true`). The dedicated outbound worker also requires that component: it must not start if the persistent spool is not configured. **Enabling mailbox sending therefore requires the durable encrypted spool, but does not mean exposing an inbound MX listener.** The archive fails closed with a 256 KiB hard cap on signed mail; source drafts already have a 64 KiB UTF-8 body cap.

All internal endpoints must remain off the public Cloudflare Tunnel/Pages routes, and the dedicated MTA feedback token must never appear in React/Vite build variables or browser assets.

## Example normalized feedback contract (private, trusted MTA adapter only)

```http
POST /internal/v1/mail/outbound/feedback
Authorization: Bearer <internal-service-token>
X-UnifyDesk-Mail-Feedback-Token: <dedicated-mta-secret>
Content-Type: application/json

{
  "organizationId":"123",
  "mailboxId":"456",
  "intentId":"789",
  "recipientId":"987",
  "address":"recipient@example.com",
  "externalEventId":"postfix-queue-ABC-delivered-1",
  "type":"DELIVERED",
  "smtpCode":"250"
}
```

`type` can be `DEFERRED`, `DELIVERED`, `BOUNCED` or `COMPLAINED`. Snowflake IDs are decimal strings. **This is not a public email webhook.** Suppressions, complaint blacklisting and the actual trusted DSN-to-event adapter have NOT yet been connected to the local MTA. Sending must stay disabled until those pieces are proven.

## Performance / cost

- Source MIME bounded at 64 KiB, signed archive at **256 KiB**, and maximum 20 recipients per outgoing intent. DKIM MIME preparation and archive verification are O(n) time/space, with a fixed hard upper limit. One archive object is shared by all recipient handoff attempts.
- Indexed point lookups by organization, mailbox, intent and recipient; `FOR UPDATE` serializes a single recipient's feedback events. Archive creation is unique-key idempotent.
- Operator views return only aggregates or at most 100 uncertain handoffs. Partial indexes narrow active/unknown queue scans. Long-term event history needs retention policy, off-node encrypted backups and legal/compliance review before public release.
- Uses existing Spring Boot process, PostgreSQL, Redis, Postfix and local persistent storage. **No new managed cloud product or additional running service was provisioned.** Disk, backups, scanner RAM and outbound IP reputation are not free and must be budgeted.

## Release blockers — NOT verified by running tests

- Java 25 Maven build/Spotless/JUnit; Node 24 Vite builds with flags on/off. Existing GitHub Actions failure before runner execution must be diagnosed separately.
- PostgreSQL fresh Flyway V1–V19 and upgraded V18→V19 with real signed MIME, constraints, concurrent sender claims, idempotent archive insert and corruption tests.
- Crash matrix: before fsync, after fsync but before database insert, after SMTP DATA but before 250, after SMTP 250 but before database commit, and during Sent-folder insertion. All uncertain outcomes must remain `UNKNOWN` and never automatically resend.
- Multi-recipient To/Cc/Bcc sending: prove shared signed MIME is stable and Bcc isn't present in visible headers.
- Verified private MTA origin, exactly-once feedback delivery, duplicate/reordered and contradictory DSN events, spoofed recipient IDs, ambiguous queue ID, secret rotation and audit history.
- Establish actual bounce correlation: reliable per-recipient Postfix queue-ID/VERP mapping to the feedback request, RFC 3464 DSN parsing in an isolated trusted local adapter, complaints/suppression and abuse suspension.
- Monitor spool disk headroom, backups and restore, orphan reconciliation, feedback retention, bounce rates and recurring unknown outcomes; test queue-lag alerting on the real mail node.
- Independent security review of mailbox ACLs, revoked grants, sender identity and any operator action; IAM parity with UnifyDesk App before GA.

**Still missing for a complete mail product:** public MX and inbound MTA configuration, real DSN adapter, suppressions, attachment handling, message search, authoritative delivery/bounce reconciliation, mailbox recovery, and IAM parity. Do not merge this R8 branch or enable SMTP solely based on source code being present.
