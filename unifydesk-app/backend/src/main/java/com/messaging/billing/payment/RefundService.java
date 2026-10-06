package com.messaging.billing.payment;

import com.messaging.billing.entity.Refund;
import com.messaging.billing.enums.RefundStatus;
import com.messaging.billing.repository.RefundRepository;
import com.messaging.common.exception.BadRequestException;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

@Service
@RequiredArgsConstructor
public class RefundService {

  private final RefundStore store;
  private final RefundRepository refundRepository;
  private final PaymentProviderGateway gateway;

  /**
   * Durable orchestration boundary:
   * local refund intent commit -> provider call -> provider result commit.
   */
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

    RefundStore.Prepared prepared = store.prepare(companyId, paymentId, amount, key);
    if (prepared.status() == RefundStatus.SUCCEEDED) {
      return refundRepository.findById(prepared.refundId()).orElseThrow();
    }

    try {
      PaymentProviderGateway.RemoteRefund remote;
      if (prepared.newlyCreated()) {
        remote = createRemote(prepared, note);
      } else {
        remote = recoverOrCreateIfDefinitelyAbsent(prepared, note);
      }
      store.applyRemote(prepared.refundId(), remote);
      return refundRepository.findById(prepared.refundId()).orElseThrow();
    } catch (RuntimeException exception) {
      store.markAmbiguous(prepared.refundId());
      throw exception;
    }
  }

  private PaymentProviderGateway.RemoteRefund recoverOrCreateIfDefinitelyAbsent(
      RefundStore.Prepared prepared, String note) {
    try {
      return gateway.fetchRefund(prepared.providerOrderId(), prepared.providerRefundId());
    } catch (RestClientResponseException exception) {
      if (exception.getStatusCode().value() != 404) throw exception;
      return createRemote(prepared, note);
    }
  }

  private PaymentProviderGateway.RemoteRefund createRemote(
      RefundStore.Prepared prepared, String note) {
    return gateway.createRefund(
        prepared.providerOrderId(),
        prepared.providerRefundId(),
        prepared.amount(),
        note,
        ProviderIdempotency.key("refund", prepared.refundId()));
  }
}
