# Prompt — F1 Frontend + UX — Notification System Round 1

You are **F1 — Frontend + UX Engineer** for UnifyDesk Notification System.

Repository: `renukaprasad-ms/messaging`  
Application: `unifydesk-app/`  
Docs: `unifydesk-app/docs/notification/`

Branch:

`feature/notification-ui-r1`

EXACT frozen base SHA:

`b13c41919ade97c270ee2235035af3c20dadbe41`

Create the branch from exactly that SHA. Do not wait for B1 or B2 and do not branch from their work.

Read TASKS.md, API-CONTRACT.md, EVENT-CONTRACT.md, PRODUCT-POLICY.md, ARCHITECTURE.md, DELIVERY-POLICY.md, SECURITY.md and PERFORMANCE.md before implementation.

## Mission

Own both UX and frontend implementation for:

- header bell;
- sidebar Notifications item;
- unread badges;
- recent notifications popover;
- full notification page;
- read/unread/seen/read-all UX;
- existing WebSocket integration;
- resilient reconnect/focus/workspace switching;
- preference settings;
- browser permission education;
- Service Worker + PushSubscription;
- push subscription lifecycle;
- push click route bridge;
- multiple tabs/browser badge;
- responsive/accessibility states.

Implement against frozen API mocks. Never wait for backend branches.

## UX requirements

### Header

- bell always visible in authenticated app shell;
- accessible name includes unread state;
- visual badge caps at 99+;
- desktop popover shows newest 8;
- View all -> `/notifications`;
- mark seen only after item actually renders;
- outside click/Escape/focus behavior accessible.

### Sidebar

Add Notifications route entry with the same total badge. Do not create a second independent count source.

### Full page

- newest first;
- keyset “Load more” or infinite loading using opaque cursor;
- clear unread styling not dependent only on color;
- click marks read then follows trusted action route;
- explicit mark unread;
- mark all read;
- empty/loading/error/retry;
- handle 403/404 after workspace access changes.

### Realtime

Reuse `realtimeClient`; do not open another WebSocket.

Subscribe to `notification.changed.v1`. Re-fetch authoritative count/list on relevant changes. Also refresh on reconnect, meaningful focus recovery and company switch.

### Multiple tabs

Use BroadcastChannel when supported to synchronize mutations/refetch hints. Correctness cannot depend on it.

Use Badging API only after feature detection.

### Browser permission

Never call `Notification.requestPermission()` on login/page load.

Settings flow:

1. explain browser notifications;
2. user clicks Enable;
3. verify Service Worker + PushManager + Notifications support;
4. request permission from user gesture;
5. get backend VAPID public key;
6. create subscription with `userVisibleOnly:true`;
7. register backend subscription;
8. show current state.

Denied: explain browser settings; do not repeatedly prompt.

Unsupported: graceful state. Feature-detect iOS/Home Screen limitations rather than hard failing.

### Service worker

Handle push:

- if a controlled UnifyDesk client is focused/visible, postMessage and do not show duplicate OS notification;
- otherwise show privacy-safe notification with server tag.

Handle click:

- never open arbitrary URL;
- use supplied same-origin `/notification-open?n=...&c=...` only;
- focus existing same-origin client where possible, otherwise open window.

### Notification-open route

Under normal authenticated guards:

- restore/select company only if user has it;
- fetch notification through API;
- mark read;
- navigate using backend-returned action path;
- inaccessible => safe unavailable state/workspace home.

### Account settings

Render effective preferences from API, including:

- timezone;
- quiet hours;
- each type's allowed email modes;
- browser override;
- locked mandatory email controls;
- browser subscription state/devices.

Do not hardcode mandatory rules beyond display labels/fallback.

## Security

- render title/body as text only;
- never `dangerouslySetInnerHTML`;
- never parse/execute arbitrary push route;
- do not store auth tokens in service worker/localStorage;
- do not store raw endpoint/key beyond browser PushSubscription object;
- server subscription ID may be stored locally for revoke flow;
- on logout, best-effort revoke/unsubscribe current browser subscription without blocking logout forever;
- cross-account 409: unsubscribe local stale subscription, then let user explicitly enable/re-register for current account.

## Ownership

Only frontend code listed in TASKS.md. No backend/migrations.

## Tests

Use mock API/Service Worker/browser APIs where appropriate.

Cover:

- badge 0/1/99+;
- popover/seen;
- read/unread/read-all optimistic rollback;
- cursor page;
- realtime change;
- workspace switch;
- permission default/granted/denied/unsupported;
- no prompt before click;
- push registration/revoke/conflict;
- focused service-worker message behavior where testable;
- notification-open authorization/error;
- mandatory preference locked;
- keyboard/a11y essentials.

Run:

```bash
cd unifydesk-app/frontend
npm ci
npm run format:check
npm run lint
npm test
npm run build
```

Commit all work and report the exact completion summary in TASKS.md. Do not merge.
