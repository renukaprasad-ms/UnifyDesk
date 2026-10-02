# E2E Verification Matrix

## Classification rules

`PASS` requires executable evidence. `FAIL` means the required runtime/source behavior is violated. `BLOCKED` means a dependency prevents execution. `NOT EXECUTED` means no execution evidence exists yet.

| ID | Journey stage | Provider/channel | Preconditions | Test/action | Expected API / durable state | Provider/event expectation | User-visible result | Executable evidence to capture | Current result |
|---|---|---|---|---|---|---|---|---|---|
| CMR1-001 | Workspace isolation | all | two companies, same browser session | A1 -> B -> A2 with delayed requests | no A1 state can render/mutate B or A2 | N/A | only current-workspace data/actions | frontend delayed-promise tests + browser trace | NOT EXECUTED |
| CMR1-002 | Contact import | all | deterministic CSV | import new contacts with `SKIP` | Contact/identity rows created once | N/A | correct imported/failed counts | import API response + DB rows | NOT EXECUTED |
| CMR1-003 | Consent import | Email | existing exact Contacts | re-import with `MERGE_EXACT`, EMAIL opted-in rows | effective EMAIL/MARKETING preference becomes `OPTED_IN` with evidence/time | N/A | eligible count increases only for consented rows | preference rows + Preview result | NOT EXECUTED |
| CMR1-004 | Consent import | SMS | existing exact Contacts | re-import with `MERGE_EXACT`, SMS opted-in rows | effective SMS/MARKETING preference becomes `OPTED_IN` | N/A | SMS eligible rows match fixture | preference rows + Preview | NOT EXECUTED |
| CMR1-005 | Consent import | WhatsApp | existing exact Contacts | re-import with `MERGE_EXACT`, WHATSAPP opted-in rows | effective WHATSAPP/MARKETING preference becomes `OPTED_IN` | N/A | WhatsApp eligible rows match fixture | preference rows + Preview | NOT EXECUTED |
| CMR1-006 | Duplicate behavior | all | existing exact identity | re-import same row with `SKIP` and changed consent | existing Contact returned; consent not applied | N/A | no misleading new opt-in | before/after preference query | NOT EXECUTED |
| CMR1-007 | List Preview | Email | active List + sender | Preview List | authoritative source descriptor + selected/eligible/excluded accounting | none | exclusion reasons/samples shown | API payload + source version | NOT EXECUTED |
| CMR1-008 | Segment Preview | Email | active Segment | validate + Preview | backend normalized AST used for Preview; editor state unchanged | none | filter UI does not reset | API trace + component test/browser trace | NOT EXECUTED |
| CMR1-009 | Preview/freeze drift | all canonical | Preview then mutate List/Segment | Send/Schedule with stale descriptor | HTTP 409 `BROADCAST_AUDIENCE_PREVIEW_STALE`; no run | none | user must re-preview | API response + DB absence of run | NOT EXECUTED |
| CMR1-010 | Audience freeze | all canonical | unchanged Preview descriptor | Send/Schedule | immutable snapshot/candidates captured and bound to message | none | launch proceeds | snapshot/candidate/message rows | NOT EXECUTED |
| CMR1-011 | Send-time recheck | all canonical | frozen recipient then opt-out/suppress/change identity | allow preparation then dispatch | recipient becomes `SKIPPED`; no provider submission | provider must not receive request | explicit skip reason | recipient row + provider/mock trace | NOT EXECUTED |
| CMR1-012 | Bounded execution | all | 2k/5k/10k eligible Contacts | send controlled fixture | bounded preparation, batches and claims; no unbounded UI payload | bounded provider calls | progress remains responsive | DB batch/claim metrics + memory/latency | NOT EXECUTED |
| CMR1-013 | Zero-pending terminal | all | snapshot where all recipients skip | complete preparation | run/message `COMPLETED`; exactly one durable completion event | no provider request | one terminal Notification | run/message/stats/notification rows | NOT EXECUTED |
| CMR1-014 | Managed Email readiness | AWS SES | managed tenant/domain/sender ACTIVE and secret/runtime configured | readiness + launch | launch guard accepts | SES available | sender shown READY | runtime API + DB state | NOT EXECUTED |
| CMR1-015 | Managed Email acceptance | AWS SES | CMR1-014 | send one opted-in recipient | recipient -> `ACCEPTED_BY_PROVIDER`; provider message ID persisted | SES send accepted | accepted/sent, not falsely Delivered | SES response + recipient/runtime rows | NOT EXECUTED |
| CMR1-016 | Managed Email delivered | AWS SES | accepted message + SNS configured | deliver to real mailbox | provider projection becomes delivered; core acceptance truth preserved | signed SNS delivery event | Delivered only after provider truth | SNS payload ID + event/projection rows | NOT EXECUTED |
| CMR1-017 | Managed Email hard bounce | AWS SES | deterministic bounce target | send + process bounce | bounce projection; recipient may -> `BOUNCED`; canonical Contact suppression converges | matched permanent BOUNCE SNS event | bounce/suppression visible | provider event, suppression and stats rows | NOT EXECUTED |
| CMR1-018 | Managed Email complaint | AWS SES | provider complaint test path | process complaint | canonical Contact suppression converges idempotently | matched complaint SNS event | complaint/suppression visible | provider event + suppression rows | NOT EXECUTED |
| CMR1-019 | Managed Email duplicate event | AWS SES | CMR1-016/017 event captured | replay exact event | no duplicate effect/counter regression | same SNS event/dedupe key | no duplicate user signal | row counts before/after | NOT EXECUTED |
| CMR1-020 | Managed SMS readiness | MSG91 | feature enabled, provider account, DLT/profile/header/template ready | readiness + launch | managed launch guard accepts | MSG91 credentials/assets valid | sender/template READY | runtime API + DB | NOT EXECUTED |
| CMR1-021 | Managed SMS send | MSG91 | CMR1-020 + opted-in phone | send one recipient | provider request/correlation persisted | MSG91 message/request ID | accepted/sent truthfully | HTTP/provider response + rows | NOT EXECUTED |
| CMR1-022 | Managed SMS callback | MSG91 | accepted SMS | process real callback | monotonic provider projection + terminal accounting | authenticated/deduped callback | delivery result updates | callback evidence + event rows | NOT EXECUTED |
| CMR1-023 | Managed SMS duplicate callback | MSG91 | captured callback | replay callback | no duplicate transition/usage | same dedupe identity | no duplicate notification/state | before/after DB | NOT EXECUTED |
| CMR1-024 | WhatsApp production signup | Meta | eligible Tech Provider/business setup | normal Embedded Signup | connection reaches ACTIVE only after server-side validation/persistence | Meta onboarding succeeds | Connect WhatsApp completes | Meta + API evidence | BLOCKED BY META per handoff |
| CMR1-025 | WhatsApp test outbound | Meta test WABA | test WABA/phone + secure server-side credentials | template send | Broadcast recipient accepted with provider message ID | Cloud API accepts | accepted/sent | request/response + rows | NOT EXECUTED |
| CMR1-026 | WhatsApp delivery/read | Meta test WABA | CMR1-025 | receive delivered/read callbacks | monotonic provider projection | signed webhook statuses | Delivered/Read only on provider truth | webhook/event rows | NOT EXECUTED |
| CMR1-027 | WhatsApp inbound -> Inbox | Meta test WABA | webhook reachable | send inbound reply | Inbox conversation/message persisted | signed inbound webhook | reply appears in Inbox | webhook + inbox rows | NOT EXECUTED |
| CMR1-028 | Inbox -> Contact link | Email/WhatsApp | canonical Contact with exact identity | ingest inbound participant | `inbox_participant_contact_links` -> `MATCHED` with Contact/identity IDs | N/A | View Contact navigation works | link row + DTO/UI | NOT EXECUTED |
| CMR1-029 | Unknown Inbox participant | Email/WhatsApp | no matching Contact | ingest inbound participant | link stays UNMATCHED/AMBIGUOUS; no implicit Contact | N/A | explicit Create/Review action | DB + Contacts count | NOT EXECUTED |
| CMR1-030 | Broadcast terminal Notification | all | successful/partial/failed runs | finalize | deterministic `broadcast.completed:<runId>` or `broadcast.failed:<runId>` event once | N/A | in-app notification materializes | notification_events + delivery evidence | NOT EXECUTED |
| CMR1-031 | Direct Gmail Broadcast | Gmail | current canonical Broadcast source | attempt modern sender/launch | current source rejects provider via `supports()` | no request | cannot launch canonical Broadcast | source + API reproduction | FAIL: current source/scope mismatch |
| CMR1-032 | Direct Microsoft Broadcast | Microsoft | current canonical Broadcast source | attempt modern sender/launch | current source rejects provider via `supports()` | no request | cannot launch canonical Broadcast | source + API reproduction | FAIL: current source/scope mismatch |
| CMR1-033 | Direct SMTP Broadcast | SMTP/IMAP_SMTP | current canonical Broadcast source | attempt modern sender/launch | current source rejects provider via `supports()` | no request | cannot launch canonical Broadcast | source + API reproduction | FAIL: current source/scope mismatch |
| CMR1-034 | CI matrix | N/A | exact frozen SHA | run `Checks` workflow | all defined jobs execute | N/A | release evidence available | workflow run/jobs/logs | NOT EXECUTED |

## Required DB questions per run

Every provider run must make it possible to answer:

1. Which canonical Contact and identity were selected?
2. What effective marketing preference existed at send time?
3. What List/Segment/Contacts source descriptor/version/hash was previewed?
4. Which immutable snapshot was frozen?
5. Which Contacts entered candidate/member rows?
6. Which recipients were skipped at final eligibility and why?
7. Which sender/provider was selected?
8. Which provider correlation/message ID was persisted?
9. Which provider event IDs/dedupe keys arrived?
10. Did suppression state converge idempotently?
11. Did the run/message become terminal exactly once?
12. Was the durable Notification event recorded exactly once?
13. For inbound Email/WhatsApp, did the participant link resolve to the expected canonical Contact?

## Runtime commands to execute in the deployed environment

Examples; use the deployed namespace/context and never print secret values.

```bash
git rev-parse HEAD
kubectl get deploy,pods
kubectl get secret messaging-env
kubectl exec deploy/backend -- sh -lc 'env | cut -d= -f1 | grep -E "^(APP_MANAGED_EMAIL|APP_MANAGED_SMS|AWS_|META_WHATSAPP|CHANNEL_CREDENTIAL)" | sort'
```

For each test, capture API request/response IDs, run/broadcast IDs, provider IDs and narrowly-scoped SQL results. Do not dump credentials or full customer payloads into evidence.
