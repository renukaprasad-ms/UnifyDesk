# R10 — Local Postfix inbound SMTP to private UnifyDesk Mail

Status: SOURCE ONLY, NOT INSTALLED, NOT DEPLOYED. No DNS, firewall, k3s or
Postfix runtime was changed. Production SES/Managed Email is unaffected.

## Data flow

External SMTP (later) -> dedicated inbound Postfix -> PostgreSQL recipient
validation at RCPT -> Postfix durable queue -> 1-recipient private pipe ->
authenticated internal HTTP 202 -> durable MIME PVC + PENDING_SCAN row ->
local malware/spam scanning -> mailbox INBOX or SPAM.

No 202 = EX_TEMPFAIL 75, so Postfix retains its queued message and retries.
Re-deliveries from one Postfix queue ID and envelope recipient share a stable
delivery key and cannot be double-counted in the same mailbox.

## Default-deny domain and recipient checks

V22 adds email_mail_domains.inbound_enabled BOOLEAN NOT NULL DEFAULT FALSE.
DNS ownership proof alone NEVER opens incoming SMTP. Postfix's pgsql virtual
domain/recipient tables and InboundIntakeService both require:
- ACTIVE organization
- VERIFIED domain with inbound_enabled=true
- ACTIVE exact mailbox, with matching org/domain

Postfix rejects unknown addresses at RCPT, before collecting mail DATA.
The application checks the same gates again immediately before MIME writing
and inside its quota-reservation transaction.

## Install and verify only on an isolated staging mail node

1. Run Flyway V1 through V22 on an isolated PostgreSQL database (preserve the
   existing V14 usage migration). Run Maven tests, Java 25, Node 24 locally;
   GitHub Actions runners have not been executing steps.
2. Install a SEPARATE inbound Postfix instance with postfix-pgsql, curl and
   coreutils. Configure its main.cf from main.cf.example, its master.cf from
   master.cf.snippet and its two PGSQL maps from the .cf.example templates.
   Keep inet_interfaces = loopback-only while testing. Do NOT overwrite
   the existing sending/bounce Postfix instance.
3. Create an independent PostgreSQL READ-ONLY role with only SELECT on
   email_mail_domains, email_mailboxes and email_product_organizations
   (SQL example included). Keep DB credentials in 0640 Postfix config.
   Use loopback DB access or a private TLS connection. No public DB port.
4. Create a no-login udmail-inbound service account. Install the pipe script
   as /usr/local/libexec/udmail-inbound-pipe.sh (root-owned, 0755).
   Install a private inbound-curl.conf readable only by this service user
   (0400), containing only the Authorization bearer secret, 32+ characters.
   Keep real secrets out of the repository and Cloudflare Pages.
5. Assign a STABLE, unique short node ID to this Postfix instance in
   /etc/unifydesk-mail/inbound-node-id (example: mx1). Do not rotate it
   while mail remains queued. Each Postfix retry must reuse the same ID.
6. Configure a LOOPBACK-ONLY bridge at 127.0.0.1:8010 to the internal
   email-provider Spring service in k3s, with ingress ACL. The internal
   HTTP endpoint must NEVER appear on public Cloudflare Tunnel/Pages.
7. Provide a persistent encrypted/backed-up mail PVC, scanner services
   ClamAV and Rspamd, and working backend storage/quota/scan feature gates.
   Verify ClamAV RAM requirements on your low-cost Ubuntu server.
8. On your TEST domain, finish DNS TXT verification and mailbox activation.
   After security approval, enable inbound_enabled for that exact tenant
   using an operator-reviewed SQL UPDATE constrained by domain ID,
   organization ID and VERIFIED status. This PR exposes NO public toggle.

Example local checks (on the dedicated Postfix node):

  postconf -m | grep -w pgsql
  postmap -q test.example.net pgsql:/etc/postfix/pgsql-udmail-domains.cf
  postmap -q owner@test.example.net pgsql:/etc/postfix/pgsql-udmail-recipients.cf
  postmap -q unknown@test.example.net pgsql:/etc/postfix/pgsql-udmail-recipients.cf
  postfix check

Run bash email-provider/tests/inbound-r10-smoke.sh from the repository root
on the isolated Postfix node; this performs syntax/pgsql checks and does
NOT send email unless R10_SEND_LOCAL_FIXTURE=YES is set explicitly.

The first two pgsql lookups return a value for a prepared test domain/mailbox;
the unknown recipient returns no value. With inbound_enabled=false, neither
domain nor recipient lookup should return a value.

Use swaks/sendmail locally to check successful RCPT, rejected unknown
recipient, rejected unrelated-domain relay, real 202 durable intake,
one PENDING_SCAN row, exactly-once quota use, successful scanning and
Inbox/Spam access for a granted user. Force a backend timeout, scanner
outage, DB outage and full PVC; ensure Postfix RETAINS queued mail.

## Boundaries and safety

- Set enable_long_queue_ids=yes and preserve a unique, stable node ID;
  Postfix short queue IDs can be reused after older deliveries.
- Postfix pipe expands queue_id + recipient; the one-recipient delivery limit
  prevents sending one MIME to multiple mailboxes using one queue header.
- Stable keys include unique node ID, queue ID, and recipient SHA256 prefix.
- Only HTTP 202 confirms durable handoff. Redirects and HTTP 200 fail CLOSED.
- Postfix accepts at most 10,000,000 bytes (headroom for generated headers),
  while the backend imposes an independent 10 MiB cap. The Java spool
  reads in 8 KiB chunks; there is no new managed service or daemon to pay for.
- Public MX requires genuine inbound TCP 25, stable public IP, TLS/STARTTLS,
  SPF/DKIM/DMARC policy, queue alerts, firewall and rate/abuse protection.
  Cloudflare HTTP Tunnel is NOT an SMTP/MX endpoint.
- Test mailbox suspension after SMTP RCPT and before pipe delivery, Postfix
  queue retries/idempotency, malformed and oversized messages, MIME scan
  failures, SMTP relay denial, and database migration upgrade.
- Postfix pipe script and templates are only reviewable source in this PR;
  no actual end-to-end tests can be claimed until executed on the mail node.
- Full hosted mailbox GA still needs attachments, retention, IAM parity,
  recovery and operational monitoring.
