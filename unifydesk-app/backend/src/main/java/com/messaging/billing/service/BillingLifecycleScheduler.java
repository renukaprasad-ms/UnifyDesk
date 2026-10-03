package com.messaging.billing.service;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BillingLifecycleScheduler {

  private final SubscriptionLifecycleService subscriptionLifecycleService;
  private final SubscriptionChangeService subscriptionChangeService;
  private final BillingPeriodService billingPeriodService;
  private final InvoiceService invoiceService;

  @Scheduled(
      fixedDelayString = "${app.billing.lifecycle.poll-interval-ms:60000}",
      initialDelayString = "${app.billing.lifecycle.initial-delay-ms:30000}")
  public void run() {
    Instant now = Instant.now();
    subscriptionLifecycleService.applyDueLifecycle(now);
    subscriptionChangeService.applyDueDowngrades(now);
    billingPeriodService.markDueClosing(now);
    invoiceService.finalizeReadyClosingPeriods(now);
  }
}
