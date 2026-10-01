# Platform Hardening R1 Integration Status

## Lead planning

Starting `dev` SHA:

`c541899f238dbe6f70f9b91f5c513fa1b10f5641`

Frozen worker base:

`c541899f238dbe6f70f9b91f5c513fa1b10f5641`

Lead planning branch:

`docs/platform-hardening-r1-plan`

## Workers

| Worker | Branch | Scope | Status | Final SHA / PR |
|---|---|---|---|---|
| B1 | `fix/platform-backend-db-hardening-r1` | backend/Flyway/migrations/tests | READY AFTER PLAN MERGE | — |
| B2 | `fix/platform-ci-infrastructure-r1` | canonical CI/email-provider/website | READY AFTER PLAN MERGE | — |
| F1 | `fix/platform-frontend-verification-r1` | frontend verification failures | READY AFTER PLAN MERGE | — |

## Current executable evidence before remediation

Historical zero-step jobs: **EXECUTION BLOCKED**.

Current GitHub-hosted runner allocation: **EXECUTING**.

Current canonical Checks: **EXECUTED AND FAILED**, which is useful evidence and not an infrastructure non-execution.

Known current failures:

- backend Spotless;
- backend normal matrix regressions/environment;
- frontend tests/lint/format;
- email-provider Maven wrapper bootstrap;
- website lockfile/cache configuration.

## Integration

L1 integrated SHA:

**PENDING**

Do not start SEC/QA.

## SEC/QA handoff

Exact production-code SHA:

**PENDING**

The QA prompt file exists as a blocked template and must be updated by L1 only after integration.

## Risk-state rules

PLAT-002 is not closed merely because runners execute once. Close only after the canonical integrated current-head matrix produces trustworthy executed evidence.

PLAT-003 is not closed until both the application/default configuration is false and executable tests prove default-false plus explicit-opt-in behavior.
