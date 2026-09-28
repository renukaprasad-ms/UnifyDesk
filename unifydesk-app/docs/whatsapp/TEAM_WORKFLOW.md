# Team Workflow — UnifyDesk Meta WhatsApp

## Team

This project uses exactly five chats:

1. **L1 — Lead Architect / Integration Lead**
2. **B1 — Backend Connection / Control Plane Engineer**
3. **B2 — Backend Messaging / Webhook / Delivery Engineer**
4. **F1 — Frontend + WhatsApp UX Engineer**
5. **QA/SEC — QA + Security Engineer**

The Product Manager owns scope and sequencing. L1 owns architecture, technical contracts, worker decomposition, integration and remediation planning.

## Round 0 — L1 only

L1 starts from:

- architecture branch: `docs/whatsapp-architecture-r0`
- bootstrap source: `dev @ 6daf9d89ac3b2c58b794c0b82dc2917d5737caa7`

L1 must first research the current repository and current Meta requirements.

L1 must create at minimum:

- `RESEARCH-META-WHATSAPP.md`
- `ARCHITECTURE.md`
- `DECISIONS.md`
- `DATA-MODEL.md`
- `API-CONTRACT.md`
- `EVENT-CONTRACT.md` if asynchronous contracts warrant it
- `SECURITY.md`
- `PERFORMANCE.md`
- `QA-SEC-PLAN.md`
- `TASKS.md`
- `INTEGRATION_STATUS.md`
- `BACKEND_B1_CHAT_PROMPT.md`
- `BACKEND_B2_CHAT_PROMPT.md`
- `FRONTEND_CHAT_PROMPT.md`
- `SECURITY_QA_CHAT_PROMPT.md`

L1 may add focused docs such as `WEBHOOK-CONTRACT.md`, `MESSAGE-STATE.md`, `META-SETUP-RUNBOOK.md`, or `TEMPLATE-POLICY.md` when useful.

Round 0 is architecture/contracts, not broad implementation.

## Current-research requirement

L1 must use current official Meta documentation wherever possible and record source links plus access/research date.

Research must cover at least:

- WhatsApp Cloud API;
- Embedded Signup and its currently supported version/configuration;
- Graph API versioning relevant to the repo;
- OAuth/code/token lifecycle;
- system-user/business tokens if relevant;
- WABA and phone-number registration;
- app subscription/webhooks;
- webhook payload and retry behavior;
- messages endpoint;
- message IDs/status webhooks;
- customer service / template messaging rules;
- template management;
- media APIs;
- rate/throughput/quality concepts;
- coexistence/current-number migration behavior where applicable;
- token revocation/permissions;
- provider error structure and safe diagnostics.

If official documentation is ambiguous, record the ambiguity and define the safest testable fallback. Do not fabricate Meta capabilities.

## Freeze before workers start

After Round 0 research and contracts are complete, L1 must:

1. choose one exact immutable **Round-1 worker base SHA** containing all frozen architecture/contracts but no worker implementation;
2. record that SHA in `TASKS.md` and every worker prompt;
3. define exact branch names for B1, B2 and F1;
4. define exact file/package/migration ownership;
5. freeze HTTP/DTO/state contracts required by F1;
6. freeze DB/event/seam contracts required between B1 and B2;
7. make sure each worker can compile/test from the same base without another worker branch.

No implementation worker starts before this freeze.

## Independence rules

### B1 must not wait for B2

B1 owns only the files and migrations assigned by L1. It cannot depend on B2 implementation.

### B2 must not wait for B1

If B2 requires data that B1 will own, L1 must freeze a stable contract usable from the worker base, for example:

- existing repository/interface seam;
- SQL/table contract;
- DTO/value contract already committed in Round 0;
- neutral shared interface owned by L1 before freeze.

B2 must not import a new class that exists only on B1's implementation branch.

### F1 must not wait for backend

F1 implements against frozen API/state/error contracts and uses mocks/tests where backend is absent.

### One hot file, one owner

If multiple workers need the same existing file, L1 assigns one owner. Other workers get a contract/seam.

Shared migrations are forbidden. Migration ownership must be explicit.

## Suggested responsibility split

L1 must validate and refine this after repository research.

### B1 — Connection / Control Plane

Likely areas:

- signup state lifecycle;
- code/token exchange wrapper and safe Meta errors;
- WABA/phone discovery;
- provisioning/reconciliation;
- idempotent connect/reconnect;
- connection state machine;
- credential lifecycle;
- live health;
- disconnect/re-authorization;
- control-plane APIs;
- connection-focused tests.

### B2 — Runtime Messaging / Webhook / Delivery

Likely areas:

- webhook intake hardening;
- durable async ingestion;
- event dedupe;
- inbound message mapping;
- provider status mapping;
- outbound text/template/media;
- provider message IDs;
- template-window enforcement;
- template/media runtime integration;
- retries/DLQ/reconciliation;
- runtime-focused tests.

### F1 — Frontend + UX

Likely areas:

- connect/reconnect/disconnect flow;
- only request fresh signup session immediately before launch;
- SDK event lifecycle and cleanup;
- actionable safe errors;
- connection health/status;
- WhatsApp inbox composer states;
- template-required state;
- template selection/variable UX;
- agreed media UI;
- loading/error/accessibility/responsive behavior;
- frontend tests.

This is guidance, not frozen ownership.

## Worker completion contract

Each worker must:

- create its exact branch from the exact frozen worker SHA;
- stay inside assigned ownership;
- implement the complete assignment;
- add focused regression tests;
- run required checks where the environment permits;
- commit all work;
- not merge;
- report:
  - branch;
  - final commit SHA;
  - exact files changed;
  - tasks/findings completed;
  - tests/checks run and results;
  - any blocker or contract deviation.

A worker should never be told to "wait for B1/B2/F1".

## Integration — L1

After B1, B2 and F1 finish:

1. inspect every branch and diff;
2. verify it matches frozen contracts;
3. resolve ownership/integration conflicts deliberately;
4. integrate into a dedicated integration branch or the architecture flow specified in `TASKS.md`;
5. add lead-owned cross-module glue/constraints only where planned;
6. run backend/frontend checks;
7. update `INTEGRATION_STATUS.md`;
8. publish one exact immutable **integrated QA base SHA**.

Do not hand QA/SEC a moving branch.

## QA/SEC activation

QA/SEC starts only after L1 publishes the integrated SHA.

QA/SEC creates its review branch from exactly that SHA and executes the complete `QA-SEC-PLAN.md`.

QA/SEC must test at minimum:

- authorization/company isolation;
- connect/reconnect races;
- state replay/expiry;
- token/secret leakage;
- webhook forgery/replay/body abuse;
- duplicate/out-of-order webhook events;
- inbound/outbound idempotency;
- ambiguous send behavior;
- status reconciliation;
- template-window enforcement;
- media safety;
- permission revocation;
- queue/provider/Redis/DB fault behavior;
- migration correctness;
- API/DTO contract parity;
- frontend failure/cancel/timeout states;
- performance/index assumptions;
- real Meta test-account scenarios that cannot be faithfully mocked.

QA/SEC writes `SECURITY_QA_FEEDBACK.md` with IDs, severity, reproduction/evidence, affected invariant, recommended owner, and required regression test.

QA/SEC does not merge.

## Remediation rounds

L1 reads QA/SEC feedback and creates only targeted Round-2 tasks/prompts for affected owners.

Each remediation round uses:

- exact frozen integrated base SHA;
- exact finding ownership;
- narrow branches;
- regression tests;
- re-integration;
- exact QA retest SHA.

Repeat until no release-blocking findings remain.

## Change control

After the Round-1 contracts are frozen:

- workers must not silently change API/DB/event contracts;
- any contract deviation is reported to L1;
- L1 decides whether to amend contracts and re-freeze;
- workers do not coordinate by modifying one another's branches.

This discipline is mandatory because the multi-chat workflow depends on deterministic boundaries.
