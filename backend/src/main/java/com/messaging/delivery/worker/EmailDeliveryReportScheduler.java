package com.messaging.delivery.worker;

import com.messaging.delivery.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailDeliveryReportScheduler {

  private final EmailDeliveryReportClaimService claimService;
  private final EmailDeliveryReportReconciliationService reconciliationService;

  @Scheduled(fixedDelayString = "${app.delivery.email.poll-interval-ms:5000}")
  public void process() {
    for (Long reportId : claimService.claim()) {
      try {
        reconciliationService.reconcile(reportId);
      } catch (RuntimeException exception) {
        log.warn(
            "email_delivery_report_reconciliation_failed reportId={} reason={}",
            reportId,
            exception.getClass().getSimpleName(),
            exception);
      }
    }
  }
}
