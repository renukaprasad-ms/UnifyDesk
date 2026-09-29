# WhatsApp HTTP API Contract

Status: FROZEN FOR ROUND 1

All authenticated company endpoints use the existing ApiResponse<T> envelope and decimal IDs serialized as strings in JSON.

## Public error envelope

Keep the repository's standard API error shape. WhatsApp-specific stable error codes are exposed through the normal error_code/error_message mechanism where supported.

Public codes may include:
- WHATSAPP_NOT_CONFIGURED
- WHATSAPP_SIGNUP_EXPIRED
- WHATSAPP_SIGNUP_REPLAYED
- WHATSAPP_PROVIDER_AUTH_FAILED
- WHATSAPP_PERMISSION_MISSING
- WHATSAPP_ASSET_NOT_GRANTED
- WHATSAPP_PHONE_OWNED_BY_OTHER_COMPANY
- WHATSAPP_ACTION_REQUIRED
- WHATSAPP_REAUTH_REQUIRED
- WHATSAPP_PROVIDER_TEMPORARY
- WHATSAPP_TEMPLATE_REQUIRED
- WHATSAPP_TEMPLATE_NOT_APPROVED
- WHATSAPP_MEDIA_INVALID
- WHATSAPP_SEND_AMBIGUOUS

Never expose raw provider response bodies, access tokens, codes or PINs.

## B1 control-plane APIs

Base:
`/api/v1/companies/{companyId}/channels/whatsapp/meta`

### POST /signup-session

Permission: whatsapp.connect

Called only at Connect/Reconnect click time.

Response:
```json
{
  "appId": "string",
  "configurationId": "string",
  "graphApiVersion": "vNN.0",
  "embeddedSignupVersion": "4",
  "state": "opaque",
  "expiresAt": "2026-09-28T...",
  "coexistenceAvailable": false
}
```

No page-load prefetch requirement.

### POST /complete

Permission: whatsapp.connect

Request:
```json
{
  "state": "opaque",
  "code": "authorization-code",
  "wabaId": "optional-hint",
  "phoneNumberId": "optional-hint",
  "whatsappAccountId": "optional-hint",
  "messagingAccountId": "optional-hint"
}
```

Semantics:
- one-time state consumed atomically;
- code exchanged server-side;
- all provider IDs validated;
- creates/reuses local connection and provisioning workflow;
- may return PENDING/ACTION_REQUIRED rather than falsely returning ACTIVE.

Response:
```json
{
  "connectionId": "123",
  "status": "CONNECTING|VALIDATING|ACTIVE|DEGRADED|ACTION_REQUIRED|REAUTH_REQUIRED|ERROR",
  "wabaId": "optional",
  "whatsappAccountId": "optional",
  "messagingAccountId": "optional",
  "phone": {
    "senderIdentityId": "123",
    "phoneNumberId": "provider-id",
    "displayPhoneNumber": "+...",
    "verifiedName": "optional",
    "qualityRating": "optional"
  },
  "nextAction": "NONE|WAIT|REAUTHORIZE|OPEN_META|RETRY"
}
```

HTTP 200 for idempotent completion/retry that resolves existing workflow.

### GET /connection

Permission: whatsapp.read

Response:
```json
{
  "connectionId": "123",
  "genericStatus": "PENDING|ACTIVE|ERROR|DISCONNECTED",
  "status": "CONNECTING|VALIDATING|ACTIVE|DEGRADED|ACTION_REQUIRED|REAUTH_REQUIRED|DISCONNECTED|ERROR",
  "authState": "VALID|EXPIRING|EXPIRED|REVOKED|MISSING_PERMISSION|UNKNOWN",
  "registrationState": "PENDING|REGISTERED|ACTION_REQUIRED|ERROR",
  "subscriptionState": "PENDING|SUBSCRIBED|ERROR",
  "coexistenceState": "DISABLED|ELIGIBLE_UNVERIFIED|ENABLED|ERROR",
  "lastValidatedAt": "optional timestamp",
  "phone": { "phoneNumberId":"...", "displayPhoneNumber":"...", "verifiedName":"...", "qualityRating":"..." },
  "safeErrorCode": "optional",
  "nextAction": "NONE|WAIT|REAUTHORIZE|OPEN_META|RETRY"
}
```

### POST /reconcile

Permission: whatsapp.modify

Runs/queues bounded provider reconciliation and returns current cached state. Endpoint is idempotent and rate-limited.

### POST /reauthorize-session

Permission: whatsapp.connect

Same response shape as signup-session, but backend binds expected existing connection/phone and will reject ownership transfer.

### POST /disconnect

Permission: whatsapp.disconnect

Preferred Meta-specific wrapper. Request may contain existing DisconnectDataPolicy semantics if product keeps retention choice.

Semantics:
- disable local send immediately;
- best-effort safe provider cleanup if appropriate;
- delete local credentials;
- do not delete customer Meta assets;
- schedule existing local retention/purge policy.

Generic `/channels/{connectionId}/disconnect` may remain supported, but Meta provider must implement DisconnectableChannelProvider safely.

## B2 template APIs

Base:
`/api/v1/companies/{companyId}/channels/whatsapp/meta`

### GET /templates?connectionId=...

Permission: whatsapp.template.read

Returns only company-owned connection templates.

Item:
```json
{
  "id":"local-id",
  "providerTemplateId":"optional",
  "name":"order_update",
  "language":"en_US",
  "category":"UTILITY",
  "status":"APPROVED",
  "components":[...],
  "syncedAt":"timestamp"
}
```

### POST /templates/sync

Permission: whatsapp.template.read or whatsapp.template.modify per current IAM convention; freeze to whatsapp.template.modify for explicit network mutation.

Request:
`{"connectionId":"123"}`

Returns summary:
`{"synced":12,"approved":10,"rejected":2,"syncedAt":"..."}`

## B2 Inbox/WhatsApp APIs

### Existing Inbox read endpoints

Existing paths remain:
- GET /api/v1/companies/{companyId}/inbox/conversations
- GET /api/v1/companies/{companyId}/inbox/conversations/{conversationId}
- GET /api/v1/companies/{companyId}/inbox/conversations/{conversationId}/detail
- GET /api/v1/companies/{companyId}/inbox/conversations/{conversationId}/messages
- read-state endpoints
- realtime snapshot

Frozen change:
- connectionId identifies channel.
- server resolves the connection channel and enforces EMAIL_* or WHATSAPP_* permissions accordingly.
- WhatsApp does not expose EmailFolder as a product concept. If the legacy folder query remains in the route for compatibility, server ignores/normalizes it for WhatsApp and uses WHATSAPP internally.
- list without connectionId may return permitted channels only; a user with email.read but not whatsapp.read cannot see WhatsApp conversations and vice versa.

### POST /api/v1/companies/{companyId}/inbox/conversations/{conversationId}/whatsapp/messages

Permission: whatsapp.send

Text/reply request:
```json
{
  "clientRequestId":"uuid-or-opaque-id",
  "type":"TEXT",
  "text":"hello",
  "replyToMessageId":"optional-local-inbox-message-id"
}
```

Template request:
```json
{
  "clientRequestId":"...",
  "type":"TEMPLATE",
  "templateId":"local-template-id",
  "parameters": {
    "header": [],
    "body": ["value1"],
    "buttons": []
  }
}
```

Media request:
```json
{
  "clientRequestId":"...",
  "type":"MEDIA",
  "mediaId":"existing-unifydesk-media-id",
  "caption":"optional",
  "replyToMessageId":"optional"
}
```

Response:
```json
{
  "messageId":"local-inbox-message-id",
  "clientRequestId":"...",
  "providerMessageId":"optional",
  "status":"PENDING|ACCEPTED|SENT|DELIVERED|READ|FAILED|UNKNOWN",
  "acceptedAt":"optional timestamp",
  "safeErrorCode":"optional"
}
```

Duplicate clientRequestId returns the existing logical result.

HTTP semantics:
- 409 WHATSAPP_TEMPLATE_REQUIRED when free-form is outside allowed customer-service session.
- 409 WHATSAPP_TEMPLATE_NOT_APPROVED for stale/non-approved template.
- 409/422 for invalid media/company ownership.
- 503 for definite provider temporary failure before submission.
- 202 or 200 with status UNKNOWN for ambiguous provider outcome; never tell frontend to blind retry with a new clientRequestId.

### GET /api/v1/companies/{companyId}/inbox/conversations/{conversationId}/whatsapp/composer

Permission: whatsapp.send

Response:
```json
{
  "freeFormAllowed": true,
  "customerServiceExpiresAt":"optional timestamp",
  "templateRequired": false,
  "supportedMedia":["IMAGE","DOCUMENT"],
  "connectionStatus":"ACTIVE"
}
```

This is UX guidance. Send endpoint rechecks everything.

## Frontend behavior contract

F1:
- loads SDK metadata without minting state if needed;
- obtains POST signup-session immediately before FB.login;
- launches Embedded Signup v4;
- treats CANCEL/ERROR/popup timeout separately;
- posts completion once;
- reloads connection health after completion;
- renders nextAction instead of raw Graph error;
- composer queries WhatsApp composer state and offers template picker when required.

## Security requirements for every company endpoint

- current authenticated user required;
- active company membership;
- exact permission;
- any connection/conversation/template/media ID is re-scoped by company;
- 404 preferred over revealing foreign-object existence;
- provider IDs from browser are never direct repository mutation keys.
