# Billing, Subscription & Payments R1 — FINAL Post-Remediation SEC/QA Re-Verification

## 1. Reviewed frozen production SHA

- Repository: `renukaprasad-ms/UnifyDesk`
- Exact reviewed production SHA: `b96793f15d4387f7873abd6f8b9326d42a421e85`
- QA branch: `review/billing-subscription-payments-security-qa-r1-final-reverify`
- Review type: narrow post-remediation independent SEC/QA re-verification
- Production code modified by QA: **NO**
- Rebase performed by QA: **NO**

This branch was created directly from the exact frozen SHA above. Review conclusions are based on the integrated source, not worker PR descriptions.

## 2. Prior QA lineage

Original QA:
- PR `#259`
- reviewed production SHA `3ee7333b0eab780d9f9d842092b91492ff439fb6`
- disposition: **HOLD — REMEDIATION REQUIRED**

Final QA:
- PR `#262`
- reviewed production SHA `c985a5c32cd899e04ed93297037462f2165e63a2`
- disposition: **HOLD — REMEDIATION REQUIRED**

Final QA blockers at PR #262:
- `BILL-R1-QA-001` — HIGH — residual renewal/downgrade provider-I/O race
- `BILL-R1-QA-FINAL-001` — HIGH — duplicate Cashfree automatic-renewal YAML keys
- `BILL-R1-QA-FINAL-002` — HIGH — open-ended FREE subscription could not use canonical paid-upgrade path

## 3. Remediation lineage reviewed

Lifecycle remediation:
- PR `#264`
- branch `fix/billing-final-lifecycle-qa-r1`
- final worker head `9969b85a36f57bc2a424ee4e54e0eb6d9e093d37`
- merged into `dev` before config remediation

Config remediation:
- PR `#263`
- branch `fix/billing-final-config-qa-r1`
- final worker head `01ed6c8caea9836e14e45baa279a2a9971667b82`
- merge SHA / current reviewed `dev`: `b96793f15d4387f7873abd6f8b9326d42a421e85`

## 4. Prior blocker disposition on the reviewed SHA

### BILL-R1-QA-001 — CLOSED — SOURCE VERIFIED

The prior residual late-downgrade/renewal ownership issue is materially remediated.

Source now:
- treats an already-created next PLATFORM BillingPeriod as the durable commercial owner of that renewal boundary;
- moves a later downgrade to the next valid future boundary instead of rewriting the owned renewal period;
- rejects same-boundary downgrade requests at/after the boundary;
- adds a final recurring CHARGE commercial guard immediately before Cashfree provider I/O;
- compares Payment/Invoice/BillingPeriod/company/subscription/boundary/plan/PlanPrice/amount identity to current accepted scheduled-change state;
- terminalizes stale commands locally as `COMMERCIAL_CONFLICT` and does not call Cashfree;
- preserves idempotent unresolved provider-command recovery.

Relevant source:
- `SubscriptionChangeService.java`
- `SubscriptionCheckoutService.java`
- `RecurringChargeService.java`
- `RecurringChargeStore.java`
- `AutomaticRenewalService.java`

Relevant tests reviewed:
- `SubscriptionChangeFinancialOwnershipTests`
- `RecurringChargeServiceTests`
- `RecurringChargeStoreTests`

### BILL-R1-QA-FINAL-001 — CLOSED — SOURCE VERIFIED

The duplicate Cashfree automatic-renewal YAML keys are removed.

The reviewed `application.yaml` has one canonical copy of:
- `automatic-renewal-claim-seconds`
- `automatic-renewal-interval-ms`
- `automatic-renewal-initial-delay-ms`

Canonical defaults are:
- claim seconds = `300`
- interval ms = `60000`
- initial delay ms = `60000`

A focused Boot config-data regression test now loads the packaged production `application.yaml` and checks Cashfree configuration binding:

- `CashfreeApplicationYamlConfigTests.java`

### BILL-R1-QA-FINAL-002 — CLOSED — SOURCE VERIFIED

Open-ended FREE subscriptions now have an explicit canonical paid-upgrade path.

Source now:
- detects open-ended FREE state;
- quotes FREE -> paid with zero current credit and full target price due;
- freezes one immutable paid service-period snapshot before collection;
- requires canonical paid invoice/provider-confirmed settlement for positive due;
- applies the exact frozen period after confirmation rather than re-anchoring to confirmation time;
- binds one canonical PLATFORM BillingPeriod;
- sets subscription period/billing anchor from the immutable quoted snapshot;
- supports explicit customer cancellation for open-ended FREE without fabricating provider refund/payment activity;
- preserves paid customer immediate-cancellation restrictions.

Relevant source:
- `SubscriptionChangeService.java`
- `UpgradeProrationCalculator.java`
- `CustomerSubscriptionCancellationService.java`

Relevant tests reviewed:
- `FreeToPaidSubscriptionChangeTests`
- `UpgradeProrationFreeLifecycleTests`
- `CustomerSubscriptionCancellationServiceTests`

## 5. New final finding

### BILL-R1-QA-FINAL-003 — HIGH — Multiple scheduled downgrades can terminally conflict renewal and leave ACTIVE entitlement state

**Affected paths**

- `billing/service/SubscriptionChangeService.java`
- `billing/service/SubscriptionBoundaryChangeService.java`
- `billing/repository/SubscriptionChangeRepository.java`
- `billing/payment/AutomaticRenewalService.java`
- `billing/payment/AutomaticRenewalStore.java`
- `billing/service/EntitlementService.java`
- Billing schema/migrations for `subscription_changes`

**Exact source behavior**

`SubscriptionChangeService.scheduleDowngrade(...)` enforces idempotency by company + idempotency key, but it does not enforce a single authoritative SCHEDULED downgrade per subscription/effective boundary.

Two different requests using different idempotency keys can therefore persist two SCHEDULED DOWNGRADE rows for the same subscription and the same future boundary.

There is no database uniqueness constraint over the authoritative scheduled-downgrade boundary identity.

At renewal, `SubscriptionBoundaryChangeService.resolveScheduledDowngradeForBoundary(...)` loads all scheduled downgrades up to the boundary and deliberately throws `ConflictException` when a second exact-boundary scheduled downgrade exists.

`AutomaticRenewalService` catches that conflict and terminally finishes the durable renewal job as `CONFLICT`.

`AutomaticRenewalStore.claimDue(...)` only reclaims `READY`, `RETRY`, or expired `CLAIMED` jobs. A terminal `CONFLICT` job is not retried. `discoverDue(...)` also cannot create a replacement for the same subscription/boundary because the renewal job identity is unique and uses `ON CONFLICT DO NOTHING`.

The subscription itself remains ACTIVE with its old `currentPeriodEnd`.

`EntitlementService.effectiveSubscription(...)` accepts the newest ACTIVE subscription without verifying that its paid current period is still unexpired.

**Failure scenario**

1. Customer has an ACTIVE paid CASHFREE subscription ending at boundary T.
2. Request A schedules downgrade X at T using idempotency key A.
3. Request B schedules downgrade Y at T using a different idempotency key B.
4. Both rows remain SCHEDULED.
5. At T the automatic renewal worker calls the canonical boundary resolver.
6. Resolver finds multiple scheduled downgrades and throws a commercial conflict.
7. Renewal job is terminally marked `CONFLICT`.
8. No replacement/retry job is created for the same boundary.
9. Subscription stays ACTIVE with expired paid-period boundary and no successful renewal transition.
10. Generic entitlement resolution still treats ACTIVE as effective.

This can leave the company with plan entitlements while renewal is terminally blocked.

**Violated frozen contract**

- one authoritative commercial transition per subscription boundary;
- durable scheduler convergence;
- billing conflicts must fail closed without granting an indefinite ACTIVE commercial state;
- duplicate/concurrent customer operations must not deadlock renewal financial truth.

**Required remediation**

Enforce one authoritative pending scheduled downgrade per subscription/effective boundary.

A later request must use one explicit product rule:
- reject the second scheduled downgrade; or
- transactionally replace/cancel the prior scheduled downgrade before persisting the new one.

Back this with a database invariant, not only service-level checks.

Also ensure a commercial conflict cannot leave an expired paid subscription indefinitely ACTIVE. Renewal conflict handling should move the lifecycle into an explicit recoverable/restricted state or otherwise prevent entitlement continuation past the paid period.

**Required regression tests**

At minimum:
- two different idempotency keys, same target, same boundary;
- two different idempotency keys, different targets, same boundary;
- concurrent same-boundary downgrade requests across replicas;
- replacement/cancellation semantics if replacement is the chosen product rule;
- exactly one scheduled downgrade visible to the boundary resolver;
- automatic renewal proceeds after the accepted authoritative change;
- terminal commercial conflict cannot leave expired ACTIVE entitlements usable.

## 6. Tests and execution evidence

Source assertions for the remediation tests listed above were inspected.

GitHub Actions execution remains unavailable.

PR #264 head `9969b85a36f57bc2a424ee4e54e0eb6d9e093d37`:
- Checks run `37183603413`
- backend/frontend/database/website/email-provider jobs failed before execution with `steps: null`

PR #263 head `01ed6c8caea9836e14e45baa279a2a9971667b82`:
- Checks run `37182095282`
- backend/frontend/database/website/email-provider jobs failed before execution with `steps: null`

The exact reviewed integrated SHA `b96793f15d4387f7873abd6f8b9326d42a421e85` currently has no workflow run.

Execution status:

**BLOCKED — EXECUTION ENVIRONMENT**

This runner condition is not itself treated as a source failure. Source-reviewed tests are not marked executed/PASS.

## 7. Provider verification status

**PROVIDER VERIFICATION REQUIRED**

Cashfree sandbox/live execution is still not proven.

After source acceptance, runtime evidence is still required for:
- recurring authorization and CHARGE;
- provider duplicate/out-of-order events;
- reconciliation;
- refunds;
- mandate cancellation/expiry;
- actual signed webhook timestamp behavior;
- SES Managed Email;
- MSG91 SMS delivery/segment/retry behavior.

## 8. Final release disposition

# HOLD — REMEDIATION REQUIRED

Current source disposition:

- `BILL-R1-QA-001` — **CLOSED — SOURCE VERIFIED**
- `BILL-R1-QA-002` — **CLOSED — SOURCE VERIFIED**
- `BILL-R1-QA-003` — **CLOSED — SOURCE VERIFIED**
- `BILL-R1-QA-004` — **CLOSED — SOURCE VERIFIED**
- `BILL-R1-QA-FINAL-001` — **CLOSED — SOURCE VERIFIED**
- `BILL-R1-QA-FINAL-002` — **CLOSED — SOURCE VERIFIED**
- `BILL-R1-QA-FINAL-003` — **HIGH — OPEN**

Billing R1 must remain on HOLD until `BILL-R1-QA-FINAL-003` is remediated and re-verified.

QA did not modify production code.
