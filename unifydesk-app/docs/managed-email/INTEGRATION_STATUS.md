# Managed Email Integration Status

**Owner:** Lead / Architect

## Current round

Round: 2 — QA remediation planned  
Round-1 integrated QA base: `8708a51c53eb53a5cdca89a205deb7ba7a010bc7`  
Round-1 QA findings merge: `c20b088b6dd9ddd365c0c61f18f3f5144abd0327`  
Round-2 task source: `ROUND2_TASKS.md`

Round-1 QA disposition: **NOT RELEASE-READY**

Release blockers assigned for Round 2:
- ME-SEC-001
- ME-SEC-002
- ME-SEC-003
- ME-SEC-004
- ME-SEC-005
- ME-SEC-006
- ME-SEC-008

Hardening findings also assigned:
- ME-SEC-007
- ME-SEC-009
- ME-SEC-010

## Round-2 workstreams

| Workstream | Branch | Status |
|---|---|---|
| B1 Control Plane hardening | `fix/managed-email-control-plane-r2` | READY_AFTER_PLAN_MERGE |
| B2 Delivery/security remediation | `fix/managed-email-delivery-security-r2` | READY_AFTER_PLAN_MERGE |
| F1 Managed Email privacy UX | `fix/managed-email-ui-privacy-r2` | READY_AFTER_PLAN_MERGE |
| QA/SEC retest | `review/managed-email-security-qa-r2` | BLOCKED_BY_R2_INTEGRATION |

All three implementation workers must start from the exact Round-2 base SHA published by the Architecture Lead after this planning PR is merged.

Do not branch from a later moving dev tip.

## Required completion order

1. Merge this Round-2 planning documentation.
2. Start B1, B2 and F1 concurrently from the exact frozen Round-2 base.
3. Workers open PRs to dev but do not merge them.
4. Architecture Lead reviews and integrates the three PRs.
5. Architecture Lead freezes one integrated Round-2 candidate SHA.
6. QA/SEC retests ME-SEC-001 through ME-SEC-010 on that exact SHA.

See `ROUND2_TASKS.md` for exact ownership and forbidden-file rules.
