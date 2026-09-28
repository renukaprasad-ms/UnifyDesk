# Meta App Setup Runbook — WhatsApp

Status: PRE-PRODUCTION CHECKLIST  
Date: 2026-09-28

This is configuration outside application code and must be completed in Meta before production acceptance.

## Meta app

Use the intended UnifyDesk Meta developer/business app.

Required/current concepts:
- WhatsApp product / Cloud API
- Facebook Login for Business
- Embedded Signup configuration using v4
- WhatsApp account asset access
- WhatsApp business management/messaging permissions required by the configured flow

Do not reuse an Ads-only configuration ID unless Meta explicitly shows it has the required WhatsApp Embedded Signup assets/products.

## Embedded Signup v4

Create/verify a v4 Configuration ID.

Record securely in deployment:
- META WhatsApp app ID
- app secret
- v4 configuration ID
- Graph API version
- webhook verify token

Frontend receives:
- app ID
- configuration ID
- Graph version
- signup state

Frontend never receives:
- app secret
- verify token
- provider access token
- PIN

## Domains

In Meta app settings configure production HTTPS domains/origins used by UnifyDesk.

Validate:
- frontend JavaScript SDK origin
- backend OAuth/business login redirect requirements if applicable
- privacy policy / terms / data deletion URLs required for app review
- no localhost in production config except separate development app/config if desired

## Webhook

Callback:
`https://<api-domain>/api/v1/webhooks/meta/whatsapp`

Configure verify token to match backend secret.

Subscribe the relevant WhatsApp messages/account fields required by current Cloud API.

Perform:
1. GET verification test.
2. signed POST test from Meta.
3. inbound text from test/customer number.
4. confirm sent/delivered/read/failed status delivery.

## App review / business verification

Before onboarding external businesses, verify Meta requirements for UnifyDesk's current Tech Provider/business portfolio:
- business verification
- advanced access/app review for requested permissions
- required business portfolio ownership/partner setup
- approved privacy/data handling URLs.

Exact dashboard labels can change; QA records screenshots/config IDs without committing secrets.

## Embedded Signup v4 migration

Production release MUST NOT depend on sessionInfoVersion 3.

Before 2026-10-15:
- v4 config created
- frontend launch tested with v4
- fresh customer onboarding tested
- reconnect tested

## Real-account matrix

Test at least:
- Meta test number / test business
- fresh production-like number
- existing WABA/account with one phone
- account with multiple phones
- reconnect after permission revocation
- disconnect + reconnect
- already registered phone retry
- coexistence candidate only if product plans to enable it

## Coexistence gate

Keep product feature disabled until all are proven:
- number remains valid in intended existing app scenario
- Cloud API send works
- inbound webhook arrives exactly once as expected
- template ownership/billing account understood
- disconnect does not break customer-owned WhatsApp Business App unexpectedly

## Secrets

Production secrets belong in existing secret/deployment mechanism, not git:
- APP_CHANNEL_META_WHATSAPP_APP_SECRET
- webhook verify token
- encryption master key already used by ProviderCredentialService

Rotate app secret/verify token using a documented change window because webhook signature verification depends on them.

## Go-live evidence

QA/SEC must attach/report:
- v4 config tested
- token type/expiry observed
- account IDs observed (legacy/new model)
- registration/subscription success
- inbound message
- outbound text
- outbound approved template
- image/PDF if enabled
- sent/delivered/read
- revoked permission -> REAUTH_REQUIRED
- no secret in logs
