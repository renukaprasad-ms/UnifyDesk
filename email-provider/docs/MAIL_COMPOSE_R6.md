# UnifyDesk Mail R6 — drafts, reply preparation and held submission boundary

**Status: Source implemented on a development branch. Unverified, NOT merged, NOT production-ready.**

R6 stacks on PR #367 (R5), which depends on #366, #365, #364 and #362. Continue to use Amazon SES for the existing UnifyDesk App Managed Email channel; these changes do not touch its route.

## What works in source

- V17 adds organization/mailbox/user-scoped saved drafts. All relationship FKs, including reply target and submission intent, use **composite same-tenant references**.
- Console-session-only REST APIs:
  - `POST /console/v1/mail/mailboxes/{mailboxId}/drafts` — save a new unsent draft
  - `GET /console/v1/mail/mailboxes/{mailboxId}/drafts` — up to 50 of this user's saved drafts
  - `GET /console/v1/mail/mailboxes/{mailboxId}/drafts/{draftId}` — load one saved draft
  - `PUT /console/v1/mail/mailboxes/{mailboxId}/drafts/{draftId}` — update with explicit revision
  - `DELETE /console/v1/mail/mailboxes/{mailboxId}/drafts/{draftId}` — delete a DRAFT
  - `POST /console/v1/mail/mailboxes/{mailboxId}/drafts/{draftId}/hold` with `Idempotency-Key` — **only create a durable HELD intent; does not deliver**. Disabled by default.
- Drafts require active organization, verified mail domain, active mailbox, explicit per-user mailbox grant and author-owned draft ID. There is **no admin mailbox read bypass**.
- Text/plain body <=65,536 **UTF-8 bytes**, subject <=998 characters without CR/LF, max 20 distinct conservatively validated ASCII addresses across To/Cc/Bcc.
- All drafts within 50-per-(mailbox,user) limit are bounded and listable. A mailbox-level row lock serializes the cap; optimistic `revision` check rejects conflicting editor saves.
- `replyToMessageId`, when present, must belong to the exact same organization/mailbox and to a completed inbound scan. The original inbound message is never trusted as a usable reply address unless the user verifies it.
- The hosted mail console gains a **Save draft** editor and **Prepare reply draft** action. There is no visible Send button, no fake Sent state, and no unsafe HTML editor/renderer.
- The held outbound intent has no worker; **HELD ≠ sent, delivered, queued at SMTP or accepted by Postfix.**

## Flags (all default false)

```env
EMAIL_MAIL_COMPOSE_ENABLED=false
EMAIL_MAIL_SUBMISSION_ENABLED=false
VITE_MAIL_COMPOSE_UI_ENABLED=false
```

The R6 frontend also requires `VITE_MAIL_UI_ENABLED=true`; the backend additionally requires the prior R1-R5 mailbox, domain, scan and activation gates and actual healthy infrastructure. **Do not enable any production flag in this PR.**

## Architecture: what is intentionally separate

The existing `POST /v1/emails` contract is **marketing-only** and requires consent and unsubscribe support. Ordinary person-to-person mail must not impersonate a campaign or get free access to an unrelated developer project's sender identity, quota, unsubscribe behavior or reputation. R6 does NOT call `EmailMessageService.accept` or the marketing route.

A future secure mailbox delivery worker must:
1. Require a verified sender identity for the exact mailbox and organization; bind sender-from to the mailbox address.
2. Generate standards-correct MIME with From, To, Cc, Bcc envelope handling, Reply-To/In-Reply-To/References, stable message ID, and DKIM.
3. Admit a distinct mailbox delivery budget and reputation/IP pool separate from marketing/bulk traffic.
4. Lease an authorized HELD intent, assign a durable delivery attempt idempotently, and hand off to the existing Postfix SMTP adapter only after anti-abuse checks.
5. Persist Sent/Failed/Unknown statuses, distinguishing **SMTP handoff accepted** from **remote inbox delivery**, and never retry blindly after an unknown DATA result.
6. Preserve bounded retries, quota accounting, bounce/DSN processing, abuse suspension and operator observability.
7. Ensure a draft already HELD cannot be edited or deleted without an explicitly designed cancellation/reconciliation protocol.

Until that is in place, `EMAIL_MAIL_SUBMISSION_ENABLED` must remain `false`.

## Low-cost complexity

- Draft save/read/update/delete: indexed point queries approximately O(log N) for identifying a record; O(body+recipients) for serialization and transfer.
- List maximum 50 summaries per user/mailbox; per-author full text remains limited to 50 x 64KiB ~= 3.2 MiB of body text before PostgreSQL row and index overhead.
- No attachment storage, no new queue service, no new cloud product, and no additional running daemon for R6.
- Existing PostgreSQL WAL and normal backup requirements still apply. For production, tune connection pooling and quotas on real data, not by assuming complexity bounds guarantee latency.

## QA before any merge

- Build and test on **JDK 25**, PostgreSQL and Node 24. Run spotless:check, all backend tests, and both Vite flag builds.
- Flyway migration V1-V17 on clean and V16->V17 on upgraded PostgreSQL. Check composite foreign keys, concurrent draft cap and unique submission keys.
- Security: cross-organization mailbox ID, cross-user draft ID, spoofed reply ID, disabled domain/mailbox, missing grants, compromised user session and no project API-key access.
- Concurrency: simultaneous create (max 50), edit with stale revision, double hold with same/different idempotency keys, DB error while inserting outbound intent; verify transactional rollback.
- Validate newline/header injection, duplicate recipients across To/Cc/Bcc, non-ASCII addresses, huge UTF-8 body, and invalid Snowflake IDs.
- Browser: lost session, stale mailbox switch, mobile editor, saved-draft reload, reply with display-name-only From, deletion confirmation.
- **No real sending may occur from the R6 held intent.** Confirm the existing SES and marketing path are unaffected.

## Future IAM parity requirement (preserved)

Before early-access GA, Mail should match UnifyDesk App authentication and permission standards with MFA, account recovery, custom roles, role audits, fine-grained delegations and revocations. Keep per-mailbox grants explicit even for admins. This comes after secure core SMTP delivery, not instead of it.

**GitHub Actions for preceding R1 PR still fails without executing any runner steps. This is not a test pass or evidence of a code compile failure; do not merge drafts based only on source checks.**
