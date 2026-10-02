# Test Data

## Canonical 1,000-contact fixture

Use deterministic identifiers so every result can be reconciled back to the CSV row.

Expected consent groups:

- Contacts 1-200: EMAIL `OPTED_IN`
- Contacts 201-500: SMS `OPTED_IN`
- Contacts 501-600: WHATSAPP `OPTED_IN`
- Contacts 601-1000: no explicit opt-in / `UNKNOWN` as applicable

Do not infer consent from identity presence.

## Suggested CSV columns

- `external_id`
- `display_name`
- `first_name`
- `last_name`
- `email`
- `phone`
- `country_code`
- `source`
- `email_marketing_consent`
- `sms_marketing_consent`
- `whatsapp_marketing_consent`
- `consent_evidence`
- `consent_occurred_at`

Consent columns remain **Don't import** in ordinary Contact field mappings.

Use **Advanced: import marketing consent** for consent mappings.

### Consent mappings

EMAIL:

`stateColumn = email_marketing_consent`

SMS:

`stateColumn = sms_marketing_consent`

WHATSAPP:

`stateColumn = whatsapp_marketing_consent`

Evidence:

`evidenceColumn = consent_evidence`

Occurred at:

`occurredAtColumn = consent_occurred_at`

Use an ISO-8601 timestamp that passes the backend future-skew/max-age validation.

## Duplicate strategy fixtures

### New import

Use `SKIP` where the intent is to preserve existing Contacts unchanged.

### Re-import to apply/update consent

Use `MERGE_EXACT`.

Reason: current mutation code returns immediately for an existing exact identity under `SKIP`; consent application occurs later in the row mutation flow.

### Conflict fixture

Use `FAIL` to prove exact identity conflicts are surfaced as row failures.

## Minimum provider fixtures

For every provider under test create:

- one eligible opted-in Contact;
- one `UNKNOWN` consent Contact;
- one explicitly opted-out Contact;
- one canonical-suppressed Contact;
- one Contact whose identity is changed after freeze;
- one Contact whose consent is revoked after freeze;
- one duplicate provider-event fixture.

Additional Managed Email fixtures:

- deliverable mailbox;
- deterministic permanent-bounce mailbox/path;
- complaint test path only where the provider safely supports it.

Additional SMS fixtures:

- approved DLT/header/template destination;
- one provider rejection/NDNC path if safely supported.

Additional WhatsApp fixtures:

- Meta test recipient;
- approved template;
- inbound reply from the same phone;
- duplicate delivered/read webhook payload.

## 2k / 5k / 10k fixtures

Generate deterministic active Contacts and Lists/Segments at:

- 2,000
- 5,000
- 10,000

Do not send provider traffic to all 10k unless the account and cost controls explicitly permit it. Bounded execution can be measured using a provider sandbox/stub for pipeline behavior, while separate small real-provider runs prove provider integration.

Record:

- snapshot candidate count;
- eligible member count;
- preparation chunk count;
- batch count;
- maximum claim size;
- run duration;
- DB query/lock observations where available;
- backend memory/CPU;
- no large recipient payload returned to the frontend.

## Cleanup

Use a dedicated test company/workspace where possible.

Cleanup must preserve evidence until results are captured, then remove or archive:

- test Contacts/Lists/Segments;
- test Broadcast drafts/runs if product rules permit;
- test provider sender assets that are safe to remove;
- test Inbox conversations/messages where policy permits.

Do not delete durable provider-event evidence before duplicate/idempotency checks are complete.
