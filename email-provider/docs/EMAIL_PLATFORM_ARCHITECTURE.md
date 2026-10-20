# UnifyDesk Email — Architecture and Operational Design

**Status:** architecture reference / implementation plan, not production certification.  
**Updated:** 2026-10-20  
**Scope:** standalone `email-provider/` product, not the UnifyDesk App's existing managed SES delivery.  
**Safety:** Do not turn on public MX, SMTP submission, live outbound transport, or mailbox feature flags based on this document alone.

## 1. Product boundaries

UnifyDesk Email serves two related but distinct workloads:

1. **Programmable email delivery** (SES/SendGrid-like): organization → project → API key → approved sending domain → send request → durable queue → MTA → remote recipient. Transactional and marketing workloads require separate policy, quotas, reputation pools and suppression decisions.
2. **Hosted business mail** (Gmail/Exchange Online/Zoho Mail-like): organization → verified mail domain → user/identity → explicitly granted mailbox → inbound messages, inbox/search, drafts/replies, Sent, attachments and lifecycle operations.

Share a control plane for domain ownership, org administration, billing and observability only where authorization boundaries allow. **Do not** allow a bulk-send project API key to read private mailbox contents or impersonate mailbox users.

The UnifyDesk App is an API customer of the standalone product; keep its currently operational SES delivery unchanged until this product meets its release gates.

## 2. System architecture

```text
 React webmail + Admin UI        Customer application / UnifyDesk App
           | HTTPS session                    | HTTPS project API key
           +-------------------+---------------+
                               v
                Spring Boot Email control/API plane
                AuthN/AuthZ, tenants, DNS, validation,
                policy, quota, billing, mail metadata
                    |             |           |
              PostgreSQL        Redis        Kafka/outbox
                    |             |           |
                    +-------------+-----------+
                                  |
                   +--------------+-----------------+
                   |                                |
           OUTBOUND SUBSYSTEM                 INBOUND SUBSYSTEM
        MIME/DKIM + Postfix mailer     Public MX → isolated Postfix
        queue / retries / feedback      recipient validation at RCPT
                   |                     durable SMTP queue / pipe
             destination MX                private HTTP intake
                   |                       durable raw MIME spool
          Gmail/Outlook/Zoho/etc.          malware+spam scan
                                           quarantine / Inbox/Spam
                                           mailbox API / indexing
```

**Control plane** (React, Spring Boot, database) is not the SMTP mail transport. Postfix delivery nodes must own transport queues, public SMTP endpoints and deliverability reputation. Outbound and inbound MTAs can start on a small host but need isolated configuration, network listeners, queues and failure domains. A separate listener alone does not establish separate IP reputation.

### Repository implementation map

| Concern | Existing source/reference | Current confidence |
|---|---|---|
| API product and sending pipeline | `README.md`, `docs/PRODUCT_API_R1.md`, backend | Code and documented design; runtime QA still required |
| Sending safety and transport | `docs/DELIVERY_SAFETY.md`, `postfix/` | Live deliverability and feedback need verification |
| Domain/mailbox ownership | `docs/MAIL_DNS_INBOUND_R2.md` | Feature-gated design |
| Scan, inbox and preview | `docs/MAIL_SCAN_INBOX_R3.md`, `docs/MAIL_READER_R5.md` | Feature-gated; do not assume operational |
| Compose, outgoing and Sent | `docs/MAIL_COMPOSE_R6.md`, `docs/MAIL_OUTBOUND_R7.md`, `docs/MAIL_SENT_FEEDBACK_R8.md` | Draft/QA requirements documented |
| Public MX adapter candidate | `postfix/inbound-r10/` | Source/config samples; explicitly not deployed |
| Release checks | `docs/MAIL_R1_R9_CONSOLIDATION_QA.md`, `docs/MAIL_RELEASE_GATES_AND_IAM_PLAN.md` | QA gates, not evidence of passed live tests |

**Important distinction:** committed source ≠ built/tested ≠ deployed ≠ reliably deliverable. Feature flags must remain off until their actual deployment prerequisites and end-to-end QA pass. Historical document status may refer to earlier PRs; inspect current branch, merges, runtime flags and CI before asserting a particular feature is shipped.

## 3. Outbound mail: exact sequence

Example: `alice@company.com` sends to `bob@gmail.com`.

1. Authenticate API project key or mailbox user. Check organization state, sender permissions, verified domain, mailing consent/suppressions, message limits and idempotency key.
2. Persist the send intent, immutable recipient list and transactional outbox event before returning acceptance. HTTP `202 Accepted` means queued, **not delivered**.
3. A bounded worker claims the job; enforce Redis+database limits without relying on in-process state alone.
4. Construct MIME with correctly encoded headers and envelope recipient; sign with the sending domain's DKIM key. Bcc belongs in SMTP envelope but not visible MIME headers.
5. Hand off to a trusted, non-open-relay Postfix listener. Postfix queues durably and resolves recipients' MX records. It performs SMTP deliveries to their servers over TCP/25, opportunistically using STARTTLS when available.
6. Handle replies: SMTP 4xx is normally deferred/retried with backoff; 5xx is typically a definitive failure, subject to interpretation and bounce classification. A 250 from local Postfix means **local handoff**. A 250 from destination MX means remote SMTP acceptance, **not proof of Inbox placement or reading**.
7. Correlate Postfix queue IDs, envelope sender/recipient and DSN feedback; persist remote states separately: deferred, accepted/delivered-at-SMTP, bounced, complained, unknown. Only issue customer webhooks from trusted verified events.
8. Update suppression and reputation signals, notify the project, and retain enough audit data for recovery.

**Failure semantics:** if the network drops after SMTP DATA and acceptance is uncertain, mark `UNKNOWN`, reconcile against the MTA queue/logs, and never blindly auto-retry an ambiguous message (duplicate risk). Exact-once end-to-end email delivery cannot be guaranteed across independent SMTP servers.

**Isolation:** separate transactional, human business mail and marketing bulk by logical queue, service credentials, rate limits, bounce policy and ideally egress IP/pool. Human mailbox credentials must never grant bulk campaign authority.

## 4. Inbound email: exact sequence

Example: an outside sender emails `support@company.com` hosted by UnifyDesk.

1. Customer verifies the domain through an ownership TXT challenge; operator and tenant explicitly activate inbound after readiness review. Ownership verification is **not** MX readiness.
2. Customer publishes an MX record pointing at a stable UnifyDesk mail hostname; the hostname resolves to a publicly reachable mail-node IP. Incoming SMTP port 25 must be available.
3. A remote sending MTA looks up the domain MX and connects to isolated inbound Postfix.
4. At `RCPT TO`, Postfix checks a currently active tenant/domain/mailbox against a narrowly privileged read-only recipient directory. Reject unknown addresses before accepting DATA; never accept arbitrary destinations (open relay).
5. Postfix queues DATA durably and passes each envelope recipient + original RFC 5322 MIME into a private authenticated intake adapter with a stable delivery key. Do not trust `To:` headers for actual envelope routing.
6. Backend validates recipient and tenant again, streams raw MIME to a durable spool, fsyncs/atomically commits, reserves quota transactionally, stores a pending-scan record, and only then returns private HTTP 202.
7. If the adapter does not receive confirmed HTTP 202, it must return a temporary failure so Postfix retains and retries its durable queue item.
8. Scan malware and spam, quarantine malicious content, then idempotently promote safe items to Inbox/Spam. Store immutable object keys, checksums, size and indexed metadata in PostgreSQL; keep original MIME and attachments outside the relational database.
9. Authorized mailbox users read using the session-scoped HTTPS API. Enforce per-user mailbox ACLs, safe text/HTML rendering, attachment restrictions, search/index permissions, and data retention.
10. Back up encrypted objects, metadata and keys off node; test restoration and orphan/duplicate reconciliation.

**Storage design:** PostgreSQL = domain/mailbox, ACL, folder, metadata, state, index pointers; durable disk/S3-compatible object store = RFC 5322 MIME and attachments; search index = query/search; Redis = short-lived limits/cache, never authoritative mail storage. A local-path Kubernetes PVC alone is neither HA nor automatically encrypted/backed up.

## 5. Protocols and network responsibilities

| Protocol | Ports | Role |
|---|---|---|
| SMTP between MTAs | TCP 25 | Public inbound MX reception and outbound delivery to remote MX |
| Authenticated SMTP submission | TCP 587 with STARTTLS (optionally 465 implicit TLS) | Standard external mail clients submit outbound mail |
| IMAP over TLS | TCP 993 | External clients synchronize hosted mailboxes and folders |
| HTTPS | TCP 443 | Webmail, administration, application APIs and webhooks |

Webmail may initially use only HTTPS; desktop/mail applications require a separately designed IMAP service (or other supported integration) and SMTP submission. An HTTP Cloudflare Tunnel is not equivalent to public SMTP MX reception or SMTP transport. Keep `/internal/v1/` off all public routes, secured by network ACL and rotating service credentials.

## 6. DNS and authentication

A hypothetical hosted domain `company.example` could configure:

```dns
company.example.                   MX 10 mx1.mail.unifydesk.in.
mx1.mail.unifydesk.in.            A  203.0.113.10
company.example.                   TXT "v=spf1 include:_spf.mail.unifydesk.in ~all"
selector1._domainkey.company.example. TXT "v=DKIM1; k=rsa; p=EXAMPLE_PUBLIC_KEY"
_dmarc.company.example.            TXT "v=DMARC1; p=none; rua=mailto:dmarc@company.example"
```

These are **illustrative reserved addresses and placeholders, not live configuration**. Select an actual hostname/IP and publish validated key material. Define and publish `_spf.mail.unifydesk.in` correctly if advertising an include. The `rua` inbox must exist/authorize reporting as required. Move DMARC from monitoring to enforcement only after evaluating all legitimate senders.

- **MX:** locates the incoming SMTP service; changing MX transfers inbound routing.
- **A/AAAA:** resolves the MX host; MX targets must be hostnames, not CNAME aliases.
- **PTR/rDNS:** IP-to-host mapping controlled by the IP owner/cloud provider; match EHLO/forward DNS operationally.
- **SPF:** authorizes envelope sender infrastructure, not necessarily visible `From:`.
- **DKIM:** cryptographic domain signature, requiring selector, key rotation and protection.
- **DMARC:** alignment between visible From domain and a passing SPF or DKIM identity plus domain policy.

A customer using Zoho or Microsoft for inbound mail can use UnifyDesk for outbound application email **without** moving their MX records. A customer migrating hosted mail **must** coordinate MX cutover, existing mail migration, retention and rollback.

## 7. Shared versus dedicated IPs

A **shared sending IP** is used by several tenants; poor behavior by one can harm the pool. A **dedicated sending IP** is assigned to one customer or isolated workload; that customer gains more control over reputation but must generate enough consistent legitimate volume for warming and monitoring. It is not automatically more deliverable.

**Recommended initial UnifyDesk policy:**
- Start with a small, tightly moderated shared transactional pool and separate business-mail pool; isolate marketing/bulk traffic from both.
- Use dedicated sending IPs for appropriately approved higher-volume customers when operationally justified, not all new tenants.
- Use static public IPs, outbound TCP/25 permission and provider-controlled PTR for outbound Postfix.
- Maintain IP-pool assignments, limits, gradual warm-up, bounces, complaints, suppression and blacklist monitoring; halt abusive tenants.
- Separate **inbound MX IP** from the notion of a **dedicated outbound IP**. Incoming reception usually does not require each tenant to have a unique public IP.
- Dedicated IP is still subject to domain-level reputation, authentication and recipient behavior.

## 8. Industry architecture comparison (publicly observable patterns only)

| Provider | Publicly observable capabilities | What to borrow |
|---|---|---|
| Gmail / Google Workspace | Hosted MX, user mailboxes, webmail, spam controls, standard client integrations, domain auth | Strong tenant and mailbox isolation, spam defenses, fast search, reliability |
| Microsoft Exchange Online / Outlook | Hosted mailboxes, transport pipeline, enterprise policy, client access, mailbox availability/recovery | Retention/compliance policies, reliable replicas and enterprise access control |
| Zoho Mail | Business-domain hosting, MX onboarding, webmail, SMTP/IMAP access, filtering | Straightforward domain onboarding, economical business mailbox UX |
| Amazon SES / SendGrid / Mailgun | API/SMTP sending, authentication, queues/feedback and reputation tooling | Sending-domain verification, project APIs, bounce lifecycle and dedicated IP pools |

These are **functional architecture comparisons**, not claims to know proprietary internal server layouts or exact deployment topology.

## 9. Production roadmap and acceptance gates

### P0 — Validate current code (no public SMTP or DNS changes)
- [ ] Audit actual `dev` code, migrations, historical stacked PR status, feature flags and endpoint authorization.
- [ ] Run JDK 25 Maven format/build/test and frontend Node 24 builds with feature flags off/on.
- [ ] Execute fresh and upgrade Flyway migrations against disposable real PostgreSQL.
- [ ] Prove queues, transactional outbox, idempotency, tenant ACL and failure recovery in integration tests.
- [ ] Verify existing UnifyDesk App SES remains unaffected.

### P1 — Safe outbound testing
- [ ] One staging sending domain and one mail node with static IP, PTR/EHLO, outbound TCP/25, SPF/DKIM/DMARC.
- [ ] Trusted Postfix listener; prove no unauthorized relay.
- [ ] Verify actual Gmail/Outlook/Zoho recipient SMTP delivery and DKIM; no Inbox-placement guarantee.
- [ ] Test 250/4xx/5xx, timeout after DATA, DNS outage, Postfix restart, duplicate events, bounce DSNs and suppressions.
- [ ] Monitor queue size, backlog age, delivery lag, complaint/bounce rates and IP reputation.

### P2 — Durable private inbound and mailbox
- [ ] Follow `postfix/inbound-r10/` guide on isolated staging; do not simply expose sample listener.
- [ ] Enforce verified domain + active mailbox + inbound enabled, including RCPT-time and DB-time rechecks.
- [ ] Confirm encrypted persistent mail objects, disk-full response and off-node backup/restore.
- [ ] Confirm private handoff only acknowledges successful durable storage.
- [ ] Pass malware/spam scanning, MIME parsing, inbox/Spam ACLs, attachment safety and recovery tests.
- [ ] Cut over only a controlled test domain MX after QA.

### P3 — Real hosted-mail usability
- [ ] Safe text/HTML rendering, attachments, pagination, mailbox search and indexing.
- [ ] Complete drafts, immutable Sent archive, Bcc-safe sending, reply/forward, folders, trash and retention.
- [ ] MFA, account recovery, mailbox delegation/revocation/audit and tenant separation.
- [ ] Authenticated SMTP submission and IMAP compatibility for external clients.
- [ ] Mail import/export, deletion/account closure, monitoring, restore drills and documented support response.

### P4 — Early access and scale
- [ ] Separate bulk/transactional/human IP pools and policies; conservative new-tenant quotas.
- [ ] Domain-by-domain onboarding, DNS verification guidance, gradual warming and reputation checks.
- [ ] Run abuse/fraud reviews, deliverability testing, incident response exercises and measurable SLOs.
- [ ] Scale API, queues, Postfix and storage independently; only promise HA after tested multi-failure recovery.

## 10. Operator runbook / failure examples

| Symptom | Inspect first | Safe response |
|---|---|---|
| API accepted but no recipient email | Outbox, Kafka lag, worker leases, Postfix queue ID and SMTP results | Distinguish queued/handoff/remote acceptance; no blind duplicate sends |
| SMTP 4xx / greylisting | Remote response and retry queue | Bounded backoff, monitor age |
| SMTP 5xx / hard bounce | Enhanced status code, DSN validation | Mark failed/suppress according to policy |
| High spam placement | SPF/DKIM/DMARC alignment, complaints, IP/domain reputation, engagement | Throttle, investigate and improve list quality; no guaranteed Inbox outcome |
| Incoming recipient rejected | Domain/MX, tenant state, mailbox status, recipient map | Fix ownership/activation; reject unknown recipients before DATA |
| Mail accepted by Postfix but absent from inbox | Pipe retry, private API 202, spool/fsync, scanner backlog, quotas | Preserve mail in MTA queue on intake failure; reconcile pending objects |
| Disk near full | Mail spool/PVC and backups, orphan cleanup policy | Stop admission safely, alert, expand durable capacity; never silently drop messages |
| Worker crash after SMTP DATA | Lease state, Postfix queue/logs, remote feedback | Mark ambiguous UNKNOWN; reconcile before any resend |
| Stolen API key or compromised mailbox | Audit events, scoped grants, transport pools | Revoke credentials/session, quarantine sending, rotate secrets, review impact |

## 11. Related documentation

- [Product boundary](PRODUCT_BOUNDARY.md)
- [Product API](PRODUCT_API_R1.md)
- [Delivery safety](DELIVERY_SAFETY.md)
- [Inbound staging](MAIL_DNS_INBOUND_R2.md)
- [Scan and inbox](MAIL_SCAN_INBOX_R3.md)
- [Mailbox reader](MAIL_READER_R5.md)
- [Compose](MAIL_COMPOSE_R6.md)
- [Mailbox outgoing](MAIL_OUTBOUND_R7.md)
- [Sent and feedback](MAIL_SENT_FEEDBACK_R8.md)
- [QA consolidation](MAIL_R1_R9_CONSOLIDATION_QA.md)
- [Release gates](MAIL_RELEASE_GATES_AND_IAM_PLAN.md)
- [Postfix inbound R10](../postfix/inbound-r10/README.md)

**Rule:** no public deployment or MX cutover until security, durability, SMTP behavior and restore testing are independently signed off.
