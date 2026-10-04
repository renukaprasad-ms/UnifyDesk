# Billing, Subscription & Payments R1 — Independent Security / Production-Readiness QA

## 1. Reviewed frozen SHA

- Repository: `renukaprasad-ms/UnifyDesk`
- Frozen integrated production-code SHA: `3ee7333b0eab780d9f9d842092b91492ff439fb6`
- QA branch: `review/billing-subscription-payments-security-qa-r1`
- Review type: independent SEC/QA source review
- Production code modified by QA: **NO**

This review was performed against the exact frozen SHA above. PR descriptions were not treated as proof.

## 2. Review scope

The review covered Billing R1 commercial catalog and visibility, subscription lifecycle, initial checkout, upgrade/downgrade, renewal, cancellation, dunning, BillingPeriod and Invoice truth, Cashfree collection/webhook/reconciliation/refunds, Managed Email/SMS financial usage, historical pricing, prior-period adjustments, financial retention, backend tenant/authority controls, and F1 Billing/payment-return/admin workspace behavior.

Frozen product decisions supplied by MAIN were treated as authoritative.

## 3. Architecture / contracts read

Read completely from the frozen tree under `unifydesk-app/docs/billing-subscription-payments-r1/`:

- `README.md`
- `CURRENT-ARCHITECTURE.md`
- `CURRENT-STATE-AUDIT.md`
- `PRODUCT-BILLING-MODEL.md`
- `PLAN-CATALOG-AND-VISIBILITY.md`
- `END-TO-END-FLOW.md`
- `CANONICAL-CONTRACTS.md`
- `STATE-MACHINES.md`
- `CASHFREE-CAPABILITY-MATRIX.md`
- `USAGE-METERING-AND-RATING.md`
- `SECURITY-AND-IDEMPOTENCY.md`
- `GAP-MATRIX.md`
- `WORK-BREAKDOWN.md`
- `MAIN-DECISIONS-REQUIRED.md`
- `F1_BACKEND_CONTRACT_GAPS.md`

Architecture lineage was also checked against PR #254, B1 PR #255, B2 PR #257, F1 PR #258, and the final frozen integration SHA.

## 4. Production code paths reviewed

Representative production paths read directly at the frozen SHA include:

### Commercial core / lifecycle
- `billing/controller/BillingController.java`
- `billing/controller/BillingCommercialAdminController.java`
- `billing/controller/AdminBillingController.java`
- `billing/service/BillingCatalogService.java`
- `billing/service/PlanEligibilityService.java`
- `billing/service/PlanAssignmentService.java`
- `billing/service/PlanChangeClassifier.java`
- `billing/service/SubscriptionCheckoutService.java`
- `billing/service/SubscriptionChangeService.java`
- `billing/service/SubscriptionLifecycleService.java`
- `billing/service/BillingLifecycleScheduler.java`
- `billing/service/BillingPeriodService.java`
- `billing/service/InvoiceService.java`
- `billing/service/UpgradeInvoiceService.java`
- `billing/service/UpgradeProrationCalculator.java`
- `billing/service/EntitlementService.java`

### Payments / Cashfree
- `billing/payment/BillingPaymentController.java`
- `billing/payment/BillingPaymentOrchestrator.java`
- `billing/payment/PaymentCollectionService.java`
- `billing/payment/PaymentCollectionStore.java`
- `billing/payment/PaymentConfirmationService.java`
- `billing/payment/CashfreePaymentGateway.java`
- `billing/payment/CashfreeProperties.java`
- `billing/payment/CashfreeWebhookController.java`
- `billing/payment/CashfreeWebhookVerifier.java`
- `billing/payment/CashfreeWebhookService.java`
- `billing/payment/PaymentReconciliationService.java`
- `billing/payment/RecurringAuthorizationService.java`
- `billing/payment/RecurringAuthorizationStore.java`
- `billing/payment/RecurringChargeService.java`
- `billing/payment/RecurringChargeStore.java`
- `billing/payment/AutomaticRenewalService.java`
- `billing/payment/AutomaticRenewalStore.java`
- `billing/payment/RenewalBoundaryService.java`
- `billing/payment/DunningService.java`
- `billing/payment/RefundService.java`
- `billing/payment/RefundStore.java`
- `billing/payment/RefundReconciliationService.java`

### Managed PAYG
- `billing/service/PricingResolver.java`
- `billing/service/UsageBillingService.java`
- `billing/service/ManagedUsageInvoiceService.java`
- `managedemail/delivery/ManagedEmailLaunchGuard.java`
- `managedemail/delivery/ManagedEmailDeliveryGuard.java`
- `managedemail/metering/ManagedEmailUsageService.java`
- `managedemail/metering/ManagedEmailUsageReconciler.java`
- `managedsms/delivery/ManagedSmsLaunchGuard.java`
- `managedsms/delivery/ManagedSmsDeliveryGuard.java`
- `managedsms/delivery/ManagedSmsDeliveryCoordinator.java`
- `managedsms/metering/ManagedSmsSegmentCalculator.java`
- `managedsms/metering/ManagedSmsUsageService.java`
- `managedsms/metering/ManagedSmsUsageReconciler.java`

### Schema / retention / frontend
- Billing migrations `V86` through `V93`
- `lifecycle/service/EntityPurgeService.java`
- `frontend/src/features/billing/billingApi.ts`
- `frontend/src/features/billing/billingModel.ts`
- `frontend/src/features/billing/cashfreeCheckout.ts`
- `frontend/src/features/billing/pendingCheckout.ts`
- `frontend/src/features/platformBilling/billingAdminModel.ts`
- `frontend/src/pages/company/CompanyPlan.tsx`
- `frontend/src/pages/billing/PaymentReturnPage.tsx`
- `frontend/src/pages/platform/PlatformPlanDetails.tsx`
- `frontend/src/routes/AppRoutes.tsx`
- `frontend/src/components/workspace/WorkspaceEpochBoundary.tsx`
- `frontend/src/features/workspace/useWorkspaceEpochScope.ts`

## 5. Test paths reviewed

Assertions were read, not inferred from filenames. Reviewed tests include:

- `BillingCatalogServiceTests`
- `BillingCatalogAuditTests`
- `PlanEligibilityServiceTests`
- `PlanAssignmentServiceTests`
- `PlanChangeClassifierTests`
- `SubscriptionCheckoutServiceTests`
- `SubscriptionChangeServiceTests`
- `SubscriptionLifecycleServiceTests`
- `BillingPeriodServiceTests`
- `InvoiceServiceTests`
- `UpgradeInvoiceServiceTests`
- `UpgradeProrationCalculatorTests`
- `PaymentCollectionServiceTests`
- `PaymentConfirmationServiceTests`
- `CashfreeWebhookVerifierTests`
- `CashfreeWebhookRecoveryTests`
- `RecurringAuthorizationServiceTests`
- `RecurringChargeServiceTests`
- `RecurringChargeStoreTests`
- `AutomaticRenewalServiceTests`
- `DunningServiceTests`
- `RefundServiceTests`
- `RefundStoreTests`
- `PricingResolverHistoricalTests`
- `UsageBillingOwnershipTests`
- F1 Billing model, payment-return, upgrade, workspace-epoch, and `PlatformPlanDetails.test.tsx` coverage.

The reviewed tests do not cover the two cross-component HIGH findings below.

## 6. Security findings

### BILL-R1-QA-001 — HIGH — Scheduled downgrade and paid renewal are not one authoritative boundary transition

**Affected paths**

- `billing/service/SubscriptionChangeService.java` — `applyDueDowngrades(...)`
- `billing/service/BillingLifecycleScheduler.java`
- `billing/payment/AutomaticRenewalService.java`
- `billing/payment/AutomaticRenewalStore.java`
- `billing/payment/RenewalBoundaryService.java`
- `billing/service/SubscriptionCheckoutService.java` — renewal invoice preparation

**Exact source behavior**

At the downgrade effective boundary, `SubscriptionChangeService.applyDueDowngrades` can directly switch the subscription to the target plan/price, advance `currentPeriodStart/currentPeriodEnd`, create/resolve the new platform BillingPeriod, and mark the change APPLIED. It does not require a canonical paid renewal invoice or provider-confirmed settlement before advancing a paid next period.

Automatic renewal runs independently. Its durable renewal job is keyed to the old `currentPeriodEnd` and re-validates that the subscription still has that boundary. Renewal invoice preparation also snapshots the subscription's current plan/price independently from the scheduled downgrade.

**Failure scenario**

Two valid scheduler orders produce inconsistent financial truth:

1. Downgrade executor wins first: it advances the paid subscription into the target period. The automatic-renewal job for the old boundary then sees the boundary changed and may skip collection, yielding a paid platform period without required payment.
2. Automatic renewal wins first: it can collect/finalize the old-plan renewal invoice, after which the downgrade executor switches the subscription to the lower target plan for that same boundary. The customer can be charged the wrong plan/price.

An early renewal preparation before the boundary can likewise freeze the old plan even though a scheduled downgrade is already authoritative for that boundary.

**Violated frozen contract**

- downgrade effective at the next BillingPeriod boundary;
- target PlanPrice validated at that boundary;
- paid renewal must use canonical invoice/provider truth;
- next paid period must not activate without required payment;
- financial snapshot and period linkage must be authoritative and concurrency-safe.

**Required remediation**

Make scheduled downgrade + renewal one canonical, serialized boundary transition. Under the same durable lock/CAS discipline, resolve the scheduled change before creating the renewal financial snapshot, create the next period/invoice for the target plan/PlanPrice, collect that exact invoice, and only advance/apply the paid target period after confirmed settlement. Stale/pre-created old-plan renewal financial objects must be safely rejected/superseded without rewriting finalized truth.

**Required regression tests**

- scheduled paid downgrade with downgrade worker running before renewal worker;
- same boundary with renewal worker running first;
- pre-created renewal invoice plus later scheduled downgrade;
- duplicate/multi-replica scheduler claims;
- provider failure/pending at the downgrade boundary must not advance the paid target period;
- successful provider settlement applies exactly one target period and one downgrade.

### BILL-R1-QA-002 — HIGH — PAST_DUE grace still permits new Managed Email/SMS financial exposure

**Affected paths**

- `billing/service/EntitlementService.java`
- `managedemail/delivery/ManagedEmailLaunchGuard.java`
- `managedemail/delivery/ManagedEmailDeliveryGuard.java`
- `managedemail/metering/ManagedEmailUsageService.java`
- `managedsms/delivery/ManagedSmsLaunchGuard.java`
- `managedsms/delivery/ManagedSmsDeliveryGuard.java`
- `managedsms/delivery/ManagedSmsDeliveryCoordinator.java`
- `managedsms/metering/ManagedSmsUsageService.java`

**Exact source behavior**

`EntitlementService.effectiveSubscription` intentionally returns a PAST_DUE subscription while its grace period remains open. `assertWithinLimit` blocks PAST_DUE/SUSPENDED expansion, but Managed Email/SMS launch/reservation paths do not use that guard. They call `booleanValue`, `integerValue`, and managed quota reservation methods, which remain usable during PAST_DUE grace.

The managed delivery guards likewise do not perform a financial-exposure status check immediately before provider delivery. Successful provider acceptance is subsequently bridged into MANAGED financial UsageEvent records.

**Failure scenario**

A platform renewal fails and the company transitions to PAST_DUE with the seven-day grace window. During that grace, the company can start new Managed Email/SMS sends, consume provider capacity, and create fresh PAYG liability even though the frozen dunning contract explicitly blocks new MANAGED financial exposure.

**Violated frozen contract**

During PAST_DUE grace, preserve data/read/inbound access but **block new MANAGED financial exposure**.

**Required remediation**

Add one authoritative backend guard for managed financial exposure and enforce it at both launch/reservation and the final provider-I/O boundary. PAST_DUE and SUSPENDED must fail closed for net-new Managed Email/SMS spend while preserving the allowed non-financial grace behavior.

**Required regression tests**

- PAST_DUE Managed Email launch/reserve rejected;
- PAST_DUE Managed SMS launch/reserve rejected;
- SUSPENDED equivalents rejected;
- ACTIVE remains permitted;
- race where status becomes PAST_DUE after launch validation but before provider I/O is rejected at the final delivery boundary;
- customer-owned execution remains unaffected and never becomes Managed PAYG.

### BILL-R1-QA-003 — MEDIUM — Active financial pricing records can be created with null effectiveFrom

**Affected paths**

- `billing/dto/CreatePlanPriceRequest.java`
- `billing/dto/CreateRateCardRequest.java`
- `billing/dto/CreateCompanyRateOverrideRequest.java`
- `billing/service/BillingCatalogService.java`
- `billing/service/PricingResolver.java`

**Exact source behavior**

The backend create DTOs do not require `effectiveFrom`. `BillingCatalogService.validatePeriod` accepts null starts and the create methods persist ACTIVE PlanPrice/RateCard/CompanyRateOverride rows with a null start. The overlap helpers silently substitute `Instant.now()` for overlap testing, but the persisted row remains null.

`PricingResolver.matches` later executes `from.isAfter(at)` and its sort path compares `effectiveFrom`, so a matching active RateCard/override with null `effectiveFrom` can fail rating with a null dereference instead of participating in a defined half-open interval. The free-plan selection path can also select a zero-valued ACTIVE PlanPrice without validating non-null/effective/INR semantics.

F1 requires a datetime in its form, but frontend validation is not backend authority.

**Failure scenario**

A SUPER_ADMIN/API client creates an ACTIVE managed-service rate with no effective start. Managed provider delivery may be accepted, then financial rating cannot resolve deterministically and can fail after external provider exposure. Separately, an invalid zero-valued PlanPrice can become subscription state through the free-selection path.

**Violated frozen contract**

Financially usable PlanPrices and managed-service pricing rules require explicit half-open `[effectiveFrom,effectiveTo)` semantics, including defined null handling and R1 financial correctness.

**Required remediation**

Require non-null `effectiveFrom` in backend DTO/service validation (and preferably DB constraints) for financially active PlanPrice, RateCard, and CompanyRateOverride rows. Make publish/selection/rating paths reject legacy null-start rows explicitly rather than dereferencing them. Apply one shared effective-range validator to free and paid PlanPrice selection.

**Required regression tests**

- backend create rejects null `effectiveFrom` for all three financial rule types;
- legacy null-start rows fail closed with a controlled commercial error, never NPE;
- exact `effectiveFrom` is valid;
- exact `effectiveTo` is invalid;
- future/expired/null-start/non-INR zero-price selection is rejected where R1 requires INR/effectivity.

### BILL-R1-QA-004 — LOW — Cashfree webhook verifier authenticates timestamp but does not enforce freshness

**Affected paths**

- `billing/payment/CashfreeWebhookVerifier.java`
- `billing/payment/CashfreeWebhookController.java`
- `billing/payment/CashfreeWebhookService.java`
- `billing/payment/CashfreeWebhookVerifierTests.java`

**Exact source behavior**

The verifier correctly computes HMAC-SHA256 over `timestamp + exact raw body` and performs constant-time signature comparison, but it never parses the timestamp or rejects timestamps outside a bounded past/future window.

The durable webhook inbox deduplicates identical raw payloads and protects normal duplicate delivery, but it is not a freshness check for a valid signed payload's first/very-late arrival.

**Failure scenario**

A previously valid signed payload obtained outside the application can remain cryptographically acceptable indefinitely. Downstream correlation and monotonic state checks reduce impact, but the source does not enforce the requested timestamp/replay boundary at ingress.

**Violated frozen contract**

Cashfree webhook timestamp/replay handling must be explicit in addition to signature validation and durable event idempotency.

**Required remediation**

Introduce a configurable allowed timestamp age/clock-skew window and reject stale or implausibly future signed requests before financial event processing. Keep durable event dedupe and monotonic transition protections.

**Required regression tests**

- valid fresh timestamp accepted;
- too-old timestamp rejected;
- excessively future timestamp rejected;
- exact duplicate fresh event remains idempotent;
- out-of-order provider events retain monotonic success behavior.

## 7. Commercial correctness findings

Release-blocking commercial findings are `BILL-R1-QA-001`, `BILL-R1-QA-002`, and `BILL-R1-QA-003`.

Source-accepted commercial areas, subject to execution evidence:

- PUBLIC/PRIVATE/INTERNAL discovery and direct private-plan re-authorization;
- private assignment half-open/revocation behavior without killing an existing valid subscription;
- PUBLISHED commercial/entitlement immutability;
- backend upgrade/downgrade direction classification;
- deterministic backend proration;
- paid upgrade canonical invoice and provider-confirmed application;
- initial paid checkout durable intent + period + invoice before provider I/O;
- manual grant policy rejects CASHFREE fabrication and validates explicit supplied paid PlanPrice as same-plan, ACTIVE, INR and effective at grant start;
- customer cancellation is period-end only; immediate cancellation is SUPER_ADMIN-only with reason;
- yearly platform / monthly managed cadence separation;
- invoice snapshot/line mutation is guarded after finalization;
- prior-period usage adjustments are placed on a later invoice without rewriting the finalized original.

## 8. Concurrency / idempotency findings

`BILL-R1-QA-001` is the blocking cross-scheduler concurrency defect.

Otherwise source shows strong idempotency/concurrency controls, including:

- company-scoped checkout idempotency;
- one canonical invoice per BillingPeriod and one upgrade invoice per SubscriptionChange;
- invoice-line idempotency keys;
- provider order/payment/refund durable identities;
- recurring command identity and fetch-before-recreate recovery;
- webhook durable inbox and claim leases;
- Dunning `FOR UPDATE SKIP LOCKED` claims and stage progression;
- uniqueness on renewal job subscription/boundary;
- managed usage financial idempotency and one allocation per UsageEvent;
- payment success/refund success monotonicity against later stale failure.

These remain source-accepted only; multi-replica execution is blocked by the CI environment.

## 9. Tenant-isolation findings

No additional source blocker was found in the reviewed Billing tenant boundary.

Source-accepted controls include:

- company Billing reads/mutations re-authorize the authenticated user against the path `companyId`;
- checkout/upgrade/renewal payment APIs derive financial objects server-side and are company-bound;
- checkout activation rejects another company's intent;
- provider order/payment correlation is bound through local Payment/Invoice/company records;
- private plan discovery and direct selection are company-authorized;
- SUPER_ADMIN commercial operations are backend-enforced rather than frontend-only;
- payment-return/CompanyPlan routes are workspace-epoch remounted and async results are epoch/company checked.

Execution of cross-tenant negative integration tests remains blocked.

## 10. Cashfree findings

Source accepted, except `BILL-R1-QA-004`:

- API version defaults to `2025-01-01`;
- return URL default is `https://app.unifydesk.in/billing/payment-return`;
- notify URL default is `https://api.unifydesk.in/api/v1/webhooks/cashfree`;
- server credentials are not exposed through reviewed browser DTOs;
- exact raw body is verified before webhook trust;
- durable provider event inbox exists;
- payment/order amount, INR currency, invoice and company correlation are server-side;
- provider IDs are treated as opaque strings;
- redirect/browser state is not payment truth;
- reconciliation reads provider state and does not fabricate success;
- success is monotonic against stale later failure;
- refund amount is bounded under locked successful Payment state and refund idempotency is durable.

No Cashfree sandbox/live execution evidence was present. Therefore none of the above constitutes provider runtime verification.

## 11. Managed usage / rating findings

`BILL-R1-QA-002` and `BILL-R1-QA-003` are open.

Source accepted, subject to runtime/provider verification:

- `CUSTOMER_OWNED` exits `UsageBillingService.record` before financial persistence/rating;
- only `MANAGED` usage with a stable managed SKU can become Managed PAYG;
- historical pricing resolves commercial context at `occurredAt`;
- CompanyRateOverride precedes plan RateCard;
- channel/provider/category/direction/unit/country/carrier dimensions participate in matching;
- R1 managed usage is fail-closed unless the resolved currency is INR;
- Managed Email accepted usage uses one financial idempotency identity;
- Managed SMS uses deterministic local segment estimation and `MANAGED_SMS_LOCAL_SEGMENT_ESTIMATE` semantics;
- late usage keeps original service-period attribution and is allocated once to a later invoice as `PRIOR_PERIOD_USAGE_ADJUSTMENT`;
- finalized invoice/period financial truth is not recomputed for late usage.

MSG91/provider authoritative segment behavior remains **PROVIDER VERIFICATION REQUIRED**.

## 12. Frontend findings

No additional F1 release blocker was found in source.

Source accepted:

- checkout/proration amount is backend-derived;
- Cashfree `paymentSessionId` remains opaque;
- return URL query parameters are not used as payment proof;
- INITIAL return confirmation polls authoritative company subscription state within captured company context;
- paid UPGRADE return remains fail-closed/pending because normalized payment-read backend contract is intentionally absent in R1;
- CompanyPlan and PaymentReturn are wrapped in `WorkspaceEpochBoundary` and also guard late async work by company+epoch;
- exact subscribed `planPriceId` is used for current-price display;
- downgrade target pricing is evaluated at `currentPeriodEnd`;
- admin frontend rejects missing effective starts and performs local datetime ↔ ISO Instant conversion;
- DRAFT-only catalog editing is reflected in UI while backend independently enforces it;
- CLICK/IMPRESSION remain available and SMS defaults to SEGMENT.

The documented `F1_BACKEND_CONTRACT_GAPS.md` read-model omissions were not converted into findings because F1 remains fail-closed where required and they are documented R1 contract gaps rather than violations of frozen behavior.

## 13. Execution evidence

### GitHub Actions

The frozen production SHA `3ee7333b0eab780d9f9d842092b91492ff439fb6` has no workflow run.

Recent Billing worker heads show the known pre-execution CI failure:

- B1 head `c41f30bad6b3d0d6c5cb56a0333b217492daf35d` — Checks run `37115839149`
- B2 head `7ce09bfa5f84549549b82d26468378d83f1290b0` — Checks run `37131397123`
- F1 head `89e18d0034af52ed31719e5ea7b0ec90c85f0136` — Checks run `37173773710`

For all three runs, backend/frontend/database/email-provider/website jobs concluded failure with **`steps: null`**. No build/test step executed.

Therefore:

- source review findings above are real source findings;
- test execution/build verification is **BLOCKED — EXECUTION ENVIRONMENT**;
- runner failure is not treated as a source failure;
- tests are not marked PASS merely because source assertions were reviewed.

Blocked execution items include backend unit/integration tests, frontend Vitest execution, DB migration execution, multi-replica scheduler race verification, and end-to-end payment/usage flows.

## 14. Provider-verification status

**SOURCE CONTRACT PARTIALLY ACCEPTED — PROVIDER VERIFICATION REQUIRED AFTER SOURCE REMEDIATION**

No sandbox/live Cashfree execution evidence was available in this review.

Required provider evidence remains:

- one-time payment success/failure/user-drop;
- recurring/mandate authorization success/failure/pending;
- exact-amount recurring/on-demand charge;
- duplicate and out-of-order webhook delivery;
- exact raw-body signature behavior and provider timestamp semantics;
- provider event identity/retry behavior;
- payment/subscription GET reconciliation;
- full and partial refund;
- mandate cancellation/expiry;
- merchant-enabled recurring rail behavior and retry constraints.

Managed runtime verification also remains required for SES/Managed Email acceptance/idempotency and MSG91 SMS authoritative segment/duplicate/retry behavior.

## 15. Final release disposition

# HOLD — REMEDIATION REQUIRED

Reason: source blockers exist at the frozen production SHA. CI execution is also blocked and provider verification remains pending, but those do not override the source HOLD.

Open findings:

| ID | Severity | Summary |
|---|---|---|
| `BILL-R1-QA-001` | HIGH | Scheduled downgrade and renewal can race into unpaid or wrong-price paid periods |
| `BILL-R1-QA-002` | HIGH | PAST_DUE grace permits new Managed Email/SMS financial exposure |
| `BILL-R1-QA-003` | MEDIUM | Backend permits ACTIVE financial pricing rules with null effective start |
| `BILL-R1-QA-004` | LOW | Cashfree webhook timestamp is signed but has no ingress freshness window |

## 16. Exact remediation requirements

1. **BILL-R1-QA-001:** serialize downgrade + renewal into one authoritative next-boundary financial transition; target plan/PlanPrice must determine the canonical renewal period/invoice before collection; do not advance a paid target period until required settlement is confirmed; add both scheduler-order and multi-replica regression coverage.
2. **BILL-R1-QA-002:** add a shared backend managed-financial-exposure guard and enforce it at launch/reservation plus the final provider-I/O boundary for both Managed Email and Managed SMS; PAST_DUE/SUSPENDED must reject new spend.
3. **BILL-R1-QA-003:** require and enforce non-null effective starts for financially active PlanPrice/RateCard/CompanyRateOverride records; defend against legacy null rows; add half-open boundary/null/INR regression tests.
4. **BILL-R1-QA-004:** add bounded Cashfree webhook timestamp freshness/future-skew validation while retaining durable dedupe and monotonic event handling.
5. Restore an execution environment in which backend/frontend/database checks actually run, then execute the focused regression suite plus the existing Billing R1 suite.
6. After source remediation receives final SEC/QA acceptance, execute and record the Cashfree and Managed Email/SMS provider/runtime verification matrix. Provider behavior must not be marked verified from source inspection alone.
