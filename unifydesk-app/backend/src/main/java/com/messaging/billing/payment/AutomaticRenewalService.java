package com.messaging.billing.payment;

import com.messaging.billing.dto.InvoiceResponse;
import com.messaging.billing.enums.PaymentStatus;
import com.messaging.billing.enums.SubscriptionStatus;
import com.messaging.billing.service.SubscriptionCheckoutService;
import com.messaging.billing.service.SubscriptionLifecycleService;
import com.messaging.common.exception.ConflictException;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Restart-safe automatic paid renewal boundary.
 *
 * <p>This worker never recalculates pricing. It asks B1 for the canonical next period/invoice,
 * creates or resolves one B2 logical renewal Payment, binds the already-authorized Razorpay
 * subscription, and raises the exact canonical invoice amount through the recurring CHARGE path.
 */
@Service
@RequiredArgsConstructor
public class AutomaticRenewalService {

  private static final Duration PROVIDER_OBSERVATION_DELAY = Duration.ofHours(1);
  private static final Duration LOCAL_RETRY_DELAY = Duration.ofMinutes(5);

  private final AutomaticRenewalStore store;
  private final SubscriptionCheckoutService checkoutService;
  private final PaymentCollectionService collectionService;
  private final RecurringAuthorizationService authorizationService;
  private final RecurringChargeService chargeService;
  private final PaymentConfirmationService confirmationService;
  private final SubscriptionLifecycleService lifecycleService;
  private final RazorpayProperties properties;
  private final com.messaging.billing.service.BillingNotificationService billingNotificationService;

  @Scheduled(cron = "0 0 2 * * *", zone = "Asia/Kolkata")
  public void run() {
    if (!properties.isRecurringEnabled()) return;
    Instant now = Instant.now();
    store.discoverDue(now);
    for (AutomaticRenewalStore.Job job :
        store.claimDue(now, properties.getAutomaticRenewalClaimSeconds())) {
      processClaimed(job);
    }
  }

  void processClaimed(AutomaticRenewalStore.Job job) {
    Instant now = Instant.now();
    Long invoiceId = job.invoiceId();
    Long paymentId = job.paymentId();

    try {
      if (paymentId != null) {
        PaymentStatus priorStatus = store.paymentStatus(paymentId);
        if (priorStatus == PaymentStatus.SUCCEEDED) {
          confirmationService.applyDeferredRenewal(paymentId, now);
          store.finish(job, "RECOVERED", invoiceId, paymentId, null);
          return;
        }
        if (priorStatus == PaymentStatus.FAILED && store.hasDunning(paymentId)) {
          store.finish(job, "DUNNING", invoiceId, paymentId, null);
          return;
        }
      }

      AutomaticRenewalStore.SubscriptionSnapshot subscription = store.subscription(job);
      if (subscription == null) {
        store.finish(job, "SKIPPED", invoiceId, paymentId, "SUBSCRIPTION_MISSING");
        return;
      }
      if (store.zeroDueBoundaryAlreadyApplied(job)) {
        store.finish(job, "ZERO_DUE_APPLIED", invoiceId, null, null);
        return;
      }
      if (subscription.status() == SubscriptionStatus.PAST_DUE
          && paymentId != null
          && store.hasDunning(paymentId)) {
        store.finish(job, "DUNNING", invoiceId, paymentId, null);
        return;
      }
      if (subscription.status() != SubscriptionStatus.ACTIVE
          || subscription.cancelAtPeriodEnd()
          || !"RAZORPAY".equals(subscription.paymentPolicy())
          || subscription.currentPeriodEnd() == null
          || !subscription.currentPeriodEnd().equals(job.dueBoundary())) {
        store.finish(job, "SKIPPED", invoiceId, paymentId, "SUBSCRIPTION_NOT_DUE");
        return;
      }

      InvoiceResponse invoice =
          checkoutService.createOrResolveRenewalInvoice(
              job.companyId(), job.subscriptionId(), now, null);
      invoiceId = Long.valueOf(invoice.invoiceId());
      if (job.invoiceId() != null && !job.invoiceId().equals(invoiceId)) {
        throw new ConflictException(
            "Renewal job is already bound to a different immutable invoice");
      }
      if (invoice.servicePeriodStart() == null
          || !invoice.servicePeriodStart().equals(job.dueBoundary())) {
        throw new ConflictException(
            "Automatic renewal invoice does not match the claimed renewal boundary");
      }

      if (invoice.amountDue().signum() == 0) {
        if (paymentId != null) {
          throw new ConflictException(
              "Zero-due boundary is already bound to provider payment state");
        }
        store.bindFinancialObjects(job, invoiceId, null);
        lifecycleService.applyZeroDueBoundaryTransition(job.subscriptionId(), invoiceId, now);
        store.finish(job, "ZERO_DUE_APPLIED", invoiceId, null, null);
        return;
      }

      PaymentCollectionService.CheckoutResult payment =
          collectionService.prepareRecurringRenewal(
              job.companyId(), invoiceId, job.subscriptionId());
      paymentId = payment.paymentId();
      store.bindFinancialObjects(job, invoiceId, paymentId);

      if (payment.status() == PaymentStatus.SUCCEEDED) {
        confirmationService.applyDeferredRenewal(paymentId, now);
        store.finish(job, "RECOVERED", invoiceId, paymentId, null);
        billingNotificationService.sendRenewalInvoiceEmail(job.companyId(), invoiceId);
        return;
      }

      RecurringAuthorizationService.AuthorizationResult authorization =
          authorizationService.refreshBound(job.companyId(), job.subscriptionId());
      if (authorization == null || !"ACTIVE".equals(authorization.status())) {
        confirmationService.markRenewalDueUncollectible(
            paymentId, job.dueBoundary(), "RECURRING_AUTHORIZATION_NOT_ACTIVE");
        store.finish(job, "DUNNING", invoiceId, paymentId, "RECURRING_AUTHORIZATION_NOT_ACTIVE");
        return;
      }

      authorizationService.bindPayment(authorization.authorizationId(), paymentId);

      // If an earlier or interrupted recurring command is unresolved, charge() first fetches that
      // same command identity. It creates a new command only after provider truth proves there is no
      // unresolved command to recover.
      chargeService.charge(paymentId, job.dueBoundary());

      PaymentStatus after = store.paymentStatus(paymentId);
      if (after == PaymentStatus.SUCCEEDED) {
        confirmationService.applyDeferredRenewal(paymentId, now);
        store.finish(job, "RECOVERED", invoiceId, paymentId, null);
        billingNotificationService.sendRenewalInvoiceEmail(job.companyId(), invoiceId);
        return;
      }
      if (store.latestUnresolvedRecurringAttempt(paymentId) != null) {
        store.release(job, now.plus(PROVIDER_OBSERVATION_DELAY), "PROVIDER_PENDING");
        return;
      }

      if (after == PaymentStatus.FAILED) {
        // Anchor the lifecycle/dunning decision to the canonical due boundary even if a provider
        // response reports a strange or missing event timestamp.
        confirmationService.markRenewalDueUncollectible(
            paymentId, job.dueBoundary(), "DUE_COLLECTION_FAILED");
        store.finish(job, "DUNNING", invoiceId, paymentId, "DUE_COLLECTION_FAILED");
        return;
      }

      store.release(job, now.plus(PROVIDER_OBSERVATION_DELAY), "COLLECTION_NOT_TERMINAL");
    } catch (ConflictException exception) {
      store.finish(job, "CONFLICT", invoiceId, paymentId, "COMMERCIAL_BOUNDARY_CONFLICT");
    } catch (RuntimeException exception) {
      if (paymentId != null) {
        try {
          PaymentStatus status = store.paymentStatus(paymentId);
          if (status == PaymentStatus.SUCCEEDED) {
            confirmationService.applyDeferredRenewal(paymentId, now);
            store.finish(job, "RECOVERED", invoiceId, paymentId, null);
            return;
          }
          if (store.latestUnresolvedRecurringAttempt(paymentId) != null) {
            store.release(
                job, now.plus(PROVIDER_OBSERVATION_DELAY), exception.getClass().getSimpleName());
            return;
          }
          if (status == PaymentStatus.FAILED) {
            confirmationService.markRenewalDueUncollectible(
                paymentId, job.dueBoundary(), "DUE_COLLECTION_FAILED");
            store.finish(
                job, "DUNNING", invoiceId, paymentId, exception.getClass().getSimpleName());
            return;
          }
        } catch (RuntimeException recoveryFailure) {
          // Fall through to a bounded local retry. Durable invoice/payment/provider identities
          // remain unchanged and the job lease is released.
        }
      }
      store.release(job, now.plus(LOCAL_RETRY_DELAY), exception.getClass().getSimpleName());
    }
  }
}
