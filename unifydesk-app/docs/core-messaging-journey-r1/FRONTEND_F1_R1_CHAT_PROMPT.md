# F1 R1 Chat Prompt

You are **F1** for UnifyDesk Core Messaging Journey R1.

## Frozen base

**FROZEN_WORKER_BASE_SHA: <MAIN_TO_FILL_AFTER_L1_PLAN_MERGE>**

Do not start until Main replaces the placeholder with one exact SHA.

Create:
`feature/core-messaging-ui-r1`

from EXACTLY that SHA.

Read:
- all files under `unifydesk-app/docs/core-messaging-journey-r1/`;
- current Contacts frontend/API contracts;
- current Broadcast frontend;
- Managed Email/SMS UI contracts;
- WhatsApp Inbox/connection UI;
- Inbox frontend and workspace state.

## Owned findings

Frontend portions of `CMJ-R1-001`, `CMJ-R1-002`, `CMJ-R1-006`, `CMJ-R1-007`, `CMJ-R1-008`, `CMJ-R1-010`.

Consume the frozen contracts. Do not invent backend behavior.

Core outcome:
- normal Broadcast flow selects canonical Lists/Segments/bounded Contacts, not a duplicate Broadcast recipient directory;
- eligibility preview and exclusion reasons are clear;
- channel/sender readiness is product-level and provider details are shown only when useful;
- Managed Email is branded as UnifyDesk Managed Email;
- WhatsApp Broadcast composition follows approved template contract;
- progress distinguishes provider accepted/sent from delivered/read;
- Contact <-> Inbox navigation uses matched Contact links;
- unmatched Inbox participant gets an explicit add/create Contact action;
- every journey async request/realtime callback uses a workspace epoch so A -> B -> A cannot reactivate stale A work;
- mobile/responsive flow remains usable.

Legacy Broadcast Group history may remain accessible, but do not make it the primary new audience workflow.

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
