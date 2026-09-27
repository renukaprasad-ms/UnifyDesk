# Notification HTTP API Contract

Status: frozen for Round 1  
Base prefix: `/api/v1/notifications`

All endpoints require an authenticated verified user. Existing cookie authentication and CSRF behavior applies. Mutating endpoints MUST NOT be added to the CSRF ignore list.

All IDs are JSON strings.

All responses use existing `ApiResponse<T>`.

## 1. Scope semantics

`companyId` is optional only when account-only behavior is meaningful.

When a valid `companyId` is supplied:

- the server requires active company access;
- list/count returns ACCOUNT notifications plus that COMPANY's currently visible notifications.

When omitted:

- list/count returns ACCOUNT scope only.

For company-scoped notification operations, a missing/mismatched company context must not leak existence; return 404 when appropriate after authorization-safe lookup.

## 2. List notifications

`GET /api/v1/notifications?companyId={id}&limit=20&cursor={opaque}&unreadOnly=false`

Limits:

- default 20;
- min 1;
- max 50.

Ordering:

`lastEventAt DESC, id DESC`.

Cursor is opaque base64url versioned server data. F1 MUST NOT parse it.

Response data:

```json
{
  "items": [
    {
      "id": "922337203685470001",
      "type": "INBOX_ACTIVITY",
      "category": "INBOX",
      "severity": "NORMAL",
      "scope": {
        "kind": "COMPANY",
        "companyId": "922337203685470100"
      },
      "title": "New inbox activity",
      "body": "3 new inbox messages",
      "aggregateCount": 3,
      "action": {
        "kind": "ROUTE",
        "path": "/inbox/email?connectionId=922337203685470200"
      },
      "firstEventAt": "2026-09-27T06:30:00Z",
      "lastEventAt": "2026-09-27T06:33:00Z",
      "seenAt": null,
      "readAt": null,
      "unread": true
    }
  ],
  "nextCursor": "eyJ2IjoxLC...",
  "hasMore": true
}
```

Expired/inaccessible company rows are excluded.

Errors:

- 400 invalid limit/cursor/company ID format;
- 401 unauthenticated;
- 403 no access to requested company.

## 3. Get notification

`GET /api/v1/notifications/{notificationId}?companyId={id}`

For account notification, companyId may be omitted.

Response: one Notification item with the same shape as list.

404 is used for missing/not-owned/not-visible notification to avoid ownership enumeration.

## 4. Unread count

`GET /api/v1/notifications/unread-count?companyId={id}`

Response:

```json
{
  "account": 2,
  "company": 7,
  "total": 9
}
```

Without companyId:

```json
{
  "account": 2,
  "company": 0,
  "total": 2
}
```

Counts are 64-bit backend values; frontend visual badge caps at `99+`.

## 5. Mark seen

`POST /api/v1/notifications/seen`

Request:

```json
{
  "companyId": "922337203685470100",
  "notificationIds": ["1","2","3"]
}
```

Constraints:

- 1..100 distinct IDs;
- account IDs can be included;
- company IDs must belong to supplied selected company.

Semantics:

- set `seen_at` only if null;
- idempotent;
- does not change unread count;
- silently ignores IDs that are no longer visible only if they belonged to the current user; foreign IDs yield no information and count as not updated.

Response:

```json
{
  "updated": 2
}
```

## 6. Mark one read/unread

`PATCH /api/v1/notifications/{notificationId}/read-state`

Request:

```json
{
  "companyId": "922337203685470100",
  "read": true
}
```

For account notification companyId may be null/omitted.

Semantics:

- idempotent;
- read=true sets `read_at` to now if null and decrements correct counter once;
- read=false clears `read_at` if non-null and increments correct counter once;
- if item expired/inaccessible, 404.

Response:

```json
{
  "notification": { "...": "same notification shape" },
  "unreadCount": {
    "account": 2,
    "company": 6,
    "total": 8
  }
}
```

## 7. Mark visible scope read

`POST /api/v1/notifications/read-all`

Request:

```json
{
  "companyId": "922337203685470100",
  "before": null
}
```

`before` optional ISO timestamp.

Server resolves `cutoffAt = min(request.before, requestStartTime)`, or requestStartTime when absent.

With companyId, operation applies to ACCOUNT + selected COMPANY notifications visible to current user with `last_event_at <= cutoffAt`.

Notifications arriving after cutoff remain unread.

Response:

```json
{
  "updated": 12,
  "cutoffAt": "2026-09-27T06:35:00Z",
  "unreadCount": {
    "account": 0,
    "company": 1,
    "total": 1
  }
}
```

This avoids a race where a newly arriving notification is incorrectly marked read by an unbounded update.

## 8. Preferences — get

`GET /api/v1/notifications/preferences?companyId={id}`

Response:

```json
{
  "scope": {
    "companyId": "922337203685470100"
  },
  "timezone": {
    "value": "Asia/Kolkata",
    "source": "COMPANY_DEFAULT"
  },
  "quietHours": {
    "enabled": true,
    "start": "22:00",
    "end": "08:00"
  },
  "channels": {
    "emailAvailable": true,
    "browserPushConfigured": true
  },
  "types": [
    {
      "type": "INBOX_ACTIVITY",
      "label": "Inbox activity",
      "category": "INBOX",
      "mandatoryEmail": false,
      "email": {
        "allowedModes": ["OFF","DIGEST"],
        "defaultMode": "OFF",
        "effectiveMode": "DIGEST",
        "overrideMode": "DIGEST",
        "locked": false
      },
      "browser": {
        "defaultEnabled": false,
        "effectiveEnabled": true,
        "overrideEnabled": true,
        "locked": false
      }
    },
    {
      "type": "SECURITY_ACCOUNT_CRITICAL",
      "label": "Security alerts",
      "category": "SECURITY",
      "mandatoryEmail": true,
      "email": {
        "allowedModes": ["IMMEDIATE"],
        "defaultMode": "IMMEDIATE",
        "effectiveMode": "IMMEDIATE",
        "overrideMode": null,
        "locked": true
      },
      "browser": {
        "defaultEnabled": false,
        "effectiveEnabled": false,
        "overrideEnabled": null,
        "locked": false
      }
    }
  ]
}
```

F1 renders from this contract; it does not hardcode which mandatory types can be disabled.

## 9. Preferences — update

`PUT /api/v1/notifications/preferences?companyId={id}`

Request replaces the user's overrides/settings for the supplied scope:

```json
{
  "timezone": "Asia/Kolkata",
  "quietHours": {
    "enabled": true,
    "start": "22:00",
    "end": "08:00"
  },
  "types": [
    {
      "type": "INBOX_ACTIVITY",
      "emailMode": "DIGEST",
      "browserEnabled": true
    }
  ]
}
```

Semantics:

- timezone must be a valid IANA `ZoneId`;
- time is local `HH:mm`;
- start=end means API normalizes quiet hours disabled;
- only registry-listed type/channel modes accepted;
- attempting `OFF` for mandatory email returns 400;
- omitted type override is removed/inherits default;
- whole update occurs transactionally.

Response is the same effective preferences shape as GET.

## 10. Push capability

`GET /api/v1/notifications/push/capability`

Response:

```json
{
  "configured": true,
  "vapidPublicKey": "B...",
  "maxPayloadBytes": 4096
}
```

If backend VAPID secret is missing:

```json
{
  "configured": false,
  "vapidPublicKey": null,
  "maxPayloadBytes": 4096
}
```

No secret/private key is ever returned.

## 11. Register push subscription

`POST /api/v1/notifications/push/subscriptions`

Request mirrors browser PushSubscription data:

```json
{
  "endpoint": "https://push-service.example/opaque-capability",
  "expirationTime": null,
  "keys": {
    "p256dh": "base64url...",
    "auth": "base64url..."
  },
  "deviceLabel": "Chrome on Windows"
}
```

Validation:

- HTTPS endpoint;
- endpoint max 2048 chars;
- p256dh/auth valid bounded base64url;
- configured VAPID required;
- per-user ACTIVE subscription cap default 10;
- endpoint hash globally unique.

Ownership rule:

- same endpoint hash for same user => idempotently refresh/return existing row;
- same endpoint hash bound to another user => 409 `Push subscription is already associated with another account`; never silently transfer ownership.

Response 201/200 data:

```json
{
  "id": "123",
  "status": "ACTIVE",
  "deviceLabel": "Chrome on Windows",
  "createdAt": "2026-09-27T06:40:00Z",
  "lastSuccessAt": null
}
```

Endpoint/keys are never echoed.

## 12. List push subscriptions

`GET /api/v1/notifications/push/subscriptions`

Response:

```json
[
  {
    "id": "123",
    "status": "ACTIVE",
    "deviceLabel": "Chrome on Windows",
    "createdAt": "2026-09-27T06:40:00Z",
    "lastSuccessAt": "2026-09-27T07:00:00Z"
  }
]
```

No endpoint/key/ciphertext returned.

## 13. Revoke push subscription

`DELETE /api/v1/notifications/push/subscriptions/{subscriptionId}`

- current user ownership required;
- idempotent if already revoked;
- foreign/not-owned => 404.

Response:

```json
{
  "id": "123",
  "status": "REVOKED"
}
```

F1 also calls browser `PushSubscription.unsubscribe()` locally when available.

## 14. Notification-open bridge

Frontend route only:

`/notification-open?n={notificationId}&c={companyId?}`

This is **not** an unauthenticated backend redirect.

F1 flow:

1. normal auth guards apply;
2. if companyId present, confirm company in workspace list;
3. select that company;
4. GET the notification using normal API authorization;
5. mark read;
6. navigate to server-returned `action.path`.

If inaccessible/expired, show a safe “This notification is no longer available” state and route to workspace home.

## 15. Error contract

Uses existing envelope, e.g.:

```json
{
  "status": false,
  "status_code": 400,
  "error_message": "Invalid notification cursor"
}
```

Expected statuses:

- 400 invalid request/cursor/type/timezone/mode;
- 401 authentication required;
- 403 requested company access denied;
- 404 notification/subscription not found or not owned;
- 409 push endpoint ownership conflict;
- 429 notification preference/subscription abuse limit where applicable;
- 503 browser push unavailable because server configuration missing.

Do not return provider raw error bodies.

## 16. Optimistic frontend behavior

F1 may optimistically:

- mark read/unread;
- mark all read;
- update badge.

On request failure it must roll back/refetch authoritative state.

F1 must never infer authorization from hidden UI and must handle 403/404 after company membership changes.

## 17. Mock requirement for F1

F1 fixtures must cover:

- account + company list;
- unread and read items;
- coalesced aggregate count;
- cursor next page;
- zero/empty state;
- 403 workspace revoked;
- realtime change;
- preferences mandatory locked row;
- browser unsupported/default/granted/denied;
- push backend configured/unconfigured;
- push endpoint conflict;
- read-all concurrent new notification behavior.

No frontend worker waits for B1/B2 implementation.
