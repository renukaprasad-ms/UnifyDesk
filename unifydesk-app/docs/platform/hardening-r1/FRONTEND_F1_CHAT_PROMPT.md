# F1 Chat Prompt — Platform Hardening Round 1

You are F1 for UnifyDesk Platform Hardening Round 1.

Repository:

`renukaprasad-ms/UnifyDesk`

Your exact frozen worker base SHA is:

`c541899f238dbe6f70f9b91f5c513fa1b10f5641`

Create:

`fix/platform-frontend-verification-r1`

from exactly that SHA.

Do not branch from moving `dev`. Do not rebase unless L1 explicitly instructs you.

Read:

- `unifydesk-app/docs/platform/hardening-r1/README.md`
- `unifydesk-app/docs/platform/hardening-r1/CI-INVESTIGATION.md`
- `unifydesk-app/docs/platform/hardening-r1/CHECK-MATRIX.md`
- `unifydesk-app/docs/platform/hardening-r1/ROUND1_TASKS.md`

## Scope

Own only frontend verification failures.

From `unifydesk-app/frontend`, execute independently:

```bash
npm ci
npm test
npm run build
npm run lint
npm run format:check
```

Current evidence:

- `npm ci`: executes successfully;
- production TypeScript/Vite build: executes successfully;
- tests: failing;
- lint: failing in the full matrix;
- format check: failing in the full matrix.

Observed test failures include Contacts dialog/page duplication and Segment editor assertions, notification tests expecting older “New inbox activity” copy instead of the current grouped-email subject presentation, Contacts permission-route behavior, and Broadcast/Inbox slice expectations.

## Required method

For every failure classify it before changing code:

1. stale test against intentional current behavior;
2. test isolation/cleanup problem;
3. real frontend implementation defect.

If current implementation matches frozen/current API/product behavior, repair the test without weakening its invariant.

If implementation is wrong, make the smallest frontend correction.

Do not:

- redesign UI;
- change product scope;
- remove assertions just to pass;
- add arbitrary sleeps/timeouts to hide races;
- suppress lint;
- exclude files from Prettier;
- edit backend;
- edit workflows;
- edit website/email-provider.

Pay attention to repeated “Found multiple elements” failures: determine whether tests leak rendered trees/mocks between cases or whether the component actually duplicates UI. Fix the root cause.

For notification tests, current intended rendering may show grouped counts and subject text. Update only if source/product behavior is already authoritative.

## Acceptance

All five commands must execute with exit code 0.

Keep build output warnings (for example bundle-size warnings) separate from failures; do not change bundling in this round unless build fails.

## Deliverable

Open a PR to `dev`.

Report:

- branch/base/final SHA;
- exact failing tests originally reproduced;
- classification of each corrected failure;
- files changed;
- `npm ci` result;
- test result;
- build result;
- lint result;
- format result;
- any EXECUTION BLOCKED item.

Do not merge your own PR.
