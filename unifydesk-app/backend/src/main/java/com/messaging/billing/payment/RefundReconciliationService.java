package com.messaging.billing.payment;

import com.messaging.billing.enums.RefundStatus;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RefundReconciliationService {

  private final JdbcTemplate jdbcTemplate;
  private final RefundStore store;
  private final PaymentProviderGateway gateway;

  @Scheduled(
      fixedDelayString = "${app.billing.razorpay.reconciliation-interval-ms:300000}",
      initialDelayString = "${app.billing.razorpay.reconciliation-initial-delay-ms:60000}")
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
    List<Row> rows =
        jdbcTemplate.query(
            """
            select r.status, r.provider_refund_id, p.provider_order_id
              from refunds r
              join payments p on p.id = r.payment_id
             where r.id = ?
            """,
            (rs, rowNum) ->
                new Row(
                    RefundStatus.valueOf(rs.getString("status")),
                    rs.getString("provider_refund_id"),
                    rs.getString("provider_order_id")),
            refundId);
    if (rows.isEmpty() || rows.getFirst().status() == RefundStatus.SUCCEEDED) return;
    Row row = rows.getFirst();
    var remote = gateway.fetchRefund(row.providerOrderId(), row.providerRefundId());
    store.applyRemote(refundId, remote);
  }

  private record Row(
      RefundStatus status, String providerRefundId, String providerOrderId) {}
}
