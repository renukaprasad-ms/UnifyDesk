# Live Execution — Core Messaging Runtime R1

## Frozen production baseline

`f2760fe0a11ad571f984636a65304a64b77459ac`

All executable evidence in this file must refer to that exact production SHA unless L1 integrates a narrow production remediation and deliberately freezes a new SHA.

## Phase A — prove the deployed build

Run from the trusted UnifyDesk server/runtime environment.

```bash
cd ~/messaging
git fetch origin
printf 'local HEAD: '; git rev-parse HEAD
printf 'origin/dev: '; git rev-parse origin/dev

kubectl get deploy,pods -o wide
kubectl get deploy backend -o jsonpath='{.spec.template.spec.containers[?(@.name=="backend")].image}{"\n"}'
kubectl get pods -l app=backend -o jsonpath='{range .items[*]}{.metadata.name}{"  "}{.status.phase}{"  "}{.spec.containers[?(@.name=="backend")].image}{"\n"}{end}'
```

Required source SHA:

`f2760fe0a11ad571f984636a65304a64b77459ac`

If the deployed artifact cannot be tied to that SHA, stop and classify Phase A `BLOCKED`. Do not execute Contacts/provider tests against an unknown build.

Inspect runtime configuration **names/presence only**:

```bash
kubectl exec deploy/backend -- sh -lc \
'env | cut -d= -f1 | grep -E "^(APP_MANAGED_EMAIL|APP_MANAGED_SMS|AWS_|META_WHATSAPP|CHANNEL_CREDENTIAL)" | sort'
```

Never print secret values into evidence.

## Phase B — Contact import

Use the deterministic smoke fixture in `TEST-DATA.md`.

Current API sequence:

```text
POST /api/v1/companies/{companyId}/contacts/imports
<upload file using the returned upload.url contract or serverUploadUrl>
POST /api/v1/companies/{companyId}/contacts/imports/{importId}/preview
POST /api/v1/companies/{companyId}/contacts/imports/{importId}/commit
GET  /api/v1/companies/{companyId}/contacts/imports/{importId}
GET  /api/v1/companies/{companyId}/contacts/imports/{importId}/errors
```

Create request:

```json
{
  "fileName": "core-messaging-runtime-smoke.csv",
  "contentType": "text/csv",
  "sizeBytes": 1234
}
```

Use the actual file byte size.

Expected field mappings for the smoke fixture:

```json
[
  {"column":"display_name","target":{"type":"BUILTIN","field":"displayName"}},
  {"column":"email","target":{"type":"IDENTITY","identityType":"EMAIL"}},
  {"column":"phone","target":{"type":"IDENTITY","identityType":"PHONE"}}
]
```

Do not map consent columns as ordinary Contact fields.

First import duplicate strategy:

`SKIP`

Capture:

- import ID;
- total/success/error counts;
- representative Contact IDs;
- canonical EMAIL/PHONE identity IDs/normalized values;
- invalid row error classification;
- tenant/company ownership.

## Phase C — Email marketing consent

Consent import uses `consentMappings`, not ordinary field mappings.

Email mapping:

```json
{
  "channel": "EMAIL",
  "stateColumn": "email_marketing_consent",
  "source": "IMPORT",
  "evidenceColumn": "consent_evidence",
  "occurredAtColumn": "consent_occurred_at"
}
```

For an existing exact Contact that must receive an imported consent change, use:

`MERGE_EXACT`

Negative control:

1. re-import an existing exact Contact with `SKIP`;
2. change the CSV consent value;
3. prove the imported consent is not applied.

Positive control:

1. re-import the same exact Contact with `MERGE_EXACT`;
2. prove the effective `EMAIL / MARKETING` preference changes to the intended state;
3. capture source/evidence/occurred-at.

Do not infer consent from identity existence.

## Phase D — List

Current List APIs:

```text
POST /api/v1/companies/{companyId}/contacts/lists
POST /api/v1/companies/{companyId}/contacts/lists/{listId}/members
GET  /api/v1/companies/{companyId}/contacts/lists/{listId}
GET  /api/v1/companies/{companyId}/contacts/lists/{listId}/members
```

Create List:

```json
{
  "name": "CMR1 Runtime Email Smoke",
  "description": "Core Messaging Runtime R1 deterministic smoke audience"
}
```

Add members:

```json
{
  "contactIds": ["<opted-in-a>", "<opted-in-b>", "<unknown>", "<opted-out>"]
}
```

Capture List ID, version and member count.

## Phase D2 — Segment Validate / Preview

Current endpoints:

```text
POST /api/v1/companies/{companyId}/contacts/segments/validate
POST /api/v1/companies/{companyId}/contacts/segments/preview
POST /api/v1/companies/{companyId}/contacts/segments
```

Example Email opted-in Segment filter:

```json
{
  "filterVersion": 1,
  "filter": {
    "type": "group",
    "op": "AND",
    "children": [
      {
        "type": "predicate",
        "field": {
          "kind": "CHANNEL_PREFERENCE",
          "key": "EMAIL"
        },
        "operator": "IS_OPTED_IN",
        "value": null
      }
    ]
  },
  "sampleSize": 20
}
```

If the runtime rejects this exact field shape, capture the response and inspect current server field metadata before changing the test. Do not silently alter the contract to force a green result.

Required runtime proof:

- `/validate` returns HTTP 200 without Jackson 3 serialization/deserialization failure;
- `valid=true` for the intended filter;
- `normalizedFilter` and `filterHash` are captured;
- `/preview` returns the expected Contacts/count;
- browser Segment editor state remains unchanged after Preview.

## Phase E — Broadcast audience Preview

Current endpoint:

```text
POST /api/v1/companies/{companyId}/broadcast/audiences/preview
```

List request:

```json
{
  "sourceType": "LIST",
  "sourceId": <listId>,
  "contactIds": null,
  "channelType": "EMAIL",
  "senderId": <managedEmailSenderId>
}
```

Segment request uses `sourceType = SEGMENT` and that Segment ID.

Capture the complete returned:

`sourceDescriptor`

plus:

- `selectedCount`
- `eligibleCount`
- `excludedCount`
- exclusion counts/reasons
- eligible/excluded samples
- sender readiness
- `computedAt`

Required invariant:

`selectedCount = eligibleCount + excludedCount`

## Phase E2 — stale Preview rejection

For List and Segment independently:

1. Preview and record source descriptor X.
2. Mutate List membership or Segment definition.
3. Create/update the Broadcast draft as needed.
4. attempt Send/Schedule with exactly descriptor X.

Expected:

- HTTP 409;
- canonical stale-preview error such as `BROADCAST_AUDIENCE_PREVIEW_STALE`;
- no run created;
- no provider request;
- no newer source silently frozen.

Any deviation is a candidate runtime source defect and must be recorded before remediation.

## Phase E3 — unchanged-source freeze

Repeat Preview without modifying the source.

Create canonical audience Broadcast:

```text
POST /api/v1/companies/{companyId}/broadcast/audiences/messages
```

The request must use the same List/Segment source and Email sender used for Preview.

Send:

```text
POST /api/v1/companies/{companyId}/broadcast/messages/{broadcastId}/send
```

Body:

```json
{
  "expectedSourceDescriptor": {
    "sourceType": "LIST",
    "sourceId": "<exact Preview sourceId>",
    "sourceVersion": "<exact Preview sourceVersion>",
    "filterVersion": null,
    "filterHash": null,
    "contactSelectionHash": null,
    "resolvedAt": "<exact Preview resolvedAt>"
  }
}
```

For Segment, echo every non-null descriptor field exactly.

Expected before provider verification:

- immutable audience snapshot exists;
- snapshot candidates are frozen from the confirmed source descriptor;
- Broadcast/message is bound to the frozen snapshot;
- eligible/excluded accounting matches the runtime contract.

## Phase F — send-time eligibility

Freeze an opted-in Contact, then before provider dispatch perform one controlled safety mutation:

- opt out;
- suppress; or
- change/remove the frozen destination identity.

Expected:

- recipient becomes `SKIPPED`;
- explicit reason is durable;
- provider receives no request for that recipient.

## Phase G — all skipped / zero pending

Make every frozen recipient ineligible before dispatch.

Expected:

- no provider call;
- preparation finishes;
- run/message reaches terminal state;
- exactly one durable `broadcast.completed:<runId>` or applicable terminal event is present;
- no duplicate Notification.

## Phase H — Managed Email / SES

Only begin after Phase A–G have meaningful evidence.

Readiness APIs:

```text
GET /api/v1/companies/{companyId}/managed-email/overview
GET /api/v1/companies/{companyId}/managed-email/domains
GET /api/v1/companies/{companyId}/managed-email/senders
GET /api/v1/companies/{companyId}/broadcast/readiness
```

Required readiness proof:

- Managed Email enabled;
- platform sending enabled;
- AWS runtime identity available;
- region correct;
- SES can send;
- managed tenant ACTIVE/ready;
- domain ACTIVE/verified;
- sender ACTIVE;
- configuration-set/SNS path present where required.

If a prerequisite is absent, classify `BLOCKED` / environment-configuration issue. Do not weaken source guards.

## Phase H2 — one-recipient SES smoke

Send exactly one explicitly opted-in Email Contact.

Capture:

- company/workspace ID;
- Contact ID;
- identity ID;
- effective preference/evidence;
- List/Segment ID and descriptor;
- Broadcast ID;
- run ID;
- snapshot ID;
- recipient ID;
- sender ID;
- provider;
- SES provider message ID;
- acceptance timestamp;
- durable recipient state.

After SES API acceptance, expected core state is acceptance/sent truth such as `ACCEPTED_BY_PROVIDER`.

Do **not** call this Delivered.

## Phase I — SNS delivery / bounce / duplicate event

For a deliverable test mailbox:

`SES acceptance -> signed SNS event -> durable provider event -> projection/state/stats`

Capture SES message ID, SNS/event ID, event type and persisted projection.

For permanent bounce, verify the complete matched provider event reaches suppression feedback and canonical Contact suppression converges.

Where safe, replay the same provider event and compare before/after counts. Duplicate processing must not duplicate recipient transitions, usage, suppression, Broadcast finalization or Notification.

Complaint remains `BLOCKED` or `NOT EXECUTED` unless a valid provider test path exists.

## Evidence format

For every case write exactly one classification:

`PASS | FAIL | BLOCKED | NOT EXECUTED`

A PASS requires actual execution evidence. Source review is never sufficient.
