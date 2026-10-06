package com.messaging.billing.payment;

import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Applies provider-confirmed renewals only when the canonical next service period begins. */
@Service
@RequiredArgsConstructor
public class RenewalBoundaryService {

  private final JdbcTemplate jdbcTemplate;
  private final PaymentConfirmationService confirmationService;

  @Scheduled(
      fixedDelayString = "${app.billing.cashfree.renewal-boundary-interval-ms:60000}",
      initialDelayString = "${app.billing.cashfree.renewal-boundary-initial-delay-ms:60000}")
  public void applyDueConfirmedRenewals() {
    List<Long> ids =
        jdbcTemplate.queryForList(
            """
            select p.id
              from payments p
              join invoices i on i.id = p.invoice_id
             where p.purpose = 'RENEWAL'
               and p.status = 'SUCCEEDED'
               and p.lifecycle_applied_at is null
               and i.service_period_start <= now()
             order by i.service_period_start, p.id
             limit 100
            """,
            Long.class);
    Instant now = Instant.now();
    for (Long id : ids) {
      try {
        confirmationService.applyDeferredRenewal(id, now);
      } catch (RuntimeException ignored) {
        // Payment and paid invoice remain durable; the idempotent B1 lifecycle boundary is retried.
      }
    }
  }
}
