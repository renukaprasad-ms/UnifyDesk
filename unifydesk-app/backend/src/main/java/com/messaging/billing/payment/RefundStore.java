package com.messaging.billing.payment;

import com.messaging.billing.entity.Payment;
import com.messaging.billing.entity.Refund;
import com.messaging.billing.enums.PaymentStatus;
import com.messaging.billing.enums.RefundStatus;
import com.messaging.billing.repository.PaymentRepository;
import com.messaging.billing.repository.RefundRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.ConflictException;
import com.messaging.common.id.IdGenerator;
import java.math.BigDecimal;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Local durable half of refund orchestration. This bean never performs provider I/O. */
@Service
@RequiredArgsConstructor
class RefundStore {

  private final RefundRepository refundRepository;
  private final PaymentRepository paymentRepository;
  private final JdbcTemplate jdbcTemplate;

  @Transactional
  Prepared prepare(
      Long companyId,
      Long paymentId,
      BigDecimal amount,
      String idempotencyKey) {
    var prior = refundRepository.findByCompanyIdAndIdempotencyKey(companyId, idempotencyKey);
    if (prior.isPresent()) {
      Refund existing = prior.get();
      assertSame(existing, paymentId, amount);
      return snapshot(existing, false);
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
    refund.setId(IdGenerator.nextId());
    refund.setCompanyId(companyId);
    refund.setPayment(payment);
    refund.setAmount(amount);
    refund.setCurrency(payment.getCurrency());
    refund.setIdempotencyKey(idempotencyKey);
    refund.setProviderRefundId("UDR-" + refund.getId());
    refund.setProviderOperationState("CREATE_PENDING");
    refund = refundRepository.saveAndFlush(refund);
    return snapshot(refund, true);
  }

  @Transactional
  Prepared applyRemote(Long refundId, PaymentProviderGateway.RemoteRefund remote) {
    Refund refund =
        refundRepository
            .findByIdForUpdate(refundId)
            .orElseThrow(() -> new ConflictException("Refund intent no longer exists"));
    if (!refund.getProviderRefundId().equals(remote.refundId())) {
      throw new ConflictException("Provider returned a conflicting refund identity");
    }
    if (remote.amount() != null && refund.getAmount().compareTo(remote.amount()) != 0) {
      throw new ConflictException("Provider refund amount conflicts with canonical refund");
    }
    if (remote.currency() != null
        && !refund.getCurrency().equalsIgnoreCase(remote.currency())) {
      throw new ConflictException("Provider refund currency conflicts with canonical refund");
    }

    refund.setProviderRefundReference(remote.providerReference());
    refund.setProviderStatus(remote.status());
    refund.setProviderCreatedAt(remote.providerCreatedAt());
    refund.setProviderProcessedAt(remote.providerProcessedAt());
    refund.setProviderOperationState("CONFIRMED");

    RefundStatus incoming = map(remote.status());
    if (refund.getStatus() != RefundStatus.SUCCEEDED) {
      refund.setStatus(incoming);
    }
    return snapshot(refundRepository.saveAndFlush(refund), false);
  }

  @Transactional
  void markAmbiguous(Long refundId) {
    refundRepository
        .findByIdForUpdate(refundId)
        .ifPresent(
            refund -> {
              if (refund.getStatus() != RefundStatus.SUCCEEDED) {
                refund.setProviderOperationState("AMBIGUOUS");
                if (refund.getStatus() == RefundStatus.CREATED) {
                  refund.setStatus(RefundStatus.PENDING);
                }
                refundRepository.saveAndFlush(refund);
              }
            });
  }

  private RefundStatus map(String status) {
    if (status == null) return RefundStatus.PENDING;
    return switch (status.toUpperCase(Locale.ROOT)) {
      case "SUCCESS" -> RefundStatus.SUCCEEDED;
      case "CANCELLED" -> RefundStatus.CANCELLED;
      case "REJECTED" -> RefundStatus.FAILED;
      default -> RefundStatus.PENDING;
    };
  }

  private void assertSame(Refund refund, Long paymentId, BigDecimal amount) {
    if (!refund.getPayment().getId().equals(paymentId)
        || refund.getAmount().compareTo(amount) != 0) {
      throw new ConflictException(
          "Refund idempotency key was reused for a materially different refund");
    }
  }

  private Prepared snapshot(Refund refund, boolean newlyCreated) {
    return new Prepared(
        refund.getId(),
        refund.getCompanyId(),
        refund.getPayment().getId(),
        refund.getPayment().getProviderOrderId(),
        refund.getProviderRefundId(),
        refund.getProviderOperationState(),
        refund.getStatus(),
        refund.getAmount(),
        refund.getCurrency(),
        newlyCreated);
  }

  record Prepared(
      Long refundId,
      Long companyId,
      Long paymentId,
      String providerOrderId,
      String providerRefundId,
      String providerOperationState,
      RefundStatus status,
      BigDecimal amount,
      String currency,
      boolean newlyCreated) {}
}
