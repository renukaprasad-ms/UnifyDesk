# Live Execution — Phase 1

## Frozen production baseline

`f2760fe0a11ad571f984636a65304a64b77459ac`

Do not run this phase against a moving `dev` checkout.

## Goal

Prove the first executable chain:

`Contacts -> Email marketing consent -> List/Segment audience -> Preview -> immutable freeze -> UnifyDesk Managed Email -> AWS SES acceptance -> SES/SNS delivery truth`

## Gate 0 — exact deployment and runtime prerequisites

Before mutating test data, capture:

```bash
cd ~/messaging
git fetch origin
git rev-parse HEAD
git rev-parse origin/dev
kubectl get deploy,pods
kubectl get secret messaging-env
kubectl exec deploy/backend -- sh -lc 'env | cut -d= -f1 | grep -E "^(APP_MANAGED_EMAIL|AWS_|CHANNEL_CREDENTIAL)" | sort'
```

Required production Git SHA:

`f2760fe0a11ad571f984636a65304a64b77459ac`

Never print secret values into evidence.

For Managed Email, verify presence/configuration through product/runtime responses and safe environment-name checks for:

- `APP_MANAGED_EMAIL_ENABLED`
- `APP_MANAGED_EMAIL_PLATFORM_SENDING_ENABLED`
- `APP_MANAGED_EMAIL_AWS_REGION`
- `APP_MANAGED_EMAIL_SNS_TOPIC_ARN`
- AWS workload/default credential availability

## Gate 1 — Managed Email readiness

Authenticated APIs:

```text
GET /api/v1/companies/{companyId}/managed-email/overview
GET /api/v1/companies/{companyId}/managed-email/domains
GET /api/v1/companies/{companyId}/managed-email/senders
GET /api/v1/companies/{companyId}/broadcast/readiness
```

Required before provider execution:

- managed tenant active/sending enabled;
- domain ACTIVE/verified;
- sender ACTIVE/eligible;
- Broadcast readiness reports the sender usable.

Capture IDs/status only; redact credentials.

## Gate 2 — deterministic Contacts import

Import API:

```text
POST /api/v1/companies/{companyId}/contacts/imports
POST /api/v1/companies/{companyId}/contacts/imports/{importId}/preview
POST /api/v1/companies/{companyId}/contacts/imports/{importId}/commit
GET  /api/v1/companies/{companyId}/contacts/imports/{importId}
```

Use the deterministic fixture documented in `TEST-DATA.md`.

For a fresh import, use `SKIP`.

For re-import where existing Contacts must receive consent, use `MERGE_EXACT`.

Email consent mapping:

```json
{
  "channel": "EMAIL",
  "stateColumn": "email_marketing_consent",
  "source": "IMPORT",
  "evidenceColumn": "consent_evidence",
  "occurredAtColumn": "consent_occurred_at"
}
```

Expected proof:

- exact Contact/EMAIL identity exists;
- effective EMAIL/MARKETING state is `OPTED_IN` only for intended rows;
- evidence/timestamp is retained;
- UNKNOWN/non-opted-in rows remain ineligible when affirmative opt-in is required.

## Gate 3 — audience

Create/use a canonical List or Segment under:

```text
/api/v1/companies/{companyId}/contacts/lists
/api/v1/companies/{companyId}/contacts/segments
```

For Segment, validate/preview through:

```text
POST /api/v1/companies/{companyId}/contacts/segments/validate
POST /api/v1/companies/{companyId}/contacts/segments/preview
```

Record the source ID/version/filter hash as applicable.

## Gate 4 — Broadcast Preview

```text
POST /api/v1/companies/{companyId}/broadcast/audiences/preview
```

Example List request:

```json
{
  "sourceType": "LIST",
  "sourceId": 123,
  "contactIds": null,
  "channelType": "EMAIL",
  "senderId": 456
}
```

Capture the complete authoritative `sourceDescriptor`, selected/eligible/excluded counts and exclusion reasons.

Invariant:

`selectedCount = eligibleCount + excludedCount`

## Gate 5 — immutable freeze and send

Create the canonical Broadcast draft:

```text
POST /api/v1/companies/{companyId}/broadcast/audiences/messages
```

Then send:

```text
POST /api/v1/companies/{companyId}/broadcast/messages/{broadcastId}/send
```

with exactly the descriptor returned by Preview:

```json
{
  "expectedSourceDescriptor": {
    "...": "exact Preview sourceDescriptor"
  }
}
```

Before the positive send, execute one negative stale-Preview case by mutating List/Segment after Preview and proving:

`409 BROADCAST_AUDIENCE_PREVIEW_STALE`

with no Broadcast run created.

## Gate 6 — SES provider truth

For the positive eligible recipient capture:

1. Broadcast/run/recipient IDs.
2. frozen audience snapshot ID.
3. recipient Contact and identity IDs.
4. recipient state before provider call.
5. actual SES provider message ID.
6. transition to `ACCEPTED_BY_PROVIDER`.
7. signed SNS event ID/message ID.
8. delivery projection.
9. durable Broadcast terminal state.
10. `broadcast.completed:<runId>` or failure event as applicable.

Provider acceptance is not delivery.

Only mark CMR1-015 PASS after an actual SES request succeeds and its provider message ID is persisted.

Only mark CMR1-016 PASS after the signed SNS delivery event is ingested and correlated.

## Gate 7 — suppression/idempotency

After the normal delivery smoke test, separately exercise a safe permanent-bounce path.

Verify:

- matched Managed Email provider event;
- canonical Contact suppression convergence;
- legacy Broadcast suppression compatibility;
- recipient/stats transition where applicable;
- replay of the same provider event does not duplicate effects.

## Evidence discipline

Record identifiers and state transitions, not secrets.

For every result use exactly:

`PASS | FAIL | BLOCKED | NOT EXECUTED`

If the exact frozen SHA is not deployed, stop provider verification and classify deployment as BLOCKED/NOT EXECUTED rather than testing an unknown build.
