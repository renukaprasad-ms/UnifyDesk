# Team Workflow — UnifyDesk Contacts & Audience

## Team

Exactly five chats:

1. L1 — Product Manager / Lead Architect / Integration Lead
2. B1 — Backend Contacts / Control Plane
3. B2 — Backend Audience / Data Processing
4. F1 — Frontend Contacts & Audience UX
5. QA/SEC — Integrated QA + Security

## Round 0 — L1 only

Architecture branch:

`docs/contacts-audience-architecture-r0`

Source baseline:

`dev @ 88af16fe1cd6966730c26447119416104039e6bb`

L1 researches existing UnifyDesk architecture and current public product/provider behavior.

Round 0 is docs/contracts only except minimal shared scaffolding if absolutely required.

## Freeze

Before workers:

1. merge architecture/contracts to dev;
2. record exact resulting dev SHA;
3. that SHA becomes immutable Round-1 worker base;
4. publish it in TASKS and worker prompts;
5. do not move the worker base after implementation begins.

The metadata commit that writes the already-known SHA into prompts occurs after the frozen base and therefore is not itself the worker base. Workers read the prompt from current dev/docs but create branches from the exact older frozen SHA. This is the same deterministic pattern previously used by UnifyDesk modules.

## Branches

Round-1:

- B1: `feature/contacts-core-r1`
- B2: `feature/contacts-audience-runtime-r1`
- F1: `feature/contacts-audience-ui-r1`
- QA later: `review/contacts-audience-security-qa-r1`

Workers do not merge.

## Worker independence

All workers branch from the same exact base SHA.

### B1

Must compile/test without B2.

### B2

Must compile/test without B1 implementation classes.

B2 may use the frozen SQL seam in DATA-MODEL.md and mock/create fixture tables in tests.

B2 cannot edit B1 migrations.

### F1

Implements API clients/types/UI against API-CONTRACT.md and mocks.

F1 does not wait for backend branches.

## One-hot-file rule

If two workers need the same existing file, one owns it.

Other worker:
- codes to frozen seam;
- reports integration request;
- does not edit the file.

Lead resolves shared glue after worker completion.

## Migration allocation

Current highest migration on inspected dev baseline is V79.

Contacts reserves non-colliding 90-series because Ad Center is active separately.

- B1: V90, V91, V92
- B2: V93, V94, V95
- L1 integration: V96

Workers use only their numbers.

A worker may use fewer numbers.

Never repurpose another worker's reserved number.

## Worker completion

Each worker must:

- branch from exact frozen SHA;
- stay in ownership;
- implement all assigned scope;
- add tests;
- run required checks;
- commit;
- open PR to dev or report PR number;
- not merge;
- report:
  - branch;
  - final SHA;
  - PR;
  - files changed;
  - migrations;
  - tests/checks;
  - contract deviations;
  - blockers.

## Integration — L1

After B1/B2/F1 complete:

1. inspect every PR/diff;
2. verify no ownership violation;
3. verify migrations;
4. integrate B1;
5. integrate B2;
6. add V96 cross-worker constraints/glue;
7. add shared IAM/bootstrap/lifecycle/Broadcast/Inbox adapters as frozen;
8. integrate F1;
9. resolve contract mismatches only through L1;
10. run backend/frontend checks;
11. run clean and upgrade migration tests;
12. update INTEGRATION_STATUS;
13. freeze exact integrated QA SHA;
14. activate QA/SEC from that SHA.

## QA

QA starts only from frozen integrated SHA.

QA does not fix findings.

QA writes SECURITY_QA_FEEDBACK.md with IDs:

`CA-R1-NNN`

Each finding includes:
- severity;
- invariant;
- reproduction;
- expected;
- actual;
- code/test evidence;
- owner;
- regression test;
- status.

Disposition:
- RELEASE-READY
- NOT RELEASE-READY

## Remediation

L1 creates targeted Round-2 assignments only for actual findings.

Each remediation round has:
- exact base SHA;
- exact finding IDs;
- narrow owner branch;
- tests;
- integration;
- exact QA retest SHA.

No broad reimplementation after QA unless architecture itself is proven invalid.

## Contract change control

After freeze:

- worker cannot silently alter API/DB/event semantics;
- deviation goes to L1;
- if contract must change, L1 updates docs and explicitly re-freezes before dependent work;
- workers never coordinate by editing one another's branches.
