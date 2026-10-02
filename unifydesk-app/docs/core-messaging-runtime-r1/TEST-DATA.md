# Test Data

## Phase 1 smoke fixture

Before the 1,000-contact fixture, use this small deterministic set so Contacts -> consent -> audience -> freeze can be reconciled manually.

Use a current valid **past** ISO-8601 `consent_occurred_at` when executing. The examples below use `2026-10-01T12:00:00Z` for the current verification date.

| external_id | display_name | email | phone | email_marketing_consent | sms_marketing_consent | whatsapp_marketing_consent | consent_evidence | expected purpose |
|---|---|---|---|---|---|---|---|---|
| CMR1-001 | Runtime Email Opted In A | cmr1.email.a@example.com | +919900000001 | YES | UNKNOWN | UNKNOWN | runtime-r1-explicit-consent | eligible Email fixture |
| CMR1-002 | Runtime Email Opted In B | cmr1.email.b@example.com | +919900000002 | YES | UNKNOWN | UNKNOWN | runtime-r1-explicit-consent | second eligible fixture |
| CMR1-003 | Runtime Email Unknown | cmr1.email.unknown@example.com | +919900000003 | UNKNOWN | UNKNOWN | UNKNOWN |  | consent-required fixture |
| CMR1-004 | Runtime Email Opted Out | cmr1.email.optout@example.com | +919900000004 | NO | UNKNOWN | UNKNOWN | runtime-r1-explicit-preference | opted-out fixture |
| CMR1-005 | Runtime SMS Opted In | cmr1.sms@example.com | +919900000005 | UNKNOWN | YES | UNKNOWN | runtime-r1-explicit-consent | SMS fixture |
| CMR1-006 | Runtime WhatsApp Opted In | cmr1.wa@example.com | +919900000006 | UNKNOWN | UNKNOWN | YES | runtime-r1-explicit-consent | WhatsApp fixture |
| CMR1-007 | Runtime Invalid Identity | not-an-email |  | YES | UNKNOWN | UNKNOWN | runtime-r1-explicit-consent | invalid-row fixture |

For ordinary field mappings map only the Contact columns needed for identity/name. Leave the three consent columns out of ordinary field mapping and configure them under **Advanced: import marketing consent**.

For the first import use `SKIP`. Record the invalid-row outcome rather than fixing the row before evidence is captured.

For duplicate/consent semantics:

1. re-import an existing exact Contact with `SKIP` after changing its Email consent value;
2. prove the existing Contact is reused and imported consent is not applied;
3. re-import the same exact Contact with `MERGE_EXACT`;
4. prove the intended consent state/evidence is then applied.

Do not infer any consent state from email/phone presence alone.

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
