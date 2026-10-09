# UnifyDesk Mail — foundation R1

Status: **schema-only development increment; not production-ready**.

## Existing systems and safety boundaries

- Keep `email-provider/backend` outbound Postfix + Kafka delivery, domain verification and API-key authentication untouched.
- Keep `unifydesk-app` Managed Email on AWS SES; do not change feature gates or provider selection.
- Add mailbox hosting independently: inbound SMTP/MX, mailbox authorization, MIME object storage, webmail, IMAP and submission must all be gated until audited.
- No public inbound port, MX change, mailbox provisioning API or UI is activated by this increment.

## Foundation

`V14__mailbox_foundation.sql` defines organization-owned domains, mailboxes and mailbox message metadata. The original RFC 5322 message is intended for an encrypted, immutable object in private object storage; a relational row holds only its object key and listing metadata. Domain addresses are unique globally. New mailboxes are DISABLED, domains PENDING. Application services must verify the domain-to-organization and mailbox-to-domain relationships transactionally.

## Next development slices (not implemented here)

1. Add domain ownership verification and a fail-closed organization-scoped mailbox/domain API; disallow mailboxes until DNS proof and domain status are verified.
2. Add mailbox users, roles, credential/SSO binding, per-mailbox delegation and strict ownership enforcement on every read and write. An API project key must **never** grant mailbox access.
3. Add inbound SMTP gateway with recipient allowlisting, relay protection, TLS, size limits and queue admission. Distinguish envelope sender from From header. Deliver to private object storage only after a durable record/queue transaction.
4. Add malware/spam filtering, quarantine, rejected-recipient semantics, duplicate/delivery idempotency and bounce-safe operation.
5. Add compose/sent/drafts/reply/forward/attachments with limits, storage accounting and retention; keep mailbox outbound identity and queues isolated from marketing IP pools.
6. Add React mail UI, secure session APIs, cursor pagination and threading/search. IMAP compatibility follows controlled mailbox API and storage validation.

## Preconditions for a release

- Migrations from V1 through V14 pass on fresh and upgraded PostgreSQL with backups.
- Test incorrect organization/domain/mailbox combinations and mixed-tenant batch requests.
- Simulate Kafka replay, object-store partial failure, SMTP interruption, concurrent quota claims and cleanup of orphan objects.
- Verify DNS MX/A/PTR, SPF/DKIM/DMARC, outbound submission and live deliverability on dedicated static-IP mail nodes. Cloudflare Tunnel/Pages handles HTTPS interfaces, not generic public SMTP/MX.
- Never share marketing-sending IP reputation with mailbox transactional sending.
- Independent security and QA approval required. Do not enable `EMAIL_PROVIDER_DELIVERY_ENABLED` or `APP_UNIFYDESK_EMAIL_ENABLED` as a side effect of this branch.
