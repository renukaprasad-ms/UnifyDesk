# Core Messaging Journey Round 1 — Final Narrow SEC/QA Re-verification

## Review identity

- Review type: FINAL narrow independent SEC/QA re-verification
- Repository: `renukaprasad-ms/UnifyDesk`
- Exact reviewed production-code SHA: `353069f922040be922ec353961386fb962d8d187`
- QA branch: `review/core-messaging-security-qa-r1-final`
- Original QA PR: `#244`
- Original QA head: `ae014e143809af7aa23e101e6b2b1e9ee2eedec2`
- Original reviewed production SHA: `d2499238e40065fee3c66405c74adc294d57c99b`
- Remediation lineage:
  - B1 PR `#246`, head `46dffaed9e6bf984eccd395268ab43c101141ee6`
  - B2 PR `#245`, head `3f10f7d812a978986b7ae285a67142e7cefa1168`
  - F1 PR `#247`, head `0fa17f9f0a9d1927a20e6f396be48b29888509ec`
- Integrated order: `#246 -> #245 -> #247`
- Production code modified by QA: **No**
- Review scope: only `CMJ-R1-QA-001`, `CMJ-R1-QA-002`, `CMJ-R1-QA-003` and directly affected regression/integration surfaces.

## Final disposition

# PASS — Core Messaging Journey Round 1 remediation accepted

All three original source findings are remediated at the exact frozen integrated production SHA.

This PASS is a **source-remediation acceptance**. It does not convert unavailable executable or provider verification into PASS. Executable CI/local verification remains **BLOCKED** and real-provider verification remains **BLOCKED** where credentials/webhooks would be required.

| Finding | Original severity | Remediation status | Source evidence | Executable evidence |
| --- | --- | --- | --- | --- |
| CMJ-R1-QA-001 | HIGH | REMEDIATED | SOURCE VERIFIED | BLOCKED |
| CMJ-R1-QA-002 | HIGH | REMEDIATED | SOURCE VERIFIED | BLOCKED |
| CMJ-R1-QA-003 | MEDIUM | REMEDIATED | SOURCE VERIFIED | BLOCKED |

---

## CMJ-R1-QA-001 — Preview-to-freeze source binding

- **Original severity:** HIGH
- **Remediation status:** **REMEDIATED**
- **Source evidence:** **SOURCE VERIFIED**
- **Automated verification:** **BLOCKED**
- **Local/integration verification:** **BLOCKED**
- **Provider verification:** not required for this source contract
- **Remaining risk:** the focused tests were not executable in this QA environment; no remaining source defect was found.

### Source evidence

#### Authoritative Preview descriptor

`BroadcastAudiencePreviewService.preview(...)` now returns `BroadcastExpectedSourceDescriptor` as the canonical `sourceDescriptor`.

The descriptor includes:

- `sourceType`
- `sourceId`
- `sourceVersion`
- `filterVersion`
- `filterHash`
- `contactSelectionHash`
- `resolvedAt`

Relevant files:

- `unifydesk-app/backend/src/main/java/com/messaging/broadcast/dto/BroadcastExpectedSourceDescriptor.java`
- `unifydesk-app/backend/src/main/java/com/messaging/broadcast/service/BroadcastAudiencePreviewService.java`

For List and Segment Preview, the descriptor is produced from `AudienceSourceResolver.describeForBroadcast(...)`, so the version/filter fields are server-authoritative.

For bounded direct Contacts, Preview computes the durable SHA-256 selection hash over the canonical sorted/deduplicated Contact IDs.

#### Exact descriptor is carried through Send/Schedule

The API contract now carries the confirmed descriptor:

- `SendBroadcastRequest.expectedSourceDescriptor`
- `ScheduleBroadcastRequest.expectedSourceDescriptor`

`BroadcastController` passes it to `BroadcastMessageService`.

The frontend `broadcastApi.send(...)` and `broadcastApi.schedule(...)` serialize the exact descriptor under `expectedSourceDescriptor`.

`CanonicalBroadcastJourney` captures `confirmedPreview = currentPreview` and passes `confirmedPreview.sourceDescriptor` unchanged to the subsequent Send/Schedule request.

#### List equality

`BroadcastAudienceFreezeService.validateExpectedDescriptor(...)` verifies:

- descriptor type matches the Broadcast source type;
- expected List source ID matches the Broadcast source ID;
- current authoritative source ID/type match;
- current List `sourceVersion` equals the Preview `sourceVersion`.

A changed List version therefore cannot silently become the frozen execution population.

#### Segment equality

For Segment, first freeze verifies:

- source ID;
- source version;
- filter version;
- filter hash.

A Segment version/filter change is translated to the deterministic stale Preview conflict rather than silently accepting the newer Segment.

#### Contacts selection equality

For `CONTACTS`:

- Preview computes a SHA-256 `contactSelectionHash`;
- draft creation creates the durable selection from the submitted Contact IDs and stores the same canonical selection hash;
- freeze resolves the durable selection and verifies both `sourceVersion` and `contactSelectionHash` against the confirmed Preview hash.

A mismatched selection hash fails closed.

#### `resolvedAt` is metadata, not source equality

`resolvedAt` is required as part of a well-formed confirmation descriptor, but `BroadcastAudienceFreezeService` does **not** compare its timestamp to current source state and does not use it as List/Segment/Contacts equality.

Equality is based on source identity/version/hash fields.

#### Deterministic conflict

Source drift before first freeze throws:

`ConflictException("BROADCAST_AUDIENCE_PREVIEW_STALE")`

`ConflictException` maps to HTTP 409 through `GlobalExceptionHandler`, preserving the exact API error message:

`BROADCAST_AUDIENCE_PREVIEW_STALE`

No run is created when first-freeze validation fails because canonical freeze occurs in `BroadcastMessageService` before `BroadcastLaunchService.launch(...)`.

#### UI behavior after stale conflict

`CanonicalBroadcastJourney` detects only the deterministic 409 stale-preview error and then:

- clears `preview`;
- clears `previewOwnerKey`;
- shows an explicit “audience changed” message;
- leaves Send/Schedule disabled;
- requires the customer to run Preview again.

There is no automatic refresh-and-send and no automatic resend.

#### Transactionally protected candidate capture

`AudienceSnapshotService.createForBroadcast(...)` creates the snapshot and captures candidate IDs in the same transaction.

`AudienceSourceResolver.captureCandidates(...)` holds a source-row share lock while performing the set-based candidate capture:

- List: `SELECT ... FOR SHARE`, verify List version, then candidate `INSERT ... SELECT`;
- Segment: lock active Segment row, verify version/filter hash/filter version, then candidate `INSERT ... SELECT`;
- Contacts: `SELECT selection_hash ... FOR SHARE`, verify hash, then candidate `INSERT ... SELECT`.

A source mutation detected during capture raises `SourceChangedException`, which is translated back to the same stale Preview HTTP 409. The transaction therefore does not commit a retargeted snapshot.

#### Retry/re-entry after successful freeze

If `BroadcastMessage.frozenAudienceSnapshotId` already exists, `BroadcastAudienceFreezeService` loads and reuses that immutable snapshot and does not re-read the mutable List/Segment source.

This covers:

- Send retry/re-entry;
- later scheduled launch;
- source mutation after successful freeze.

The Broadcast does not retarget to a newer audience after the first successful freeze.

#### Legacy Broadcast Group compatibility

`BroadcastAudienceFreezeService.freeze(...)` immediately returns `null` for legacy Group-backed Broadcasts.

The frontend API continues to permit legacy Send/Schedule without an `expectedSourceDescriptor`. The new binding requirement applies only to canonical Contacts/List/Segment Broadcasts.

### Focused test evidence present in source

The following tests directly cover the remediation contract:

`BroadcastAudienceFreezeServiceTests`

- `unchangedListDescriptorFreezesValidatedSourceAndCapturesCandidates`
- `unchangedSegmentDescriptorFreezesAuthoritativeVersionAndFilter`
- `sourceDriftDetectedDuringCandidateCaptureReturnsSameConflict`
- `staleListVersionIsRejectedBeforeSnapshotCreation`
- `staleSegmentVersionFilterVersionOrHashIsRejected`
- `matchingContactSelectionHashFreezesAndMismatchIsRejected`
- `alreadyFrozenSnapshotIsReusedWithoutRevalidatingMutatedSource`
- `missingCanonicalConfirmationDescriptorFailsClosed`
- `legacyBroadcastGroupRemainsCompatibleWithoutDescriptor`

`BroadcastAudiencePreviewBindingPostgresTests`

- `listMembershipMutationAfterPreviewProducesDeterministicConflict`
- `segmentMutationAfterPreviewProducesDeterministicConflict`

`BroadcastAudiencePreviewServiceTests`

- direct Contacts duplicate/eligibility accounting
- authoritative Segment filter version/hash

`broadcastApi.previewBinding.test.ts`

- exact descriptor posted on Send
- exact descriptor posted on Schedule
- legacy Group requests omit descriptor compatibly
- deterministic stale-preview 409 recognition

`CanonicalBroadcastJourney.workspaceEpoch.test.tsx`

- exact Preview descriptor object passed to Send
- exact Preview descriptor object passed to Schedule
- stale 409 clears Preview and requires explicit re-preview
- stale draft completion cannot continue into Send.

These are **source-visible test definitions only** in this review. They are not classified AUTOMATED VERIFIED because they were not executed on an allocated runner.

---

## CMJ-R1-QA-002 — Workspace epoch ownership/isolation

- **Original severity:** HIGH
- **Remediation status:** **REMEDIATED**
- **Source evidence:** **SOURCE VERIFIED**
- **Automated verification:** **BLOCKED**
- **Local/integration verification:** **BLOCKED**
- **Provider verification:** not required for the frontend epoch contract
- **Remaining risk:** delayed-promise/component regression suites are present but unexecuted in this QA environment; no remaining source defect was found.

### Canonical workspace identity

The remediation consistently uses:

`{companyId, workspaceEpoch}`

`useWorkspaceEpochScope(...)` provides:

- a captured token containing company ID + epoch;
- currentness validation against the latest rendered company+epoch;
- an `activeRef` that becomes false on component unmount.

This means an A1 token fails currentness in B and remains stale after returning to A2 because the epoch changed.

### Component lifetime invalidation

`WorkspaceEpochBoundary` keys its child subtree by:

`companyId:workspaceEpoch`

A workspace transition therefore remounts the protected journey subtree. Tenant-sensitive component-local state is discarded synchronously and outstanding work belonging to the unmounted component is invalidated by `useWorkspaceEpochScope`.

### Production route coverage

`AppRoutes.tsx` wraps the relevant production routes in `WorkspaceEpochBoundary`, including:

- Email Inbox
- WhatsApp/social Inbox
- Broadcast Email/SMS/WhatsApp
- Contacts
- Lists
- List detail
- Segments
- Segment editor
- **`contacts/:contactId`**

The Contact detail production route therefore has the required epoch boundary.

### Canonical Broadcast journey

`CanonicalBroadcastJourney` now scopes catalogs/readiness/senders with a `journeyScopeKey` containing company+epoch+channel.

It also owns Preview by a `previewRequestKey` containing:

- workspace scope;
- sender;
- audience selector.

`canLaunch` requires the current catalog scope and current Preview owner. Old Preview/readiness/catalog data cannot enable launch after a workspace transition.

Async loads capture the workspace token and guard state writes.

Critically, after `createAudienceMessage(...)` resolves, the component checks the same captured scope before chaining into Send/Schedule. A stale A1 draft creation cannot trigger a provider-facing Broadcast launch while B or A2 is current.

Managed SMS Preview timers are scope-checked and cancelled during cleanup.

### Broadcast workspace/realtime/timers

`BroadcastWorkspace`:

- derives currentness through the workspace epoch token;
- renders Redux groups/history only when company+epoch match;
- guards local async detail/recipient loads;
- guards the delayed recipient-refresh timer;
- guards Broadcast realtime callbacks;
- clears the timer and unsubscribes on cleanup.

A stale realtime/timer callback cannot mutate the new scope.

### Contacts page and Contact list hook

`ContactsPage` now consumes `workspaceEpoch` and uses `useWorkspaceEpochScope`.

Tenant-dependent tags/Lists are cleared before reload and delayed results are guarded.

Contact row state comes from `useContacts(companyId, epoch, query)`, which:

- clears existing items before a new scoped load;
- uses both a request generation and workspace token;
- guards initial load;
- guards refresh;
- guards load-more.

Create Contact completion is also scoped before customer-visible state/follow-up refresh.

The search debounce has cleanup on unmount.

### Contact detail / PII / activity / consent / suppressions

`ContactDetailPage` now:

- reads `workspaceEpoch`;
- uses `useWorkspaceEpochScope`;
- guards detail/tags/custom-field/activity responses before any setter;
- guards preference/suppression responses before any setter;
- guards mutations before follow-up notices/reloads;
- guards activity pagination.

Because `contacts/:contactId` is wrapped by `WorkspaceEpochBoundary`, old A1 Contact PII is removed when the epoch changes rather than remaining rendered while B loads.

Delayed A1 activity, preferences and suppressions cannot repopulate B/A2 state.

### Lists / List detail

`ListsPage` guards list load and create completion by captured workspace token.

`ListDetailPage` guards:

- detail load;
- member load/load-more;
- generic mutations before follow-up reload;
- archive completion before navigation.

Stale A1 mutation completions therefore cannot trigger B/A2 follow-up state/navigation.

### Segments / Segment editor

`SegmentsPage` guards delayed Segment catalog results.

`SegmentEditorPage` guards:

- field metadata;
- Segment load;
- validation;
- Preview;
- save/update;
- optimistic-conflict reload;
- archive/navigation.

After asynchronous validation, the code checks current workspace scope **before** performing the subsequent create/update mutation. A stale A1 validation completion cannot initiate a B/A2 mutation.

### WhatsApp Inbox

`WhatsAppInbox` now uses `useWorkspaceEpochScope`.

Directly reviewed paths guard:

- connections;
- conversations;
- detail/messages;
- composer state;
- templates;
- text/template/media send completion;
- multi-stage media upload continuation;
- realtime status callbacks;
- focus refresh callbacks;
- reconnect refresh callbacks.

Conversation/message/composer/template/reply/send-result local state is cleared when the current connection/load scope changes.

The realtime subscription checks the captured epoch before requesting/applying a snapshot, and focus/reconnect listeners are removed on cleanup.

Old A1 connection/conversation state cannot become A2 state merely because the company ID matches again.

### A1 -> B -> A2 regression test evidence present in source

Component-level delayed-promise tests are present rather than relying only on reducers.

`CanonicalBroadcastJourney.workspaceEpoch.test.tsx`

- delayed A1 catalog never renders after B -> A2;
- delayed A1 Preview is ignored and launch remains disabled;
- stale A1 draft completion stops before Send;
- Preview descriptor Send/Schedule chain is retained;
- stale Preview requires explicit re-preview.

`ContactDetailPage.workspaceEpoch.test.tsx`

- A1 Contact state is synchronously removed on B and only fresh A2 state renders;
- delayed A1 detail/activity cannot enter A2;
- delayed A1 preferences/suppressions cannot enter A2.

`ContactsAudienceWorkspaceEpoch.test.tsx`

- delayed A1 Lists cannot replace A2;
- delayed A1 Segments cannot replace A2;
- delayed A1 List detail/members cannot replace A2.

`useContacts.workspaceEpoch.test.tsx`

- delayed A1 list load cannot repopulate B/A2;
- refresh is epoch guarded;
- load-more is epoch guarded.

`SegmentEditorPage.workspaceEpoch.test.tsx`

- stale A1 validation completion cannot continue into an update mutation.

`WhatsAppInbox.workspaceEpoch.test.tsx`

- delayed A1 connection result is ignored in A2;
- delayed A1 conversation result is ignored after A1 -> B -> A2;
- a retained realtime callback from unmounted A1 cannot trigger the A2 snapshot path.

Again, these are **source-visible tests**, not executed proof in this QA run.

---

## CMJ-R1-QA-003 — All-skipped/zero-pending terminal Notification

- **Original severity:** MEDIUM
- **Remediation status:** **REMEDIATED**
- **Source evidence:** **SOURCE VERIFIED**
- **Automated verification:** **BLOCKED**
- **Local/integration verification:** **BLOCKED**
- **Provider verification:** not required
- **Remaining risk:** transactional/idempotency tests are present but unexecuted in this QA environment; no remaining source defect was found.

### Terminal transition

`BroadcastPreparationChunkService.completePreparation(...)` now handles `stats.pending() == 0` by setting:

- run status: `COMPLETED`
- run `startedAt`
- run `completedAt`
- Broadcast message status: `COMPLETED`

It then calls the existing:

`NotificationEventRecorder.recordBroadcastCompleted(...)`

for that terminal path.

### Existing durable event contract retained

`NotificationEventRecorder.recordBroadcastCompleted(...)` retains:

`event_key = "broadcast.completed:" + runId`

and:

`event_type = "broadcast.completed"`

It is annotated:

`@Transactional(propagation = Propagation.MANDATORY)`

The preparation `process(...)` entrypoint is `@Transactional`, so the durable Notification event is recorded in the same transaction as the zero-pending terminal transition.

The recorder inserts with:

`ON CONFLICT (event_key) DO NOTHING`

and resolves the same durable event on replay, preserving idempotency.

### Non-zero pending path

When `stats.pending() > 0`, preparation transitions the run/message to `RUNNING` and does not call a terminal Notification recorder.

There is no premature completion event.

### Existing finalizer/failure behavior

`BroadcastRunFinalizerService` remains responsible for ordinary RUNNING completion/partial-failure finalization and still records:

- completed -> `recordBroadcastCompleted(...)`
- partial failure -> `recordBroadcastFailed(...)`

`BroadcastRunFailureService` still records `recordBroadcastFailed(...)` for ordinary terminal failure.

The remediation did not replace or remove these paths.

### Realtime remains post-commit

Preparation still publishes Broadcast realtime progress through an `afterCommit(...)` callback.

The realtime signal is therefore still a presentation/update mechanism and is not substituted for the durable Notification event.

### Focused test evidence present in source

`BroadcastTerminalNotificationServiceTests`

- `zeroPendingPreparationCompletesAndRecordsDurableTerminalEvent`
- `replayedZeroPendingPreparationDoesNotRecordSecondTerminalEvent`
- `nonZeroPendingPreparationTransitionsToRunningWithoutTerminalNotification`
- `ordinaryRunFinalizerStillRecordsCompletedNotification`
- `ordinaryRunFailureStillRecordsFailedNotification`

`BroadcastTerminalNotificationDatabaseTests`

- `completedEventUsesDeterministicRunKeyAndReplayIsIdempotent`

The database test asserts a single durable row for:

`broadcast.completed:30`

after recording the completion twice.

These tests are present as source evidence but were not executable in the current QA environment.

---

## Executable verification evidence

### Exact integrated QA SHA

For:

`353069f922040be922ec353961386fb962d8d187`

GitHub currently reports:

- workflow runs: **none**
- commit status contexts: **none**

Therefore there is no executable CI evidence on the exact reviewed integrated SHA.

### Remediation heads

GitHub Actions runs exist on the three remediation heads, but they are **not executable test failures and are not PASS evidence**.

B1 head `46dffaed9e6bf984eccd395268ab43c101141ee6`:

- workflow run `36976564245`
- jobs report `runner_id=0`
- `runner_name=""`
- `steps=[]`

B2 head `3f10f7d812a978986b7ae285a67142e7cefa1168`:

- workflow run `36975868363`
- jobs report `runner_id=0`
- `runner_name=""`
- `steps=[]`

F1 head `0fa17f9f0a9d1927a20e6f396be48b29888509ec`:

- workflow run `36979925712`
- jobs report `runner_id=0`
- `runner_name=""`
- `steps=[]`

The GitHub UI/API conclusion is `failure`, but no runner executed the jobs. Per the QA evidence rules these attempts are classified **BLOCKED**, not FAIL and not PASS.

No checked-out executable repository/Postgres/browser test environment was available to this independent QA session.

### Executable disposition

- Backend focused unit tests: **BLOCKED**
- Backend Postgres preview-binding tests: **BLOCKED**
- Notification database idempotency test: **BLOCKED**
- Frontend Vitest/component delayed-promise tests: **BLOCKED**
- Browser/local A1 -> B -> A2 integration: **BLOCKED**
- Exact integrated build/package verification: **BLOCKED**

The existence and content of the test files is **SOURCE VERIFIED**, not AUTOMATED VERIFIED.

---

## Provider verification

No real provider execution was performed in this narrow re-verification.

- Amazon SES/provider callbacks: **BLOCKED**
- MSG91 provider callbacks: **BLOCKED**
- Meta WhatsApp provider callbacks: **BLOCKED**

Provider execution is not needed to establish the source remediation of these three findings, but no provider result is claimed as PASS.

---

## Narrow verification matrix

| Verification | Result |
| --- | --- |
| Preview returns authoritative expected source descriptor | SOURCE VERIFIED |
| Exact Preview descriptor carried to Send | SOURCE VERIFIED |
| Exact Preview descriptor carried to Schedule | SOURCE VERIFIED |
| List ID + source version equality | SOURCE VERIFIED |
| Segment ID + source version + filter version/hash equality | SOURCE VERIFIED |
| Contacts durable selection hash equality | SOURCE VERIFIED |
| `resolvedAt` excluded from source equality | SOURCE VERIFIED |
| Stale source -> HTTP 409 `BROADCAST_AUDIENCE_PREVIEW_STALE` | SOURCE VERIFIED |
| Stale UI Preview cleared; explicit re-preview required | SOURCE VERIFIED |
| No automatic refresh-and-send/re-send | SOURCE VERIFIED |
| Candidate capture source lock/version protection | SOURCE VERIFIED |
| Frozen snapshot reused after successful freeze | SOURCE VERIFIED |
| Legacy Group send/schedule compatibility | SOURCE VERIFIED |
| Broadcast catalog/readiness/Preview epoch ownership | SOURCE VERIFIED |
| Stale draft cannot chain into Send/Schedule | SOURCE VERIFIED |
| Contacts/list/detail/segment delayed response guards | SOURCE VERIFIED |
| Contact activity/preferences/suppressions epoch guards | SOURCE VERIFIED |
| `contacts/:contactId` uses `WorkspaceEpochBoundary` | SOURCE VERIFIED |
| A1 Contact PII removed by epoch remount | SOURCE VERIFIED |
| WhatsApp connection/conversation/template/composer guards | SOURCE VERIFIED |
| WhatsApp stale realtime/focus/reconnect callbacks blocked | SOURCE VERIFIED |
| Zero-pending run/message -> COMPLETED | SOURCE VERIFIED |
| Zero-pending uses existing Notification recorder | SOURCE VERIFIED |
| Event key `broadcast.completed:<runId>` | SOURCE VERIFIED |
| Notification event shares terminal transaction | SOURCE VERIFIED |
| Completion replay idempotency | SOURCE VERIFIED |
| Non-zero pending -> RUNNING, no terminal event | SOURCE VERIFIED |
| Ordinary finalizer behavior unchanged | SOURCE VERIFIED |
| Ordinary failure notification behavior unchanged | SOURCE VERIFIED |
| Realtime remains post-commit | SOURCE VERIFIED |
| Focused backend/frontend tests actually executed | BLOCKED |
| Local/browser integration actually executed | BLOCKED |
| Real-provider execution | BLOCKED |

---

## Final assessment

- **Exact reviewed production SHA:** `353069f922040be922ec353961386fb962d8d187`
- **CMJ-R1-QA-001:** **REMEDIATED — SOURCE VERIFIED**
- **CMJ-R1-QA-002:** **REMEDIATED — SOURCE VERIFIED**
- **CMJ-R1-QA-003:** **REMEDIATED — SOURCE VERIFIED**
- **Executable verification:** **BLOCKED**
- **Provider verification:** **BLOCKED**
- **Remaining source blockers:** **None**
- **Remaining evidence limitations:** executable CI/local integration and real-provider execution were unavailable and remain BLOCKED.
- **Production-code changes by QA:** **None**
- **Final disposition:** **PASS — Core Messaging Journey Round 1 remediation accepted**
