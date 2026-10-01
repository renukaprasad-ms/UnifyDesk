# Current Architecture

Audit baseline: `dev@1a39af74156e21ae5d4d7e7f4e0c32ddc0851daf`.

This document describes current repository reality, not the desired R1 end-state.

## 1. Workspace / tenant boundary

Company authorization is explicit on the audited Broadcast HTTP surface through `CompanyAuthorizationService` and company-scoped repository reads. Contacts, sender connections, Managed Email/SMS control planes, WhatsApp connections and Inbox conversations all carry company ownership.

Frontend state is mixed:
- Inbox Redux selectors protect rendered data with company/connection scope.
- Managed SMS contains explicit tenant-epoch hardening.
- Broadcast Redux scopes groups/history by company, but several component-local async paths are only cleanup-flag guarded. `refreshSenders()` can set an old company's sender list after an A -> B switch because it lacks a request/tenant epoch check.
- Broadcast realtime progress payloads do not carry company id and the component subscribes to the shared event type, relying on selected IDs and server-side subscription scoping.

Evidence:
- `frontend/src/components/WorkspaceSwitcher.tsx`
- `frontend/src/store/workspace/workspaceSlice.ts`
- `frontend/src/pages/broadcast/BroadcastWorkspace.tsx`
- `frontend/src/pages/inbox/EmailInbox.tsx`
- `frontend/src/features/managedSms/tenantScope.ts`

## 2. Canonical Contacts / Audience

The Contacts domain is the intended canonical audience domain.

Canonical structures include:
- `contacts`
- `contact_identities`
- `contact_lists` / `contact_list_members`
- `segments`
- contact preferences/consent/suppressions
- import/bulk-processing jobs
- `contact_audience_snapshots` and snapshot members

Identity normalization:
- email: canonical lower-cased local/domain representation after validation;
- phone: libphonenumber E.164;
- external identity: namespaced opaque value.

Evidence:
- `contacts/core/service/IdentityNormalizer.java`
- `contacts/core/service/ContactIdentityLookup.java`
- `contacts/consent/ContactEligibilityService.java`
- `contacts/audience/AudienceSourceResolver.java`
- `contacts/audience/AudienceSnapshotService.java`
- migrations `V80..V83`.

## 3. Broadcast has two audience models

### Canonical audience path

Backend Broadcast can create a draft from:
- Contact List;
- Segment.

Endpoint:
`POST /api/v1/companies/{companyId}/broadcast/audiences/messages`

The message stores `audienceSourceType` + `audienceSourceId`. Launch creates a durable Contact audience snapshot and materializes eligible canonical Contact identities into Broadcast recipients.

Evidence:
- `broadcast/controller/BroadcastController.java`
- `broadcast/service/BroadcastMessageService.java`
- `broadcast/enums/BroadcastAudienceSourceType.java`
- `contacts/audience/AudienceSnapshotService.java`
- migration `V82__contacts_audience_integration.sql`.

### Legacy Broadcast Group path

A separate permanent domain still exists:
- `broadcast_groups`
- `broadcast_group_members`
- member add/import/remove APIs;
- group-scoped draft creation/history.

The current frontend uses this legacy path exclusively. It does not expose List/Segment audience creation and its Broadcast types still require `groupId`.

Evidence:
- `frontend/src/features/broadcast/broadcastApi.ts`
- `frontend/src/features/broadcast/types.ts`
- `frontend/src/pages/broadcast/BroadcastWorkspace.tsx`
- migration `V15__broadcast_foundation.sql`.

Therefore canonical Contacts/Audience support is SOURCE VERIFIED in backend but UX-DISCONNECTED.

## 4. Eligibility and snapshots

Canonical Contact eligibility currently evaluates:
- required channel identity;
- purpose/channel preference;
- opt-out;
- affirmative consent when policy requires it;
- canonical Contact suppression;
- policy hook.

At run preparation:
1. source List/Segment version/hash is frozen for snapshot materialization;
2. Contact candidates are keyset paged in bounded chunks;
3. only eligible Contact identities are persisted as snapshot members;
4. Broadcast recipient rows are created from eligible snapshot members;
5. old `broadcast_suppressions` are separately checked;
6. each Contact recipient is rechecked immediately before provider delivery; identity changes or new ineligibility become SKIPPED.

Current snapshot metadata keeps candidate and eligible counts, but not a durable reason-by-reason exclusion breakdown. There is no Broadcast recipient-resolution preview API.

## 5. Scheduling / freeze semantics

Current behavior:
- scheduling sets `scheduledAt` and status `SCHEDULED`;
- no canonical audience snapshot is frozen at schedule time;
- when due, the run is launched and the List/Segment's then-current version becomes the snapshot source version;
- a List/Segment mutation during snapshot materialization fails that run with `AUDIENCE_SOURCE_CHANGED`;
- Contact consent/suppression/identity is revalidated at actual send time.

This means a scheduled Broadcast can execute against a materially different audience from what the user saw when scheduling, with no explicit preview/freeze contract.

## 6. Broadcast execution engine

Current high-volume execution is asynchronous and bounded:

`HTTP send/schedule -> BroadcastRun + stats + outbox -> Kafka -> snapshot preparation -> batches -> recipient claims -> provider transport`

Source implements:
- transactional run/stats/outbox creation;
- bounded snapshot pages;
- bounded recipient/batch creation;
- `FOR UPDATE SKIP LOCKED` batch and recipient claims;
- network calls outside claim transactions;
- configurable concurrency and claim size;
- rate limiting;
- incremental stats;
- progress realtime;
- stale claim recovery;
- ambiguous-outcome `UNKNOWN` instead of unsafe duplicate retry.

Core files:
- `BroadcastLaunchService`
- `BroadcastOutboxPublisher`
- `BroadcastPreparationChunkService`
- `BroadcastBatchProcessor`
- `BroadcastRecipientClaimService`
- `BroadcastRecoveryService`
- `BroadcastRunFinalizerService`.

## 7. Current Broadcast status models

Message:
`DRAFT, SCHEDULED, QUEUED, RUNNING, PAUSED, COMPLETED, PARTIALLY_FAILED, CANCELLED, FAILED`

Run:
`PREPARING, RUNNING, PAUSED, COMPLETED, PARTIALLY_FAILED, CANCELLED, FAILED`

Recipient:
`PENDING, PROCESSING, RETRY, ACCEPTED_BY_PROVIDER, FAILED, BOUNCED, UNKNOWN, SKIPPED`

Provider delivery/read information may live in provider-specific projections instead of the main recipient enum.

## 8. Channel/provider execution

### Customer-connected Broadcast Email

SOURCE VERIFIED transports:
- Amazon SES
- SendGrid
- Azure Communication Services Email
- MSG91 Email

The sender connect/refresh APIs validate credentials/readiness and credentials are encrypted. Provider acceptance is represented as acceptance, not final delivery.

Final delivery callback integration is not uniform for these direct providers. Existing `backend/docs/broadcast.md` is stale in several places: it describes some managed/provider event work as future even though Managed Email/SMS implementations now exist.

### UnifyDesk Managed Email

SOURCE VERIFIED:
- product-level overview/readiness;
- hosted/customer-domain control plane;
- managed Broadcast launch/delivery guard;
- SES send;
- tenant quotas/rate/usage safeguards;
- signed SNS verification;
- correlation and ownership checks;
- idempotent event ledger;
- delivery/open/click projection;
- hard-bounce/complaint suppression and reputation effects.

Managed Email intentionally hides raw provider quota details from normal customer response surfaces.

### Customer-connected SMS

MSG91 SMS Broadcast transport is SOURCE VERIFIED through the Broadcast sender/transport abstraction.

### UnifyDesk Managed SMS

SOURCE VERIFIED:
- DLT/profile/header/template readiness;
- managed MSG91 runtime;
- send guard;
- delivery event authentication/correlation;
- provider-event dedupe and worker processing;
- usage projection;
- rate/safety controls.

Provider/compliance production verification remains externally blocked in existing Managed SMS documentation.

### WhatsApp

The WhatsApp Inbox/runtime is substantial and SOURCE VERIFIED:
- Embedded Signup/control-plane source;
- tenant/phone ownership mapping;
- approved template cache and parameter validation;
- free-form/session-window policy;
- text/template/media sends;
- idempotent client request reservation;
- distributed rate limiting and provider cooldown;
- inbound messages/media into Inbox;
- delivered/read/failure state machine;
- signed/trusted webhook pipeline and bounded retry.

However Broadcast launch explicitly rejects WhatsApp: **WhatsApp Broadcast execution is not implemented**.

Existing WhatsApp final QA also says real Meta provider execution remains unverified.

## 9. Delivery events and suppression

Managed Email and Managed SMS have provider event projections with duplicate/correlation protections.

Important duplication:
- canonical Contacts uses `contact_suppressions`;
- Broadcast still has legacy `broadcast_suppressions`;
- Managed Email hard bounce/complaint feedback currently upserts `broadcast_suppressions`, not the canonical Contact suppression domain.

Thus future Broadcast eligibility remains protected, but Contact-level compliance state is not the single source of truth.

## 10. Inbox

Inbox stores:
- company;
- provider connection;
- provider external conversation id;
- participants as provider addresses;
- normalized message records.

Email and WhatsApp use the same generic Inbox entities.

The current Inbox domain does **not** store/expose canonical Contact association. `ContactIdentityLookup` exists but Inbox does not use it. A WhatsApp inbound participant is stored as the provider phone string and an email participant as an address; those can remain disconnected from canonical Contact identity.

No Inbox Contact activity adapter exists in the current tree; Contact activity currently has a Broadcast adapter.

## 11. Notifications

The notification platform already defines customer preference types for `BROADCAST_COMPLETED` and `BROADCAST_FAILED`, plus integration degradation/disconnection. Those Broadcast definitions currently have no source event mapping/recorder path comparable to `inbox.activity`.

Inbox inbound notification integration is already wired through `InboxMessagePostProcessor`.

## 12. Leads

The audited repository contains no first-class marketing Lead capture domain or Lead -> Contact conversion implementation. Ads Center has campaign/audience work, but no repository evidence of a captured Lead entity/process to convert. R1 therefore defines only the future ingestion seam: captured lead identity/provenance must enter Contact upsert/import contracts, not a new messaging recipient database.

## 13. Test/verification reality

There is substantial source test coverage for Contacts, Managed Email, Managed SMS, WhatsApp, Inbox and Notification. Canonical Audience -> Broadcast integration itself has little/no focused end-to-end automated test coverage visible under the Broadcast tests.

At audited SHA, GitHub Actions `Checks` run 36874477918 ended before runner allocation. All jobs had blank runner and zero steps. Therefore:
- source/tests present: yes;
- executed automated verification at audited SHA: BLOCKED;
- local/integration verification for this audit: NOT PERFORMED;
- real provider verification: only historical documentation where explicitly stated; do not infer from source.
