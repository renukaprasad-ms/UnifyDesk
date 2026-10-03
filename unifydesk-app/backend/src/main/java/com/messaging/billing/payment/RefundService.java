package com.messaging.billing.payment;

import com.messaging.billing.entity.Payment;
import com.messaging.billing.entity.Refund;
import com.messaging.billing.enums.PaymentStatus;
import com.messaging.billing.enums.RefundStatus;
import com.messaging.billing.repository.PaymentRepository;
import com.messaging.billing.repository.RefundRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.ConflictException;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RefundService {

  private final RefundRepository refundRepository;
  private final PaymentRepository paymentRepository;
  private final PaymentProviderGateway gateway;
  private final JdbcTemplate jdbcTemplate;

  @Transactional
  public Refund refund(
      Long companyId,
      Long paymentId,
      BigDecimal amount,
      String idempotencyKey,
      String note) {
    if (amount == null || amount.signum() <= 0) {
      throw new BadRequestException("Refund amount must be positive");
    }
    String key =
        idempotencyKey == null || idempotencyKey.isBlank() ? null : idempotencyKey.trim();
    if (key == null) throw new BadRequestException("Refund idempotency key is required");

    var prior = refundRepository.findByCompanyIdAndIdempotencyKey(companyId, key);
    if (prior.isPresent()) {
      Refund existing = prior.get();
      if (!existing.getPayment().getId().equals(paymentId)
          || existing.getAmount().compareTo(amount) != 0) {
        throw new ConflictException(
            "Refund idempotency key was reused for a materially different refund");
      }
      return existing;
    }

    Payment payment =
        paymentRepository
            .findByIdForUpdate(paymentId)
            .orElseThrow(() -> new BadRequestException("Payment not found"));
    if (!payment.getCompany().getId().equals(companyId)) {
      throw new BadRequestException("Payment does not belong to the company");
    }
    if (payment.getStatus() != PaymentStatus.SUCCEEDED) {
      throw new ConflictException("Only a successful payment can be refunded");
    }

    BigDecimal alreadyRequested =
        jdbcTemplate.queryForObject(
            """
            select coalesce(sum(amount), 0)
              from refunds
             where payment_id = ?
               and status in ('CREATED', 'PENDING', 'SUCCEEDED')
            """,
            BigDecimal.class,
            paymentId);
    if (alreadyRequested == null) alreadyRequested = BigDecimal.ZERO;
    if (alreadyRequested.add(amount).compareTo(payment.getAmount()) > 0) {
      throw new ConflictException("Refund total cannot exceed the successful payment");
    }

    Refund refund = new Refund();
    refund.setCompanyId(companyId);
    refund.setPayment(payment);
    refund.setAmount(amount);
    refund.setCurrency(payment.getCurrency());
    refund.setIdempotencyKey(key);
    refund = refundRepository.saveAndFlush(refund);

    String providerRefundId = "UDR-" + refund.getId();
    var remote =
        gateway.createRefund(
            payment.getProviderOrderId(),
            providerRefundId,
            amount,
            note,
            "refund:" + refund.getId());
    refund.setProviderRefundId(
        remote.providerRefundId() == null ? providerRefundId : remote.providerRefundId());
    refund.setProviderStatus(remote.status());
    refund.setStatus(
        "SUCCESS".equalsIgnoreCase(remote.status()) ? RefundStatus.SUCCEEDED : RefundStatus.PENDING);
    return refundRepository.saveAndFlush(refund);
  }
}
