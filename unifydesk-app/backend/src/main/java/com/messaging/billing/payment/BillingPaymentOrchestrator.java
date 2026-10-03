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
    return collectionService.createOrResolve(
        companyId,
        Long.valueOf(intent.invoice().invoiceId()),
        PaymentPurpose.INITIAL_SUBSCRIPTION,
        "payment:initial:" + intent.intentId(),
        Long.valueOf(intent.intentId()),
        null,
        null,
        customer);
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
    return collectionService.createOrResolve(
        companyId,
        Long.valueOf(invoice.invoiceId()),
        PaymentPurpose.RENEWAL,
        "payment:renewal:" + invoice.invoiceId() + ":" + commandKey,
        null,
        null,
        subscriptionId,
        customer);
  }
}
