# Billing, Subscription & Payments R1 — FINAL-003 Independent SEC/QA Re-Verification

## 1. Review identity

- Repository: `renukaprasad-ms/UnifyDesk`
- Exact reviewed integrated production SHA: `7848b1b08cb82a832777ed80f34efb675d23f425`
- QA branch: `review/billing-subscription-payments-security-qa-r1-final-003`
- Review type: FINAL narrow independent SEC/QA re-verification
- Production code modified by QA: **NO**
- Rebase performed by QA: **NO**

The QA branch was created directly from the exact frozen integrated production SHA above. Conclusions below are based on the integrated source, not the worker PR description.

## 2. Remediation lineage reviewed

Primary finding:

- `BILL-R1-QA-FINAL-003 — HIGH`

Original finding source:

- QA PR: `#265`
- QA head: `405ffe0f54786e8e4ae850bd4e78f3a431f2d8a2`
- Original reviewed production SHA: `b96793f15d4387f7873abd6f8b9326d42a421e85`

Remediation:

- B1 PR: `#266`
- Branch: `fix/billing-final-scheduled-downgrade-r1`
- Reviewed B1 head: `d1cfc7d63f9bf6ef07f49d265d06b898e968b9f9`
- Integrated production SHA: `7848b1b08cb82a832777ed80f34efb675d23f425`

The complete PR #265 QA report, `FINAL-003-REMEDIATION.md`, and the Billing R1 canonical architecture/contracts were read before disposition.

## 3. FINAL-003 independent source verification

### 3.1 Authoritative scheduled-downgrade invariant

**VERIFIED**

Migration `V95__billing_authoritative_scheduled_downgrade.sql` creates:

`uk_subscription_changes_scheduled_downgrade_boundary`

as a PostgreSQL **partial unique index** over:

`(subscription_id, effective_at)`

where:

`change_type = 'DOWNGRADE' AND status = 'SCHEDULED'`.

Therefore PostgreSQL, not an in-memory/service-only check, is the authoritative cross-replica arbiter. At most one authoritative `DOWNGRADE + SCHEDULED` row can exist for one subscription/effective boundary after V95 is applied.

`SubscriptionChangeService.scheduleDowngrade(...)` also performs a deterministic service-level lookup before insert, but the database invariant remains authoritative for a true persistence race.

### 3.2 First committed winner owns the boundary

**VERIFIED**

The frozen rule is implemented as reject-later, not replacement:

- an existing scheduled owner causes a commercial `ConflictException`;
- no overwrite, mutation, cancellation, supersession, or replacement path is introduced;
- a true concurrent insert race is rejected by the V95 unique index;
- the accepted row remains unchanged.

The real PostgreSQL concurrency regression test opens two database connections and proves only one same-boundary SCHEDULED DOWNGRADE can commit.

### 3.3 Idempotency replay and materially different commands

**VERIFIED**

The company-scoped idempotency lookup runs before the new same-boundary owner check.

Exact replay of the original key returns the canonical existing change without re-entering scheduling.

`assertSameCommand(...)` continues to reject a reused key when the target plan, target PlanPrice, or change type differs.

Existing company scoping remains intact; the same raw idempotency key in another company does not collide.

### 3.4 Persistence uniqueness-race translation

**VERIFIED**

`scheduleDowngrade(...)` catches `DataIntegrityViolationException` only around the scheduled-downgrade persistence.

Translation to the canonical commercial conflict occurs only when the exception/cause chain contains the exact named invariant:

`uk_subscription_changes_scheduled_downgrade_boundary`.

If that exact invariant name is absent, the original integrity exception is rethrown. Unrelated integrity violations are therefore not intentionally masked as FINAL-003 commercial conflicts.

### 3.5 V95 migration safety and ordering

**VERIFIED**

V95 is ordered directly after existing V94 (`V94__billing_zero_due_free_period.sql`).

Before index creation, V95 explicitly checks for legacy duplicate authoritative rows grouped by `subscription_id + effective_at`. If duplicates exist, it raises SQLSTATE `23505` with an explicit FINAL-003 uniqueness message.

The migration:

- does not choose a winner;
- does not delete rows;
- does not rewrite historical commercial state;
- does not mutate prior SubscriptionChange status;
- creates only the intended partial unique index after the explicit legacy duplicate check.

Migration regression coverage advances clean and representative upgraded schemas to V95, validates Hibernate schema compatibility, verifies V94 -> V95 duplicate failure, and verifies index presence.

### 3.6 Defensive multiple-row resolver

**VERIFIED — FAIL CLOSED**

`SubscriptionBoundaryChangeService.resolveScheduledDowngradeForBoundary(...)` retains the defensive conflict behavior.

If impossible/corrupt historical state presents more than one exact-boundary scheduled downgrade, the resolver throws `ConflictException`; it does not select an arbitrary winner.

A stale scheduled downgrade before the authoritative renewal boundary also fails closed.

### 3.7 Canonical renewal convergence and financial ownership

**VERIFIED — NO FINAL-003 SOURCE REGRESSION FOUND**

FINAL-003 does not introduce alternate financial ownership.

Reviewed source/tests continue to preserve:

- immutable PLATFORM BillingPeriod ownership;
- canonical invoice ownership;
- automatic renewal’s one durable subscription/boundary job;
- zero-due scheduled downgrade application without fabricated provider payment;
- provider-confirmed paid renewal;
- renewal Payment/Invoice/BillingPeriod identity;
- recurring CHARGE guard immediately before provider read/submit;
- stale recurring provider command terminalization as local commercial conflict;
- no provider I/O after the commercial guard fails;
- unresolved provider-command recovery using the same command identity.

A normal single scheduled downgrade remains consumed by the existing canonical renewal path and is marked APPLIED only through the accepted boundary transition.

### 3.8 ACTIVE paid entitlement half-open period

**VERIFIED**

`EntitlementService` remains `@Transactional(readOnly = true)` on its public read APIs and does not mutate lifecycle state.

For an ACTIVE subscription classified as paid by its canonical plan/PlanPrice commercial terms:

- `now < currentPeriodEnd` -> entitled;
- `now == currentPeriodEnd` -> expired;
- `now > currentPeriodEnd` -> expired;
- missing `currentPeriodEnd` -> fail closed.

The implementation uses `now.isBefore(currentPeriodEnd)`, so equality is correctly end-exclusive under the Billing R1 half-open `[start,end)` contract.

An expired ACTIVE paid row returns empty immediately and does not fall through to older trial/grace state.

### 3.9 Grant/payment-policy regression review

**VERIFIED — NO CONCRETE REGRESSION FOUND**

The grant/payment-policy contracts were reviewed directly rather than treating Plan price alone as payment truth.

- **FREE/open-ended:** canonical FREE zero-cost subscriptions remain entitled with a null paid-period end.
- **COMPED:** paid manual grants require an explicit real PlanPrice and derive a bounded platform period from its billing cycle.
- **OFFLINE_CONTRACT:** paid manual grants retain the real PlanPrice and use an explicit contract period end when supplied, otherwise the canonical plan cadence boundary.
- **CASHFREE/provider-confirmed paid:** initial activation uses the immutable checkout service-period end; paid renewal advances to the immutable next PLATFORM BillingPeriod.
- **PAST_DUE:** explicit grace handling remains unchanged and remains bounded by `graceEndsAt`.
- No provider payment rows are synthesized for COMPED/OFFLINE_CONTRACT by this remediation.

Accordingly, the new ACTIVE paid expiry guard is compatible with the actual grant contracts inspected on the frozen SHA.

### 3.10 Cross-company isolation/collision

**VERIFIED**

The new uniqueness invariant is intentionally scoped to `subscription_id + effective_at`.

Subscription IDs are the authoritative subscription identity; the scheduling service derives the subscription ID from the company-scoped locked current subscription rather than accepting an arbitrary subscription ID from the downgrade command.

The existing company lock, current-subscription ownership, plan eligibility, company-scoped idempotency key, and checkout/renewal company/subscription ownership checks remain intact.

No new cross-company lookup or mutation path was introduced by FINAL-003.

## 4. Test implementation inspected

The actual integrated test source was inspected for the requested behaviors.

### FINAL-003 focused tests

- same target + different idempotency key -> later request conflicts;
- different target + different idempotency key -> later request conflicts and winner remains unchanged;
- exact downgrade idempotency replay -> canonical existing change returned;
- materially different same-key command -> conflict;
- named persistence uniqueness-race translation -> commercial conflict;
- real PostgreSQL concurrent uniqueness race -> one authoritative row commits;
- V95 failure with legacy duplicate SCHEDULED DOWNGRADE rows;
- partial unique-index presence/predicate;
- impossible duplicate resolver -> fail closed;
- expired ACTIVE paid entitlement -> denied;
- valid current paid entitlement -> allowed;
- exact `currentPeriodEnd` equality -> expired;
- open-ended FREE regression -> allowed.

### Existing Billing R1 regressions inspected

- provider-confirmed scheduled-downgrade renewal;
- zero-due scheduled downgrade;
- provider-confirmed paid renewal and replay;
- automatic renewal preparation/collection;
- recurring CHARGE provider-I/O commercial guard;
- stale commercial state -> local terminalization/no provider call;
- existing unresolved recurring command recovery;
- manual COMPED grant period derivation;
- OFFLINE_CONTRACT explicit bounded period.

The focused unit set does not add a dedicated null-`currentPeriodEnd` paid-entitlement test, but the frozen source branch is direct and fail-closed (`currentPeriodEnd != null && now.isBefore(currentPeriodEnd)`). This is not assessed as a source blocker.

## 5. Automated execution evidence

### B1 GitHub Checks run `37266256132`

Independently re-checked.

All five jobs are marked failure:

- `backend`
- `database`
- `frontend`
- `website`
- `email-provider`

However, every job exposes an empty step list:

`steps: []`

Therefore this run is classified as:

**AUTOMATED EXECUTION UNAVAILABLE**

It is **not** classified as a test PASS and **not** classified as a source/test failure.

The exact integrated production SHA `7848b1b08cb82a832777ed80f34efb675d23f425` has no associated workflow run returned by the repository connector at review time.

### QA environment

This QA session does not have a checked-out UnifyDesk backend plus a working PostgreSQL test service available for execution. The relevant backend/PostgreSQL suites were therefore source-inspected but not executed by QA.

## 6. Finding disposition

### BILL-R1-QA-FINAL-003 — CLOSED — SOURCE VERIFIED

No remaining source blocker was found for FINAL-003 on:

`7848b1b08cb82a832777ed80f34efb675d23f425`

Previously closed Billing R1 findings were reviewed for concrete regression in the affected paths and remain closed.

## 7. Billing R1 source disposition

**SOURCE QA PASS / SOURCE ACCEPTED**

The integrated source now satisfies the FINAL-003 frozen contract and no concrete regression was found in the previously accepted Billing R1 architecture.

This source disposition does not claim runtime/provider completion.

## 8. Provider/runtime verification status

**REQUIRED — NOT COMPLETED BY THIS SOURCE QA**

Cashfree merchant/runtime verification remains a separate dependency, including at minimum:

- merchant-enabled recurring authorization / CHARGE;
- duplicate and out-of-order provider events;
- reconciliation and ambiguous outcomes;
- refunds;
- mandate cancellation/expiry lifecycle;
- signed webhook timestamp behavior.

Existing Managed Email/SES and MSG91 runtime verification dependencies also remain separate where applicable.

## 9. Final release recommendation

**Accept the Billing R1 FINAL-003 source remediation.**

Do not represent Billing R1 as fully production-runtime verified yet.

Overall production release should remain gated on:

1. a working backend/PostgreSQL automated execution environment producing real test results; and
2. the outstanding Cashfree merchant/runtime provider verification.

There is no remaining FINAL-003 source remediation requested by SEC/QA.
