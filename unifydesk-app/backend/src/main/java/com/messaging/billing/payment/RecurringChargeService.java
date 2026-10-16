package com.messaging.billing.payment;

import com.messaging.billing.enums.PaymentAttemptStatus;
import com.messaging.common.exception.ConflictException;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

@Service
@RequiredArgsConstructor
public class RecurringChargeService {

  private final RecurringAuthorizationStore authorizationStore;
  private final RecurringChargeStore chargeStore;
  private final PaymentProviderGateway gateway;
  private final PaymentConfirmationService confirmationService;
  private final JdbcTemplate jdbcTemplate;

  public void charge(Long paymentId, Instant scheduledAt) {
    var payment =
        jdbcTemplate.queryForList(
            "select company_id from payments where id = ?", Long.class, paymentId);
    if (payment.isEmpty()) throw new ConflictException("Payment not found for recurring charge");

    Long companyId = payment.getFirst();
    Long subscriptionId =
        jdbcTemplate.queryForObject(
            "select subscription_id from payments where id = ?", Long.class, paymentId);
    if (subscriptionId == null) {
      throw new ConflictException("Renewal payment has no subscription");
    }

    RecurringAuthorizationStore.Prepared authorization =
        authorizationStore.active(companyId, subscriptionId);
    if (authorization == null) {
      throw new ConflictException("No active recurring authorization is available");
    }

    RecurringChargeStore.Prepared prepared =
        chargeStore.prepare(paymentId, authorization.providerSubscriptionId());
    if (prepared.logicalSucceeded()) return;

    // This is the final local commercial gate before any provider read/submit for the recurring
    // command. A stale command is terminalized locally and never reaches Cashfree.
    try {
      chargeStore.assertCommerciallyCurrent(prepared, scheduledAt);
    } catch (ConflictException conflict) {
      chargeStore.markCommercialConflict(prepared.attemptId());
      throw conflict;
    }

    try {
      PaymentProviderGateway.RemoteSubscriptionPayment remote =
          prepared.newlyCreated()
              ? submit(prepared, scheduledAt)
              : recoverOrSubmitIfDefinitelyAbsent(prepared, scheduledAt);
      applyRemote(prepared, remote);
    } catch (RuntimeException exception) {
      chargeStore.markAmbiguous(prepared.attemptId());
      throw exception;
    }
  }

  public void reconcileAttempt(Long attemptId) {
    RecurringChargeStore.Prepared prepared = chargeStore.require(attemptId);
    if (prepared.logicalSucceeded()) return;
    try {
      PaymentProviderGateway.RemoteSubscriptionPayment remote =
          gateway.fetchSubscriptionPayment(
              prepared.providerSubscriptionId(), prepared.providerCommandId());
      applyRemote(prepared, remote);
    } catch (RuntimeException exception) {
      chargeStore.markAmbiguous(attemptId);
      throw exception;
    }
  }

  @Scheduled(
      fixedDelayString = "${app.billing.cashfree.reconciliation-interval-ms:300000}",
      initialDelayString = "${app.billing.cashfree.reconciliation-initial-delay-ms:60000}")
  public void reconcileAmbiguousRecurringCharges() {
    List<Long> ids =
        jdbcTemplate.queryForList(
            """
            select id
              from payment_attempts
             where provider_command_id is not null
               and status in ('CREATED', 'PENDING')
               and provider_submission_state in ('AMBIGUOUS', 'CONFIRMED')
             order by created_at
             limit 100
            """,
            Long.class);
    for (Long id : ids) {
      try {
        reconcileAttempt(id);
      } catch (RuntimeException ignored) {
        // Durable attempt remains recoverable and will be retried on the next reconciliation poll.
      }
    }
  }

  private PaymentProviderGateway.RemoteSubscriptionPayment recoverOrSubmitIfDefinitelyAbsent(
      RecurringChargeStore.Prepared prepared, Instant scheduledAt) {
    try {
      return gateway.fetchSubscriptionPayment(
          prepared.providerSubscriptionId(), prepared.providerCommandId());
    } catch (RestClientResponseException exception) {
      boolean isAbsent = exception.getStatusCode().value() == 404
          || (exception.getStatusCode().value() == 400
              && (exception.getResponseBodyAsString().contains("payment_id_not_found")
                  || exception.getResponseBodyAsString().contains("payment_not_found")));
      if (!isAbsent) throw exception;
      return submit(prepared, scheduledAt);
    }
  }

  private PaymentProviderGateway.RemoteSubscriptionPayment submit(
      RecurringChargeStore.Prepared prepared, Instant scheduledAt) {
    return gateway.chargeAuthorizedSubscription(
        prepared.providerSubscriptionId(),
        prepared.providerCommandId(),
        prepared.amount(),
        scheduledAt,
        ProviderIdempotency.key("recurring-charge", prepared.attemptId()));
  }

  private void applyRemote(
      RecurringChargeStore.Prepared prepared,
      PaymentProviderGateway.RemoteSubscriptionPayment remote) {
    if (!prepared.providerCommandId().equals(remote.paymentId())) {
      throw new ConflictException("Recurring charge returned a conflicting command identity");
    }
    if (remote.amount() != null && prepared.amount().compareTo(remote.amount()) != 0) {
      throw new ConflictException("Recurring charge amount does not match canonical invoice");
    }
    if (remote.currency() != null
        && !prepared.currency().equalsIgnoreCase(remote.currency())) {
      throw new ConflictException("Recurring charge currency does not match canonical invoice");
    }

    chargeStore.bindRemote(prepared.attemptId(), remote);
    PaymentAttemptStatus status =
        "SUCCESS".equalsIgnoreCase(remote.status())
            ? PaymentAttemptStatus.SUCCEEDED
            : ("PENDING".equalsIgnoreCase(remote.status())
                    || "INITIALIZED".equalsIgnoreCase(remote.status())
                ? PaymentAttemptStatus.PENDING
                : PaymentAttemptStatus.FAILED);
    confirmationService.confirmRecurringAttempt(
        prepared.providerCommandId(),
        prepared.providerSubscriptionId(),
        remote.providerPaymentId(),
        prepared.amount(),
        prepared.currency(),
        status,
        remote.failureCode(),
        remote.providerCreatedAt());
  }
}
