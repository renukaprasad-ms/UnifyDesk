# Notification System Research

Status: primary-source research completed for Round 0  
Research date: 2026-09-27

## 1. Repository findings

### Existing notification/email path

The current `com.messaging.notification` package is an OTP/SMTP-oriented utility, not a durable product-notification platform:

- `NotificationService` validates a `SendNotificationRequest`, then optionally submits it to a small in-memory `ThreadPoolTaskExecutor`.
- Exceptions thrown inside the async task are intentionally swallowed.
- `EmailNotificationSender` uses the shared Spring `JavaMailSender` and platform `app.notification.email.from` configuration.
- `EmailTemplateService` currently renders OTP templates.
- `OtpRequestedEventHandler` already starts from Kafka, then calls the legacy notification facade.

Conclusion: keep the existing OTP path compatible. The new durable product-notification runtime reuses the platform SMTP infrastructure but MUST NOT rely on the in-memory async facade as its reliability boundary.

### Managed Email is not the system-email transport

Managed Email is customer-facing Broadcast infrastructure. It owns customer/managed sender domains, SES tenant/configuration-set concerns, campaign delivery, suppression, usage and reputation. Product notifications are UnifyDesk account/service messages and MUST NOT consume a customer's Broadcast sender, campaign quota or provider credentials.

### Kafka

The repository already has:

- `KafkaEvent<T>` with eventId/type/version/time/company/user/correlation/causation/priority;
- priority topics (critical/high/medium/low);
- idempotent producer settings;
- manual consumer acknowledgement;
- retry/backoff and DLT;
- a Redis `KafkaEventIdempotencyService`;
- deterministic key resolution.

The Redis consumer check is useful but not a sufficient correctness boundary: it is TTL-based and check-then-act. Notification effects therefore require PostgreSQL uniqueness/idempotency in addition to existing Kafka protection.

### Realtime

The existing realtime stack is reusable:

- one authenticated WebSocket endpoint: `/api/v1/realtime/ws`;
- Redis pub/sub for multi-pod fanout;
- `RealtimeService.sendToAllUserSessions(...)`;
- frontend `realtimeClient` with reconnect and heartbeat;
- `MainLayout` already owns connection lifecycle.

Redis pub/sub is intentionally ephemeral. Durable notification state must be committed before a realtime hint is attempted.

### Inbox seam

Gmail and IMAP/SMTP persistence invoke every `InboxMessagePostProcessor` after persisting an inbound message, inside the persistence transaction. A new B1 post-processor can record a notification event without editing each provider implementation.

Important: the callback can run again when an existing inbound message is re-synced. The event key therefore MUST be derived from the immutable local message ID, e.g. `inbox.message.received:<messageId>`, and the recorder must be idempotent.

### Company creation seam

`CompanyService.create` is transactional and creates the company + owner membership. B1 is the sole Round-1 writer of this hot file and records `company.created:<companyId>` in the same transaction.

### Data/API/security conventions

- Snowflake `Long` IDs internally; HTTP DTOs serialize IDs as strings.
- Company authorization uses current authenticated user + active membership/permission.
- API envelope is `ApiResponse<T>` with `status`, `status_code`, `data`, `message`, `error_message`.
- CSRF uses the XSRF cookie + `X-XSRF-TOKEN` on mutating authenticated requests.
- PostgreSQL/Flyway is the primary store.
- Spring `@Scheduled` workers are common. No Quartz dependency exists at this baseline.
- Actuator + Micrometer/Prometheus already exist.
- Existing background workers commonly use bounded polling/recovery patterns.

## 2. Web Notifications, Push and Service Workers

### Notifications API

MDN documents `Notification.requestPermission()` as a secure-context API. Permission is origin-level and resolves to `default`, `granted` or `denied`.

Decision: UnifyDesk never asks on page load. F1 exposes an explicit “Enable browser notifications” action and calls permission/subscription APIs only from that user gesture.

Sources:
- https://developer.mozilla.org/en-US/docs/Web/API/Notification/requestPermission_static
- https://developer.mozilla.org/en-US/docs/Web/API/Notifications_API/Using_the_Notifications_API

### Service-worker notifications

For durable/background delivery, use an active service worker and `ServiceWorkerRegistration.showNotification()`, not only `new Notification()` on the page.

Source:
- https://developer.mozilla.org/en-US/docs/Web/API/ServiceWorkerRegistration/showNotification

### Push API and VAPID

A `PushSubscription` contains a capability endpoint plus encryption/authentication material. The endpoint must be treated as sensitive. `PushManager.subscribe()` supports `userVisibleOnly` and an application-server public key; Chrome/Edge require `userVisibleOnly: true` and VAPID-style application-server key use.

Sources:
- https://developer.mozilla.org/en-US/docs/Web/API/Push_API
- https://developer.mozilla.org/en-US/docs/Web/API/PushSubscription
- https://developer.mozilla.org/en-US/docs/Web/API/PushManager/subscribe
- RFC 8292: https://www.rfc-editor.org/rfc/rfc8292.html
- RFC 8291: https://www.rfc-editor.org/rfc/rfc8291.html

### iOS/iPadOS

WebKit documents that standards-based Web Push works for web apps added to the Home Screen on iOS/iPadOS, and permission must follow direct user interaction.

Source:
- https://webkit.org/blog/13878/web-push-for-web-apps-on-ios-and-ipados/

Decision: feature-detect capabilities. Do not browser-sniff or promise background web push on unsupported contexts.

### Push protocol reliability

RFC 8030 defines TTL, urgency and topic replacement. It requires push services to support payloads of at least 4096 bytes, allows subscriptions to expire, and defines 404 for expired subscriptions. Practical web-push guidance also treats 410 as a permanently invalid subscription and 429 as rate limiting.

Sources:
- RFC 8030: https://www.rfc-editor.org/rfc/rfc8030.html
- https://web.dev/articles/push-notifications-web-push-protocol

Decisions:
- keep encrypted push payloads small (target <= 2 KiB JSON; hard reject before 4 KiB);
- use TTL/urgency by notification class;
- delete/deactivate 404/410 subscriptions;
- honor `Retry-After` for 429;
- use stable notification/group IDs for client dedupe/coalescing.

### Notification click/deep-link behavior

Service-worker `notificationclick`, `clients.matchAll()`, `focus()` and `openWindow()` support focusing/opening a same-origin app surface.

Sources:
- https://developer.mozilla.org/en-US/docs/Web/API/ServiceWorkerGlobalScope/notificationclick_event
- https://developer.mozilla.org/en-US/docs/Web/API/Clients/matchAll
- https://developer.mozilla.org/en-US/docs/Web/API/Clients/openWindow

Decision: push clicks open a dedicated same-origin `/notification-open` bridge so F1 can restore the correct workspace before navigating to a protected deep link.

### Multi-tab coordination and app badge

`BroadcastChannel` communicates between same-origin tabs/workers. Page Visibility allows foreground/background detection. The Badging API is useful but not universally available.

Sources:
- https://developer.mozilla.org/en-US/docs/Web/API/Broadcast_Channel_API
- https://developer.mozilla.org/en-US/docs/Web/API/Page_Visibility_API
- https://developer.mozilla.org/en-US/docs/Web/API/Badging_API

Decision: use BroadcastChannel opportunistically for tab synchronization; use `navigator.setAppBadge` only behind feature detection; document title + in-app badges remain the universal fallback.

## 3. Event-driven reliability

AWS Prescriptive Guidance describes the transactional outbox pattern: store domain change/event information atomically, publish later, and design consumers to be idempotent because duplicate publication/delivery remains possible.

Sources:
- https://docs.aws.amazon.com/prescriptive-guidance/latest/cloud-design-patterns/transactional-outbox.html
- https://docs.aws.amazon.com/prescriptive-guidance/latest/modernization-integrating-microservices/asynchronous.html

Decision: `notification_events` is the durable event/outbox record. The source transaction inserts it; publication and downstream effects are retryable and idempotent.

## 4. PostgreSQL worker/index guidance

PostgreSQL 18 explicitly documents `SKIP LOCKED` as appropriate for multiple consumers of queue-like tables, while warning that it is not a general-consistency view. PostgreSQL also documents partial and multicolumn indexes and advises matching leftmost columns to common predicates.

Sources:
- https://www.postgresql.org/docs/18/sql-select.html
- https://www.postgresql.org/docs/18/sql-createindex.html
- https://www.postgresql.org/docs/18/indexes-multicolumn.html

Decision:
- workers claim a small bounded batch with `FOR UPDATE SKIP LOCKED`, commit the claim, then perform external work outside the transaction;
- hot API indexes lead with `company_id,user_id`;
- unread indexes are partial;
- list pagination is keyset/seek, never deep OFFSET.

PostgreSQL partitioning can make bulk retention cheaper for very large tables, but adds planning/operational cost and is most useful once tables are very large.

Source:
- https://www.postgresql.org/docs/18/ddl-partitioning.html

Decision: do not partition Round-1 tables prematurely. Retain a documented monthly-range migration path if sustained volume drives `user_notifications`/attempt tables into tens of millions of rows and beyond the database memory/maintenance envelope.

## 5. Email deliverability/unsubscribe research

Google's current sender guidance requires authentication and, for senders above the Gmail bulk threshold, one-click unsubscribe for marketing/subscribed mail. It distinguishes subscription mail from transactional service messages.

Sources:
- https://support.google.com/mail/answer/81126
- https://support.google.com/mail/answer/14229414
- https://support.google.com/mail/answer/15263077

RFC 8058 defines one-click List-Unsubscribe semantics and recommends an opaque/hard-to-forge identifier; the POST does not rely on browser cookies/auth.

Source:
- https://www.rfc-editor.org/rfc/rfc8058.html

Round-1 decision:
- mandatory security/account and one-off workspace welcome remain service/transactional mail;
- optional recurring notification digests always contain a visible authenticated “Manage notification preferences” link;
- if UnifyDesk classifies/scales a digest stream as subscribed/bulk mail, RFC-8058 one-click support becomes a release prerequisite for that stream. It is not silently simulated by a GET link.

AWS SES guidance also reinforces suppressing addresses after hard bounce/complaint and distinguishing permanent from temporary failure.

Sources:
- https://docs.aws.amazon.com/ses/latest/dg/send-email-concepts-deliverability.html
- https://docs.aws.amazon.com/ses/latest/dg/sending-email-suppression-list.html

The current product-notification SMTP transport does not expose provider feedback equivalent to Managed Email SES events. Therefore Round 1 records provider acceptance/failure, not “delivered to inbox”, and does not invent delivery certainty.

## 6. Java Web Push implementation research

The maintained `web-push-libs/webpush-java` project documents payload + VAPID support; its current published example uses `nl.martijndwars:web-push:5.1.2`.

Source:
- https://github.com/web-push-libs/webpush-java

B2 owns the dependency decision/file. If compatibility testing with Java 25/Spring Boot 4.1.1 fails, B2 must report a blocker rather than hand-roll RFC 8291 cryptography in Round 1.
