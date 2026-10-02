# Core Messaging Journey Round 1 — Final Independent SEC/QA Feedback

## Review identity

- Review type: FINAL independent SEC/QA
- Exact reviewed production-code SHA: `d2499238e40065fee3c66405c74adc294d57c99b`
- QA branch: `review/core-messaging-security-qa-r1`
- Review scope: integrated Core Messaging Journey R1 only
- Production code modified by QA: **No**
- Executable CI evidence on the exact reviewed SHA: **BLOCKED** — GitHub reports no workflow runs and no commit-status contexts for this SHA.
- Provider verification: **BLOCKED** where real provider credentials/accounts/events are required.

## Executive result

**Release disposition: HOLD — source remediation required before Core Messaging Journey R1 can be accepted.**

Three source findings remain open:

| Finding | Severity | Summary |
| --- | --- | --- |
| CMJ-R1-QA-001 | HIGH | Previewed List/Segment version is not bound to freeze, so send/schedule can silently freeze a different audience than the customer confirmed. |
| CMJ-R1-QA-002 | HIGH | Frontend workspace epoch protection is incomplete on journey-local state; stale workspace data can remain visible/reactivate and Contact async responses are not epoch guarded. |
| CMJ-R1-QA-003 | MEDIUM | Broadcasts that become terminal during preparation with zero pending recipients bypass the Notification platform terminal event. |

The PR #243 regression in the ordinary asynchronous `AudienceSnapshotService.create(...)` path is **source-correct at the reviewed SHA**: both ordinary async and Broadcast snapshot creation capture immutable candidate IDs before batch processing. Its focused tests exist, but those tests were **not executed in this QA environment** and are therefore not classified as AUTOMATED VERIFIED.

## Findings

### CMJ-R1-QA-001 — Preview descriptor is advisory only; freeze can silently use a newer List/Segment

- **Severity:** HIGH
- **Affected contract/capability:** recipient preview, source-version semantics, send/schedule confirmation, frozen audience membership
- **Verification evidence:** SOURCE VERIFIED defect; AUTOMATED VERIFIED = BLOCKED; LOCAL/INTEGRATION VERIFIED = BLOCKED; PROVIDER VERIFIED = not applicable

#### Exact code evidence

1. `BroadcastAudiencePreviewResponse` returns a real source descriptor containing `sourceVersion`, `filterVersion`, `filterHash`, `contactSelectionHash`, and `resolvedAt`:
   - `unifydesk-app/backend/src/main/java/com/messaging/broadcast/dto/BroadcastAudiencePreviewResponse.java:10-40`
2. `BroadcastAudiencePreviewService.preview(...)` resolves the current descriptor and returns it:
   - `unifydesk-app/backend/src/main/java/com/messaging/broadcast/service/BroadcastAudiencePreviewService.java:39-47`
   - `...:84-95`
   - `...:123-143`
3. The frontend stores that descriptor in `RecipientResolutionPreview`, but the create payload has no expected source descriptor/version field:
   - `unifydesk-app/frontend/src/features/broadcast/types.ts:237-272`
4. The canonical UI previews at `CanonicalBroadcastJourney.tsx:449-467`, then later confirms the preview count at `...:470-482`, but creates the Broadcast using only source type/source ID or Contact IDs at `...:486-506`; it does not send `preview.sourceDescriptor`.
5. Send/schedule then calls `broadcastApi.send(...)` or `broadcastApi.schedule(...)` with no expected audience version:
   - `unifydesk-app/frontend/src/components/broadcast/CanonicalBroadcastJourney.tsx:509-519`
   - `unifydesk-app/frontend/src/features/broadcast/broadcastApi.ts:277-289`
6. At freeze time, `BroadcastAudienceFreezeService` calls `AudienceSnapshotService.createForBroadcast(...)` using only the message’s current source type/source ID:
   - `unifydesk-app/backend/src/main/java/com/messaging/broadcast/service/BroadcastAudienceFreezeService.java:21-40`
7. `AudienceSnapshotService.createForBroadcast(...)` freshly calls `sourceResolver.describe(...)` and captures that newly resolved source:
   - `unifydesk-app/backend/src/main/java/com/messaging/contacts/audience/AudienceSnapshotService.java:60-76`
8. `BroadcastMessage` stores audience source type/source ID and frozen snapshot ID, but no previewed source version/filter hash:
   - `unifydesk-app/backend/src/main/java/com/messaging/broadcast/entity/BroadcastMessage.java:28-49`

#### Reproduction / failure scenario

1. User previews List L at version 10 and sees 2,000 selected / 1,900 eligible.
2. Before the user clicks Send or Schedule, List L membership changes to version 11.
3. The UI still presents the old preview and confirms “send/schedule to 1,900 eligible recipients”.
4. The create/send/schedule requests carry only L’s ID.
5. Freeze resolves version 11 and captures its current members.
6. The execution candidate population can therefore differ from the population the customer confirmed.

The same issue applies to Segments when the segment/filter version changes after preview and before freeze.

#### Impact

- The product can send to newly added members that were not in the confirmed preview.
- Removed members can disappear from the execution population without an explicit re-confirmation.
- Auditability and customer trust are weakened because the displayed confirmation is not the authoritative frozen membership.
- This violates the frozen contract requirement that R1 must not silently resolve a different source after confirmation.

#### Required remediation

Bind confirmation to the previewed source descriptor. At minimum:

- carry an expected source descriptor/version/hash from preview into the create/launch contract;
- persist the expected descriptor with the draft or pass it atomically to freeze;
- compare the current List/Segment descriptor under the same freeze transaction;
- if it changed, return a deterministic conflict requiring a fresh preview/re-confirmation;
- preserve the current post-freeze immutable candidate table behavior.

Add integration tests that mutate a List and a Segment after preview and prove launch is rejected until re-previewed.

---

### CMJ-R1-QA-002 — Workspace epoch does not own/clear all journey-local frontend state

- **Severity:** HIGH
- **Affected contract/capability:** tenant isolation, A1 -> B -> A2 stale-state safety, Broadcast journey UX, Contact journey, frontend async isolation
- **Verification evidence:** SOURCE VERIFIED defect; AUTOMATED VERIFIED = BLOCKED; LOCAL/INTEGRATION VERIFIED = BLOCKED; PROVIDER VERIFIED = not applicable

#### Exact code evidence

The global epoch primitive itself is correct:

- `workspaceSlice.ts` increments the epoch whenever selected company changes.
- `workspaceEpoch.ts` considers work current only when both company ID and epoch match.
- Redux Broadcast/Inbox fulfilled reducers include company+epoch guards.
- Source tests exist for A -> B -> A Redux stale responses.

However, not all local component state is owned by that epoch.

**Canonical Broadcast journey**

1. `CanonicalBroadcastJourney` keeps Lists, Segments, Contacts, sender/readiness, preview and compose state as unscoped component-local state:
   - `unifydesk-app/frontend/src/components/broadcast/CanonicalBroadcastJourney.tsx:163-207`
2. On company/epoch change, the load effect sets `loading` and `error`, starts new requests, and only guards the incoming response. It does **not** synchronously clear/tag the previously rendered audience/sender/preview state:
   - `...:275-309`
3. Preview is reset only when `selector`, `senderId`, or `channel` changes:
   - `...:311-313`
4. `canLaunch` is derived from the existing selector/preview/readiness/content and does not require that those values were produced for the current epoch:
   - `...:265-273`
5. The component is rendered without a workspace/epoch `key` from `BroadcastWorkspace`:
   - `unifydesk-app/frontend/src/pages/broadcast/BroadcastWorkspace.tsx:1298-1302`

**Contact detail**

1. `ContactDetailPage` reads only `selectedCompanyId`; it does not read `selectWorkspaceEpoch`.
2. `load()` captures `companyId` through closure, then directly calls setters after asynchronous responses:
   - `unifydesk-app/frontend/src/pages/contacts/ContactDetailPage.tsx:42-100`
3. The effect simply reruns on `[companyId, contactId]` and has no abort/current-token guard:
   - `...:103-105`

Other Contacts pages in the acceptance journey (`ContactsPage.tsx`, `ListDetailPage.tsx`, `SegmentsPage.tsx`) likewise do not use the workspace epoch primitive.

**WhatsApp Inbox**

`WhatsAppInbox` correctly guards incoming async results/realtime callbacks with the epoch, but its connection list is not synchronously cleared on an ordinary workspace change before the new company load completes; stale local connection state can remain rendered while the current-company request is pending/failing.

#### Reproduction / failure scenario

Canonical Broadcast:

1. In workspace A1, load a List, sender and a valid recipient preview.
2. Switch to B.
3. Before B’s requests complete, the component still owns/rendered A1 local state; `loading` does not itself make `canLaunch` false.
4. If B loading fails, stale A state can remain visible.
5. Switch back to A (A2). The epoch correctly changes, but the old A1 local values are not themselves tagged with the old epoch and can become actionable before fresh A2 state arrives.

Contact:

1. Open Contact A in workspace A.
2. Make the A detail request slow.
3. Switch to workspace B while the A request is in flight.
4. B starts its new request, but the late A response can still execute `setContact(detail)`, `setActivity(...)`, etc.
5. A Contact data can therefore appear while B is selected until later state replaces it.

#### Impact

- Cross-workspace stale data can be displayed in the wrong active workspace context.
- A1 state can reactivate in A2 despite the global epoch changing.
- Backend company scoping prevents the stale foreign IDs from becoming a direct backend IDOR in the paths reviewed, but frontend tenant isolation still fails the required contract and stale A2-valid values can be acted upon without fresh A2 resolution.
- Contact details/activity may expose stale PII under the wrong selected workspace.

#### Required remediation

- Treat every tenant-sensitive component-local state object as owned by `{companyId, epoch}`.
- On any company/epoch transition, synchronously invalidate/clear tenant-sensitive local state before rendering/actions continue, or remount the relevant journey surface with a workspace epoch key.
- Make `canLaunch` require that preview, readiness and sender state were produced for the current workspace epoch.
- Add epoch/current-request guards (or AbortController plus epoch validation) to Contacts/List/Segment/detail async loaders and mutations in the Core Messaging journey.
- Clear WhatsApp connection-local state on workspace epoch transition.
- Add delayed-response component tests for A1 -> B -> A2, not only reducer-level tests.

---

### CMJ-R1-QA-003 — Zero-pending preparation terminal path bypasses Broadcast terminal notification

- **Severity:** MEDIUM
- **Affected contract/capability:** terminal Broadcast notifications, Notification platform integration, idempotent completion signaling
- **Verification evidence:** SOURCE VERIFIED defect; AUTOMATED VERIFIED = BLOCKED; LOCAL/INTEGRATION VERIFIED = BLOCKED; PROVIDER VERIFIED = not applicable

#### Exact code evidence

1. During preparation, recipients may become `SKIPPED` and a chunk can have zero pending recipients:
   - `unifydesk-app/backend/src/main/java/com/messaging/broadcast/service/BroadcastPreparationChunkService.java:90-108`
   - canonical audience processing has the equivalent skip path.
2. `finishChunk(...)` records snapshot stats and continues preparation:
   - `...:246-260`
3. When preparation reaches the end, `completePreparation(...)` directly sets the run/message to `COMPLETED` when `stats.pending() == 0`, persists them, then only publishes realtime progress:
   - `...:263-284`
4. That method has no `NotificationEventRecorder.recordBroadcastCompleted(...)` call and does not delegate to `BroadcastRunFinalizerService`.
5. The normal finalizer/failure paths do use the existing Notification platform with idempotent event keys based on run ID.

#### Reproduction / failure scenario

1. Freeze a non-empty candidate population.
2. Before/during materialization, all candidates become ineligible or all materialized recipients are skipped (for example, identity removed or suppression becomes active).
3. Preparation finishes with `pending == 0`.
4. `completePreparation(...)` marks the Broadcast `COMPLETED`.
5. No durable `broadcast.completed` notification event is recorded for that terminal transition.

#### Impact

- The product can reach a terminal Broadcast state without the required terminal notification.
- Realtime progress is not a durable substitute for the existing Notification platform.
- Customer notification behavior differs depending on which terminal state-transition path is taken.

#### Required remediation

Route this terminal transition through the same idempotent finalization/notification mechanism used by ordinary completion, or record the same `broadcast.completed:<runId>` event transactionally in this path. Add a focused test for an all-skipped/zero-pending canonical audience.

## Source verification notes

### Canonical Contacts / Audience

**SOURCE VERIFIED**, except CMJ-R1-QA-001/002:

- List, Segment and direct Contact selection are canonical Contacts/Audience sources.
- Direct selection is bounded server-side to 500 IDs and validates company ownership before persisting the ephemeral selection.
- No new permanent recipient directory is introduced; execution provenance points back to canonical Contact/identity.
- Preview accounting explicitly checks `selected = eligible + excluded`.
- Exclusion mapping covers duplicate, missing identity, invalid/mismatched identity, opt-out, consent required, suppression and channel/policy ineligibility.
- Preview samples are masked and bounded.

### Ordinary async AudienceSnapshotService regression

**SOURCE VERIFIED**:

- `AudienceSnapshotService.create(...)` now calls `sourceResolver.captureCandidates(companyId, source, snapshot.id())` before returning.
- `processBatch(...)` pages `contact_audience_snapshot_candidates`, rather than assuming only Broadcast snapshots populated that table.
- `AudienceSnapshotServiceTests` contains focused ordinary async List and Segment tests verifying candidate capture and processing.
- The tests are **not** classified AUTOMATED VERIFIED because no executable run was available.

### Freeze after capture and send-time safety

**SOURCE VERIFIED** after the actual freeze:

- membership is captured in `contact_audience_snapshot_candidates`;
- processing pages that captured set rather than re-resolving mutable List/Segment membership;
- canonical recipient provenance includes Contact and identity IDs;
- delivery rechecks Contact eligibility before provider submission;
- identity destination drift becomes `SKIPPED` rather than silently sending to the changed identity.

The gap is the **preview -> freeze boundary** in CMJ-R1-QA-001, not post-freeze immutability.

### Execution/idempotency/recovery

**SOURCE VERIFIED**:

- launch returns an existing run for duplicate launch attempts;
- outbox drives preparation;
- recipient and batch claims use `FOR UPDATE SKIP LOCKED`;
- claims are bounded;
- processing begins a durable attempt before provider submission;
- stale work with a started provider attempt is recovered as `UNKNOWN`, suppressing blind retry;
- retryable failures have bounded retry behavior;
- `UNKNOWN` is represented distinctly from provider acceptance/final delivery;
- cancellation/retry state is source-guarded;
- stats are transitioned with recipient state rather than recomputed from unbounded payloads.

### Email

**SOURCE VERIFIED; PROVIDER VERIFIED = BLOCKED**:

- UnifyDesk Managed Email launch remains gated by company-scoped sender/runtime readiness, entitlement, platform/reputation safety, campaign/ramp caps and usage reservation.
- Customer-connected provider paths remain separate from Managed Email.
- UI wording distinguishes provider acceptance from Delivered/Read and does not claim final delivery merely because a provider accepted a request.
- Managed Email SNS ingress calls `SnsMessageVerifier.verify(body)` before event processing; event processing verifies company/run/sender/domain/recipient/provider-message/correlation ownership before projection.
- Real SES delivery/bounce/complaint verification is BLOCKED without provider execution.

### Managed SMS

**SOURCE VERIFIED; PROVIDER VERIFIED = BLOCKED**:

- managed launch requires a current managed template runtime bound to the selected sender;
- provider flow reference/readiness and compliance freshness are required;
- approved template body/fingerprint/schema/variables are re-rendered and compared with the Broadcast content;
- campaign/tenant/usage limits are enforced;
- event ingress uses the locally correlated provider account plus webhook token, provider request ID, event deduplication key and replay window;
- NDNC/rejection suppression convergence is company/recipient scoped and uses canonical Contact provenance.
- Real MSG91 DLT/header/template acceptance and delivery callbacks are BLOCKED.

### WhatsApp Broadcast

**SOURCE VERIFIED; PROVIDER VERIFIED = BLOCKED**:

- launch requires an active Meta WhatsApp sender/connection and revalidates the approved template;
- structured template parameters are converted through the existing Meta template service;
- run snapshots provider components;
- delivery resolves existing Meta credentials, verifies connection/company/phone identity, uses the distributed limiter/cooldown and never uses a free-form Broadcast bypass;
- ambiguous provider/runtime failures transition to `UNKNOWN` rather than blind retry;
- Meta webhook POST validates `X-Hub-Signature-256` with HMAC-SHA256 over exact bytes before persisting normalized events.
- Real Meta send/delivery/read verification is BLOCKED.

### Provider-event convergence

**SOURCE VERIFIED for reviewed event paths; PROVIDER VERIFIED = BLOCKED**:

- Managed Email verifies SNS before processing and performs local ownership/correlation checks.
- Managed SMS derives tenant/run/recipient from the local correlation record, verifies provider account/request, deduplicates and bounds replay age.
- Meta WhatsApp validates webhook signatures on exact request bytes.
- Managed Email and SMS suppression convergence uses company-scoped recipient provenance and re-resolves the exact canonical identity before applying Contact suppression.
- Source projections protect delivered states from later failure/rejected downgrades where reviewed.

### Inbox <-> Contact

**SOURCE VERIFIED**:

- Inbox participant linkage is company scoped.
- Email addresses normalize through canonical email identity lookup.
- WhatsApp/phone participants normalize through canonical phone identity handling.
- matched participants expose canonical Contact/identity IDs;
- unmatched participants remain valid and no Contact is silently created;
- Inbox UI provides explicit View/Create/Review Contact actions.
- Contact activity reads company-scoped MATCHED Inbox links and exposes Inbox conversation activity.

### Notification platform

**SOURCE DEFECT: CMJ-R1-QA-003**.

Normal completion/failure finalizer paths use the existing durable Notification event platform with run-derived idempotency keys. The zero-pending preparation completion path bypasses it.

### Customer UX / product abstraction

**SOURCE VERIFIED with tenant-state exception CMJ-R1-QA-002**:

- canonical journey exposes Contacts/List/Segment selection, preview eligibility, sender readiness, channel-correct composition, Managed SMS template selection, approved WhatsApp templates, send/schedule confirmation and run status;
- copy explicitly states provider acceptance is not the same as Delivered/Read;
- direct Contacts UI is bounded;
- reviewed layouts contain responsive/mobile behavior;
- Managed Email remains presented through the product abstraction rather than exposing SES/AWS operational internals in normal customer flow.

## Migration verification

### V84 — `V84__core_messaging_journey_r1.sql`

**SOURCE VERIFIED**:

- introduces ephemeral Contact selection storage and snapshot candidate capture;
- company ownership is represented on new tables;
- composite company/snapshot and company/selection foreign-key relationships support tenant-safe access;
- candidate/member paging indexes exist;
- adds frozen audience and WhatsApp snapshot fields without destructive data removal;
- source-type constraints are expanded for canonical Contacts.

### V85 — `V85__core_messaging_identity_events_r1.sql`

**SOURCE VERIFIED**:

- follows V84 with no migration-number collision;
- Inbox Contact-link table carries company ownership and composite company/conversation relationship;
- resolution and lookup indexes support bounded reads/reconciliation;
- changes are additive/non-destructive in the reviewed migration.

**Migration execution:** BLOCKED — no executable migration run was available for the exact reviewed SHA.

## Performance review

**Source disposition: ACCEPTABLE WITH EXECUTION BLOCKED.**

Source controls found:

- keyset/cursor snapshot processing;
- candidate/member processing in bounded batches;
- direct Contact selection cap;
- masked/bounded preview samples;
- recipient and batch claim limits;
- `SKIP LOCKED` concurrency;
- bounded stale-recovery batches;
- no 2k/5k/10k recipient payload is sent to the frontend;
- no source evidence of an intentionally unbounded in-memory 10k recipient materialization in the execution pipeline.

The preview endpoint iterates the complete selected audience synchronously but does so in bounded DB/eligibility batches. Actual latency/query-count/contention for representative 2k/5k/10k audiences is **BLOCKED** because no executable environment was available.

## Explicit verification matrix

| Capability | Source evidence | Automated evidence | Local/integration | Provider |
| --- | --- | --- | --- | --- |
| Backend API/company authorization and repository scoping | SOURCE VERIFIED | BLOCKED | BLOCKED | N/A |
| Frontend A1 -> B -> A2 isolation | **SOURCE DEFECT — CMJ-R1-QA-002** | BLOCKED | BLOCKED | N/A |
| Canonical List/Segment/Contacts source | SOURCE VERIFIED | BLOCKED | BLOCKED | N/A |
| Bounded direct Contact selection | SOURCE VERIFIED | BLOCKED | BLOCKED | N/A |
| Preview accounting/exclusions | SOURCE VERIFIED | BLOCKED | BLOCKED | N/A |
| Preview-to-freeze source identity | **SOURCE DEFECT — CMJ-R1-QA-001** | BLOCKED | BLOCKED | N/A |
| Post-freeze immutable membership | SOURCE VERIFIED | BLOCKED | BLOCKED | N/A |
| Ordinary async AudienceSnapshotService path | SOURCE VERIFIED | BLOCKED | BLOCKED | N/A |
| Send-time consent/suppression/identity recheck | SOURCE VERIFIED | BLOCKED | BLOCKED | N/A |
| Duplicate launch/run idempotency | SOURCE VERIFIED | BLOCKED | BLOCKED | N/A |
| Outbox/preparation/recovery | SOURCE VERIFIED | BLOCKED | BLOCKED | N/A |
| Bounded recipient/batch claim + SKIP LOCKED | SOURCE VERIFIED | BLOCKED | BLOCKED | N/A |
| Ambiguous provider outcome -> UNKNOWN / no blind retry | SOURCE VERIFIED | BLOCKED | BLOCKED | BLOCKED |
| Pause/cancel/retry source behavior | SOURCE VERIFIED | BLOCKED | BLOCKED | N/A |
| Stats/metering source safety | SOURCE VERIFIED | BLOCKED | BLOCKED | BLOCKED |
| Managed Email launch/event safety | SOURCE VERIFIED | BLOCKED | BLOCKED | BLOCKED |
| Customer-connected Email path | SOURCE VERIFIED | BLOCKED | BLOCKED | BLOCKED |
| Managed SMS DLT/template/runtime safety | SOURCE VERIFIED | BLOCKED | BLOCKED | BLOCKED |
| WhatsApp template-only Broadcast path | SOURCE VERIFIED | BLOCKED | BLOCKED | BLOCKED |
| Provider webhook trust/correlation/dedupe | SOURCE VERIFIED | BLOCKED | BLOCKED | BLOCKED |
| Canonical suppression convergence | SOURCE VERIFIED | BLOCKED | BLOCKED | BLOCKED |
| Inbox canonical Contact association | SOURCE VERIFIED | BLOCKED | BLOCKED | BLOCKED |
| Inbox -> Contact navigation | SOURCE VERIFIED | BLOCKED | BLOCKED | N/A |
| Contact -> Inbox/activity integration | SOURCE VERIFIED | BLOCKED | BLOCKED | N/A |
| Broadcast terminal Notification | **SOURCE DEFECT — CMJ-R1-QA-003** | BLOCKED | BLOCKED | N/A |
| Responsive/nontechnical journey source UX | SOURCE VERIFIED except CMJ-R1-QA-002 | BLOCKED | BLOCKED | N/A |
| V84/V85 ordering/schema review | SOURCE VERIFIED | BLOCKED | BLOCKED | N/A |
| 2k/5k/10k behavior | SOURCE VERIFIED bounded design | BLOCKED | BLOCKED | N/A |

## Blocked executable verification

The exact reviewed SHA has no successful executable CI evidence available to this review:

- GitHub workflow runs for `d2499238e40065fee3c66405c74adc294d57c99b`: none returned.
- Commit-status contexts for the same SHA: none returned.
- No checked-out repository/runtime was available in the QA execution environment.
- Therefore backend tests, frontend tests, packaging/build, migration execution, DB concurrency tests, A1 -> B -> A2 browser/component tests, and 2k/5k/10k load runs are **BLOCKED**, not PASS and not FAIL.
- Existing source test files are useful source evidence only; their mere presence is not AUTOMATED VERIFIED.

## Provider-verification blocked items

- Real Amazon SES acceptance/delivery/bounce/complaint callbacks.
- Real customer-connected Email provider acceptance and any supported delivery/read events.
- Real MSG91 DLT/header/template acceptance, send, delivery, NDNC and billing behavior.
- Real Meta WhatsApp template send, delivery/read callback ordering, rate-limit/cooldown behavior and credentials.
- Any provider-side ambiguity/reconciliation case requiring actual network/provider state.

Mocks/source adapters are not counted as provider verification.

## Final dispositions

- **Exact reviewed production SHA:** `d2499238e40065fee3c66405c74adc294d57c99b`
- **Release disposition:** **HOLD — remediation required**
- **Open source findings:** `CMJ-R1-QA-001`, `CMJ-R1-QA-002`, `CMJ-R1-QA-003`
- **Executable-verification blocked items:** backend/frontend executable matrix, migrations, browser A1 -> B -> A2 race tests, DB concurrency/crash recovery, 2k/5k/10k performance
- **Provider-verification blocked items:** SES/customer Email, MSG91, Meta WhatsApp real-provider paths
- **Migration disposition:** **SOURCE VERIFIED; execution BLOCKED**
- **Performance disposition:** **bounded design SOURCE VERIFIED; representative load execution BLOCKED**
- **Tenant-isolation disposition:** **NOT ACCEPTED** due CMJ-R1-QA-002; backend company scoping reviewed as source-correct, but frontend journey isolation is incomplete
- **Production-code remediation performed by QA:** **None**

Per the QA assignment, production remediation is not implemented on this branch. L1 owns remediation handoff.
