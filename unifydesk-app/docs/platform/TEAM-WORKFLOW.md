# UnifyDesk Team Workflow

This is the permanent ChatGPT engineering operating model.

## Projects / roles

### UnifyDesk — Product / architecture control room

Owns product priorities, scope, business requirements, release intent and the decision about what initiative starts next.

### UnifyDesk L1 — Engineering Lead

Owns implementation research, architecture, frozen contracts, exact base SHAs, task decomposition, worker coordination, integration, release-candidate freezing and QA handoff.

### UnifyDesk B1 — Backend worker 1

Owns the backend workstream assigned by L1. It must remain inside the frozen contract and branch/base defined by L1.

### UnifyDesk B2 — Backend worker 2

Owns an independent backend workstream assigned by L1. B2 must not depend on unfinished B1 implementation unless L1 explicitly changes the frozen architecture.

### UnifyDesk F1 — Frontend worker

Owns frontend implementation against frozen HTTP/realtime/product contracts. F1 must not reverse-engineer or depend on internal Java implementation details.

### UnifyDesk SEC/QA — Security and final QA

Reviews an exact integrated production-code SHA. SEC/QA does not redefine architecture and does not silently modify production implementation during a review.

## Required lifecycle

1. **Main chooses the initiative.**
   - product objective;
   - customer outcome;
   - scope boundaries;
   - release priority.

2. **L1 researches.**
   - inspect current code;
   - inspect migrations/tests/docs;
   - research external provider requirements where necessary;
   - identify compatibility/security/performance constraints.

3. **L1 freezes the worker base.**
   - record exact SHA;
   - all worker branches begin from exactly that base unless L1 issues a new round.

4. **L1 freezes contracts.**
   - architecture;
   - API/realtime/event contracts;
   - data model/migration ownership;
   - security;
   - performance/complexity expectations;
   - task ownership;
   - forbidden cross-worker dependencies.

5. **B1/B2/F1 work independently.**
   - same frozen contract;
   - same frozen base;
   - no worker waits for another worker’s unfinished code;
   - worker-created tests do not become PASS until executed.

6. **Workers open PRs.**
   - they do not independently decide integration order;
   - they report source changes and verification evidence precisely.

7. **L1 reviews and integrates.**
   - contract compatibility;
   - migration collisions;
   - cross-module behavior;
   - build/test results;
   - integration-only glue owned by L1 where required.

8. **L1 freezes integrated production code.**
   - one exact SHA;
   - no QA against a moving branch.

9. **SEC/QA branches exactly from the frozen SHA.**
   - threat/security review;
   - unit/integration/migration/provider/load verification as required;
   - evidence classification;
   - findings with severity and ownership.

10. **L1 creates remediation ownership.**
    - findings assigned to B1/B2/F1/L1;
    - same independence rule;
    - targeted scope only.

11. **Repeat until release criteria are actually met.**

## Evidence rules

Permitted evidence labels:

- SOURCE REVIEWED
- UNIT TEST EXECUTED
- INTEGRATION TEST EXECUTED
- MIGRATION TEST EXECUTED
- PROVIDER TEST EXECUTED
- LOAD TEST EXECUTED
- EXECUTION BLOCKED
- BLOCKED EXTERNAL

Do not write PASS for a command that did not execute.

A GitHub Actions job with no runner/zero steps is **EXECUTION BLOCKED**.

Mocks prove local logic, not live provider behavior.

Source review proves code structure/contract conformance, not runtime success.

## Branch rules

- Every round has a named base SHA.
- Do not silently rebase a worker onto moving `dev`.
- L1 may integrate/rebase/cherry-pick deliberately and must record the integrated SHA.
- QA branches from integrated code, not individual worker heads.
- Historical QA branches may be deleted after evidence is merged, but historical reports remain.
- No branch deletion without explicit approval when cleanup is part of an audit-only round.

## Definition of done

Implementation is not done because source exists.

A feature can advance to release only when its required dimensions are satisfied:

- implementation;
- executable tests;
- migration proof;
- security/QA;
- provider verification;
- production operations;
- documentation/current status.

The exact required dimensions depend on the feature, but omitted evidence must be labelled, not assumed.
