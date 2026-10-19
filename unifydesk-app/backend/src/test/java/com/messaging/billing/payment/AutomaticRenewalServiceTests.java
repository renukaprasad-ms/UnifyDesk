package com.messaging.billing.payment;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.messaging.billing.dto.InvoiceResponse;
import com.messaging.billing.enums.InvoiceStatus;
import com.messaging.billing.enums.PaymentStatus;
import com.messaging.billing.enums.SubscriptionStatus;
import com.messaging.billing.service.SubscriptionCheckoutService;
import com.messaging.billing.service.SubscriptionLifecycleService;
import com.messaging.common.exception.ConflictException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AutomaticRenewalServiceTests {

  private AutomaticRenewalStore store;
  private SubscriptionCheckoutService checkoutService;
  private PaymentCollectionService collectionService;
  private RecurringAuthorizationService authorizationService;
  private RecurringChargeService chargeService;
  private PaymentConfirmationService confirmationService;
  private SubscriptionLifecycleService lifecycleService;
  private RazorpayProperties properties;
  private com.messaging.billing.service.BillingNotificationService notificationService;
  private AutomaticRenewalService service;

  @BeforeEach
  void setUp() {
    store = mock(AutomaticRenewalStore.class);
    checkoutService = mock(SubscriptionCheckoutService.class);
    collectionService = mock(PaymentCollectionService.class);
    authorizationService = mock(RecurringAuthorizationService.class);
    chargeService = mock(RecurringChargeService.class);
    confirmationService = mock(PaymentConfirmationService.class);
    lifecycleService = mock(SubscriptionLifecycleService.class);
    properties = new RazorpayProperties();
    properties.setAutomaticRenewalClaimSeconds(120L);
    properties.setRecurringEnabled(true);
    notificationService = mock(com.messaging.billing.service.BillingNotificationService.class);
    service =
        new AutomaticRenewalService(
            store,
            checkoutService,
            collectionService,
            authorizationService,
            chargeService,
            confirmationService,
            lifecycleService,
            properties,
            notificationService);
  }

  @Test
  void scheduledRunAutomaticallyPreparesAndCollectsEligibleDueSubscription() {
    Instant boundary = Instant.parse("2026-10-03T00:00:00Z");
    var job = job(boundary, null, null);
    when(store.claimDue(any(), eq(120L))).thenReturn(List.of(job));
    when(store.subscription(job))
        .thenReturn(
            new AutomaticRenewalStore.SubscriptionSnapshot(
                SubscriptionStatus.ACTIVE, boundary, false, "RAZORPAY"));
    when(checkoutService.createOrResolveRenewalInvoice(eq(10L), eq(77L), any(), isNull()))
        .thenReturn(invoice(boundary));
    when(collectionService.prepareRecurringRenewal(10L, 20L, 77L))
        .thenReturn(checkout(PaymentStatus.PENDING));
    when(authorizationService.refreshBound(10L, 77L)).thenReturn(activeAuthorization());
    when(store.paymentStatus(30L)).thenReturn(PaymentStatus.SUCCEEDED);

    service.run();

    verify(store).discoverDue(any());
    verify(checkoutService).createOrResolveRenewalInvoice(eq(10L), eq(77L), any(), isNull());
    verify(collectionService).prepareRecurringRenewal(10L, 20L, 77L);
    verify(authorizationService).bindPayment(90L, 30L);
    verify(chargeService).charge(30L, boundary);
    verify(confirmationService).applyDeferredRenewal(eq(30L), any());
    verify(store).finish(job, "RECOVERED", 20L, 30L, null);
  }

  @Test
  void zeroDueBoundaryAppliesWithoutCashfreeCollectionOrFakePayment() {
    Instant boundary = Instant.parse("2026-10-10T00:00:00Z");
    var job = job(boundary, null, null);
    when(store.subscription(job))
        .thenReturn(
            new AutomaticRenewalStore.SubscriptionSnapshot(
                SubscriptionStatus.ACTIVE, boundary, false, "RAZORPAY"));
    when(checkoutService.createOrResolveRenewalInvoice(eq(10L), eq(77L), any(), isNull()))
        .thenReturn(zeroDueInvoice(boundary));

    service.processClaimed(job);

    verify(store).bindFinancialObjects(job, 20L, null);
    verify(lifecycleService).applyZeroDueBoundaryTransition(eq(77L), eq(20L), any());
    verify(store).finish(job, "ZERO_DUE_APPLIED", 20L, null, null);
    verify(collectionService, never()).prepareRecurringRenewal(anyLong(), anyLong(), anyLong());
    verify(authorizationService, never()).refreshBound(anyLong(), anyLong());
    verify(authorizationService, never()).bindPayment(anyLong(), anyLong());
    verify(chargeService, never()).charge(anyLong(), any());
    verify(confirmationService, never()).applyDeferredRenewal(anyLong(), any());
    verify(confirmationService, never())
        .markRenewalDueUncollectible(anyLong(), any(), anyString());
  }

  @Test
  void zeroDueBoundaryWithExistingPaymentFailsClosed() {
    Instant boundary = Instant.parse("2026-10-10T00:00:00Z");
    var job = job(boundary, 20L, 30L);
    when(store.paymentStatus(30L)).thenReturn(PaymentStatus.PENDING);
    when(store.subscription(job))
        .thenReturn(
            new AutomaticRenewalStore.SubscriptionSnapshot(
                SubscriptionStatus.ACTIVE, boundary, false, "RAZORPAY"));
    when(checkoutService.createOrResolveRenewalInvoice(eq(10L), eq(77L), any(), isNull()))
        .thenReturn(zeroDueInvoice(boundary));

    service.processClaimed(job);

    verify(store).finish(job, "CONFLICT", 20L, 30L, "COMMERCIAL_BOUNDARY_CONFLICT");
    verify(lifecycleService, never()).applyZeroDueBoundaryTransition(anyLong(), anyLong(), any());
    verify(collectionService, never()).prepareRecurringRenewal(anyLong(), anyLong(), anyLong());
  }

  @Test
  void replayAfterCommittedZeroDueTransitionFinishesSameDurableJobSuccessfully() {
    Instant boundary = Instant.parse("2026-10-10T00:00:00Z");
    var job = job(boundary, 20L, null);
    when(store.subscription(job))
        .thenReturn(
            new AutomaticRenewalStore.SubscriptionSnapshot(
                SubscriptionStatus.ACTIVE, null, false, "RAZORPAY"));
    when(store.zeroDueBoundaryAlreadyApplied(job)).thenReturn(true);

    service.processClaimed(job);

    verify(store).finish(job, "ZERO_DUE_APPLIED", 20L, null, null);
    verify(checkoutService, never()).createOrResolveRenewalInvoice(anyLong(), anyLong(), any(), any());
    verify(collectionService, never()).prepareRecurringRenewal(anyLong(), anyLong(), anyLong());
    verify(lifecycleService, never()).applyZeroDueBoundaryTransition(anyLong(), anyLong(), any());
  }

  @Test
  void commercialBoundaryConflictIsTerminalAndAuditable() {
    Instant boundary = Instant.parse("2026-10-10T00:00:00Z");
    var job = job(boundary, 20L, 30L);
    when(store.paymentStatus(30L)).thenReturn(PaymentStatus.PENDING);
    when(store.subscription(job))
        .thenReturn(
            new AutomaticRenewalStore.SubscriptionSnapshot(
                SubscriptionStatus.ACTIVE, boundary, false, "RAZORPAY"));
    when(checkoutService.createOrResolveRenewalInvoice(eq(10L), eq(77L), any(), isNull()))
        .thenThrow(new ConflictException("old-plan boundary already owns financial truth"));

    service.processClaimed(job);

    verify(store).finish(job, "CONFLICT", 20L, 30L, "COMMERCIAL_BOUNDARY_CONFLICT");
    verify(collectionService, never()).prepareRecurringRenewal(anyLong(), anyLong(), anyLong());
    verify(confirmationService, never()).applyDeferredRenewal(anyLong(), any());
  }

  @Test
  void earlyFailedRenewalIsRetriedAtBoundaryUsingSameInvoiceAndPaymentThenEntersDunning() {
    Instant boundary = Instant.parse("2026-10-10T00:00:00Z");
    var job = job(boundary, 20L, 30L);
    when(store.paymentStatus(30L))
        .thenReturn(PaymentStatus.FAILED)
        .thenReturn(PaymentStatus.FAILED);
    when(store.hasDunning(30L)).thenReturn(false);
    when(store.subscription(job))
        .thenReturn(
            new AutomaticRenewalStore.SubscriptionSnapshot(
                SubscriptionStatus.ACTIVE, boundary, false, "RAZORPAY"));
    when(checkoutService.createOrResolveRenewalInvoice(eq(10L), eq(77L), any(), isNull()))
        .thenReturn(invoice(boundary));
    when(collectionService.prepareRecurringRenewal(10L, 20L, 77L))
        .thenReturn(checkout(PaymentStatus.FAILED));
    when(authorizationService.refreshBound(10L, 77L)).thenReturn(activeAuthorization());
    when(store.latestUnresolvedRecurringAttempt(30L)).thenReturn(null);

    service.processClaimed(job);

    verify(collectionService).prepareRecurringRenewal(10L, 20L, 77L);
    verify(chargeService).charge(30L, boundary);
    verify(confirmationService)
        .markRenewalDueUncollectible(30L, boundary, "DUE_COLLECTION_FAILED");
    verify(store).finish(job, "DUNNING", 20L, 30L, "DUE_COLLECTION_FAILED");
  }

  @Test
  void pendingProviderOutcomeIsObservedWithoutLaunchingDuplicateDueCharge() {
    Instant boundary = Instant.parse("2026-10-10T00:00:00Z");
    var job = job(boundary, 20L, 30L);
    when(store.paymentStatus(30L)).thenReturn(PaymentStatus.PENDING);
    when(store.subscription(job))
        .thenReturn(
            new AutomaticRenewalStore.SubscriptionSnapshot(
                SubscriptionStatus.ACTIVE, boundary, false, "RAZORPAY"));
    when(checkoutService.createOrResolveRenewalInvoice(eq(10L), eq(77L), any(), isNull()))
        .thenReturn(invoice(boundary));
    when(collectionService.prepareRecurringRenewal(10L, 20L, 77L))
        .thenReturn(checkout(PaymentStatus.PENDING));
    when(authorizationService.refreshBound(10L, 77L)).thenReturn(activeAuthorization());
    when(store.latestUnresolvedRecurringAttempt(30L)).thenReturn(41L);

    service.processClaimed(job);

    verify(chargeService).charge(30L, boundary);
    verify(store).release(eq(job), any(), eq("PROVIDER_PENDING"));
    verify(confirmationService, never())
        .markRenewalDueUncollectible(anyLong(), any(), anyString());
  }

  private AutomaticRenewalStore.Job job(
      Instant boundary, Long invoiceId, Long paymentId) {
    return new AutomaticRenewalStore.Job(
        1L, 10L, 77L, boundary, invoiceId, paymentId, 0, "claim-1");
  }

  private InvoiceResponse invoice(Instant boundary) {
    return new InvoiceResponse(
        "20",
        "10",
        "200",
        "INV-20",
        InvoiceStatus.OPEN,
        "INR",
        new BigDecimal("499.00"),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        new BigDecimal("499.00"),
        new BigDecimal("499.00"),
        boundary,
        boundary.plusSeconds(30L * 24L * 3600L),
        boundary.minusSeconds(60),
        boundary,
        null);
  }

  private InvoiceResponse zeroDueInvoice(Instant boundary) {
    return new InvoiceResponse(
        "20",
        "10",
        "200",
        "INV-20",
        InvoiceStatus.OPEN,
        "INR",
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        boundary,
        null,
        boundary.minusSeconds(60),
        boundary,
        null);
  }

  private PaymentCollectionService.CheckoutResult checkout(PaymentStatus status) {
    return new PaymentCollectionService.CheckoutResult(
        30L,
        20L,
        null,
        null,
        new BigDecimal("499.00"),
        "INR",
        status,
        null,
        null);
  }

  private RecurringAuthorizationService.AuthorizationResult activeAuthorization() {
    return new RecurringAuthorizationService.AuthorizationResult(
        90L, "UDS-I-555", "subs-session", "ACTIVE", null);
  }
}
