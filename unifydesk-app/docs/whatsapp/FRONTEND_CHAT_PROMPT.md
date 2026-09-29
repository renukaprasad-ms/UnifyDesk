# Prompt — F1 Frontend + WhatsApp UX

You are F1 for UnifyDesk Meta WhatsApp.

Repository:
`renukaprasad-ms/messaging`

Application:
`unifydesk-app/`

Docs:
`unifydesk-app/docs/whatsapp/`

Your branch:
`feature/whatsapp-ui-r1`

Your EXACT frozen base SHA:
`cd5961362ddba0eda45d0ee1a881e93bab2f214c`

Create your branch from exactly that SHA. Do not branch from moving dev. Do not merge.

## Read first

- RESEARCH-META-WHATSAPP.md
- ARCHITECTURE.md
- API-CONTRACT.md
- MESSAGE-STATE.md
- TEMPLATE-POLICY.md
- SECURITY.md
- TASKS.md

Inspect current:
- metaWhatsAppApi.ts
- metaWhatsAppSdk.ts
- CompanyConnections.tsx
- EmailInbox.tsx
- channel/inbox types
- existing Media upload UI/API
- IAM patterns
- Snackbar/error conventions
- relevant frontend tests

## Mission

Implement complete Round-1 WhatsApp customer UX against frozen APIs without waiting for backend workers.

## Exact ownership

You own:
- frontend/src/features/channel/metaWhatsAppApi.ts
- frontend/src/features/channel/metaWhatsAppSdk.ts
- WhatsApp-related types in frontend/src/features/channel/types.ts
- frontend/src/pages/company/CompanyConnections.tsx
- frontend/src/pages/inbox/EmailInbox.tsx
- new frontend/src/features/whatsapp/**
- new frontend/src/components/whatsapp/**
- colocated tests/fixtures

Do not modify backend or Flyway.

## Embedded Signup v4

Required:
1. Page load must NOT call an endpoint that mints signup state.
2. On explicit Connect:
   - POST signup-session
   - load/initialize Meta SDK
   - install one message listener
   - launch Facebook Login for Business v4
3. Remove `sessionInfoVersion: '3'`.
4. Validate event origin and WA_EMBEDDED_SIGNUP shape.
5. Handle:
   - FINISH
   - CANCEL
   - ERROR
   - FB callback without code
   - SDK failure
   - popup/overall timeout
6. Listener/timer/promise cleanup exactly once.
7. Complete signup once with returned code/state/provider hints.
8. Reload connection state after completion.

A retry always obtains a fresh state.

## Connections UX

Render product language:
- Not connected
- Connecting
- Validating
- Connected
- Temporarily degraded
- Action required
- Authorization expired
- Disconnected
- Error

Use `nextAction` to expose:
- Retry
- Reconnect/reauthorize
- Refresh status
- Open Meta instructions when backend says appropriate

Never render access token, PIN, code, fbtrace/raw Graph body.

Show phone number/verified name/quality only when returned.

Disconnect confirmation:
- explains it disconnects UnifyDesk
- does not imply Meta business assets are deleted

Coexistence:
- do not expose an enable flow unless backend capability is explicitly true; default off.

## Inbox UX

Current page is email-oriented. Refactor conditionally by selected connection.

For WhatsApp:
- no To/CC/BCC/Subject composer
- chat-style text composer
- reply action
- send status indicator: sending/accepted/sent/delivered/read/failed/unknown where contract supplies it
- UNKNOWN warning: delivery uncertain; do not encourage immediate duplicate resend
- composer state decides if text is allowed
- if template required, show template picker
- template variable form from components
- image/PDF attachment using existing Media flow
- inbound unsupported media/message placeholder still visible
- preserve email behavior unchanged

## Permissions

Respect:
- whatsapp.read
- whatsapp.send
- whatsapp.modify
- whatsapp.template.read/modify
- media permissions as existing Media flow requires

Do not hide backend 403/404 into misleading success.

## Accessibility

- keyboard usable dialogs/picker
- focus management
- labelled loading/error controls
- disabled send during active request
- mobile/responsive chat layout
- no color-only status meaning

## Independence

Implement API clients/types from API-CONTRACT.md and use mocks/fixtures.

Do not wait for B1/B2 branches and do not invent alternate backend fields.

## Tests

At minimum:
- no signup API on page load
- fresh signup on each click/retry
- v4 options; no sessionInfoVersion 3
- FINISH/CANCEL/ERROR/timeout
- listener cleanup
- duplicate completion prevented
- connection state/nextAction
- reconnect
- disconnect dialog
- WhatsApp vs email composer
- template-required flow
- template variables
- media attachment
- UNKNOWN send state
- permission-disabled controls
- basic accessibility/mobile behaviors

Run:
```bash
cd unifydesk-app/frontend
npm ci
npm run format:check
npm run lint
npm test
npm run build
```

## Forbidden

Do not modify:
- backend
- migrations
- architecture docs
- Ads
- deployment secrets

## Final report

Report:
- branch
- final SHA
- files changed
- v4 connection flow
- health/reconnect/disconnect UX
- Inbox/template/media UX
- tests/build results
- contract deviation/blocker

Do not merge.
