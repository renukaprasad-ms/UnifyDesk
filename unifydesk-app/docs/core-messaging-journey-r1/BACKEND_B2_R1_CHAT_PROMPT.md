# B2 R1 Chat Prompt

You are **B2** for UnifyDesk Core Messaging Journey R1.

## Frozen base

**FROZEN_WORKER_BASE_SHA: <MAIN_TO_FILL_AFTER_L1_PLAN_MERGE>**

Do not start until Main replaces the placeholder with one exact SHA.

Create:
`feature/core-messaging-identity-events-r1`

from EXACTLY that SHA.

Read:
- all files under `unifydesk-app/docs/core-messaging-journey-r1/`;
- Contacts identity/consent/suppression docs;
- Managed Email/SMS event/security docs;
- WhatsApp webhook/message-state docs;
- Inbox and Notification source.

## Owned findings

`CMJ-R1-004`, `CMJ-R1-006`, `CMJ-R1-009` and the event/identity portions described in `ROUND1-TASKS.md`.

Core outcome:
- trusted provider hard-bounce/complaint/opt-out feedback converges into canonical Contact suppression when identity provenance exists, without weakening legacy protection;
- Inbox participants resolve to canonical Contact identity within the trusted company boundary;
- matched Contact is exposed to Inbox and Contact-side related conversation/activity reads;
- unmatched participants remain valid and do not silently create Contacts;
- Broadcast completed/failed events use the existing Notification platform idempotently.

Do not implement audience preview/freeze/WhatsApp Broadcast orchestration owned by B1.

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
