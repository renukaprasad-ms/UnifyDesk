# UnifyDesk Mail R9 — signed bounce correlation, suppression, safe recovery inventory

**Source-only development. No live MX/Postfix changes. No verified compilation or integration tests. Not production-ready.**

R9 is stacked onto the R8 draft branch (PR #370). All earlier R1–R8 drafts remain unmerged into `dev`. The established UnifyDesk App SES managed-email channel remains unchanged.

## R9 features

**1. Cryptographically correlated return path.** The R7 outbound SMTP worker now uses a distinct `MAIL FROM` of the form:

```text
ud+<intentId>.<recipientId>.<16-hex HMAC tag>@<bounce-domain>
```

The **visible From header is still the verified mailbox identity**. The 16-hex HMAC tag is the first 64 bits of HMAC-SHA256 over intent and recipient IDs, keyed by a server-only secret of at least 32 bytes. This keeps the local part <=64 ASCII characters even for full 64-bit IDs. The recipient's message retains the same DKIM-signed MIME and stable Message-ID from R7/R8. Only an authorized private MTA adapter should deliver incoming DSNs to this application.

The bounce domain requires real DNS MX, an inbound bounce-only route to Postfix, and anti-abuse controls. **Those infrastructure steps were not deployed**. Without an operational bounce MX, live sending MUST remain disabled to avoid undeliverable return paths.

**2. Strict trusted DSN intake.** `POST /internal/v1/mail/outbound/dsn` consumes up to 65,536 raw bytes, using the existing internal bearer filter plus a **separate dedicated feedback secret**. The parser requires exactly one `Final-Recipient`, one `Action`, and one `Status` (valid 2.x/4.x/5.x status appropriate to delivered/delayed/failed). Multiple recipient blocks or contradictory DSNs are rejected rather than applied to the wrong mailbox. The signed return-path identifies exactly one intent and recipient; PostgreSQL must confirm the Final-Recipient address equals that recipient. The event ID is derived from SHA-256 of envelope address and raw report, so replay is idempotent under the existing R8 `UNIQUE(recipient_id,external_event_id)` constraint.

**Important:** This is an API/decoder designed for a *trusted local Postfix bounce adapter*. Postfix's actual bounce pipe/service and safe intake of arbitrary RFC 3464 multipart reports are not yet integrated or tested. Many compliant DSNs may contain multi-part/multi-recipient content and will be rejected conservatively. Deliveries confirmed solely by remote MTA log events still need a separate trusted normalized feedback adapter. Do not declare complete real-world DSN processing.

**3. Tenant-isolated hard bounce suppression.** Flyway V20 creates `email_mail_outbound_suppressions(organization_id,recipient_address)`. An authenticated `COMPLAINED` event suppresses immediately; a `BOUNCED` event suppresses only with a 5.x permanent enhanced status. Suppression insertion and the recipient feedback transition occur in the same PostgreSQL transaction. The mailbox worker checks suppression before contacting its dedicated SMTP relay. This is independent of the marketing suppression system.

Suspended/complained addresses require an explicit future auditable appeal/removal policy. There is intentionally **no public suppression removal endpoint**. A narrow concurrency window remains between checking suppression and remote SMTP delivery; operator freeze/queue controls must be verified for high-risk incidents.

**4. Orphan-file recovery inventory, no deletion.** Private `GET /internal/v1/mail/outbound/orphans`, gated on `EMAIL_MAIL_OPS_ENABLED`, scans at most 2,000 entries from the existing encrypted persistent mail directory and returns at most 100 candidates. It considers only safe UUID .eml filenames, files older than 72 hours, no symlinks, and confirms a candidate is not referenced by inbound jobs, outbound archives or mailbox messages. This is an **advisory sampled inventory**, not proof of permanent orphanhood and not a full-directory report for large mail stores. It performs **no file deletion, no quota adjustment and no automatic cleanup**.

A future explicit cleanup job requires off-node encrypted backups, a transaction-safe two-phase quarantine/mark window, retention/legal holds, proof no pending database commit still references a file, and operator-reviewed metrics.

## Environment and cost

```env
EMAIL_MAIL_BOUNCE_DOMAIN=
EMAIL_MAIL_BOUNCE_HMAC_SECRET=
EMAIL_MAIL_OUTBOUND_ENABLED=false
EMAIL_MAIL_SUBMISSION_ENABLED=false
EMAIL_MAIL_FEEDBACK_ENABLED=false
EMAIL_MAIL_FEEDBACK_TOKEN=
EMAIL_MAIL_OPS_ENABLED=false
```

- An enabled outbound worker **fails startup closed** without a configured bounce domain and HMAC secret. Enabling the DSN intake path also depends on the outbound bounce verifier, so all relevant toggles/configuration must be provided together in isolated QA.
- Rotate HMAC keys with an overlap procedure only after a multi-key verifier is designed. Naive key replacement invalidates bounces for previously queued mail.
- Use server-only environment secrets, never Cloudflare Pages/Vite variables.
- Reuses existing Spring Boot, PostgreSQL, Postfix and local disk. **No additional managed cloud service** introduced, but DNS, static mail IP, backups, maintenance, spam control, disk and operational alerting remain real expenses.
- Signed return path generation/validation is O(1); bounded DSN parsing O(n) for n<=65,536; per-recipient suppression is an indexed O(log N) lookup; orphan inventory inspects <=2,000 filenames and performs <=100 indexed reference probes, using O(100) in-memory results.

## Required independent QA before any merge

1. Run Java 25 Maven build, Spotless and all tests including R9; Node 24 Vite builds with both flag combinations. Existing GitHub Actions runner failures block release; source-only inspection is not a substitute.
2. Run PostgreSQL V1–V20 fresh migration and V19→V20 upgrade against real PostgreSQL. Test transaction rollback, duplicate feedback, suppression and cross-organization recipient FK constraints.
3. Verify actual Postfix envelope return path, DNS MX, signed VERP validation, positive/negative/multiple-recipient DSN fixtures, forged token, tampered recipient ID, token rotation and duplicate reports. Use an isolated non-public SMTP staging server.
4. Test one sender to To, Cc and Bcc recipients; each should have a distinct signed return path and correctly correlated feedback while retaining a shared signed MIME body.
5. Test suppression on 5.x bounce and complaint, no suppression on 4.x deferral, and no SMTP handoff on a suppressed recipient. Prove simultaneous feedback and sending do not bypass operational safety controls.
6. Test file inventory on real encrypted PVC with 0, 100 and >2,000 entries, symlinks, in-flight writer, active references, DB outage, missing volume, disk-full, recoverable orphan and backup restoration.
7. Validate worker crash during/after SMTP DATA, Postgres commit ambiguity, archived MIME integrity, orphan reconciliation and **no blind retry on UNKNOWN**.
8. Confirm `/internal/v1/` endpoints are not exposed by Cloudflare Tunnel, frontend proxy or public ingress. Verify both internal bearer and dedicated feedback secret, rate limits, audit trails, service credential isolation and no leakage of message bodies.

## What comes next

- Wire and verify an actual local Postfix DSN pipe adapter and remote-delivery/bounce event collector; add operator-only suppression and orphan resolution after a recovery policy is proven.
- Public inbound SMTP/MX and attachment architecture, quota billing, storage recovery, anti-abuse and audit policies.
- Full IAM parity with UnifyDesk App (shared login, MFA, custom roles, delegation, explicit read grants and audit) before public early-access launch.

**No source branch should be merged to `dev` solely because this PR exists. R1–R9 remain blocked on build, CI, migration, live MTA and security evidence.**
