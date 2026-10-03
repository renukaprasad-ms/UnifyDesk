package com.messaging.billing.payment;

import com.messaging.billing.enums.RefundStatus;
import com.messaging.billing.repository.RefundRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RefundReconciliationService {

  private final JdbcTemplate jdbcTemplate;
  private final RefundRepository refundRepository;
  private final RefundStore store;
  private final PaymentProviderGateway gateway;

  @Scheduled(
      fixedDelayString = "${app.billing.cashfree.reconciliation-interval-ms:300000}",
      initialDelayString = "${app.billing.cashfree.reconciliation-initial-delay-ms:60000}")
  public void reconcilePendingRefunds() {
    List<Long> ids =
        jdbcTemplate.queryForList(
            """
            select id
              from refunds
             where status in ('CREATED', 'PENDING')
               and provider_refund_id is not null
             order by updated_at asc
             limit 100
            """,
            Long.class);
    for (Long id : ids) {
      try {
        reconcile(id);
      } catch (RuntimeException ignored) {
        // Durable refund state remains pending/ambiguous and will be retried on the next poll.
      }
    }
  }

  public void reconcile(Long refundId) {
    var refund = refundRepository.findById(refundId).orElse(null);
    if (refund == null || refund.getStatus() == RefundStatus.SUCCEEDED) return;
    var remote =
        gateway.fetchRefund(
            refund.getPayment().getProviderOrderId(), refund.getProviderRefundId());
    store.applyRemote(refundId, remote);
  }
}
