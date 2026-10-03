package com.messaging.billing.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.messaging.billing.dto.InvoiceIdentitySnapshot;
import com.messaging.billing.dto.InvoiceResponse;
import com.messaging.billing.entity.BillingPeriod;
import com.messaging.billing.entity.CompanySubscription;
import com.messaging.billing.entity.Invoice;
import com.messaging.billing.entity.Plan;
import com.messaging.billing.entity.PlanPrice;
import com.messaging.billing.entity.SubscriptionCheckoutIntent;
import com.messaging.billing.enums.BillingCycle;
import com.messaging.billing.enums.BillingPeriodKind;
import com.messaging.billing.enums.BillingPeriodStatus;
import com.messaging.billing.enums.InvoiceStatus;
import com.messaging.billing.enums.PlanStatus;
import com.messaging.billing.enums.PlanVisibility;
import com.messaging.billing.enums.RateCardStatus;
import com.messaging.billing.enums.SubscriptionCheckoutIntentStatus;
import com.messaging.billing.enums.SubscriptionStatus;
import com.messaging.billing.repository.CompanySubscriptionRepository;
import com.messaging.billing.repository.InvoiceRepository;
import com.messaging.billing.repository.PlanPriceRepository;
import com.messaging.billing.repository.PlanRepository;
import com.messaging.billing.repository.SubscriptionCheckoutIntentRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.ConflictException;
import com.messaging.company.entity.Company;
import com.messaging.company.repository.CompanyRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SubscriptionCheckoutServiceTests {

  @Mock private SubscriptionCheckoutIntentRepository intentRepository;
  @Mock private CompanyRepository companyRepository;
  @Mock private PlanRepository planRepository;
  @Mock private PlanPriceRepository planPriceRepository;
  @Mock private InvoiceRepository invoiceRepository;
  @Mock private CompanySubscriptionRepository subscriptionRepository;
  @Mock private PlanEligibilityService eligibilityService;
  @Mock private BillingPeriodService billingPeriodService;
  @Mock private InvoiceService invoiceService;
  @Mock private SubscriptionLifecycleService lifecycleService;

  private SubscriptionCheckoutService service;
  private Company company;
  private Plan plan;
  private PlanPrice monthlyPrice;
  private Instant requestedAt;

  @BeforeEach
  void setUp() {
    service =
        new SubscriptionCheckoutService(
            intentRepository,
            companyRepository,
            planRepository,
            planPriceRepository,
            invoiceRepository,
            subscriptionRepository,
            eligibilityService,
            billingPeriodService,
            invoiceService,
            lifecycleService);
    requestedAt = Instant.parse("2026-10-03T06:00:00Z");
    company = company(10L);
    plan = plan(20L);
    monthlyPrice = price(30L, plan, BillingCycle.MONTHLY, "999.00");
  }

  @Test
  void initialPaidCheckoutUsesEffectivePriceAndDerivesMonthlyPeriod() {
    BillingPeriod period =
        period(
            40L,
            company,
            plan,
            monthlyPrice,
            requestedAt,
            Instant.parse("2026-11-03T06:00:00Z"));
    Invoice invoiceEntity = invoiceEntity(50L, company, period, InvoiceStatus.OPEN, "999.00");
    AtomicReference<SubscriptionCheckoutIntent> saved = new AtomicReference<>();

    stubInitialCreate(period, invoiceEntity, saved);

    var response =
        service.createOrResolveInitialCheckout(
            10L, 20L, 30L, "checkout-1", 77L, requestedAt, identity());

    assertEquals("60", response.intentId());
    assertEquals(new BigDecimal("999.00"), response.amount());
    assertEquals(requestedAt, response.servicePeriodStart());
    assertEquals(Instant.parse("2026-11-03T06:00:00Z"), response.servicePeriodEnd());
    assertEquals("50", response.invoice().invoiceId());
    assertEquals(new BigDecimal("999.00"), response.invoice().amountDue());
    assertEquals(SubscriptionCheckoutIntentStatus.PENDING_PAYMENT, response.status());
    verify(billingPeriodService)
        .ensurePlatformPeriodSnapshot(
            company,
            plan,
            monthlyPrice,
            requestedAt,
            Instant.parse("2026-11-03T06:00:00Z"));
  }

  @Test
  void initialPaidCheckoutDerivesYearlyPeriod() {
    PlanPrice yearly = price(31L, plan, BillingCycle.YEARLY, "9999.00");
    BillingPeriod period =
        period(
            41L,
            company,
            plan,
            yearly,
            requestedAt,
            Instant.parse("2027-10-03T06:00:00Z"));
    Invoice invoiceEntity = invoiceEntity(51L, company, period, InvoiceStatus.OPEN, "9999.00");

    when(companyRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(company));
    when(intentRepository.findByCompanyIdAndIdempotencyKey(10L, "yearly")).thenReturn(Optional.empty());
    when(lifecycleService.currentOptional(10L)).thenReturn(Optional.empty());
    when(planRepository.findById(20L)).thenReturn(Optional.of(plan));
    when(planPriceRepository.findById(31L)).thenReturn(Optional.of(yearly));
    when(billingPeriodService.ensurePlatformPeriodSnapshot(
            company,
            plan,
            yearly,
            requestedAt,
            Instant.parse("2027-10-03T06:00:00Z")))
        .thenReturn(period);
    when(intentRepository.saveAndFlush(any(SubscriptionCheckoutIntent.class)))
        .thenAnswer(
            invocation -> {
              SubscriptionCheckoutIntent intent = invocation.getArgument(0);
              if (intent.getId() == null) intent.setId(61L);
              return intent;
            });
    when(invoiceService.createOrResolvePlatformInvoice(10L, 41L, identity(), requestedAt))
        .thenReturn(invoiceResponse(51L, 10L, 41L, "9999.00"));
    when(invoiceRepository.findByIdForUpdate(51L)).thenReturn(Optional.of(invoiceEntity));

    var response =
        service.createOrResolveInitialCheckout(
            10L, 20L, 31L, "yearly", 77L, requestedAt, identity());

    assertEquals(Instant.parse("2027-10-03T06:00:00Z"), response.servicePeriodEnd());
  }

  @Test
  void checkoutReplayConvergesOnSameIntentAndInvoice() {
    BillingPeriod period =
        period(
            40L,
            company,
            plan,
            monthlyPrice,
            requestedAt,
            Instant.parse("2026-11-03T06:00:00Z"));
    SubscriptionCheckoutIntent intent =
        intent(60L, company, plan, monthlyPrice, period, "checkout-1", requestedAt);
    Invoice invoiceEntity = invoiceEntity(50L, company, period, InvoiceStatus.OPEN, "999.00");
    intent.setInvoice(invoiceEntity);

    when(companyRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(company));
    when(intentRepository.findByCompanyIdAndIdempotencyKey(10L, "checkout-1"))
        .thenReturn(Optional.of(intent));
    when(invoiceService.createOrResolvePlatformInvoice(10L, 40L, null, requestedAt))
        .thenReturn(invoiceResponse(50L, 10L, 40L, "999.00"));

    var replay =
        service.createOrResolveInitialCheckout(
            10L, 20L, 30L, "checkout-1", 77L, requestedAt.plusSeconds(30), identity());

    assertEquals("60", replay.intentId());
    assertEquals("50", replay.invoice().invoiceId());
    verify(billingPeriodService, never()).ensurePlatformPeriodSnapshot(any(), any(), any(), any(), any());
  }

  @Test
  void sameCheckoutKeyWithDifferentPriceConflicts() {
    BillingPeriod period =
        period(
            40L,
            company,
            plan,
            monthlyPrice,
            requestedAt,
            Instant.parse("2026-11-03T06:00:00Z"));
    SubscriptionCheckoutIntent intent =
        intent(60L, company, plan, monthlyPrice, period, "checkout-1", requestedAt);

    when(companyRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(company));
    when(intentRepository.findByCompanyIdAndIdempotencyKey(10L, "checkout-1"))
        .thenReturn(Optional.of(intent));

    assertThrows(
        ConflictException.class,
        () ->
            service.createOrResolveInitialCheckout(
                10L, 20L, 31L, "checkout-1", 77L, requestedAt, identity()));
  }

  @Test
  void anotherCompanyCannotConsumeCheckoutIntent() {
    Company other = company(11L);
    BillingPeriod period =
        period(
            40L,
            company,
            plan,
            monthlyPrice,
            requestedAt,
            Instant.parse("2026-11-03T06:00:00Z"));
    SubscriptionCheckoutIntent intent =
        intent(60L, company, plan, monthlyPrice, period, "checkout-1", requestedAt);
    intent.setInvoice(invoiceEntity(50L, company, period, InvoiceStatus.PAID, "999.00"));

    when(companyRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(other));
    when(intentRepository.findByIdForUpdate(60L)).thenReturn(Optional.of(intent));

    assertThrows(
        BadRequestException.class,
        () -> service.activateConfirmedInitialCheckout(11L, 60L, 50L, requestedAt.plusSeconds(60)));
  }

  @Test
  void initialSubscriptionCannotActivateBeforeCanonicalInvoiceIsPaid() {
    BillingPeriod period =
        period(
            40L,
            company,
            plan,
            monthlyPrice,
            requestedAt,
            Instant.parse("2026-11-03T06:00:00Z"));
    SubscriptionCheckoutIntent intent =
        intent(60L, company, plan, monthlyPrice, period, "checkout-1", requestedAt);
    intent.setInvoice(invoiceEntity(50L, company, period, InvoiceStatus.OPEN, "999.00"));

    when(companyRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(company));
    when(intentRepository.findByIdForUpdate(60L)).thenReturn(Optional.of(intent));
    when(invoiceService.requirePaidCanonicalPlatformInvoice(10L, 50L))
        .thenThrow(new ConflictException("not paid"));

    assertThrows(
        ConflictException.class,
        () -> service.activateConfirmedInitialCheckout(10L, 60L, 50L, requestedAt.plusSeconds(60)));

    verify(subscriptionRepository, never()).saveAndFlush(any(CompanySubscription.class));
  }

  @Test
  void confirmedActivationCreatesOneSubscriptionAndReplayReturnsSameSubscription() {
    BillingPeriod period =
        period(
            40L,
            company,
            plan,
            monthlyPrice,
            requestedAt,
            Instant.parse("2026-11-03T06:00:00Z"));
    SubscriptionCheckoutIntent intent =
        intent(60L, company, plan, monthlyPrice, period, "checkout-1", requestedAt);
    intent.setInvoice(invoiceEntity(50L, company, period, InvoiceStatus.PAID, "999.00"));

    when(companyRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(company));
    when(intentRepository.findByIdForUpdate(60L)).thenReturn(Optional.of(intent));
    when(invoiceService.requirePaidCanonicalPlatformInvoice(10L, 50L)).thenReturn(period);
    when(lifecycleService.currentOptional(10L)).thenReturn(Optional.empty());
    when(subscriptionRepository.saveAndFlush(any(CompanySubscription.class)))
        .thenAnswer(
            invocation -> {
              CompanySubscription subscription = invocation.getArgument(0);
              subscription.setId(80L);
              return subscription;
            });
    when(intentRepository.saveAndFlush(intent)).thenReturn(intent);

    var first =
        service.activateConfirmedInitialCheckout(
            10L, 60L, 50L, requestedAt.plusSeconds(60));
    var replay =
        service.activateConfirmedInitialCheckout(
            10L, 60L, 50L, requestedAt.plusSeconds(120));

    assertEquals("80", first.subscriptionId());
    assertEquals(first.subscriptionId(), replay.subscriptionId());
    assertEquals("20", first.planId());
    assertEquals("30", first.planPriceId());
    assertEquals(requestedAt, first.currentPeriodStart());
    assertEquals(Instant.parse("2026-11-03T06:00:00Z"), first.currentPeriodEnd());
    verify(subscriptionRepository).saveAndFlush(any(CompanySubscription.class));
    verify(billingPeriodService).bindPlatformPeriodToSubscription(40L, intent.getSubscription());
  }

  @Test
  void renewalPreparationCreatesCanonicalNextPeriodInvoiceBeforeTransition() {
    CompanySubscription subscription = subscription(80L, company, plan, monthlyPrice);
    subscription.setCurrentPeriodStart(Instant.parse("2026-10-03T06:00:00Z"));
    subscription.setCurrentPeriodEnd(Instant.parse("2026-11-03T06:00:00Z"));
    PlanPrice renewalPrice = price(32L, plan, BillingCycle.MONTHLY, "1099.00");
    renewalPrice.setEffectiveFrom(Instant.parse("2026-11-01T00:00:00Z"));
    BillingPeriod next =
        period(
            41L,
            company,
            plan,
            renewalPrice,
            Instant.parse("2026-11-03T06:00:00Z"),
            Instant.parse("2026-12-03T06:00:00Z"));

    when(companyRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(company));
    when(subscriptionRepository.findByIdForUpdate(80L)).thenReturn(Optional.of(subscription));
    when(planPriceRepository.findByPlanIdAndStatusOrderByEffectiveFromDesc(20L, RateCardStatus.ACTIVE))
        .thenReturn(List.of(renewalPrice));
    when(billingPeriodService.ensurePlatformPeriodSnapshot(
            company,
            plan,
            renewalPrice,
            Instant.parse("2026-11-03T06:00:00Z"),
            Instant.parse("2026-12-03T06:00:00Z")))
        .thenReturn(next);
    when(invoiceService.createOrResolvePlatformInvoice(10L, 41L, identity(), requestedAt))
        .thenReturn(invoiceResponse(51L, 10L, 41L, "1099.00"));

    InvoiceResponse invoice =
        service.createOrResolveRenewalInvoice(10L, 80L, requestedAt, identity());

    assertEquals("51", invoice.invoiceId());
    assertEquals(new BigDecimal("1099.00"), invoice.amountDue());
    assertEquals(
        Instant.parse("2026-11-03T06:00:00Z"), invoice.servicePeriodStart());
    verify(subscriptionRepository, never()).saveAndFlush(any(CompanySubscription.class));
  }

  private void stubInitialCreate(
      BillingPeriod period,
      Invoice invoiceEntity,
      AtomicReference<SubscriptionCheckoutIntent> saved) {
    when(companyRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(company));
    when(intentRepository.findByCompanyIdAndIdempotencyKey(10L, "checkout-1"))
        .thenReturn(Optional.empty());
    when(lifecycleService.currentOptional(10L)).thenReturn(Optional.empty());
    when(planRepository.findById(20L)).thenReturn(Optional.of(plan));
    when(planPriceRepository.findById(30L)).thenReturn(Optional.of(monthlyPrice));
    when(billingPeriodService.ensurePlatformPeriodSnapshot(
            company,
            plan,
            monthlyPrice,
            requestedAt,
            Instant.parse("2026-11-03T06:00:00Z")))
        .thenReturn(period);
    when(intentRepository.saveAndFlush(any(SubscriptionCheckoutIntent.class)))
        .thenAnswer(
            invocation -> {
              SubscriptionCheckoutIntent intent = invocation.getArgument(0);
              if (intent.getId() == null) intent.setId(60L);
              saved.set(intent);
              return intent;
            });
    when(invoiceService.createOrResolvePlatformInvoice(10L, 40L, identity(), requestedAt))
        .thenReturn(invoiceResponse(50L, 10L, 40L, "999.00"));
    when(invoiceRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(invoiceEntity));
  }

  private InvoiceIdentitySnapshot identity() {
    return new InvoiceIdentitySnapshot(
        "Acme", "billing@acme.test", "1 Main", null, "Bengaluru", "KA", "560001", "IN", null, null);
  }

  private Company company(Long id) {
    Company value = new Company();
    value.setId(id);
    value.setName("Acme");
    value.setBusinessEmail("billing@acme.test");
    value.setCountry("IN");
    return value;
  }

  private Plan plan(Long id) {
    Plan value = new Plan();
    value.setId(id);
    value.setCode("GROWTH");
    value.setName("Growth");
    value.setStatus(PlanStatus.PUBLISHED);
    value.setVisibility(PlanVisibility.PUBLIC);
    value.setBillingCycle(BillingCycle.MONTHLY);
    value.setBasePrice(new BigDecimal("999.00"));
    value.setCurrency("INR");
    return value;
  }

  private PlanPrice price(Long id, Plan owner, BillingCycle cycle, String amount) {
    PlanPrice value = new PlanPrice();
    value.setId(id);
    value.setPlan(owner);
    value.setBillingCycle(cycle);
    value.setAmount(new BigDecimal(amount));
    value.setCurrency("INR");
    value.setEffectiveFrom(requestedAt.minusSeconds(3600));
    value.setStatus(RateCardStatus.ACTIVE);
    return value;
  }

  private BillingPeriod period(
      Long id,
      Company owner,
      Plan selectedPlan,
      PlanPrice price,
      Instant start,
      Instant end) {
    BillingPeriod period = new BillingPeriod();
    period.setId(id);
    period.setCompany(owner);
    period.setKind(BillingPeriodKind.PLATFORM);
    period.setCadence(price.getBillingCycle());
    period.setPlan(selectedPlan);
    period.setPlanPrice(price);
    period.setPlatformAmountSnapshot(price.getAmount());
    period.setCurrencySnapshot("INR");
    period.setStartAt(start);
    period.setEndAt(end);
    period.setStatus(BillingPeriodStatus.OPEN);
    return period;
  }

  private Invoice invoiceEntity(
      Long id, Company owner, BillingPeriod period, InvoiceStatus status, String amount) {
    Invoice invoice = new Invoice();
    invoice.setId(id);
    invoice.setCompany(owner);
    invoice.setBillingPeriod(period);
    invoice.setStatus(status);
    invoice.setCurrency("INR");
    invoice.setCompanyNameSnapshot("Acme");
    invoice.setTotal(new BigDecimal(amount));
    invoice.setAmountDue(
        status == InvoiceStatus.PAID ? BigDecimal.ZERO : new BigDecimal(amount));
    invoice.setServicePeriodStart(period.getStartAt());
    invoice.setServicePeriodEnd(period.getEndAt());
    if (status == InvoiceStatus.PAID) invoice.setPaidAt(requestedAt.plusSeconds(30));
    return invoice;
  }

  private SubscriptionCheckoutIntent intent(
      Long id,
      Company owner,
      Plan selectedPlan,
      PlanPrice price,
      BillingPeriod period,
      String key,
      Instant at) {
    SubscriptionCheckoutIntent intent = new SubscriptionCheckoutIntent();
    intent.setId(id);
    intent.setCompany(owner);
    intent.setPlan(selectedPlan);
    intent.setPlanPrice(price);
    intent.setBillingPeriod(period);
    intent.setStatus(SubscriptionCheckoutIntentStatus.PENDING_PAYMENT);
    intent.setIdempotencyKey(key);
    intent.setCurrency("INR");
    intent.setAmountSnapshot(price.getAmount());
    intent.setServicePeriodStart(period.getStartAt());
    intent.setServicePeriodEnd(period.getEndAt());
    intent.setRequestedAt(at);
    return intent;
  }

  private CompanySubscription subscription(
      Long id, Company owner, Plan selectedPlan, PlanPrice price) {
    CompanySubscription subscription = new CompanySubscription();
    subscription.setId(id);
    subscription.setCompany(owner);
    subscription.setPlan(selectedPlan);
    subscription.setPlanPrice(price);
    subscription.setStatus(SubscriptionStatus.ACTIVE);
    return subscription;
  }

  private InvoiceResponse invoiceResponse(
      Long invoiceId, Long companyId, Long periodId, String amount) {
    return new InvoiceResponse(
        invoiceId.toString(),
        companyId.toString(),
        periodId.toString(),
        "INV-" + invoiceId,
        InvoiceStatus.OPEN,
        "INR",
        new BigDecimal(amount),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        new BigDecimal(amount),
        new BigDecimal(amount),
        requestedAt,
        periodId == 40L
            ? Instant.parse("2026-11-03T06:00:00Z")
            : Instant.parse("2026-12-03T06:00:00Z"),
        requestedAt,
        requestedAt,
        null);
  }
}
