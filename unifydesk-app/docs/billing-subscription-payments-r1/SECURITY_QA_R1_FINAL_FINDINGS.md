# Billing, Subscription & Payments R1 — FINAL Independent Security / Production-Readiness Re-Verification

## 1. Exact reviewed production SHA

- Repository: `renukaprasad-ms/UnifyDesk`
- Frozen integrated production-code SHA: `c985a5c32cd899e04ed93297037462f2165e63a2`
- QA branch: `review/billing-subscription-payments-security-qa-r1-final`
- Review type: final narrow independent SEC/QA re-verification
- Production source modified by QA: **NO**
- Rebase performed by QA: **NO**

The QA branch was created directly from the exact frozen SHA above. Worker descriptions were not accepted as proof; the combined integrated source was reviewed directly.

## 2. Original QA findings

Original QA:
- PR: `#259`
- branch: `review/billing-subscription-payments-security-qa-r1`
- head: `323636e6e4d5d1d38248fce8ca7210ecffa5853b`
- reviewed production SHA: `3ee7333b0eab780d9f9d842092b91492ff439fb6`
- original release disposition: **HOLD — REMEDIATION REQUIRED**

Original findings:
- `BILL-R1-QA-001` — HIGH — scheduled downgrade and paid renewal were independent boundary owners.
- `BILL-R1-QA-002` — HIGH — PAST_DUE grace permitted new Managed Email/SMS financial exposure.
- `BILL-R1-QA-003` — MEDIUM — ACTIVE financial pricing rules could have null `effectiveFrom`.
- `BILL-R1-QA-004` — LOW — Cashfree webhook timestamp had no bounded freshness check.

The original `SECURITY_QA_R1_FINDINGS.md` was read from the original QA head because it is QA-branch documentation rather than production content in the frozen remediation SHA.

## 3. Remediation lineage

### B1

- PR: `#261`
- branch: `fix/billing-commercial-lifecycle-qa-r1`
- final worker head: `c1b8897c72acbebf2ae0b70a62650b228f08234e`
- integration SHA: `fa94bc89cd2471c3af08153f538ce43a416ddff1`
- owned: `BILL-R1-QA-001`, `BILL-R1-QA-003`, including paid -> FREE / zero-due boundary correction.

### B2

- PR: `#260`
- branch: `fix/billing-managed-exposure-webhook-qa-r1`
- final worker head: `8a0ad979dc153bc720daf18ba8285fa9d6048907`
- integrated after B1
- owned: `BILL-R1-QA-002`, `BILL-R1-QA-004`.

### Final integrated source

`c985a5c32cd899e04ed93297037462f2165e63a2`

The review below is against this combined SHA, not the worker branches independently.

## 4. BILL-R1-QA-001 disposition

# NOT CLOSED — HIGH

The main remediation direction is materially improved and closes the original independent-worker application path, but one provider-facing race remains.

### Source-accepted parts

The final integrated source now correctly provides all of the following:

- `SubscriptionChangeService.applyDueDowngrades(...)` explicitly refuses to independently apply CASHFREE downgrades.
- `SubscriptionChangeRepository.findDueDowngradesForUpdate(...)` excludes CASHFREE subscriptions in the SQL claim.
- `SubscriptionBoundaryChangeService` locks and resolves at most one exact scheduled downgrade for the subscription's canonical boundary and rejects stale-before-boundary changes.
- `SubscriptionCheckoutService.createOrResolveRenewalInvoice(...)` resolves the exact scheduled target before creating a new canonical next-period financial snapshot.
- the target Plan and PlanPrice are frozen into the next `BillingPeriod`;
- target PlanPrice is revalidated as same-plan, ACTIVE, INR, explicitly effective and half-open-effective at the boundary;
- immutable pre-created wrong-plan `BillingPeriod` state is not rewritten;
- `SubscriptionLifecycleService.renewAfterConfirmedCollection(...)` requires a PAID canonical platform invoice before a positive-due boundary transition;
- PENDING/FAILED payment does not advance the target subscription;
- wrong-plan paid invoice cannot activate a scheduled target;
- paid confirmation cannot activate a zero-due target;
- successful verified payment applies the matching boundary transition once and marks the scheduled downgrade APPLIED once;
- replay against the already-applied same period converges without a second transition;
- durable automatic-renewal jobs use unique `(subscription_id,due_boundary)` identity and `FOR UPDATE SKIP LOCKED` claim leasing.

### Paid -> FREE / zero-due source-accepted parts

For the automatic boundary path:

- exact target PlanPrice is checked as ACTIVE, INR, same-plan and effective at the boundary;
- FREE target produces an open-ended FREE `BillingPeriod`;
- zero-cost non-FREE target retains its canonical monthly/yearly end;
- canonical zero-value platform invoice is produced;
- automatic renewal does not create a Payment for zero due;
- it does not create a PaymentAttempt;
- it does not run recurring CHARGE;
- it does not enter dunning solely because the invoice is zero;
- target Plan/PlanPrice and SubscriptionChange application occur through the locked boundary transition;
- durable renewal job reaches `ZERO_DUE_APPLIED`;
- replay after committed zero-due application finishes the same durable job without double application;
- an existing Payment on a zero-due job fails closed;
- an old/wrong paid invoice cannot silently activate the FREE target.

### Remaining source defect

**Affected paths**

- `billing/controller/BillingController.java`
- `billing/payment/BillingPaymentController.java`
- `billing/payment/BillingPaymentOrchestrator.java`
- `billing/service/SubscriptionChangeService.java`
- `billing/service/SubscriptionCheckoutService.java`
- `billing/service/BillingPeriodService.java`
- `billing/payment/AutomaticRenewalService.java`
- `billing/payment/RecurringChargeService.java`
- `billing/payment/PaymentConfirmationService.java`

**Exact source behavior**

`scheduleDowngrade(...)` locks the company/subscription while it writes the scheduled change, but it does not reject a new downgrade when the same next boundary already has durable renewal financial truth (BillingPeriod / Invoice / Payment / recurring charge command). It also does not require `requestedAt < currentPeriodEnd`.

The renewal invoice transaction and the later provider-collection operations are separate transactions. Therefore the company/subscription lock serializes invoice creation versus downgrade creation only while those individual transactions overlap; it does not keep the commercial boundary frozen through provider collection.

The customer `POST /billing/payments/renewal` endpoint can also create the next renewal invoice/payment before the due boundary.

**Failure scenario**

1. The current subscription has next boundary `T`.
2. Renewal preparation wins first and freezes an old-plan BillingPeriod/invoice for `T`. This can happen through the customer renewal endpoint before `T`, or by the automatic worker at `T`.
3. Before the provider-facing renewal flow is fully complete, the customer schedules a downgrade. `scheduleDowngrade(...)` still accepts it and records it with `effectiveAt = T`.
4. If provider collection is already underway, the old-plan amount can be collected externally.
5. Later source correctly refuses to rewrite the old immutable period/invoice for the new downgrade and the lifecycle application fails closed — but that fail-closed point can occur **after provider financial exposure/collection**.
6. For a pre-due successfully paid renewal, a later downgrade can be accepted for the already-paid boundary; at the boundary the target invoice creation conflicts, leaving the customer with old-plan paid financial truth and an unapplied scheduled change.

This does not satisfy the production contract that the scheduled boundary target must own the canonical renewal financial snapshot before provider collection and that stale renewal financial state cannot result in a wrong-plan collection.

### Required remediation

Choose one authoritative policy and enforce it transactionally:

- once any durable renewal financial command for boundary `T` exists, reject a new downgrade targeting `T` and schedule it only for the next valid boundary; **or**
- make the existing not-yet-provider-submitted renewal command safely supersedable before provider I/O, with a durable state transition that proves no old-plan provider command can execute.

In all cases, immediately before provider recurring CHARGE, revalidate that the Payment/Invoice/BillingPeriod still matches the authoritative scheduled boundary commercial state. A newly scheduled change must never allow an already-stale old-plan recurring command to reach Cashfree.

### Required regression tests

- pre-due renewal invoice/payment prepared first, then downgrade requested before boundary;
- pre-due renewal successfully paid first, then downgrade requested;
- automatic worker freezes old-plan invoice at boundary, then downgrade is requested before recurring CHARGE;
- downgrade transaction first, renewal transaction second;
- provider command must never use old-plan amount after an accepted downgrade;
- stale financial state must fail before provider I/O, not merely at lifecycle application.

## 5. BILL-R1-QA-002 disposition

# CLOSED — SOURCE VERIFIED

### Canonical current-subscription selector

`SubscriptionLifecycleService.currentOptional(companyId)` uses exactly:

- ACTIVE
- TRIALING
- PAST_DUE
- SUSPENDED

and excludes terminal CANCELLED/EXPIRED history.

The database retains the partial unique current-subscription index for those same four statuses, and the locking selector uses the same current-state set. This prevents stale terminal history from outranking the canonical current subscription.

### Managed Email

Source now enforces the managed financial boundary at all required stages:

- `ManagedEmailLaunchGuard`: managed-only launch calls `ManagedFinancialExposureGuard.assertAllowed` before managed quota/provider work.
- `ManagedEmailProviderBudgetService.reserveForSend`: rechecks before provider-budget reservation and usage reservation.
- `ManagedEmailUsageService.reserve`: independently rechecks before creating the usage reservation.
- `BroadcastDeliveryService.deliverManaged`: rechecks immediately before SES transport `send(...)`.
- if Billing moves ACTIVE -> PAST_DUE after reservation but before provider I/O, the reservation is released and SES is not called.
- once provider acceptance actually happened, the commit path remains financially recordable and is not incorrectly suppressed by a later status transition.

### Managed SMS

- `ManagedSmsLaunchGuard`: managed-only launch checks exposure before managed runtime/quota work.
- `ManagedSmsUsageService.reserve`: independently rechecks before durable reservation.
- `ManagedSmsDeliveryCoordinator`: rechecks immediately before setting provider-submission-started and calling MSG91.
- an ACTIVE -> PAST_DUE race after reservation releases the reservation and does not call the gateway.
- provider-accepted or unknown-outcome requests remain recordable/held rather than being incorrectly erased after real provider exposure.

### State policy

- ACTIVE: allowed.
- TRIALING: allowed, preserving the existing R1 policy.
- PAST_DUE: blocked for net-new managed provider spend.
- SUSPENDED: blocked.
- CANCELLED / EXPIRED / no current subscription: fail closed.
- CUSTOMER_OWNED execution bypasses the Managed financial guard and remains outside Managed PAYG.
- `UsageBillingService` still refuses to financially rate CUSTOMER_OWNED usage.

No remaining source defect was found for `BILL-R1-QA-002`.

## 6. BILL-R1-QA-003 disposition

# CLOSED — SOURCE VERIFIED

The final source now closes the original null-effective-time defect.

### Create boundary

All three create DTOs require `effectiveFrom` with backend Bean Validation:

- `CreatePlanPriceRequest`
- `CreateRateCardRequest`
- `CreateCompanyRateOverrideRequest`

### Independent service validation

`BillingCatalogService` independently calls `FinancialEffectiveTime.requireExplicitRange(...)` for ACTIVE create paths. Update paths call status-aware financial interval validation, so service correctness does not depend solely on DTO validation.

### Malformed stored ACTIVE rows

`FinancialEffectiveTime.assertStoredActiveRange(...)` converts a null start or invalid interval into a controlled `ConflictException`.

`PricingResolver.matches(...)` validates a dimension-matching ACTIVE rule before interval evaluation, preventing the original null dereference.

Catalog overlap and publish/select paths likewise fail closed on malformed ACTIVE rows rather than silently substituting an unsafe financial start.

### Half-open interval

`FinancialEffectiveTime.includes(...)` implements:

`[effectiveFrom, effectiveTo)`

Therefore:
- exact `effectiveFrom`: included;
- exact `effectiveTo`: excluded.

### Free / zero-cost PlanPrice selection

`selectFreeSubscription(...)` now requires the selected zero-cost price to be:

- from the same plan query;
- ACTIVE;
- INR;
- exactly the plan's billing cycle;
- explicitly started;
- effective at the canonical selection time;
- not expired.

Malformed ACTIVE rows fail closed. Multiple effective zero-cost matches also fail closed.

No remaining source defect was found for `BILL-R1-QA-003`.

## 7. BILL-R1-QA-004 disposition

# CLOSED — SOURCE VERIFIED

`CashfreeWebhookVerifier` now adds bounded freshness without weakening signature semantics.

Verified source behavior:

- exact raw request bytes remain part of the HMAC input;
- the exact original `x-webhook-timestamp` string is used in HMAC calculation;
- timestamp parsing does not normalize the signed string;
- HMAC remains SHA-256;
- signature comparison uses `MessageDigest.isEqual`;
- malformed Base64/signatures fail closed;
- non-numeric timestamps fail closed;
- `Long.parseLong` overflow fails closed;
- implausible epoch values fail closed;
- stale signed timestamps fail;
- excessive future timestamps fail;
- exact configured past/future boundaries are accepted;
- numeric timestamp is interpreted as epoch-seconds and epoch-milliseconds independently and accepted only when **exactly one** interpretation lies in the freshness window;
- arithmetic/date conversion exceptions fail closed.

Production defaults remain:

- max past age: `300s`;
- future skew: `60s`.

Configuration fails closed when:
- past age <= 0 or > 3600s;
- future skew <= 0 or > 600s.

### Webhook idempotency regression check

Freshness remains additive to the existing financial protections:

- verified raw payload is durably stored before financial processing;
- durable event identity is based on provider + raw-payload hash;
- exact fresh duplicate insert uses `ON CONFLICT ... DO NOTHING`;
- retry worker claims with `FOR UPDATE SKIP LOCKED` and claim leases;
- processed events are not reclaimed;
- Payment success remains monotonic against stale later failure;
- recurring attempts keep stable command/provider identities;
- reconciliation independently confirms provider truth and does not fabricate success.

No remaining source defect was found for `BILL-R1-QA-004`.

## 8. New final findings

### BILL-R1-QA-FINAL-001 — HIGH — Combined SHA contains duplicate Spring Boot YAML keys and cannot safely load `application.yaml`

**Affected path**

- `unifydesk-app/backend/src/main/resources/application.yaml`

**Exact source behavior**

The final combined SHA contains duplicate keys in the same `app.billing.cashfree` mapping:

- `automatic-renewal-claim-seconds` appears twice;
- `automatic-renewal-interval-ms` appears twice, with conflicting defaults `60000` and `300000`;
- `automatic-renewal-initial-delay-ms` appears twice.

The backend parent is Spring Boot `4.1.1`.

Spring Boot 4.1.1's `OriginTrackedYamlLoader` constructs SnakeYAML `LoaderOptions` and explicitly calls:

`loaderOptions.setAllowDuplicateKeys(false)`

Therefore this is not a benign last-value-wins configuration. Loading the packaged YAML is expected to fail on duplicate mapping keys before normal application startup/config binding.

**Failure scenario**

The remediated integrated backend is deployed with the final `application.yaml`. Boot config loading encounters the duplicate Cashfree automatic-renewal keys and rejects the YAML, preventing normal backend startup. Because worker CI never executed any steps, this integration-only failure was not exercised.

**Required remediation**

Keep exactly one copy of each automatic-renewal property with the intended canonical defaults. Add a configuration-load/application-context smoke test that loads the production `application.yaml`.

**Required regression test**

A backend startup/config-data test must parse the exact packaged `application.yaml` under Spring Boot 4.1.1 and prove no duplicate-key/config-binding failure.

---

### BILL-R1-QA-FINAL-002 — HIGH — Open-ended FREE period is not compatible with the existing ACTIVE -> paid upgrade path

**Affected paths**

- `billing/service/SubscriptionLifecycleService.java`
- `billing/service/SubscriptionChangeService.java`
- `billing/service/UpgradeProrationCalculator.java`
- `billing/service/BillingCatalogService.java`
- `frontend/src/pages/company/CompanyPlan.tsx`

**Exact source behavior**

The final zero-due correction intentionally applies a FREE scheduled target with:

- `currentPeriodStart = boundary`
- `currentPeriodEnd = null`

which is consistent with V94's open-ended FREE period semantics.

However, `SubscriptionChangeService.requestUpgrade(...)` is still the authoritative ACTIVE -> ACTIVE paid-plan transition. It always calls `UpgradeProrationCalculator.quote(...)` with the current subscription period.

`UpgradeProrationCalculator.quote(...)` rejects any current subscription whose `currentPeriodEnd == null` with:

`A valid current service period is required`

There is no alternative paid initial-checkout path for a company that already has a current ACTIVE subscription; the initial paid checkout contract rejects an existing current subscription and directs plan changes through upgrade/downgrade.

The frontend still presents `Upgrade now` for non-current plans when a subscription exists, including the open-ended FREE state.

Additionally, customer period-end cancellation requires a non-null `currentPeriodEnd`; customer immediate cancellation remains unsupported. This makes the open-ended FREE lifecycle inconsistent with the generic customer cancellation contract as well.

**Failure scenario**

A paid company downgrades successfully to FREE using the newly remediated zero-due boundary. The subscription is now ACTIVE with the expected open-ended FREE period. Later the company chooses a paid plan. The only canonical ACTIVE -> ACTIVE upgrade endpoint reaches the proration calculator and rejects the null period end, so the company cannot self-service upgrade back to paid.

This is a production regression caused by introducing valid open-ended FREE current periods without adapting downstream lifecycle operations to that nullable contract.

**Required remediation**

Define and implement canonical FREE -> paid upgrade semantics. Because FREE has no prepaid remaining-period credit, the backend must derive the authoritative paid target period/amount without pretending a bounded FREE period exists, while retaining provider-confirmed activation for positive due amounts.

Also define the customer cancellation behavior for an open-ended FREE subscription (for example, an explicit zero-liability customer cancellation path) rather than leaving the account unable to use period-end or immediate customer cancellation.

**Required regression tests**

- paid -> FREE zero-due downgrade -> later FREE -> paid upgrade succeeds through canonical provider-confirmed flow;
- initial FREE subscription -> paid upgrade;
- FREE -> paid target PlanPrice remains ACTIVE/INR/effective and backend-authoritative;
- no fake current-period proration/credit is created for an open-ended FREE period;
- customer cancellation behavior for open-ended FREE is explicitly tested.

## 9. Source paths reviewed

Architecture/contracts read from `unifydesk-app/docs/billing-subscription-payments-r1/` include:

- `README.md`
- `PRODUCT-BILLING-MODEL.md`
- `CANONICAL-CONTRACTS.md`
- `STATE-MACHINES.md`
- `PLAN-CATALOG-AND-VISIBILITY.md`
- `USAGE-METERING-AND-RATING.md`
- `SECURITY-AND-IDEMPOTENCY.md`
- `CASHFREE-CAPABILITY-MATRIX.md`
- `CURRENT-ARCHITECTURE.md`
- original `SECURITY_QA_R1_FINDINGS.md`
- `B2_PROVIDER_VERIFICATION_NOTES.md`.

Production paths reviewed include, at minimum:

### Billing boundary / renewal
- `SubscriptionBoundaryChangeService.java`
- `SubscriptionChangeService.java`
- `SubscriptionCheckoutService.java`
- `SubscriptionLifecycleService.java`
- `BillingPeriodService.java`
- `InvoiceService.java`
- `AutomaticRenewalService.java`
- `AutomaticRenewalStore.java`
- `PaymentConfirmationService.java`
- `PaymentCollectionService.java`
- `PaymentCollectionStore.java`
- `BillingPaymentOrchestrator.java`
- `RecurringChargeService.java`
- `RecurringChargeStore.java`
- `SubscriptionChangeRepository.java`
- `BillingPeriodRepository.java`
- `CompanySubscriptionRepository.java`
- `BillingPeriod.java`
- `V94__billing_zero_due_free_period.sql`.

### Managed financial exposure
- `ManagedFinancialExposureGuard.java`
- `ManagedEmailLaunchGuard.java`
- `ManagedEmailDeliveryGuard.java`
- `ManagedEmailProviderBudgetService.java`
- `ManagedEmailUsageService.java`
- `BroadcastDeliveryService.java`
- `ManagedSmsLaunchGuard.java`
- `ManagedSmsDeliveryGuard.java`
- `ManagedSmsDeliveryCoordinator.java`
- `ManagedSmsUsageService.java`.

### Effective-time / pricing
- `CreatePlanPriceRequest.java`
- `CreateRateCardRequest.java`
- `CreateCompanyRateOverrideRequest.java`
- `BillingCatalogService.java`
- `FinancialEffectiveTime.java`
- `PricingResolver.java`
- PlanPrice/RateCard/CompanyRateOverride entities and repositories.

### Cashfree / webhooks
- `CashfreeWebhookVerifier.java`
- `CashfreeProperties.java`
- `CashfreeWebhookController.java`
- `CashfreeWebhookService.java`
- `PaymentReconciliationService.java`
- `PaymentConfirmationService.java`
- `DunningService.java`
- `application.yaml`.

### Frontend / FREE lifecycle
- `frontend/src/pages/company/CompanyPlan.tsx`
- related Billing model/API paths previously accepted in R1.

For build-compatibility verification, the repository's `pom.xml` was checked and pins Spring Boot `4.1.1`; the corresponding Spring Boot 4.1.1 YAML loader source was inspected to verify duplicate-key behavior.

## 10. Tests reviewed

Assertions/test bodies were inspected for:

- `SubscriptionBoundaryChangeServiceTests`
- `SubscriptionChangeServiceTests`
- `SubscriptionCheckoutServiceTests`
- `SubscriptionLifecycleServiceTests`
- `BillingPeriodServiceTests`
- `InvoiceServiceTests`
- `AutomaticRenewalServiceTests`
- `UpgradeProrationCalculatorTests`
- `FinancialEffectiveTimeTests`
- `BillingCatalogServiceTests`
- `BillingCatalogAuditTests`
- `PricingResolverTests`
- `ManagedFinancialExposureGuardTests`
- `ManagedEmailLaunchGuardTests`
- `ManagedEmailProviderBudgetServiceDatabaseTests`
- `ManagedEmailDeliveryMeteringTests`
- `ManagedEmailUsageServiceDatabaseTests`
- `ManagedSmsLaunchGuardRound2Tests`
- `ManagedSmsDeliveryCoordinatorTests`
- `ManagedSmsUsageServiceDatabaseTests`
- `CashfreeWebhookVerifierTests`
- `CashfreeWebhookRecoveryTests`
- `PaymentConfirmationServiceTests`
- `DunningServiceTests`
- `PlatformMigrationHardeningTests`.

The test suite contains strong focused coverage for the intended remediations, but it does **not** cover:
- accepting a downgrade after renewal financial truth has already been frozen/provider collection may be underway;
- FREE -> paid upgrade after an open-ended FREE current period;
- production `application.yaml` duplicate-key loading.

## 11. Execution evidence

### Final integrated SHA

`c985a5c32cd899e04ed93297037462f2165e63a2` has no GitHub Actions workflow run.

### Worker heads

B1 head `c1b8897c72acbebf2ae0b70a62650b228f08234e`:
- Checks run `37179096085`
- backend/frontend/database/website/email-provider all concluded failure with **`steps: null`**.

B2 head `8a0ad979dc153bc720daf18ba8285fa9d6048907`:
- Checks run `37179551495`
- backend/frontend/database/website/email-provider all concluded failure with **`steps: null`**.

No job step executed in those runs.

Therefore test/build execution evidence is:

# BLOCKED — EXECUTION ENVIRONMENT

This runner state is not itself counted as a source failure. Conversely, source-reviewed tests are not marked executed/PASS.

Blocked execution evidence includes:

- backend compile/startup;
- production YAML/config loading;
- backend unit/integration tests;
- PostgreSQL/Flyway V94 migration execution;
- Hibernate V94 schema validation;
- multi-replica renewal/downgrade race execution;
- frontend tests;
- end-to-end Billing/payment flows.

## 12. CI environment status

**BLOCKED — EXECUTION ENVIRONMENT**

The observed worker pattern remains the previously documented pre-step runner failure (`steps: null`). The final integrated SHA itself has no run.

Because actual source blockers are present, the release disposition remains HOLD rather than changing to the execution-only BLOCKED disposition.

## 13. Provider verification status

# PROVIDER VERIFICATION REQUIRED

No Cashfree sandbox/live runtime evidence was present in this final source re-verification.

Still required after source remediation/final source acceptance:

- one-time Cashfree payment success/failure/user-drop;
- recurring/mandate authorization success/failure/pending;
- exact-amount recurring CHARGE;
- duplicate and out-of-order webhook delivery;
- actual signed timestamp unit/freshness behavior;
- payment/subscription reconciliation;
- full/partial refund;
- mandate cancellation/expiry;
- merchant-enabled recurring rail constraints/retries;
- SES Managed Email acceptance/idempotency behavior;
- MSG91 authoritative SMS segment/duplicate/retry behavior.

Source correctness must not be labeled provider-runtime verified.

## 14. Final release disposition

# HOLD — REMEDIATION REQUIRED

Original finding dispositions:

| Finding | Original severity | Final disposition |
|---|---:|---|
| `BILL-R1-QA-001` | HIGH | **NOT CLOSED — HIGH** — late downgrade can be accepted after renewal financial truth is frozen, allowing stale old-plan provider collection |
| `BILL-R1-QA-002` | HIGH | **CLOSED — SOURCE VERIFIED** |
| `BILL-R1-QA-003` | MEDIUM | **CLOSED — SOURCE VERIFIED** |
| `BILL-R1-QA-004` | LOW | **CLOSED — SOURCE VERIFIED** |

New final findings:

| Finding | Severity | Summary |
|---|---:|---|
| `BILL-R1-QA-FINAL-001` | HIGH | duplicate Cashfree automatic-renewal YAML keys are rejected by Spring Boot 4.1.1 config loading |
| `BILL-R1-QA-FINAL-002` | HIGH | valid open-ended FREE subscription cannot use the canonical paid-upgrade path |

### Exact remediation required before another final SEC/QA

1. Close the remaining `BILL-R1-QA-001` race by making accepted scheduled-change state and renewal provider commands mutually consistent before provider I/O.
2. Remove duplicate `application.yaml` Cashfree automatic-renewal keys and add a packaged-config startup/load regression test.
3. Implement explicit open-ended FREE -> paid upgrade semantics (and explicit customer cancellation behavior for open-ended FREE) with regression coverage.
4. Restore executable CI and run the focused remediation suite plus Billing R1 regression suite.
5. After source acceptance, execute the Cashfree/SES/MSG91 runtime verification matrix.
