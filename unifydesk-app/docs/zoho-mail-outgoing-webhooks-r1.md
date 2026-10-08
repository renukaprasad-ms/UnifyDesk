# Zoho Mail outgoing webhooks — Inbox R1

This integration combines fast, signed Zoho Mail outgoing webhook **wakeups**
with the existing watermark-based Inbox/Sent API polling. Mail content is never
imported from an untrusted webhook payload.

## Activate

After merging and deploying the V98 migration, add to backend environment:

```dotenv
ZOHO_MAIL_OAUTH_ENABLED=true
ZOHO_MAIL_WEBHOOK_ENABLED=true
ZOHO_MAIL_WEBHOOK_PUBLIC_BASE_URL=https://api.unifydesk.in
ZOHO_MAIL_WEBHOOK_WORKER_INTERVAL=1s
ZOHO_MAIL_POLL_INTERVAL=2m
```

Retain your already-configured OAuth client ID, client secret, and callback.
The webhook public base URL must be a valid HTTPS origin accessible to Zoho.
No new Zoho OAuth scopes are required. These *outgoing webhooks* are created
manually inside the mailbox owner's Zoho Mail UI; they are not automatically
registered by the Zoho Mail REST API.

1. In UnifyDesk, go to **Company → Connections → Email**.
2. On an **active Zoho Mail** connection, select **Realtime webhook**.
3. Generate a connection-specific private URL and copy it immediately.
4. In the matching Zoho Mail account, open
   **Settings → Integrations → Developer Space → Outgoing Webhooks**.
5. Add a new outgoing webhook: **Mail** entity, conditions covering incoming mail,
   **Limited Data List** enabled, and paste the exact private UnifyDesk URL.
6. Save. Zoho calls the URL and expects **HTTP 200**. Its first webhook request
   provides `X-Hook-Secret` and `X-Hook-Signature`.
7. Back in UnifyDesk select **Refresh status**. Status should become **ACTIVE**.
8. Send a test email to this Zoho mailbox; confirm automatic Inbox arrival,
   read-state, threading, and reconnect/disconnect behavior.

Zoho's official documentation:
https://www.zoho.com/mail/help/dev-platform/webhook.html

## Security and reliability

- A 256-bit random URL token is generated for **each connected mailbox**.
  Only its SHA-256 hash is persisted. Do not share the callback URL.
- On first registration, a request must present that private URL AND a valid
  base64 HMAC-SHA256 `X-Hook-Signature` over the **raw request bytes** using
  the accompanying `X-Hook-Secret`. That secret is encrypted and pinned to
  this mailbox. Subsequent callbacks must validate against the pinned secret.
- The callback validates the connection is ACTIVE and is a Zoho Mail account.
  It does not trust event subject/body/recipient as authoritative messages.
- Valid webhook callbacks are acknowledged with HTTP 200 only after a **durable
  database queue update**. SQL claims and retry/backoff handle replicas/restarts.
  Events arriving during an active poll remain pending for another pass.
- The mailbox is fetched with the existing Zoho Mail API using the current
  incremental watermark, through the existing normalized Inbox persistence path.
  Existing realtime `inbox.email.changed` notifications refresh the frontend.
- The existing 2-minute safety poll stays enabled and catches missed callbacks
  or failed webhook registrations.
- Regenerating a URL invalidates the old URL and requires updating Zoho's
  outgoing webhook. Disabling or disconnecting invalidates the URL immediately.
- If Zoho automatic setup is unavailable for the mailbox's plan, the existing
  automatic polling works without webhook mode.

## Operational checks

- `GET /api/v1/companies/{companyId}/channels/email/zoho/{connectionId}/webhook`
  (authenticated with EMAIL_CONNECT) reports status and last callback/poll time.
- Public Zoho callback: `POST /api/v1/webhooks/zoho-mail/{connectionId}?token=...`.
  Do **not** log the URL token or request body. Headers contain verification secrets.
- Callback responses: 200 (persisted/queued), 403 (signature/token/connection
  rejected), 503 (temporary internal error). Check backend logs for
  `zoho_webhook_sync_failed` on worker errors.
- An `ACTIVE` webhook status only proves a signed callback was received; it
  does not prove that subsequent new messages have been successfully imported.
  Always perform a live send/receive smoke test.

NOTE: Zoho Mail outgoing webhooks are not the Zoho Campaigns/Email API delivery
webhooks for bounces, opens or clicks. Those are different products.
