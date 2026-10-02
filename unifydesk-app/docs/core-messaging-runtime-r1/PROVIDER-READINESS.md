# Provider Readiness

## General rule

Provider readiness is not inferred from source configuration. A provider becomes PASS only when the required runtime assets exist and the request/event chain actually executes.

The backend pod imports the Kubernetes Secret `messaging-env`; the repository does not reveal live secret values.

## Provider 1 — UnifyDesk Managed Email / AWS SES

### Source prerequisites

Current configuration keys include:

- `APP_MANAGED_EMAIL_ENABLED`
- `APP_MANAGED_EMAIL_PLATFORM_SENDING_ENABLED`
- `APP_MANAGED_EMAIL_AWS_REGION`
- `APP_MANAGED_EMAIL_HOSTED_BASE_DOMAIN`
- `APP_MANAGED_EMAIL_SNS_TOPIC_ARN`
- `APP_MANAGED_EMAIL_CLOUDFLARE_ZONE_ID`
- `APP_MANAGED_EMAIL_CLOUDFLARE_API_TOKEN`

Platform AWS workload/default credentials are required for Managed Email. Customer-managed SES credentials are a separate encrypted Broadcast sender path.

### Runtime assets to prove

- AWS identity available to backend runtime.
- correct region.
- SES account production/sending state.
- active managed tenant.
- active verified domain.
- active sender.
- configuration set/SNS topic wired.
- SNS endpoint reachable.
- Cloudflare credentials only where automatic hosted/customer-domain DNS provisioning is under test.

### Current audit classification

**NOT EXECUTED.** Source is present; live secret/resource state was not accessible through the repository connector.

Do not convert this into PASS until an actual SES message ID and actual SNS event evidence are captured.

## Provider 2 — UnifyDesk Managed SMS / MSG91

### Source prerequisites

Current configuration keys include:

- `APP_MANAGED_SMS_ENABLED`
- `APP_MANAGED_SMS_PROVIDER=MSG91_SMS`
- `APP_MANAGED_SMS_PROVIDER_MODE`
- `APP_MANAGED_SMS_MASTER_AUTH_KEY`
- `APP_MANAGED_SMS_WEBHOOK_TOKEN_KEY`
- `APP_MANAGED_SMS_PLATFORM_SENDING_ENABLED`

### Runtime assets to prove

- platform/provider auth.
- company/provider account readiness.
- DLT/entity/compliance freshness.
- approved sender/header.
- approved template/flow mapping.
- callback authentication configuration.
- provider balance/sending availability.

### Current audit classification

**NOT EXECUTED.** If DLT/header/template/provider assets are missing at execution time, reclassify the corresponding cases to **BLOCKED**, not FAIL.

## Provider 3 — Meta WhatsApp

### Production onboarding

The production architecture uses Embedded Signup, server-side authorization-code exchange, token inspection, WABA/phone ownership validation, encrypted credential persistence, phone registration, app subscription and reconciliation.

The initiative handoff records:

`UnifyDesk can't onboard customers right now`

Production customer onboarding is therefore currently:

**BLOCKED BY META**

Do not weaken this architecture and do not expose manual plaintext tokens to customers.

### Test WABA path

A Meta test WABA/test phone may be used for runtime verification if legitimately available. Required proof:

- server-side credential availability without customer token exposure.
- active test connection/sender.
- approved test template.
- outbound provider message ID.
- signed delivered/read/failed webhook.
- signed inbound message webhook.
- Inbox persistence.
- canonical Contact linkage.
- duplicate webhook handling.

Current classification: **NOT EXECUTED**.

## Provider 4 — Gmail / Microsoft / SMTP direct Broadcast

This verification target conflicts with current canonical Broadcast source.

`BroadcastSenderProvider` contains historical values:

- `GMAIL`
- `MICROSOFT`
- `IMAP_SMTP`
- `SMTP`

but `isBroadcastProvider()` returns false for them. `supports(channel)` requires `isBroadcastProvider()`, and `BroadcastLaunchService` rejects unsupported providers. `BroadcastSenderService.refresh()` also states mailbox providers cannot be refreshed as Broadcast senders.

Therefore canonical new Broadcast execution against Gmail/Microsoft/SMTP is not currently supported.

Current classification for the requested Provider-4 regression: **FAIL — source/scope mismatch**.

This is not a missing-secret problem. Restoring mailbox-backed canonical Broadcast would be product/architecture work and should not be smuggled into this narrow verification round without an explicit MAIN scope decision.

## Customer-managed bulk Email providers already supported by canonical Broadcast

The modern Broadcast sender service has explicit customer-managed connections for:

- Amazon SES
- SendGrid
- Azure Communication Services Email
- MSG91 Email

Those are the direct/customer-managed bulk Email providers represented by current canonical Broadcast source.
