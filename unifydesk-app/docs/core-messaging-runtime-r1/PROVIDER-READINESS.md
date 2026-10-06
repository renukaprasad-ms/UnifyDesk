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

### Current executable classification

**BLOCKED AT PHASE 0 / RUNTIME ACCESS.**

The source is present, but this execution session cannot yet prove that the deployed k3s backend is the frozen SHA or safely inspect runtime environment-variable names/presence. No Managed Email readiness API, AWS runtime identity check, SES send, or SNS event has been executed in this regenerated round.

Do not convert this into PASS until:
1. deployed SHA is proven;
2. Managed Email runtime readiness is executed;
3. an actual SES provider message ID is persisted for the controlled test recipient;
4. actual SNS/provider event evidence is captured for delivery-state claims.

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

## Provider 4 — Direct customer-managed bulk Email

### MAIN decision

Provider-4 verification scope is now explicitly:

- Amazon SES (customer-managed Broadcast connection)
- SendGrid
- Azure Communication Services Email
- MSG91 Email

Gmail, Microsoft, IMAP/SMTP and SMTP mailbox transports are **not** Provider-4 targets for this runtime-verification round. Their historical enum values remain outside the modern Broadcast-provider contract and do not require remediation here.

### Common regression contract

For each Provider-4 sender, verify that provider selection does not bypass:

- canonical Contacts/List/Segment audience selection;
- authoritative Preview descriptor;
- immutable final audience freeze;
- send-time marketing consent;
- canonical Contact suppression;
- tenant/company ownership;
- durable Broadcast recipient/run state;
- terminal finalization;
- durable Notification emission.

Provider acceptance must be presented as acceptance/sent only. Delivery/read claims require actual provider event support and evidence.

### Amazon SES — customer-managed

Required runtime assets:

- active customer-managed SES Broadcast sender;
- encrypted connection credentials or supported IAM-role setup;
- verified From identity;
- account/region sending enabled.

Capture provider message ID and Broadcast recipient state. This is distinct from **UnifyDesk Managed Email**, which uses platform-owned AWS runtime credentials/control-plane state.

### SendGrid

Required runtime assets:

- active SendGrid Broadcast sender;
- valid encrypted API key;
- verified sender identity;
- provider sending enabled.

Capture provider request/message identity where returned and preserve acceptance-vs-delivery semantics.

### Azure Communication Services Email

Required runtime assets:

- active Azure Email Broadcast sender;
- valid endpoint/access key;
- verified sender identity/domain as required by Azure;
- provider sending enabled.

Capture provider message/operation identity and do not infer final delivery without provider event evidence.

### MSG91 Email

Required runtime assets:

- active MSG91 Email Broadcast sender;
- valid auth key/domain;
- published Email template;
- provider template binding synchronized to the selected Broadcast connection.

Capture the run's provider-template binding snapshot and provider message identity.

### Current audit classification

All four Provider-4 cases are **NOT EXECUTED** until real provider calls are made.

