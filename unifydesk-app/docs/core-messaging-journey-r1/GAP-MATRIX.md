# Gap Matrix

Baseline: `dev@1a39af74156e21ae5d4d7e7f4e0c32ddc0851daf`.

| Capability | Status | Severity | Repository evidence | R1 recommendation | Candidate |
|---|---|---:|---|---|---|
| Company-scoped backend authorization | WORKING | Critical | BroadcastController; Contacts controllers; company-scoped sender/Inbox repositories | Preserve; add journey regression tests | B1/B2 |
| Frontend async state across tenant switch | PARTIAL | High | BroadcastWorkspace local sender/realtime paths; Managed SMS tenantScope is stronger reference | Adopt shared tenant/request epoch for journey surfaces | F1 |
| Manual Contact creation/import | WORKING | High | Contacts core + processing/import services/UI | Reuse unchanged | none |
| Contact List / Segment | WORKING | High | Contact list + SegmentService/Controller/UI | Reuse unchanged | none |
| Canonical identity normalization | WORKING | Critical | IdentityNormalizer | Reuse for Broadcast and Inbox | B2 |
| Broadcast consumes List/Segment backend | WORKING BUT UX-DISCONNECTED | Critical | POST broadcast/audiences/messages; AudienceSourceResolver | Make this normal UI/API path | B1/F1 |
| Broadcast consumes individual Contact | MISSING | Medium | BroadcastAudienceSourceType only LIST/SEGMENT | Add only if needed for coherent “send to selected contacts”; use canonical selector contract, not permanent group | B1/F1 |
| Legacy BroadcastGroup members/import | STALE/DUPLICATED | High | broadcast_groups/group_members + current frontend | Keep historical compatibility, remove as primary recipient-management UX | B1/F1 |
| Broadcast member CSV -> canonical Contact | MISSING | High | legacy import writes broadcast_group_members | Normal UX should import via Contacts then select List/Segment | F1; B1 compatibility |
| Recipient preview with selected/eligible/excluded reasons | MISSING | Critical | snapshots only candidate/eligible count; no Broadcast preview endpoint | Add canonical resolution preview contract and reason counts/sample | B1/F1 |
| Canonical Contact eligibility | WORKING | Critical | ContactEligibilityService | Make it the policy source of truth | B1/B2 |
| Legacy broadcast suppression | STALE/DUPLICATED | Critical | broadcast_suppressions + BroadcastPreparationChunkService | Converge provider feedback and canonical Contact suppression; retain safe compatibility migration/read gate | B2 |
| Frozen List/Segment during materialization | WORKING | Critical | AudienceSnapshotService/AudienceSourceResolver | Preserve | B1 |
| Audience frozen at scheduling | MISSING | High | schedule only stores scheduledAt; snapshot created at launch | Freeze source descriptor/version/snapshot at schedule confirmation so preview is reproducible | B1 |
| Send-time consent/suppression recheck | WORKING | Critical | BroadcastDeliveryService.recheckContactEligibility | Preserve; include canonical suppression convergence | B1/B2 |
| Bounded 2k-10k execution | WORKING (SOURCE VERIFIED) | Critical | outbox/Kafka/keyset/batches/SKIP LOCKED/recovery | Preserve; add focused executable 2k/5k/10k tests/measurements | B1/QA |
| Duplicate/ambiguous provider send protection | WORKING (SOURCE VERIFIED) | Critical | attempts + UNKNOWN + recovery | Preserve | B1/QA |
| Broadcast execution stats | WORKING | High | BroadcastStats atomic counters | Extend preview/terminal delivery presentation without counter scans | B1 |
| Managed Email send readiness | WORKING | High | ManagedEmailOverviewService/runtime guards | Present product abstraction; preserve internals for admin | F1 |
| Managed Email provider events | WORKING (SOURCE VERIFIED) | Critical | SNS verifier/event ledger/projection | Reuse; bridge canonical suppression | B2 |
| Direct Email provider final delivery events | PARTIAL | Medium | acceptance transports exist; docs identify callbacks as separate | Do not fake delivery. R1 may expose acceptance-only capability flag; defer new direct webhooks unless required | B1/F1 |
| Managed SMS readiness/send/events | WORKING (SOURCE VERIFIED) | Critical | managedsms control/delivery/events | Preserve tenant epoch and provider safety | B1/F1/QA |
| SMS inbound -> Inbox | MISSING / PROVIDER-DEPENDENT | Low | no audited inbound SMS conversation path | Defer unless provider contract is verified and product requires two-way SMS R1 | deferred |
| WhatsApp Inbox messaging | WORKING (SOURCE VERIFIED) | Critical | channel/meta runtime + WhatsAppInbox | Preserve | B2/F1 |
| WhatsApp Broadcast | MISSING | Critical | BroadcastLaunchService rejects WHATSAPP | Add approved-template Broadcast execution using existing Meta connection/runtime rules | B1/F1 |
| WhatsApp provider verification | PROVIDER-TEST-BLOCKED | High | WhatsApp final QA says real Meta not verified | Provider smoke test in release validation | QA |
| Inbox -> canonical Contact association | MISSING | Critical | InboxParticipant has no contact id; ContactIdentityLookup unused | Add tenant-scoped canonical identity association/projection and APIs | B2/F1 |
| Contact -> Inbox navigation | MISSING | High | no Contact/inbox association | Add linked conversations/action where exact identity exists | B2/F1 |
| Broadcast recipient -> Inbox reply metadata | PARTIAL | Medium | recipient keeps contact; Inbox reply has no campaign/contact relation | Contact linkage is R1; direct campaign attribution may be lightweight/deferred | B2 |
| Broadcast completed/failed notifications | PARTIAL | Low | registry types exist, no source event producer | Emit durable notification source events from terminal transitions | B2 |
| Test email isolation | WORKING | Medium | BroadcastSenderTestService sends transport directly, no run/stats rows | Preserve | QA |
| Sender readiness API | PARTIAL | High | BroadcastSenderResponse exposes technical flags but not common action/status vocabulary | Add product-level readiness contract; keep provider diagnostics internal | B1/F1 |
| Lead -> Contact | MISSING (source capability absent) | Low | no Lead capture domain found | Define future Contact upsert/import seam with source/provenance; no Ads work in R1 | deferred |
| CI executable verification at audit SHA | PROVIDER-TEST-BLOCKED / EXECUTION BLOCKED | Critical | Actions run 36874477918 jobs have no runner/steps | SEC/QA must execute matrix on real runner/environment | QA |

## Highest-priority R1 findings

- **CMJ-R1-001** — frontend uses legacy Broadcast Groups instead of canonical Contact List/Segment audience.
- **CMJ-R1-002** — no canonical recipient-resolution preview/exclusion contract.
- **CMJ-R1-003** — schedule-time audience is not frozen/reproducible.
- **CMJ-R1-004** — provider feedback suppression and canonical Contact suppression are split.
- **CMJ-R1-005** — WhatsApp Broadcast is not implemented.
- **CMJ-R1-006** — Inbox participant is not linked to canonical Contact.
- **CMJ-R1-007** — Broadcast UI lacks consistent product-level channel/sender readiness.
- **CMJ-R1-008** — Broadcast local async/realtime state lacks robust tenant epoch protection.
- **CMJ-R1-009** — Broadcast completion/failure notification types are registered but not emitted.
- **CMJ-R1-010** — direct-provider delivery semantics are not uniformly surfaced; UI must distinguish accepted from delivered.
- **CMJ-R1-011** — integration/performance/CI executable verification is currently blocked and canonical audience Broadcast lacks focused end-to-end coverage.

## Explicit R1 deferrals

- new Ads Center features;
- new lead-capture product;
- two-way SMS Inbox until provider capability/authentication is verified;
- adding new direct-provider event webhooks merely for parity;
- advanced analytics/journey automation;
- full deletion of legacy BroadcastGroup schema before migration/compatibility evidence exists;
- production HA/cloud redesign.
