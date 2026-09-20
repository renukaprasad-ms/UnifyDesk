# Gmail realtime via Google Cloud Pub/Sub

The application uses Gmail server push notifications:

```
Gmail users.watch
  -> Google Cloud Pub/Sub topic
  -> push subscription
  -> POST /api/v1/webhooks/google/gmail
  -> history.list
  -> fetch only affected messages
  -> persist
  -> inbox.email.changed WebSocket event
```

## Google Cloud setup

Use the same Google Cloud project that owns `GMAIL_OAUTH_CLIENT_ID`.

1. Enable the **Gmail API** and **Cloud Pub/Sub API**.
2. Create a Pub/Sub topic, for example:
   `projects/<project-id>/topics/messaging-gmail`.
3. Grant **Pub/Sub Publisher** on that topic to:
   `gmail-api-push@system.gserviceaccount.com`.
4. Create a **push subscription** for the topic.
5. Set the push endpoint to:

   ```
   https://messaging.renukaprasad.in/api/v1/webhooks/google/gmail?token=<GMAIL_PUBSUB_PUSH_VERIFICATION_TOKEN>
   ```

6. Use a long random verification token:

   ```bash
   openssl rand -hex 32
   ```

## Application environment

```env
GMAIL_PUBSUB_TOPIC_NAME=projects/<project-id>/topics/messaging-gmail
GMAIL_PUBSUB_PUSH_VERIFICATION_TOKEN=<same-random-token>
```

The topic project ID must match the Google developer project used by the Gmail OAuth client.

## Runtime behavior

- A watch is created when Gmail is connected.
- Watches are checked hourly and renewed approximately once per day.
- Gmail push deliveries are acknowledged quickly; the webhook only records the newest pending history ID.
- Durable workers claim pending cursors with PostgreSQL `FOR UPDATE SKIP LOCKED`.
- `history.list` retrieves only changes since the last successful cursor.
- Message IDs affected by add/delete/label changes are fetched from Gmail to determine their current state.
- Duplicate Pub/Sub notifications are coalesced.
- A five-minute safety check compares the mailbox profile history ID with the persisted cursor to recover from dropped push notifications.
- If Gmail reports that the saved history ID is too old, a bounded recent Inbox/Sent reconciliation runs before resetting the cursor.
- Watch renewal, history calls, profile checks, and message fetches all pass through the distributed Gmail quota manager.

## Disconnect

The application calls `users.stop` before revoking OAuth when the disconnected account is the final active connection for that Gmail mailbox. If the same mailbox is still connected to another workspace, the shared Gmail watch is preserved.
