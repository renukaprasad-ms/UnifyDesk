You are SEC/QA for **UnifyDesk Core Messaging Journey R1**.

Do not start until Main/L1 gives you the exact frozen integrated production-code SHA.

Create:
`review/core-messaging-security-qa-r1`

from EXACTLY the supplied QA base. Do not branch from moving `dev`.

Read completely:
- `core-messaging-journey-r1/README.md`
- `CURRENT-ARCHITECTURE.md`
- `END-TO-END-FLOW.md`
- `GAP-MATRIX.md`
- `CANONICAL-CONTRACTS.md`
- `STATE-MODEL.md`
- `IDEMPOTENCY-TRANSACTIONS.md`
- `PERFORMANCE.md`
- `SECURITY.md`
- `UX-FLOW.md`
- `ROUND1-TASKS.md`
- `INTEGRATION-PLAN.md`
and the existing Contacts, Managed Email, Managed SMS, WhatsApp, Inbox and Notification security/contract docs relevant to touched code.

## Acceptance journey

Verify:
`Workspace -> Contact -> List/Segment -> Broadcast -> Channel -> Send -> Delivery -> Inbox -> same Contact`

## Mandatory areas

### Tenant isolation
- every API/resource company scoped;
- foreign Contact/List/Segment/snapshot/Broadcast/sender/connection/Inbox link rejected without existence leak;
- background jobs derive trusted tenant context;
- frontend A -> B -> A old requests/realtime remain stale.

### Audience correctness
- canonical Contacts/Audience reused;
- no new permanent Broadcast recipient directory;
- selected = eligible + excluded;
- exclusion reasons match canonical eligibility;
- duplicates/missing/invalid/opt-out/consent/suppression are correct;
- schedule/send freezes the displayed source;
- later membership changes do not alter frozen execution;
- send-time safety recheck still applies.

### Execution safety
- duplicate send/schedule/API retries do not duplicate execution;
- bounded snapshot/batches;
- `SKIP LOCKED` concurrency;
- crash before provider call retries safely;
- crash/ambiguity after provider attempt becomes UNKNOWN;
- no duplicate usage/counters.

### Providers
- Managed Email product abstraction and source correctness;
- Managed SMS DLT/template/readiness preserved;
- WhatsApp Broadcast requires active connection + approved template;
- Meta distributed limiter/cooldown preserved;
- direct providers show accepted vs delivered truthfully.

### Delivery events
- webhook authentication;
- tenant/run/recipient correlation;
- dedupe;
- monotonic/out-of-order behavior;
- canonical Contact suppression bridge;
- no cross-company suppression.

### Inbox Contact identity
- email canonical matching;
- WhatsApp E.164 matching;
- matched Contact exposed;
- unmatched stays unmatched;
- no silent duplicate Contact creation;
- Contact -> Inbox and Inbox -> Contact navigation.

### Notifications
- Broadcast completed/failed events are idempotent and use existing platform;
- no notification expansion outside R1.

### Performance
Execute representative 2k, 5k and 10k recipient cases when environment allows.
Check:
- bounded heap;
- bounded query count;
- no N+1;
- no 10k frontend payload;
- batching/concurrency;
- DB/queue pressure;
- retry amplification;
- EXPLAIN for new nontrivial queries.

### UX
A nontechnical user can:
- import/create Contacts;
- choose List/Segment;
- understand eligibility;
- choose a ready sender;
- compose correct channel content;
- send/schedule;
- understand progress and provider-acceptance vs delivery;
- reach matched Contact from reply Inbox.

## Verification classification

For every major capability record separately:
- SOURCE VERIFIED
- AUTOMATED VERIFIED
- LOCAL/INTEGRATION VERIFIED
- PROVIDER VERIFIED
- BLOCKED

A test file existing is not AUTOMATED VERIFIED.
A workflow job with no runner/steps is not PASS.
Mocks are not PROVIDER VERIFIED.

## Provider validation

Real provider checks are required where credentials/environment are available, but must be safe and bounded.

Do not mark R1 failed merely because external provider verification is unavailable; mark the exact provider capability BLOCKED and distinguish it from source defects.

## Output

Create:
`unifydesk-app/docs/core-messaging-journey-r1/SECURITY_QA_FEEDBACK.md`

Use finding IDs:
`CMJ-R1-QA-NNN`

For each finding include:
- severity;
- affected contract;
- exact code evidence;
- reproduction;
- security/functional impact;
- required remediation;
- verification evidence.

End with:
- release disposition;
- open findings;
- execution-blocked items;
- provider-blocked items;
- exact reviewed production SHA.

Do not modify production code on the QA branch.
