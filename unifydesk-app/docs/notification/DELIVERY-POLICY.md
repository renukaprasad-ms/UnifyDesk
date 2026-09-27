# Notification Delivery Policy

Status: frozen for Round 1

## 1. Delivery is a policy decision, not a broadcast fanout rule

Every product event is first durable in-app history. Email and browser push are interruption channels evaluated independently from the durable notification row.

B2 consumes `notification_delivery_intents` but MUST re-evaluate current eligibility before an external call. An intent is not permission to bypass a newer preference, membership change, quiet-hours window, cooldown or relevance change.

## 2. Delivery modes

- `IMMEDIATE`: process as soon as possible; mandatory account/security mail can bypass quiet hours.
- `DELAYED`: wait until `not_before`; suppress if the condition was resolved/seen before send.
- `DIGEST`: aggregate multiple contributions, enforce debounce/cooldown and send one summary.
- Web push uses the intent's `not_before` and the same quiet-hours/relevance gates; it is not a separate source of truth.

## 3. Send-time eligibility order

For every due intent B2 checks, in this order:

1. intent is still claimable and not terminal;
2. user is active;
3. company still exists in a notification-eligible state;
4. company membership is active for company-scoped delivery;
5. required permission is still granted;
6. registry still permits the channel/mode;
7. effective current preference permits the channel/mode unless mandatory;
8. channel is configured/available;
9. quiet hours do not require deferral;
10. channel cooldown/rate limit is available;
11. delayed/digest relevance remains true;
12. recipient destination/subscription is current and valid.

A failed check becomes a typed `SUPPRESSED` or `DEFERRED` result, not an exception loop.

## 4. Quiet hours

Default optional-interruption quiet hours: 22:00–08:00 in effective IANA timezone.

Resolution order:

1. company-scoped user setting;
2. account-scoped user setting where applicable;
3. company timezone;
4. UTC.

Use Java `ZoneId` and local date/time resolution so DST transitions are handled by the platform timezone database.

- In-app is immediate regardless of quiet hours.
- Optional email and browser push move to the next quiet-hours end.
- Mandatory security/account email may bypass.
- A delayed condition that is no longer relevant after quiet hours is suppressed.

## 5. Email retry classification

The platform records SMTP/channel **acceptance**, not inbox delivery.

### Definite pre-acceptance retryable

Examples:

- connection timeout before any successful SMTP transaction is known;
- DNS/network/service unavailable;
- 4xx temporary SMTP response;
- local transport resource exhaustion.

Policy:

- retry with exponential backoff + jitter;
- default maximum 5 provider attempts;
- schedule approximately 1m, 5m, 15m, 1h, 4h, capped by the notification's usefulness window;
- honor a trustworthy provider retry hint when available;
- keep original logical intent/idempotency key.

### Definite permanent

Examples:

- syntactically invalid current user destination;
- explicit permanent recipient rejection;
- platform sender configuration invalid in a non-transient way.

Policy:

- `FAILED_PERMANENT`;
- no automatic repeat;
- metric + safe diagnostic code.

### Ambiguous external outcome

If the connection fails after the remote SMTP server may have accepted the message and the client cannot establish whether acceptance occurred:

- outcome = `UNKNOWN`;
- do not blindly auto-repeat;
- emit an operational metric;
- allow a future provider-specific reconciliation or carefully reviewed operator action.

This is required because SMTP does not provide an application-level exactly-once guarantee.

## 6. Email address resolution

Do not persist destination email as authoritative event data.

At send time:

- load current active user;
- normalize current `users.email`;
- require verified destination for ordinary product mail;
- mandatory account/security workflows may define their own verified/recovery-destination rule in their future producer contract.

`COMPANY_CREATED` goes to the creator/owner's user email, never automatically to `company.business_email`.

## 7. Email templates

Round-1 B2 templates:

### Company welcome

Subject concept:
`Welcome to UnifyDesk — <workspace name> is ready`

Contains:

- escaped user/workspace display text;
- concise explanation that the workspace was created;
- CTA to `/workspace` using trusted frontend base URL;
- support/preferences footer as appropriate.

No marketing upsell is required in this service email.

### Inbox activity digest

Subject concept:
`You have unread activity in <workspace name>`

Contains:

- aggregate unread/new activity counts;
- workspace name;
- CTA to `/inbox/email`;
- preferences-management link;
- no sender address, subject, body, snippet or attachment name from messages.

Render multipart text + HTML.

## 8. Inbox digest algorithm

Only users whose effective `INBOX_ACTIVITY.emailMode = DIGEST` contribute.

B2 groups pending digest intents by user/company/type.

A bucket becomes eligible when either:

- contribution count reaches 10; or
- two hours elapsed since first contribution.

Debounce:

- do not send until 15 minutes after the latest contribution;
- two-hour max-wait bounds endless debounce.

Final `eligible_at` is the earliest time satisfying both the trigger and debounce, capped by the two-hour maximum wait, then adjusted for quiet hours.

Cooldown:

- minimum 4 hours from the previous successfully accepted inbox digest for user/company;
- new contributions remain in/open into the next bucket rather than being dropped.

Rolling cap:

- optional notification email default cap: 3 accepted optional emails per user in any rolling 24 hours;
- mandatory security/account mail does not consume or obey this optional cap.

Before rendering/sending:

- recheck current membership + `email.read`;
- recheck preference;
- query authoritative relevant inbox unread count;
- if zero, suppress bucket;
- if every contribution notification is already seen/read and policy deems escalation unnecessary, suppress;
- otherwise send the latest aggregate count, not stale event count.

On accepted digest:

- mark bucket SENT;
- mark all bucket items/intents SENT as one logical digest effect;
- store accepted timestamp for cooldown.

## 9. Browser Push retry/classification

The Web Push sender uses VAPID and encrypted payloads.

### 2xx from push service

Treat as push-service acceptance, not proof the device displayed the notification.

### 404 / 410

Subscription expired/invalid:

- mark subscription EXPIRED/INVALID;
- no retry for that subscription;
- intent may still succeed for other active subscriptions.

### 401 / 403

Treat as configuration/authentication permanent for the attempt batch unless evidence indicates subscription-specific failure. Emit high-signal operational alert; do not hammer provider.

### 429

Rate limited:

- honor `Retry-After` when valid;
- otherwise exponential backoff with jitter.

### 5xx/network

Retry with bounded exponential backoff while TTL/relevance remains.

Default push retry attempts: 4.

## 10. Push TTL and urgency

Suggested defaults:

| Type | TTL | Urgency |
|---|---:|---|
| SECURITY_ACCOUNT_CRITICAL | 15 min | high |
| action-required failure | 1 h | high |
| INBOX_ACTIVITY | 30 min | normal |
| low-value optional activity | 15 min | low/normal |

Expired relevance => suppress rather than deliver stale interruption.

## 11. Push coalescing/tag

B2 payload provides a stable privacy-safe `tag`.

For inbox activity:
`ud:inbox:<companyId>:connection:<connectionId>`

The service worker uses the tag so supported browsers can replace/coalesce an older displayed notification for the same stream. Backend still enforces intent-level dedupe; browser tag is UX-level coalescing only.

## 12. Focus suppression

On `push`:

1. service worker obtains controlled window clients;
2. if any client has `visibilityState === "visible"` and is focused:
   - do not call `showNotification`;
   - `postMessage` the notification/company IDs;
3. otherwise call `registration.showNotification`.

The durable in-app notification already exists, so skipping the OS surface while focused loses no history.

## 13. Push click

`notificationclick`:

- close displayed notification;
- inspect only server-created same-origin `openPath`;
- prefer an existing same-origin client, focus it and post navigation message;
- otherwise `clients.openWindow(openPath)`;
- F1's `/notification-open` route performs authenticated company selection + notification fetch + trusted action navigation.

Never navigate directly to arbitrary payload URLs.

## 14. Subscription lifecycle

Registration:

- explicit user gesture;
- service worker ready;
- VAPID public key from capability endpoint;
- `userVisibleOnly: true`;
- POST subscription to backend.

Refresh:

- application startup may compare browser subscription to stored server ID and idempotently re-register;
- no permission prompt without gesture.

Revocation:

- UI disable revokes server record then calls browser `unsubscribe()`;
- logout should best-effort revoke the current local subscription before clearing app state;
- 404/410 server send response revokes automatically;
- expired subscription cleanup is scheduled.

A user can have up to 10 ACTIVE browser subscriptions by default.

## 15. Provider/library boundary

B2 owns an interface such as:

```text
ProductEmailGateway
WebPushGateway
```

Domain orchestration depends on interfaces, not `JavaMailSender` or a specific web-push library directly. This makes provider testing and future replacement straightforward without over-abstracting every small class.

## 16. Recovery workers

At startup/interval B2 recovers:

- expired CLAIMED delivery intent leases when no ambiguous provider call exists;
- retryable intents due now;
- open/eligible digest buckets;
- push attempts with expired leases;
- expired push subscriptions.

Recovery updates are bounded and indexed.

## 17. Dead-letter/operational recovery

A terminal DEAD/UNKNOWN item is visible to operations through metrics/log correlation. Round 1 does not expose an end-user resend button.

Any manual replay tool added later must require:

- privileged admin authorization;
- original idempotency record inspection;
- explicit reason/audit;
- channel-specific safety check against duplicate external effect.

## 18. Channel availability fallback

- Email unavailable: in-app still commits; optional email intent becomes failed/suppressed according to configuration classification.
- Push unavailable/unconfigured: in-app still commits; capability returns configured=false.
- No active subscription: push intent is suppressed `NO_ACTIVE_SUBSCRIPTION`, not retried forever.
- Realtime unavailable: no effect on email/push/in-app persistence.

No channel outage rolls back the originating business action.
