# Platform Hardening Round 1

Status: **LEAD PLAN FROZEN — WORKERS MAY START AFTER THIS PLAN IS MERGED**

Starting `dev` SHA and frozen worker base:

`c541899f238dbe6f70f9b91f5c513fa1b10f5641`

Resolved on 2026-10-01 after Platform Baseline PR #221 merged.

## Objective

Establish a trustworthy executable development baseline. This round owns:

- PLAT-002 — executable CI verification;
- PLAT-003 — Flyway fail-closed baseline behavior;
- preparation, but not activation, of PLAT-001 branch protection.

This round does **not** redesign production Kubernetes, HA, backup/restore, observability, provider onboarding or production infrastructure.

## Lead findings

### Historical zero-step Actions

Historical runs on 2026-09-29 created jobs with:

- `runner_id=0`;
- empty runner name/group;
- `steps=[]`;
- failure within seconds.

Examples:

- Checks run `36550776002`;
- WhatsApp Round 3 Final QA run `36550776200`.

The same repository/workflow family received real GitHub-hosted runners and executed steps on 2026-10-01. Therefore the historical zero-step condition is **not a workflow syntax/action-version/job-condition source failure**.

GitHub status reported no Actions incident on 2026-09-29. This private repository consumes GitHub-hosted Actions minutes. The September 29 -> October 1 recovery is strongly consistent with an account-side Actions minutes/budget/payment allocation block clearing at the billing-cycle reset, but the connected GitHub App cannot read owner billing usage, so that final account-side cause requires human confirmation.

Human confirmation path: GitHub account **Settings -> Billing & licensing -> Usage / Budgets** and verify Actions minutes, budget/spending controls and payment status.

Classification:

- historical zero-step failure: **GITHUB / ACCOUNT / RUNNER ISSUE**;
- exact billing/quota subtype: **human confirmation required**;
- do not invent a repository code fix for that historical condition.

### Current CI is executing and exposes real source/config failures

PR #221/current workflow evidence proves hosted runners now execute. Current `Checks` is red for real reasons:

- backend: Spotless reports 809 files requiring formatting before tests in the combined command;
- frontend: `npm ci` and TypeScript/Vite build execute, but tests fail; the full matrix also reports format/lint failures;
- email-provider: Maven wrapper cannot read `distributionUrl`; its properties file lacks the final newline, so the POSIX `while read` loop never processes the final property;
- website: setup-node caching references `unifydesk-website/package-lock.json`, but no lockfile exists.

A separate historical WhatsApp R2 workflow appears green on current PRs because required commands use `continue-on-error: true`. That workflow is not valid canonical release evidence.

WhatsApp R3 also runs on every PR to `dev` even though some migration assertions are frozen to historical V79 while current migration head is V83. Historical QA workflows must not masquerade as general current-head required checks.

## Documents

- [CI investigation](CI-INVESTIGATION.md)
- [Canonical check matrix](CHECK-MATRIX.md)
- [Round-1 tasks and ownership](ROUND1_TASKS.md)
- [B1 prompt](BACKEND_B1_CHAT_PROMPT.md)
- [B2 prompt](BACKEND_B2_CHAT_PROMPT.md)
- [F1 prompt](FRONTEND_F1_CHAT_PROMPT.md)
- [Integration status](INTEGRATION_STATUS.md)
- [SEC/QA handoff placeholder](SECURITY_QA_R1_CHAT_PROMPT.md)

## Worker rules

B1/B2/F1 all branch from exactly:

`c541899f238dbe6f70f9b91f5c513fa1b10f5641`

Do not rebase onto moving `dev`.

Workers open PRs to `dev` but do not merge independently. L1 reviews and integrates deliberately.

SEC/QA does not start until L1 freezes one exact integrated production-code SHA and updates the QA prompt with that SHA.
