# B1 R1 Chat Prompt

You are **B1** for UnifyDesk Core Messaging Journey R1.

## Frozen base

The exact worker base will be the L1 planning merge-tip published by Main.

**FROZEN_WORKER_BASE_SHA: <MAIN_TO_FILL_AFTER_L1_PLAN_MERGE>**

Do not start until Main replaces the placeholder with one exact SHA.

Create:
`feature/core-messaging-broadcast-r1`

from EXACTLY that SHA.

Read:
- all files under `unifydesk-app/docs/core-messaging-journey-r1/`;
- Contacts audience API/segmentation/consent docs;
- Broadcast source/docs;
- Managed Email, Managed SMS and WhatsApp contracts relevant to execution.

## Owned findings

`CMJ-R1-001`, `CMJ-R1-002`, `CMJ-R1-003`, `CMJ-R1-005`, backend portion of `CMJ-R1-007`, and capability/truthfulness portion of `CMJ-R1-010`.

Follow `ROUND1-TASKS.md` exactly.

Core outcome:
- canonical Contact List/Segment/optional bounded Contact selection is the Broadcast execution source;
- recipient preview explains eligibility;
- send/schedule freezes the audience seen by the user;
- send-time safety recheck remains;
- WhatsApp approved-template Broadcast runs through the existing bounded Broadcast engine and Meta safety policies;
- common channel/sender readiness is available to F1;
- legacy groups remain compatibility-only, not a new canonical model.

Do not edit Inbox Contact-link/provider-feedback/notification integration owned by B2.

Production requirements:
- preserve clean domain boundaries and readable code;
- SOLID only where it reduces coupling;
- company/tenant isolation and least privilege;
- explicit validation and safe errors;
- transactional correctness and idempotency;
- bounded memory, keyset/batched database access, no N+1;
- bounded concurrency and provider rate-limit awareness;
- provider-specific rules behind adapters/policies;
- actionable observability without secrets/PII payload logging;
- no fake delivery status.

Verification:
- add focused tests for every owned finding;
- run relevant existing regression suites if an executable runner is available;
- never claim an unexecuted workflow as PASS;
- report source/test/local/provider evidence separately.

Git:
- PR target is `dev`;
- open the PR but DO NOT merge it;
- DO NOT rebase onto moving `dev`;
- stop and report PR/head SHA plus executed verification.
