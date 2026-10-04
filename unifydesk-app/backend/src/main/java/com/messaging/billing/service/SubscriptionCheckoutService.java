package com.messaging.billing.service;

import com.messaging.billing.dto.CompanySubscriptionResponse;
import com.messaging.billing.dto.InvoiceIdentitySnapshot;
import com.messaging.billing.dto.InvoiceResponse;
import com.messaging.billing.dto.SubscriptionCheckoutIntentResponse;
import com.messaging.billing.entity.BillingPeriod;
import com.messaging.billing.entity.CompanySubscription;
import com.messaging.billing.entity.Plan;
import com.messaging.billing.entity.PlanPrice;
import com.messaging.billing.entity.SubscriptionCheckoutIntent;
import com.messaging.billing.enums.BillingCycle;
import com.messaging.billing.enums.PaymentPolicy;
import com.messaging.billing.enums.PlanStatus;
import com.messaging.billing.enums.RateCardStatus;
import com.messaging.billing.enums.SubscriptionCheckoutIntentStatus;
import com.messaging.billing.enums.SubscriptionStatus;
import com.messaging.billing.enums.SubscriptionTransitionType;
import com.messaging.billing.repository.CompanySubscriptionRepository;
import com.messaging.billing.repository.InvoiceRepository;
import com.messaging.billing.repository.PlanPriceRepository;
import com.messaging.billing.repository.PlanRepository;
import com.messaging.billing.repository.SubscriptionCheckoutIntentRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.ConflictException;
import com.messaging.common.exception.NotFoundException;
import com.messaging.company.entity.Company;
import com.messaging.company.repository.CompanyRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * B1-owned provider-neutral commercial contracts for initial paid subscription checkout and
 * renewal preparation.
 *
 * <p>B2 must collect the exact finalized UnifyDesk invoice returned by these contracts. B2 must not
 * recalculate plan pricing, derive service-period boundaries, construct platform invoice lines, or
 * activate subscriptions from frontend/provider redirect state.
 */
@Service
@RequiredArgsConstructor
public class SubscriptionCheckoutService {

  private static final String R1_CURRENCY = "INR";

  private final SubscriptionCheckoutIntentRepository intentRepository;
  private final CompanyRepository companyRepository;
  private final PlanRepository planRepository;
  private final PlanPriceRepository planPriceRepository;
  private final InvoiceRepository invoiceRepository;
  private final CompanySubscriptionRepository subscriptionRepository;
  private final PlanEligibilityService eligibilityService;
  private final BillingPeriodService billingPeriodService;
  private final InvoiceService invoiceService;
  private final SubscriptionLifecycleService lifecycleService;
  private final SubscriptionBoundaryChangeService boundaryChangeService;

  /**
   * Initial paid-subscription contract for B2.
   *
   * <p>Creates or resolves a durable PENDING_PAYMENT commercial intent and its one canonical
   * finalized invoice. No subscription is activated here.
   */
  @Transactional
  public SubscriptionCheckoutIntentResponse createOrResolveInitialCheckout(
      Long companyId,
      Long planId,
      Long planPriceId,
      String idempotencyKey,
      Long requestedByUserId,
      Instant requestedAt,
      InvoiceIdentitySnapshot identity) {
    if (requestedAt == null) {
      throw new BadRequestException("Checkout request time is required");
    }
    String key = normalizeIdempotencyKey(idempotencyKey);
    Company company =
        companyRepository
            .findByIdForUpdate(companyId)
            .orElseThrow(() -> new NotFoundException("Company not found"));

    var prior = intentRepository.findByCompanyIdAndIdempotencyKey(companyId, key);
    if (prior.isPresent()) {
      assertSameInitialCommand(prior.get(), planId, planPriceId);
      return toResponse(prior.get());
    }
    if (lifecycleService.currentOptional(companyId).isPresent()) {
      throw new ConflictException(
          "A current subscription already exists; use upgrade or renewal contracts");
    }

    Plan plan =
        planRepository.findById(planId).orElseThrow(() -> new NotFoundException("Plan not found"));
    if (plan.getStatus() != PlanStatus.PUBLISHED) {
      throw new BadRequestException("Only published plans can be purchased");
    }
    eligibilityService.assertCustomerEligible(companyId, plan, requestedAt);

    PlanPrice price = requireEffectivePrice(plan, planPriceId, requestedAt);
    if (price.getBillingCycle() == BillingCycle.FREE
        || price.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
      throw new BadRequestException("Paid checkout requires a positive non-FREE PlanPrice");
    }

    Instant periodStart = requestedAt;
    Instant periodEnd = nextBoundary(periodStart, price.getBillingCycle());

    BillingPeriod period =
        billingPeriodService.ensurePlatformPeriodSnapshot(
            company, plan, price, periodStart, periodEnd);

    SubscriptionCheckoutIntent intent = new SubscriptionCheckoutIntent();
    intent.setCompany(company);
    intent.setPlan(plan);
    intent.setPlanPrice(price);
    intent.setBillingPeriod(period);
    intent.setStatus(SubscriptionCheckoutIntentStatus.PENDING_PAYMENT);
    intent.setIdempotencyKey(key);
    intent.setCurrency(R1_CURRENCY);
    intent.setAmountSnapshot(price.getAmount());
    intent.setServicePeriodStart(periodStart);
    intent.setServicePeriodEnd(periodEnd);
    intent.setRequestedAt(requestedAt);
    intent.setRequestedByUserId(requestedByUserId);
    intent = intentRepository.saveAndFlush(intent);

    InvoiceResponse invoice =
        invoiceService.createOrResolvePlatformInvoice(
            companyId, period.getId(), identity, requestedAt);
    if (invoice.total().compareTo(price.getAmount()) != 0
        || invoice.amountDue().compareTo(price.getAmount()) != 0) {
      throw new ConflictException(
          "Initial platform invoice conflicts with the immutable PlanPrice snapshot");
    }

    return bindInvoiceAndReturn(intent, invoice);
  }

  /**
   * Activates only from the durable initial checkout intent and its canonical PAID invoice.
   *
   * <p>Repeated confirmed activation converges on the same CompanySubscription.
   */
  @Transactional
  public CompanySubscriptionResponse activateConfirmedInitialCheckout(
      Long companyId, Long intentId, Long invoiceId, Instant confirmedAt) {
    if (confirmedAt == null) {
      throw new BadRequestException("Provider-confirmed activation time is required");
    }
    companyRepository
        .findByIdForUpdate(companyId)
        .orElseThrow(() -> new NotFoundException("Company not found"));
    SubscriptionCheckoutIntent intent =
        intentRepository
            .findByIdForUpdate(intentId)
            .orElseThrow(() -> new NotFoundException("Subscription checkout intent not found"));
    if (!intent.getCompany().getId().equals(companyId)) {
      throw new BadRequestException("Checkout intent does not belong to the company");
    }
    if (intent.getInvoice() == null || !intent.getInvoice().getId().equals(invoiceId)) {
      throw new ConflictException("Checkout activation does not match the canonical invoice");
    }
    if (intent.getStatus() == SubscriptionCheckoutIntentStatus.ACTIVATED) {
      if (intent.getSubscription() == null) {
        throw new ConflictException("Activated checkout intent has no subscription binding");
      }
      return toSubscriptionResponse(intent.getSubscription());
    }

    BillingPeriod period =
        invoiceService.requirePaidCanonicalPlatformInvoice(companyId, invoiceId);
    assertIntentMatchesPeriod(intent, period);

    var current = lifecycleService.currentOptional(companyId);
    if (current.isPresent()) {
      if (intent.getSubscription() != null
          && current.get().getId().equals(intent.getSubscription().getId())) {
        return toSubscriptionResponse(current.get());
      }
      throw new ConflictException("Company already has another current subscription");
    }

    CompanySubscription subscription = new CompanySubscription();
    subscription.setCompany(intent.getCompany());
    subscription.setPlan(intent.getPlan());
    subscription.setPlanPrice(intent.getPlanPrice());
    subscription.setStatus(SubscriptionStatus.ACTIVE);
    subscription.setStartedAt(intent.getServicePeriodStart());
    subscription.setCurrentPeriodStart(intent.getServicePeriodStart());
    subscription.setCurrentPeriodEnd(intent.getServicePeriodEnd());
    subscription.setBillingAnchor(intent.getServicePeriodStart());
    subscription.setPaymentPolicy(PaymentPolicy.CASHFREE);
    CompanySubscription saved = subscriptionRepository.saveAndFlush(subscription);

    billingPeriodService.bindPlatformPeriodToSubscription(period.getId(), saved);
    lifecycleService.recordCreated(
        saved,
        intent.getRequestedByUserId(),
        "Provider-confirmed initial paid subscription activated",
        SubscriptionTransitionType.CREATED);

    intent.setSubscription(saved);
    intent.setStatus(SubscriptionCheckoutIntentStatus.ACTIVATED);
    intent.setActivatedAt(confirmedAt);
    intentRepository.saveAndFlush(intent);
    return toSubscriptionResponse(saved);
  }

  /**
   * B1 renewal-preparation contract for B2.
   *
   * <p>Creates/resolves the next immutable PLATFORM period and finalized invoice before collection.
   * The effective PlanPrice is resolved by B1 for the next service-period start.
   */
  @Transactional
  public InvoiceResponse createOrResolveRenewalInvoice(
      Long companyId,
      Long subscriptionId,
      Instant requestedAt,
      InvoiceIdentitySnapshot identity) {
    if (requestedAt == null) {
      throw new BadRequestException("Renewal invoice request time is required");
    }
    companyRepository
        .findByIdForUpdate(companyId)
        .orElseThrow(() -> new NotFoundException("Company not found"));
    CompanySubscription subscription =
        subscriptionRepository
            .findByIdForUpdate(subscriptionId)
            .orElseThrow(() -> new NotFoundException("Company subscription not found"));
    if (!subscription.getCompany().getId().equals(companyId)) {
      throw new BadRequestException("Subscription does not belong to the company");
    }
    if (subscription.getStatus() != SubscriptionStatus.ACTIVE
        && subscription.getStatus() != SubscriptionStatus.PAST_DUE
        && subscription.getStatus() != SubscriptionStatus.SUSPENDED) {
      throw new ConflictException("Subscription is not eligible for renewal preparation");
    }
    if (subscription.getCurrentPeriodEnd() == null || subscription.getPlanPrice() == null) {
      throw new BadRequestException("Paid renewal requires a current priced platform period");
    }

    Instant nextStart = subscription.getCurrentPeriodEnd();
    BillingCycle currentCycle = subscription.getPlanPrice().getBillingCycle();
    if (currentCycle == BillingCycle.FREE) {
      throw new BadRequestException("FREE subscriptions do not require paid renewal");
    }

    Plan renewalPlan = subscription.getPlan();
    PlanPrice renewalPrice;
    var scheduledDowngrade =
        boundaryChangeService.resolveScheduledDowngradeForBoundary(subscription, nextStart);
    if (scheduledDowngrade.isPresent()) {
      renewalPlan = scheduledDowngrade.get().getToPlan();
      renewalPrice = scheduledDowngrade.get().getToPlanPrice();
      assertPaidBoundaryPrice(renewalPlan, renewalPrice, nextStart);
    } else {
      renewalPrice = resolveEffectivePrice(renewalPlan, currentCycle, nextStart);
    }
    Instant nextEnd = nextBoundary(nextStart, renewalPrice.getBillingCycle());

    BillingPeriod period =
        billingPeriodService.ensurePlatformPeriodSnapshot(
            subscription.getCompany(), renewalPlan, renewalPrice, nextStart, nextEnd);
    return invoiceService.createOrResolvePlatformInvoice(
        companyId, period.getId(), identity, requestedAt);
  }

  private SubscriptionCheckoutIntentResponse bindInvoiceAndReturn(
      SubscriptionCheckoutIntent intent, InvoiceResponse invoice) {
    var invoiceEntity =
        invoiceRepository
            .findByIdForUpdate(Long.parseLong(invoice.invoiceId()))
            .orElseThrow(() -> new NotFoundException("Canonical checkout invoice not found"));
    if (!invoiceEntity.getBillingPeriod().getId().equals(intent.getBillingPeriod().getId())) {
      throw new ConflictException("Checkout invoice does not belong to the intent billing period");
    }
    intent.setInvoice(invoiceEntity);
    intent = intentRepository.saveAndFlush(intent);
    return toResponse(intent, invoice);
  }

  private void assertSameInitialCommand(
      SubscriptionCheckoutIntent prior, Long planId, Long planPriceId) {
    if (!prior.getPlan().getId().equals(planId)
        || !prior.getPlanPrice().getId().equals(planPriceId)) {
      throw new ConflictException(
          "Checkout idempotency key was already used for a materially different plan/price");
    }
  }

  private void assertIntentMatchesPeriod(
      SubscriptionCheckoutIntent intent, BillingPeriod period) {
    if (!period.getCompany().getId().equals(intent.getCompany().getId())
        || period.getPlan() == null
        || !period.getPlan().getId().equals(intent.getPlan().getId())
        || period.getPlanPrice() == null
        || !period.getPlanPrice().getId().equals(intent.getPlanPrice().getId())
        || !period.getStartAt().equals(intent.getServicePeriodStart())
        || !period.getEndAt().equals(intent.getServicePeriodEnd())
        || period.getPlatformAmountSnapshot() == null
        || period.getPlatformAmountSnapshot().compareTo(intent.getAmountSnapshot()) != 0
        || !R1_CURRENCY.equalsIgnoreCase(period.getCurrencySnapshot())) {
      throw new ConflictException(
          "Checkout intent no longer matches its immutable platform-period snapshot");
    }
  }

  private PlanPrice requireEffectivePrice(Plan plan, Long priceId, Instant at) {
    PlanPrice price =
        planPriceRepository
            .findById(priceId)
            .orElseThrow(() -> new NotFoundException("Plan price not found"));
    if (!price.getPlan().getId().equals(plan.getId()) || price.getStatus() != RateCardStatus.ACTIVE) {
      throw new BadRequestException("Plan price does not belong to the selected plan");
    }
    assertEffectiveInrPrice(price, at);
    return price;
  }

  private PlanPrice resolveEffectivePrice(Plan plan, BillingCycle cycle, Instant at) {
    List<PlanPrice> candidates =
        planPriceRepository
            .findByPlanIdAndStatusOrderByEffectiveFromDesc(plan.getId(), RateCardStatus.ACTIVE);
    candidates.forEach(
        price ->
            FinancialEffectiveTime.assertStoredActiveRange(
                price.getEffectiveFrom(), price.getEffectiveTo(), "PlanPrice"));
    List<PlanPrice> matches =
        candidates.stream()
            .filter(price -> price.getBillingCycle() == cycle)
            .filter(price -> R1_CURRENCY.equalsIgnoreCase(price.getCurrency()))
            .filter(
                price ->
                    FinancialEffectiveTime.includes(
                        price.getEffectiveFrom(), price.getEffectiveTo(), at))
            .sorted(Comparator.comparing(PlanPrice::getEffectiveFrom).reversed())
            .toList();
    if (matches.isEmpty()) {
      throw new ConflictException("No effective INR PlanPrice exists for the renewal boundary");
    }
    if (matches.size() > 1) {
      throw new ConflictException("Multiple effective PlanPrices exist for the renewal boundary");
    }
    return matches.getFirst();
  }

  private void assertEffectiveInrPrice(PlanPrice price, Instant at) {
    FinancialEffectiveTime.assertStoredActiveRange(
        price.getEffectiveFrom(), price.getEffectiveTo(), "PlanPrice");
    if (!R1_CURRENCY.equalsIgnoreCase(price.getCurrency())) {
      throw new BadRequestException("Billing R1 supports INR only");
    }
    if (!FinancialEffectiveTime.includes(price.getEffectiveFrom(), price.getEffectiveTo(), at)) {
      throw new BadRequestException(
          "Plan price is not effective at the canonical commercial pricing time");
    }
  }

  private void assertPaidBoundaryPrice(Plan plan, PlanPrice price, Instant at) {
    if (price == null
        || !price.getPlan().getId().equals(plan.getId())
        || price.getStatus() != RateCardStatus.ACTIVE) {
      throw new ConflictException("Scheduled downgrade target PlanPrice is no longer canonical");
    }
    FinancialEffectiveTime.assertStoredActiveRange(
        price.getEffectiveFrom(), price.getEffectiveTo(), "PlanPrice");
    if (!R1_CURRENCY.equalsIgnoreCase(price.getCurrency())
        || !FinancialEffectiveTime.includes(price.getEffectiveFrom(), price.getEffectiveTo(), at)) {
      throw new ConflictException(
          "Scheduled downgrade target PlanPrice is invalid at the renewal boundary");
    }
    if (price.getBillingCycle() == BillingCycle.FREE
        || price.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
      throw new ConflictException(
          "Automatic paid renewal requires a positive non-FREE target PlanPrice");
    }
  }

  private Instant nextBoundary(Instant start, BillingCycle cycle) {
    ZonedDateTime utc = start.atZone(ZoneOffset.UTC);
    return switch (cycle) {
      case MONTHLY -> utc.plusMonths(1).toInstant();
      case YEARLY -> utc.plusYears(1).toInstant();
      case FREE -> throw new BadRequestException("FREE has no paid platform boundary");
    };
  }

  private String normalizeIdempotencyKey(String value) {
    if (value == null || value.isBlank() || value.length() > 255) {
      throw new BadRequestException("A valid checkout idempotency key is required");
    }
    return value.trim();
  }

  private SubscriptionCheckoutIntentResponse toResponse(SubscriptionCheckoutIntent intent) {
    if (intent.getInvoice() == null) {
      throw new ConflictException("Checkout intent has no canonical invoice");
    }
    InvoiceResponse invoice =
        invoiceService.createOrResolvePlatformInvoice(
            intent.getCompany().getId(),
            intent.getBillingPeriod().getId(),
            null,
            intent.getRequestedAt());
    if (!invoice.invoiceId().equals(intent.getInvoice().getId().toString())) {
      throw new ConflictException("Checkout intent invoice binding conflicts with billing period");
    }
    return toResponse(intent, invoice);
  }

  private SubscriptionCheckoutIntentResponse toResponse(
      SubscriptionCheckoutIntent intent, InvoiceResponse invoice) {
    return new SubscriptionCheckoutIntentResponse(
        intent.getId().toString(),
        intent.getCompany().getId().toString(),
        intent.getPlan().getId().toString(),
        intent.getPlanPrice().getId().toString(),
        intent.getBillingPeriod().getId().toString(),
        invoice,
        intent.getSubscription() == null ? null : intent.getSubscription().getId().toString(),
        intent.getStatus(),
        intent.getCurrency(),
        intent.getAmountSnapshot(),
        intent.getServicePeriodStart(),
        intent.getServicePeriodEnd(),
        intent.getRequestedAt(),
        intent.getActivatedAt());
  }

  private CompanySubscriptionResponse toSubscriptionResponse(CompanySubscription subscription) {
    Plan plan = subscription.getPlan();
    return new CompanySubscriptionResponse(
        subscription.getId().toString(),
        subscription.getCompany().getId().toString(),
        plan.getId().toString(),
        subscription.getPlanPrice() == null ? null : subscription.getPlanPrice().getId().toString(),
        plan.getCode(),
        plan.getName(),
        subscription.getStatus(),
        subscription.getStartedAt(),
        subscription.getEndsAt(),
        subscription.getCurrentPeriodStart(),
        subscription.getCurrentPeriodEnd(),
        subscription.getBillingAnchor(),
        subscription.getTrialEndsAt(),
        subscription.getGraceEndsAt(),
        subscription.isCancelAtPeriodEnd(),
        subscription.getCancelledAt(),
        subscription.getCancellationEffectiveAt(),
        subscription.getPaymentPolicy(),
        subscription.getExternalContractReference());
  }
}
