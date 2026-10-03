# B2 — Core Messaging Runtime R1 recipient Email unsubscribe remediation

You are **B2** for one narrow UnifyDesk Core Messaging Runtime R1 remediation.

Repository:

`renukaprasad-ms/UnifyDesk`

Your exact frozen production/remediation base is:

`f2760fe0a11ad571f984636a65304a64b77459ac`

Create:

`fix/core-messaging-email-unsubscribe-r1`

from **exactly** that SHA.

Do NOT branch from moving `dev`.

Do NOT branch from PR #251 head.

Do NOT rebase.

Do NOT merge your own PR.

PR target:

`dev`

## Findings owned

### CMR1-RUNTIME-001A — HIGH

Recipient-facing Email marketing unsubscribe action is missing.

### CMR1-RUNTIME-001B — HIGH

Contact preference current projection is arrival-order and allows an older imported consent event to overwrite a newer recipient opt-out.

Own the complete narrow backend/source contract for these findings.

Do not redesign Contacts or Broadcast generally.

## Read completely before changing code

Read the Runtime R1 source-review evidence from PR #251 / branch:

`review/core-messaging-runtime-r1-execution`

especially:

- `unifydesk-app/docs/core-messaging-runtime-r1/EXECUTION-RESULTS.md`
- `unifydesk-app/docs/core-messaging-runtime-r1/REMEDIATION-PLAN.md`
- `unifydesk-app/docs/core-messaging-runtime-r1/E2E-VERIFICATION-MATRIX.md`
- `unifydesk-app/docs/core-messaging-runtime-r1/STATE-TRANSITIONS.md`
- `unifydesk-app/docs/core-messaging-runtime-r1/TEST-DATA.md`
- `unifydesk-app/docs/core-messaging-runtime-r1/LIVE-EXECUTION.md`

Then inspect exact frozen source, including at minimum:

- `unifydesk-app/backend/src/main/java/com/messaging/contacts/consent/ContactPreferenceService.java`
- `unifydesk-app/backend/src/main/java/com/messaging/contacts/consent/ContactPreferenceRepository.java`
- `unifydesk-app/backend/src/main/java/com/messaging/contacts/consent/ContactConsentTargetValidator.java`
- `unifydesk-app/backend/src/main/java/com/messaging/contacts/consent/ConsentTypes.java`
- `unifydesk-app/backend/src/main/java/com/messaging/contacts/consent/ContactEligibilityService.java`
- `unifydesk-app/backend/src/main/java/com/messaging/contacts/processing/ContactImportMutationAdapter.java`
- `unifydesk-app/backend/src/main/java/com/messaging/broadcast/transport/BroadcastEmailCommand.java`
- `unifydesk-app/backend/src/main/java/com/messaging/broadcast/service/BroadcastDeliveryService.java`
- `unifydesk-app/backend/src/main/java/com/messaging/broadcast/service/BroadcastTemplateRenderer.java`
- `unifydesk-app/backend/src/main/java/com/messaging/broadcast/transport/AmazonSesBroadcastEmailTransport.java`
- `unifydesk-app/backend/src/main/java/com/messaging/broadcast/sendgrid/SendGridGateway.java`
- `unifydesk-app/backend/src/main/java/com/messaging/broadcast/azure/AzureEmailGateway.java`
- `unifydesk-app/backend/src/main/java/com/messaging/broadcast/msg91/Msg91EmailGateway.java`
- `unifydesk-app/backend/src/main/java/com/messaging/config/SecurityConfig.java`
- related tests and migrations.

## Existing architecture you must preserve

Do not rebuild consent.

Canonical preference state already exists:

`UNKNOWN | OPTED_IN | OPTED_OUT`

Canonical preference scope is:

`company + contact + optional identity + channel + purpose`

Current purpose is:

`MARKETING`

Immutable preference event history already records source, sourceRef, actorType, actorId, reason, occurredAt and idempotency key.

Required recipient action provenance:

```text
channel   = EMAIL
purpose   = MARKETING
newState  = OPTED_OUT
source    = CONTACT_ACTION
actorType = CONTACT
```

Use the existing Contact preference architecture.

Do not create a separate unsubscribe preference database.

Do not make Contact itself immutable.

Suppression remains a separate, harder prohibition.

## A. Secure recipient action

Implement the narrowest production-safe public recipient Email unsubscribe action.

The recipient must not authenticate as the UnifyDesk workspace user.

The action authorization must be an opaque or cryptographically protected token.

Do not authorize using editable raw:

- company ID;
- Contact ID;
- identity ID;
- channel;
- purpose.

The protected subject must bind the minimum server-owned information needed to guarantee the token cannot be changed to mutate another recipient, including at least the semantic equivalent of:

- company;
- Contact;
- identity;
- EMAIL;
- MARKETING;
- action/token version;
- issued/expiry or equivalent validity controls.

Prefer a stateless authenticated-encryption token with a **dedicated recipient-action key**, rather than exposing signed plaintext database IDs.

Do not reuse provider credentials, auth JWT signing keys, suppression fingerprint keys, or customer credentials.

Do not log raw tokens or key material.

Use URL-safe encoding.

Add configuration validation so production cannot silently issue insecure tokens when the key is missing/invalid.

## B. Public action API

Add a narrowly scoped public recipient action endpoint.

Security requirements:

- only the exact recipient-action route becomes public;
- CSRF exemption only where required for the one-click POST contract;
- all normal Contacts preference APIs remain authenticated;
- token validation derives authoritative company/contact/identity/channel/purpose;
- client-supplied IDs must not be trusted for authorization;
- tampered/expired/foreign-subject actions do not mutate;
- responses are generic/safe and do not reveal whether unrelated Contacts exist;
- repeated valid unsubscribe action is success-equivalent.

Use the canonical preference service/repository mutation.

Required event provenance:

`CONTACT_ACTION / CONTACT`.

Use a deterministic idempotency identity derived from safe action metadata/nonce without storing/logging the raw token.

## C. Preference event ordering

Current defect:

`ContactPreferenceRepository.apply()` appends the event and then unconditionally updates the current projection.

Fix this without removing immutable event history.

Required semantics:

1. validate command;
2. preserve idempotent immutable event insertion;
3. compare incoming `occurredAt` with current projection `occurredAt`;
4. strictly newer valid evidence may update the projection;
5. strictly older evidence is retained as history but must not regress effective projection;
6. equal-time conflict must follow one explicit deterministic fail-closed rule;
7. use safety ordering for equal-time conflicting states unless repository policy proves a stronger existing rule:
   `OPTED_OUT > UNKNOWN > OPTED_IN`;
8. strictly later valid re-consent can become effective;
9. idempotency replay must return a deterministic current projection;
10. preserve advisory-lock/company/contact/identity/channel/purpose isolation.

Do not use ingestion/created time as consent occurrence time.

Review `ContactConsentMergeExtension` for policy consistency. Change it only if the new regression tests prove an actual contradiction in this narrow scope.

## D. Broadcast Email unsubscribe metadata

Canonical Broadcast delivery already rechecks:

`ContactEligibilityService(... Purpose.MARKETING ...)`

Keep that gate.

Introduce one provider-independent Email marketing compliance/action model above transports.

Do not expose arbitrary client-controlled raw headers.

The Email transport command should receive safe server-created metadata sufficient for:

- visible unsubscribe URL;
- one-click action URL;
- standards headers.

For canonical Broadcast Email MARKETING:

- text content must contain a visible unsubscribe action;
- HTML content must contain a visible unsubscribe action;
- avoid duplicate footer insertion on retry;
- preserve template-variable escaping/safety;
- no raw internal IDs/token contents are displayed.

Do not add this behavior to:

- OTP;
- password reset;
- security alerts;
- system notification email;
- unrelated transactional notification pipelines.

Current Broadcast recipient purpose is implicitly MARKETING. Do not invent a new transactional Broadcast architecture in this fix.

## E. Standards unsubscribe headers

Where provider capability supports custom headers, emit:

`List-Unsubscribe`

and:

`List-Unsubscribe-Post: List-Unsubscribe=One-Click`

with the protected public action URL.

### Amazon SES

The project uses AWS SDK v2 `2.54.17`.

Current SES v2 simple `Message` supports custom message headers.

Do **not** switch to raw MIME merely to add these headers.

Preserve:

- managed tenant name;
- configuration set;
- message tags/correlation;
- persisted From;
- provider truth;
- Managed Email event handling.

Add transport tests capturing `SendEmailRequest`.

### SendGrid

Current gateway sends v3 Mail Send JSON.

Adapt unsubscribe headers using the provider-supported header field.

Preserve:

- custom_args correlation;
- content;
- sender/reply-to;
- outcome classification.

Add request/payload-focused tests without real credentials.

### Azure Communication Services Email

Current Java `EmailMessage` supports custom headers.

Adapt through the existing EmailMessage API.

Preserve provider operation-ID semantics.

Add focused tests at the smallest seam practical.

### MSG91 Email

Current implementation is provider-template based and does not expose custom unsubscribe metadata.

MSG91 may add its own provider-managed unsubscribe UI, but that does **not** replace canonical UnifyDesk Contact preference mutation.

Do not invent unsupported custom-header fields.

Implement the narrowest provider-safe adaptation supported by the actual MSG91 API/template contract. If one-click headers are not supported, expose that capability truthfully while ensuring the recipient-visible canonical UnifyDesk opt-out path is available through a provider-template variable/binding contract only if it can be guaranteed safely.

Do not silently claim standards one-click support when provider evidence does not support it.

Do not broaden this task into redesigning MSG91 templates.

## F. Frozen-audience unsubscribe race

Add an executable backend regression proving:

```text
Preview/freeze while Contact is OPTED_IN
-> Contact action sets EMAIL/MARKETING OPTED_OUT
-> Broadcast dispatch continues
-> ContactEligibilityService recheck
-> recipient SKIPPED
-> Email transport never called
-> providerMessageId remains null
```

Prove final Broadcast stats/accounting remain correct.

Use the existing eligibility gate.

Do not create a second delivery-policy engine.

## G. Required tests

At minimum add tests for:

1. public recipient unsubscribe succeeds without workspace auth;
2. token tamper rejected/no mutation;
3. cross-company subject cannot be changed;
4. cross-contact/identity/channel/purpose tampering cannot mutate;
5. expired/invalid token is safe;
6. duplicate unsubscribe is idempotent;
7. event written with `CONTACT_ACTION / CONTACT`;
8. T1 import OPTED_IN;
9. T2 recipient OPTED_OUT;
10. T3 older imported OPTED_IN event retained but projection remains OPTED_OUT;
11. T4 strictly later valid re-consent becomes OPTED_IN;
12. equal-time conflict is deterministic/fail-closed;
13. frozen-audience unsubscribe produces SKIPPED and zero transport calls;
14. text marketing Email has visible unsubscribe action;
15. HTML marketing Email has visible unsubscribe action;
16. SES emits both unsubscribe headers without losing managed tags/configuration set;
17. SendGrid adapts headers;
18. Azure adapts headers;
19. MSG91 capability/adaptation behavior is truthful and tested;
20. unrelated non-Broadcast Email paths are unchanged.

Also keep current Contacts target validation, suppression tests, and provider transport tests green.

## H. Complexity and security

Preserve:

- O(1) token validation and Contact preference mutation;
- no new N+1 path;
- no network call inside preference DB transaction;
- bounded content transformation;
- tenant isolation;
- immutable event history;
- idempotency;
- encrypted credentials;
- no secret/token logging;
- exact provider acceptance truth.

## I. Verification

Run the actual repository backend matrix applicable to this change.

At minimum:

```bash
cd unifydesk-app/backend
SNOWFLAKE_NODE_ID=0 ./mvnw test
SNOWFLAKE_NODE_ID=0 ./mvnw package
```

Also run focused tests independently first where useful.

Do not weaken or skip existing tests to get green.

If local dependencies prevent execution, report exact BLOCKED evidence rather than claiming PASS.

## J. PR handoff

Open a PR to `dev`.

Do not merge it.

Return to L1 with:

- branch;
- PR;
- exact head SHA;
- files changed;
- exact token/action contract;
- exact event-order policy;
- provider-by-provider unsubscribe header/adaptation behavior;
- tests executed/results;
- any environment blockers;
- any scope item you could not implement without broader architecture.

Do not perform unrelated cleanup.
