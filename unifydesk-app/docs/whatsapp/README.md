# UnifyDesk Meta WhatsApp — Architecture Workspace

Status: ROUND-0 BOOTSTRAP  
Architecture branch: `docs/whatsapp-architecture-r0`  
Bootstrap base: `dev @ 6daf9d89ac3b2c58b794c0b82dc2917d5737caa7`

This folder is the architecture and delivery workspace for making the existing Meta WhatsApp integration production-grade before further Meta Ads work.

## Team

Exactly five engineering chats are used:

- **L1 — Lead Architect / Integration Lead**
- **B1 — Backend Connection / Control Plane Engineer**
- **B2 — Backend Messaging / Webhook / Delivery Engineer**
- **F1 — Frontend + WhatsApp UX Engineer**
- **QA/SEC — QA + Security Engineer**

The Product Manager owns scope. L1 owns technical research, frozen contracts, task decomposition, integration, and QA/SEC handoff.

## Workflow

1. L1 performs repository research and current official Meta research.
2. L1 writes/finalizes architecture, contracts, security model, data model, performance model, and executable worker tasks.
3. L1 freezes one exact immutable Round-1 worker base SHA.
4. B1, B2, and F1 branch independently from that same SHA. They must not wait for each other or depend on another worker's new code.
5. L1 reviews and integrates all three worker branches.
6. L1 publishes one exact immutable integrated SHA.
7. QA/SEC branches from that integrated SHA and performs the full security/QA plan.
8. QA/SEC writes findings with severity, reproduction, evidence, owner, and regression requirements.
9. L1 creates targeted remediation tasks/prompts for only the affected workers.
10. Repeat until QA/SEC passes.

Do not branch workers from moving `dev`. Do not ask a worker to cherry-pick another worker merely to compile.

## Bootstrap files

- `PROJECT_BRIEF.md` — product goal, known current state, scope and definition of done.
- `TEAM_WORKFLOW.md` — mandatory multi-chat workflow and independence rules.
- `LEAD_CHAT_PROMPT.md` — complete L1 assignment.

L1 will create the full Round-0 architecture set and all worker prompts after research.

## Scope boundary

This round is **Meta WhatsApp only**.

Meta Ads code may be inspected only when a shared Meta application/configuration concern could affect WhatsApp. Do not implement or redesign Ads in this project. Ads will be handled after WhatsApp reaches the agreed production-ready state.
