package com.messaging.billing.payment;

import com.messaging.billing.entity.Payment;
import com.messaging.billing.enums.PaymentAttemptStatus;
import com.messaging.billing.enums.PaymentStatus;
import com.messaging.billing.repository.PaymentRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentReconciliationService {

  private final JdbcTemplate jdbcTemplate;
  private final PaymentRepository paymentRepository;
  private final PaymentProviderGateway gateway;
  private final PaymentConfirmationService confirmationService;

  @Scheduled(
      fixedDelayString = "${app.billing.cashfree.reconciliation-interval-ms:300000}",
      initialDelayString = "${app.billing.cashfree.reconciliation-initial-delay-ms:60000}")
  public void reconcileAmbiguousPayments() {
    List<Long> ids =
        jdbcTemplate.queryForList(
            """
            select id from payments
             where provider = 'CASHFREE'
               and status in ('CREATED', 'PENDING', 'FAILED')
               and provider_order_id is not null
             order by updated_at asc
             limit 100
            """,
            Long.class);
    for (Long id : ids) reconcile(id);
  }

  public void reconcile(Long paymentId) {
    Payment payment = paymentRepository.findById(paymentId).orElse(null);
    if (payment == null || payment.getStatus() == PaymentStatus.SUCCEEDED) return;
    var remote = gateway.fetchOrderPayments(payment.getProviderOrderId());
    for (var attempt : remote) {
      if ("SUCCESS".equalsIgnoreCase(attempt.status())) {
        confirmationService.confirmSuccess(
            payment.getProviderOrderId(),
            attempt.providerPaymentId(),
            attempt.amount(),
            attempt.currency(),
            attempt.providerCreatedAt());
        return;
      }
    }
    for (var attempt : remote) {
      if ("PENDING".equalsIgnoreCase(attempt.status())) {
        confirmationService.recordFailure(
            payment.getProviderOrderId(),
            attempt.providerPaymentId(),
            attempt.amount(),
            attempt.currency(),
            attempt.errorCode(),
            PaymentAttemptStatus.PENDING,
            attempt.providerCreatedAt());
      }
    }
  }
}
