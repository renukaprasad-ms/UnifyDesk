package com.messaging.billing.payment;

import com.messaging.billing.dto.InvoiceIdentitySnapshot;
import com.messaging.billing.dto.InvoiceResponse;
import com.messaging.billing.dto.SubscriptionCheckoutIntentResponse;
import com.messaging.billing.enums.PaymentPurpose;
import com.messaging.billing.service.SubscriptionCheckoutService;
import com.messaging.billing.service.UpgradeInvoiceService;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BillingPaymentOrchestrator {

  private final SubscriptionCheckoutService checkoutService;
  private final UpgradeInvoiceService upgradeInvoiceService;
  private final PaymentCollectionService collectionService;
  private final RecurringAuthorizationService recurringAuthorizationService;

  public PaymentCollectionService.CheckoutResult initialCheckout(
      Long companyId,
      Long planId,
      Long planPriceId,
      String commandKey,
      Long actorUserId,
      InvoiceIdentitySnapshot identity,
      PaymentProviderGateway.Customer customer) {
    Instant now = Instant.now();
    SubscriptionCheckoutIntentResponse intent =
        checkoutService.createOrResolveInitialCheckout(
            companyId, planId, planPriceId, commandKey, actorUserId, now, identity);
    var checkout =
        collectionService.createOrResolve(
            companyId,
            Long.valueOf(intent.invoice().invoiceId()),
            PaymentPurpose.INITIAL_SUBSCRIPTION,
            "payment:initial:" + intent.intentId(),
            Long.valueOf(intent.intentId()),
            null,
            null,
            customer);

    // Initial checkout is pure one-time order payment.
    // Company AutoPay is completely optional and setup separately after payment confirmation.
    return checkout;
  }

  public PaymentCollectionService.CheckoutResult upgrade(
      Long companyId,
      Long changeId,
      String quoteReference,
      PaymentProviderGateway.Customer customer) {
    InvoiceResponse invoice =
        upgradeInvoiceService.createOrResolveUpgradeInvoice(companyId, changeId, quoteReference);
    return collectionService.createOrResolve(
        companyId,
        Long.valueOf(invoice.invoiceId()),
        PaymentPurpose.UPGRADE,
        "payment:upgrade:" + changeId,
        null,
        changeId,
        null,
        customer);
  }

  public PaymentCollectionService.CheckoutResult renewal(
      Long companyId,
      Long subscriptionId,
      String commandKey,
      InvoiceIdentitySnapshot identity,
      PaymentProviderGateway.Customer customer) {
    InvoiceResponse invoice =
        checkoutService.createOrResolveRenewalInvoice(
            companyId, subscriptionId, Instant.now(), identity);

    // Pre-due preparation may establish/refresh recurring authorization, but it does not
    // advance the subscription lifecycle. The exact charge still comes from this invoice.
    var authorization =
        recurringAuthorizationService.prepareOrResolve(
            companyId,
            subscriptionId,
            invoice.amountDue(),
            invoice.currency(),
            customer);

    var checkout =
        collectionService.createOrResolve(
            companyId,
            Long.valueOf(invoice.invoiceId()),
            PaymentPurpose.RENEWAL,
            "payment:renewal:" + invoice.invoiceId() + ":" + commandKey,
            null,
            null,
            subscriptionId,
            customer);
    recurringAuthorizationService.bindPayment(authorization.authorizationId(), checkout.paymentId());
    return checkout.withRecurringAuthorization(
        authorization.subscriptionSessionId(), authorization.actionUrl());
  }
}
