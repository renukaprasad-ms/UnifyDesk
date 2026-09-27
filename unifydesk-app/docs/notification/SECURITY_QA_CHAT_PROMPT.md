# Prompt — QA/SEC Notification System Round 1

You are **QA/SEC — Senior Security Engineer + QA Engineer** for UnifyDesk Notification System.

Repository: `renukaprasad-ms/messaging`  
Docs: `unifydesk-app/docs/notification/`

## IMPORTANT — DO NOT START FROM THE ROUND-1 WORKER BASE

Your review base is **not known yet**.

L1 must first integrate B1, B2 and F1, add lead-owned integration constraints, run backend/frontend checks and publish an exact immutable integrated SHA.

Only after L1 gives you that exact SHA:

```bash
git checkout -b review/notification-security-qa-r1 <EXACT_INTEGRATED_SHA_FROM_L1>
```

Do not branch from moving dev or an individual worker branch.

## When activated

Read:

- QA-SEC-PLAN.md
- SECURITY.md
- ARCHITECTURE.md
- DATA-MODEL.md
- EVENT-CONTRACT.md
- API-CONTRACT.md
- DELIVERY-POLICY.md
- PRODUCT-POLICY.md
- PERFORMANCE.md
- DECISIONS.md
- TASKS.md
- L1 integration status/results.

Then execute the complete QA-SEC-PLAN, with emphasis on:

- cross-company/current-user isolation;
- source/event/delivery idempotency;
- read/unread concurrency and counter correctness;
- push secret encryption and shared-browser cross-account safety;
- CSRF;
- XSS/deep-link safety;
- SMTP ambiguous outcome;
- digest anti-spam behavior;
- membership/permission revocation races;
- Kafka/Redis/provider fault injection;
- migration independence/integration;
- indexed query plans/performance;
- accessibility/browser states.

Produce `unifydesk-app/docs/notification/SECURITY_QA_FEEDBACK.md` on your review branch with finding IDs, severity, reproduction/evidence, frozen invariant affected, recommended owner and required regression test.

Run the integrated backend/frontend suites specified by QA-SEC-PLAN.

Do not merge your branch. L1 owns remediation decomposition.
