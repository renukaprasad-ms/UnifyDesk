# UnifyDesk Mail — R4 hosted-mail console preview

## Status

**Development preview only.** R4 is stacked on the R3 scanning and Inbox APIs, which depend on R2 and R1. No backend or customer MX route is enabled by this frontend work. Do not merge to `dev` until all stacked backend migrations and security controls pass QA.

## Screens implemented

The existing email API/developer console retains its sending, domain, project, webhook and usage sections unchanged. When the new build-time flag is enabled, **Hosted mail** appears in navigation with:
- Organization-scoped hosted domains: stage domain, show one-time ownership TXT, rotate proof, verify DNS ownership.
- Mailboxes under the selected domain: stage disabled mailbox and, for an OWNER/ADMIN signed in as its owner, request activation only when the backend's scan/activation gates are enabled.
- Inbox and Spam: safe metadata listing; cursor pagination in batches of 30; message selection; authenticated download of the raw RFC 5322 `.eml` as an attachment. **No remote images, HTML execution or attachment preview**.
- Per-user and per-organization authorization remains solely enforced by the backend. Frontend role hiding is not an authorization boundary.

## Feature flags and deployment

```env
# Vite/Cloudflare Pages BUILD variable, not a backend runtime secret.
VITE_MAIL_UI_ENABLED=false
```

Keep false on public builds until backend QA. Turning this UI flag on **does not** enable backend receiving. The backend flags remain false by default:

```env
EMAIL_MAIL_INBOUND_ENABLED=false
EMAIL_MAIL_SCANNER_ENABLED=false
EMAIL_MAIL_ACTIVATION_ENABLED=false
```

The frontend uses same-origin `/console/v1/` endpoints under a short-lived session token already used by the console; the proxy must not expose private `/internal/v1/` or public API-key `/v1/` endpoints to the browser. Do not route raw SMTP through Cloudflare Pages.

## Performance / safety

- No new NPM dependencies, paid cloud resources, WebSocket servers or mail parsers.
- Domain and mailbox lists are bounded by backend limits; only the **selected** domain's mailbox list is requested.
- Each message list requests at most 30 summaries with cursor pagination.
- Loading/error state and request epoch cleanups avoid showing stale asynchronous responses after switching domain, mailbox or folder.
- Original MIME download is opt-in, session-authenticated and not rendered inline as HTML. Object URLs are not persisted.
- IDs remain strings to avoid Snowflake precision loss in JavaScript.

## QA gates

1. Run `cd email-provider/frontend && npm install && npm run build` with `VITE_MAIL_UI_ENABLED=true` and false. Verify no visible Hosted mail nav when false.
2. Test actual API shape, sign-in, session expiry, role-based button visibility, TXT challenge (one-time display and rotation), domain verification, staged mailbox selection, scan/activation gates, Inbox/Spam and download headers.
3. Test browser mobile view, keyboard navigation, pagination while folders switch and rapid domain selection, malformed dates and network outages.
4. Independently verify cross-org/cross-user denial through the backend. No UI masking substitutes for authorization tests.
5. Keep customer DNS/MX unchanged. Hosted mail is not production-ready until SMTP inbound, durable backups, real scan infrastructure, malicious content handling, and disaster-recovery drills are completed.

## Known deliberate omissions

No HTML/body reading view, compose, reply, forwarding, IMAP, folder CRUD, marking read, search or attachments UI is presented as functioning. These require versioned backend contracts, MIME sanitization and thorough security reviews first.
