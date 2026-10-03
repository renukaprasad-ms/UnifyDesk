# Runtime Baseline

## Exact executable baseline

Live GitHub `dev` was inspected again at the start of executable verification.

Frozen production SHA:

`f2760fe0a11ad571f984636a65304a64b77459ac`

Commit:

`Merge PR #249: Core Messaging runtime/provider verification R1 audit`

This was the initial executable baseline. L1 later integrated the narrow unsubscribe/consent-ordering remediation and deliberately re-froze Runtime R1.

## Current post-remediation executable baseline

Integrated `dev` SHA:

`f94b303c3a33fe114dbe33162cdbad8dbab14d91`

Integration lineage:

- B2 PR #253 head: `ca2a2b1ee73b902b9f57c6d9b2eb9a782674a47b`
- B2 merge commit: `c0bb643021622f4cd68711753692d3027ddb1bff`
- F1 PR #252 head: `acd808e48de169184af50c93db307e65faee2dc5`
- final L1 integration merge: `f94b303c3a33fe114dbe33162cdbad8dbab14d91`

All new executable evidence after remediation must be attributed to this exact SHA.

Historical source-audit baseline:

`850ce7ad2874d20070067d46d8604ddc2bdd2c99`

Previous final source-remediation production SHA:

`353069f922040be922ec353961386fb962d8d187`

## Post-QA production deltas reviewed

### Managed Email post-acceptance reconciliation

`ManagedEmailPostAcceptanceReconciler` now loads the earliest matched permanent-bounce `ManagedEmailProviderEvent` for the recipient/company and passes that event to:

`ManagedEmailSuppressionFeedbackService.hardBounce(runtime, event)`

This preserves SNS event identity/timestamp provenance when converging legacy Broadcast suppression, canonical Contact suppression and Broadcast recipient bounce state.

### Segment Jackson 3 compatibility

The Segment HTTP wire model and mapper use:

`tools.jackson.databind.JsonNode`

and the mapper uses Jackson 3 `ObjectMapper`. The persisted internal Segment domain is not unnecessarily rewritten.

### Segment editor Preview behavior

`SegmentEditorPage.runPreview()` sends the server-normalized AST to the Preview API but does not call `setFilter(normalized)`. The interactive operator/value/AND-OR state therefore remains customer-owned while Preview uses backend-normalized semantics.

### Inbox -> Contacts prefill

Current Contacts UI consumes query-driven draft helpers so Inbox can navigate into explicit Contact creation with email/phone prefilled. This remains an explicit user action rather than silent Contact creation.

## Core Messaging source contracts still present

### Audience preview/freeze binding

- Preview returns an authoritative source descriptor.
- Send/Schedule carry the confirmed descriptor.
- first freeze validates List/Segment/Contacts identity/version/hash.
- source drift raises `BROADCAST_AUDIENCE_PREVIEW_STALE`.
- candidate capture is done while holding source-row share locks.
- after successful freeze, retries reuse the immutable snapshot.
- send-time Contact eligibility and destination are rechecked.

### Zero-pending terminal finalization

`BroadcastPreparationChunkService.completePreparation()` sets zero-pending runs/messages to `COMPLETED` and records the durable `broadcast.completed:<runId>` Notification event in the same transaction.

### Canonical Contact eligibility

`ContactEligibilityService` resolves the canonical channel identity, effective preference, canonical Contact suppression and policy. Marketing paths can require affirmative opt-in. A mere email/phone identity is not consent.

### Import duplicate/consent semantics

`DuplicateStrategy` is:

- `SKIP`
- `MERGE_EXACT`
- `FAIL`

For an existing exact identity, `SKIP` returns before consent application. Use `MERGE_EXACT` for deterministic re-import fixtures that must apply consent to existing Contacts.

Consent timestamps are bounded by `ContactPreferenceService.validateOccurredAt()`.

## Current source status enums

Broadcast message:

`DRAFT, SCHEDULED, QUEUED, RUNNING, PAUSED, COMPLETED, PARTIALLY_FAILED, CANCELLED, FAILED`

Broadcast run:

`PREPARING, RUNNING, PAUSED, COMPLETED, PARTIALLY_FAILED, CANCELLED, FAILED`

Broadcast recipient:

`PENDING, PROCESSING, RETRY, ACCEPTED_BY_PROVIDER, FAILED, BOUNCED, UNKNOWN, SKIPPED`

Broadcast batch:

`PENDING, PROCESSING, RETRY, COMPLETED, CANCELLED, FAILED`

Provider delivery/read states can exist in provider-specific projections and must not be invented in the core recipient enum.

## CI baseline

`.github/workflows/checks.yml` currently defines GitHub-hosted `ubuntu-latest` jobs for:

- backend Spotless, clean test, package;
- current-head database migration/schema validation;
- frontend tests, build, lint, format check;
- email-provider Spotless/test/package;
- website build.

GitHub Actions run `37011044661` exists for exact frozen SHA `f2760fe0a11ad571f984636a65304a64b77459ac`.

The run reached attempt 2 and completed with conclusion `failure`, but every job returned `steps = null` and no runner-executed application steps were available. Classification: **BLOCKED — CI execution environment**. This is neither application PASS nor application-code FAIL.

Deployment proof is also currently **BLOCKED** from this execution session: there is no authenticated shell/Kubernetes channel available here to prove the live k3s backend is running the frozen SHA, and the public API does not expose a usable build-SHA endpoint to this tool environment. Provider/data mutation must not begin until the deployed build is tied to the frozen SHA.

## Runtime-secret visibility limitation

The Kubernetes backend imports the `messaging-env` Secret with `envFrom`. Repository source does not expose the deployed secret values. Therefore the repository audit cannot truthfully claim that AWS, MSG91, Meta, Cloudflare or webhook secrets are mounted in the live pod.

The runtime verification commands in the matrix must be executed against the deployed environment before any provider item becomes PASS.
