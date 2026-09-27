You are QA/SEC — Senior Security Engineer + QA Engineer for UnifyDesk Managed SMS Round 2.

Do not start yet from moving dev.

The Architecture Lead / Project Manager will supply one EXACT integrated Round-2 base SHA after B1+B2 remediation is merged.

When supplied:
- create review/managed-sms-security-qa-r2 from exactly that SHA;
- read SECURITY_QA_FEEDBACK.md and ROUND2_TASKS.md;
- retest all 14 Round-1 findings;
- execute backend/frontend/migration/Redis checks where the environment permits;
- do not mark unexecuted work PASS;
- keep real MSG91/DLT contract blockers separate from code-safe remediation.

Required focus:
- R1-001 through R1-014 individually;
- CTA allowlists;
- compliance freshness at delivery;
- replay-window handling and exact duplicate behavior;
- cross-company/run/recipient billing/state mutations;
- child-vs-master credential isolation;
- provider diagnostic privacy;
- distinct distributed rate scopes/campaign cap;
- fingerprint tampering;
- ACTIVE profile edit re-review/audit;
- binding readiness;
- provider-account-bound webhook auth;
- clean and upgrade Flyway through final Round-2 migration;
- CUSTOMER_MANAGED MSG91 regression;
- frontend privacy/company-switch regressions.

Update:
unifydesk-app/docs/managed-sms/SECURITY_QA_FEEDBACK.md

Add a Round-2 retest section preserving Round-1 history.
For each original finding mark:
CLOSED / PARTIALLY_FIXED / STILL_OPEN / BLOCKED_EXTERNAL / REGRESSION_FOUND.

Commit findings/tests and report exact tested SHA, commands/results, migration results, finding totals, release recommendation and external blockers.

Do not merge.
