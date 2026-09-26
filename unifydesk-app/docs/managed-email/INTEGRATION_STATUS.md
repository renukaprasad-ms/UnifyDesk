# Managed Email Integration Status

**Owner:** Lead / Architect

## Current round

Round: 0 — Architecture frozen, awaiting Lead-doc merge to dev  
Architecture revision: R1-2026-09-26  
Architecture branch: `docs/managed-email-architecture-r1`  
Architecture parent dev SHA: `471dfe619c7e4ae58f4eaf58f78fed608ec4c553`  
Frozen worker base SHA: `1f79c27f580dffcccd3c067614cc46d043822dcb`

The worker base is intentionally the last Lead commit before TASKS.md and this status manifest were finalized. Workers must read the latest TASKS.md from the Lead branch, then create their branches from the exact SHA above after the Lead architecture PR is merged.

## Workstreams

| Workstream | Branch | PR | Status | Base SHA | Notes |
|---|---|---|---|---|---|
| Lead architecture | `docs/managed-email-architecture-r1` | TBD | READY_FOR_REVIEW | `471dfe619c...` | Architecture/contracts/research frozen |
| Backend A / B1 — Control Plane | `feature/managed-email-control-plane` | TBD | BLOCKED_BY_ARCHITECTURE_MERGE | `1f79c27f...` | May start after Lead docs are merged |
| Backend B / B2 — Delivery & Events | `feature/managed-email-delivery-events` | TBD | BLOCKED_BY_ARCHITECTURE_MERGE | `1f79c27f...` | May start in parallel with B1 |
| Frontend / F1 | `feature/managed-email-ui` | TBD | BLOCKED_BY_ARCHITECTURE_MERGE | `1f79c27f...` | Works from frozen HTTP contract |
| Security & QA | `review/managed-email-security-qa-r1` | TBD | BLOCKED_BY_INTEGRATION | integrated Round-1 SHA | Starts only after Lead integration review |

## Architecture checks

- [x] Existing Broadcast code inspected
- [x] Current SES tenant/identity/DKIM/MAIL FROM/DMARC/config-set/event/suppression/quota research completed
- [x] Architecture frozen
- [x] ADRs frozen
- [x] API contracts frozen
- [x] data model frozen
- [x] migration ownership frozen
- [x] shared-file ownership frozen
- [x] QA/security plan defined
- [x] shared AWS SDK prerequisite prepared
- [ ] Lead architecture PR merged to dev

## Round-1 integration checks

- [ ] Backend A merged into integration candidate
- [ ] Backend B merged into integration candidate
- [ ] Frontend merged into integration candidate
- [ ] Lead-owned V58 cross-module constraints applied
- [ ] migrations apply cleanly
- [ ] backend build/tests pass
- [ ] frontend build/tests pass
- [ ] architecture docs match implementation
- [ ] QA/SEC candidate created

## Review checks

- [ ] Security & QA review complete
- [ ] Lead triage complete
- [ ] Round-2 tasks frozen if required

## Important merge rule

No implementation worker merges its own PR.

If dev moves after this architecture branch is merged, workers still use the frozen SHA unless the Architecture Lead explicitly publishes a new Round-1 base. Unrelated new dev changes are integrated by the Lead rather than each worker rebasing independently and creating divergent assumptions.
