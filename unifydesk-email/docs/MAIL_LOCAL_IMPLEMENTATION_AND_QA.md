# Mail local implementation and QA

Updated October 21, 2026. This supersedes the old GitHub-only verification
status for the code tested locally. It does not supersede production release
gates or claim that a dedicated mail node has passed acceptance.

## Implemented

| Area | Result |
| --- | --- |
| Attachments | Bounded streaming listing and download of leaf MIME attachments from scanned inbound mail and verified Sent archives. Transfer decoding, safe filenames, forced attachment/octet-stream responses, no-store and nosniff. HTML is never rendered. |
| Mailbox privacy | Activation cannot grant access to an already active mailbox. Existing delegates control separate read/send/delegation grants. Current session, user, domain, organization and grant are checked during downloads. Content-free read and grant audit records are persisted. |
| Message lifecycle | Delegates can archive, move to Trash and restore messages. Read-only users cannot organize another person's mailbox. Trash content cannot be downloaded until restored. |
| Retention | Off by default. Requires an explicit 7–3650 day mailbox policy, no legal hold and a verified backup reference newer than the trash operation. Durable PURGING is committed before unlink; directory fsync precedes exactly-once quota release. A crash after unlink cannot make a missing object restorable. |
| Storage accounting | Inbound and outgoing archives reserve mailbox quota. Concurrent archive preparation charges once. V25 accounts for older outgoing archive bytes. |
| Account security | Encrypted authenticator enrollment, TOTP replay prevention, ten single-use hashed recovery codes, password change, operator-issued one-time password recovery and session revocation. Recovery does not bypass MFA. Enrollment is off by default; disabling enrollment does not disable an existing factor. |
| Custom roles | Owners can create/update scoped organization roles and assign/remove them from other active non-owner members after password/MFA reauthentication. Role changes revoke affected sessions. Organization permissions never imply mailbox content access. |
| Monitoring | Private aggregate Prometheus gauges for uncertain SMTP, scan backlog/age, quarantine, free storage and collection failure. Example alert rules are in `k8s/mail-alert-rules.yaml`; they have not been installed in production. |
| Integration fixes | PostgreSQL timestamp binding during scan promotion is corrected. Java and shell line endings are portable between Windows and Linux. |

The inbound Postfix pipe explicitly sets `eol=\r\n`, preserving the RFC 5322
framing required by the bounded header reader. The
[Postfix pipe manual](https://www.postfix.org/pipe.8.html) documents its
default LF delimiter; the real SMTP test checks the stored CRLF framing.

Identity decision: retain **independent Email accounts with optional SSO**.
The local account system operates without UnifyDesk App. No SSO provider has
been configured or validated in this increment; federation remains optional.

## Reproduce the disposable local stack

From `unifydesk-email/`, with Docker available:

```sh
docker compose -f tests/mail-qa/compose.yaml up --build --abort-on-container-exit --exit-code-from runner
```

The stack publishes no host ports and does not read `.env`, production
credentials or customer mail. It creates its own PostgreSQL, Redis, actual
clamd/Rspamd and loopback-only Postfix on port 2526. The runner refuses to
start outside its explicitly marked disposable Docker environment.

Tests use a private runtime database separate from test schemas. The runner
uses a fixed QA-only Snowflake ID, synthetic accounts and QA-only credentials.
The ClamAV engine uses a small local test signature; this proves protocol and
malware-fixture rejection, not production signature freshness or capacity.

After inspection, remove only this QA stack and its task-owned volumes:

```sh
docker compose -f tests/mail-qa/compose.yaml down --volumes
```

The cleanup removes the QA Maven cache as well. Existing application,
monitoring and infrastructure containers are separate.

## Verification status

- Linux Java 25: 113 tests passed, zero failures/errors/skips, with real
  PostgreSQL migrations, Linux directory fsync and actual scanner adapters.
- Fresh V1–V26 migrations and upgrades from **every prior version V1–V25**
  passed against disposable PostgreSQL 18.6.
- Real HTTP checks passed for private intake authentication/idempotency,
  scan promotion, cross-tenant/admin content denial, decoded attachment
  downloads, explicit delegation/revocation, custom-role capabilities,
  MFA/replay handling, recovery and session invalidation.
- Node 24 production builds passed with hosted-mail flags all off and all on.
- Actual Postfix recipient allowlisting, unknown-recipient rejection and
  unrelated-domain relay denial passed. A synthetic SMTP message reached
  durable intake and a scanned mailbox with CRLF framing preserved.
- An age-encrypted database/MIME snapshot was decrypted and restored into
  a separate database/storage location. Every referenced MIME size and
  SHA-256 matched.
- The offline pipe test passed its strict 202-only acknowledgment,
  error deferral, stable delivery keys and input checks.

Evidence is saved locally in `mail-container-qa.log`,
`mail-linux-verification.log`, `mail-postgres-verification.log` and
`qa-reports/` (ignored build artifacts). The disposable QA containers and
their database/cache volumes were removed after preserving the reports.

The container suite also tests concurrent inbound replays, mismatched MIME
replays, quota exhaustion, scanner failures, admission revocation, object
tampering, grant/session revocation mid-download, concurrent Sent archives,
retention legal holds/backup gates and retry after unlink-before-commit.

## Retention and recovery operating contract

Retention is disabled by `EMAIL_MAIL_RETENTION_ENABLED=false`, and each
mailbox starts with `trash_retention_days=0`. No customer endpoint can
enable retention, change legal holds or attest that a backup was verified.

Before an operator enables a policy on an exact mailbox, freeze intake and
outbound changes, take a consistent database plus MIME snapshot, encrypt it,
copy it off-node, restore it into an isolated database/storage location and
verify every retained MIME size and SHA-256. Only then set that mailbox's
`backup_reference` and `backup_verified_at` to identify the verified snapshot.
Choose retention days explicitly and review legal holds before enabling the
worker. Do not treat a local backup rehearsal as an off-node production backup.

The worker processes at most ten marks and ten purges per poll. A durable
PURGING message is not restorable. New legal holds, disabled retention,
changed retention duration or missing backup evidence stop physical purge.
Uncertain/pending outgoing recipients prevent an archive from being marked.
Quota discrepancies stop cleanup instead of flooring counters or deleting
additional content. Keep the database tombstone/audit history for recovery.

Unreferenced objects remain an advisory orphan inventory. This increment
does not automatically delete files left by ambiguous database commits.
That requires a separately reviewed writer/garbage-collector protocol.

## Remaining release work

- Outgoing attachment uploads, full HTML rendering/sanitization, mailbox
  search/threading and IMAP are not implemented by this increment.
- Optional SSO needs an actual provider configuration and federation tests.
- Production acceptance still needs the actual persistent encrypted volume,
  off-node backup/restore, disk-full/restart/load tests and scanner signature
  updates, plus alert installation and an on-call recovery procedure.
- Public MX, TLS, dedicated mail IP/PTR, DNS alignment, bounce routing,
  deliverability and independent security/release review remain separate gates.
  No local test authorizes enabling public sending, changing DNS or replacing SES.

The authenticator follows [RFC 6238](https://www.rfc-editor.org/rfc/rfc6238)
and is checked against its SHA-1 test vectors. Factor changes/recovery require
reauthentication, following the considerations in the
[OWASP MFA guidance](https://cheatsheetseries.owasp.org/cheatsheets/Multifactor_Authentication_Cheat_Sheet.html).
