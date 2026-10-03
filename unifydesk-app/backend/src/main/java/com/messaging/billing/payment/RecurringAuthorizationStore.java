package com.messaging.billing.payment;

import com.messaging.common.exception.ConflictException;
import com.messaging.common.id.IdGenerator;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class RecurringAuthorizationStore {

  private final JdbcTemplate jdbcTemplate;

  @Transactional
  Prepared prepare(
      Long companyId, Long subscriptionId, BigDecimal maxAmount, String currency) {
    List<Prepared> existing =
        jdbcTemplate.query(
            """
            select id, company_id, subscription_id, provider_subscription_id,
                   provider_subscription_session_id, authorization_payment_id,
                   authorization_status, max_amount, currency
              from payment_provider_authorizations
             where company_id = ? and subscription_id = ? and provider = 'CASHFREE'
             for update
            """,
            (rs, rowNum) ->
                new Prepared(
                    rs.getLong("id"),
                    rs.getLong("company_id"),
                    rs.getLong("subscription_id"),
                    rs.getString("provider_subscription_id"),
                    rs.getString("provider_subscription_session_id"),
                    rs.getString("authorization_payment_id"),
                    rs.getString("authorization_status"),
                    rs.getBigDecimal("max_amount"),
                    rs.getString("currency"),
                    false),
            companyId,
            subscriptionId);
    if (!existing.isEmpty()) {
      Prepared prior = existing.getFirst();
      if (!prior.currency().equalsIgnoreCase(currency)) {
        throw new ConflictException("Recurring authorization currency mismatch");
      }
      if (prior.maxAmount().compareTo(maxAmount) < 0) {
        throw new ConflictException(
            "Existing recurring authorization does not cover the canonical invoice amount");
      }
      return prior;
    }

    long id = IdGenerator.nextId();
    String providerSubscriptionId = "UDS-" + subscriptionId;
    String authorizationPaymentId = "UDA-" + id;
    jdbcTemplate.update(
        """
        insert into payment_provider_authorizations (
          id, company_id, subscription_id, provider, provider_subscription_id,
          authorization_payment_id, authorization_status, max_amount, currency,
          created_at, updated_at
        ) values (?, ?, ?, 'CASHFREE', ?, ?, 'CREATE_PENDING', ?, ?, now(), now())
        """,
        id,
        companyId,
        subscriptionId,
        providerSubscriptionId,
        authorizationPaymentId,
        maxAmount,
        currency);
    return new Prepared(
        id,
        companyId,
        subscriptionId,
        providerSubscriptionId,
        null,
        authorizationPaymentId,
        "CREATE_PENDING",
        maxAmount,
        currency,
        true);
  }

  @Transactional
  Prepared applySubscription(
      long id, PaymentProviderGateway.RecurringAuthorization remote) {
    Prepared local = requireForUpdate(id);
    if (!local.providerSubscriptionId().equals(remote.subscriptionId())) {
      throw new ConflictException("Provider returned a conflicting subscription identity");
    }
    String status =
        "ACTIVE".equalsIgnoreCase(remote.authorizationStatus()) ? "ACTIVE" : "PENDING_AUTH";
    jdbcTemplate.update(
        """
        update payment_provider_authorizations
           set provider_subscription_session_id = coalesce(?, provider_subscription_session_id),
               authorization_status = ?,
               provider_status = ?,
               provider_updated_at = now(),
               updated_at = now()
         where id = ?
        """,
        remote.subscriptionSessionId(),
        status,
        remote.authorizationStatus(),
        id);
    return requireForUpdate(id);
  }

  @Transactional
  Prepared applyAuthorizationPayment(
      long id, PaymentProviderGateway.RemoteSubscriptionPayment remote) {
    Prepared local = requireForUpdate(id);
    if (!local.authorizationPaymentId().equals(remote.paymentId())) {
      throw new ConflictException("Provider returned a conflicting authorization payment identity");
    }
    String status =
        "SUCCESS".equalsIgnoreCase(remote.status())
            ? "ACTIVE"
            : ("FAILED".equalsIgnoreCase(remote.status()) ? "FAILED" : "PENDING_AUTH");
    jdbcTemplate.update(
        """
        update payment_provider_authorizations
           set provider_authorization_payment_id = coalesce(?, provider_authorization_payment_id),
               authorization_status = ?,
               provider_status = ?,
               provider_created_at = coalesce(?, provider_created_at),
               provider_updated_at = now(),
               updated_at = now()
         where id = ?
        """,
        remote.providerPaymentId(),
        status,
        remote.status(),
        remote.providerCreatedAt() == null ? null : Timestamp.from(remote.providerCreatedAt()),
        id);
    return requireForUpdate(id);
  }

  @Transactional
  void markAmbiguous(long id) {
    jdbcTemplate.update(
        """
        update payment_provider_authorizations
           set authorization_status =
                 case when authorization_status = 'ACTIVE' then 'ACTIVE' else 'AMBIGUOUS' end,
               updated_at = now()
         where id = ?
        """,
        id);
  }

  @Transactional
  void bindPayment(long authorizationId, Long paymentId) {
    Prepared authorization = requireForUpdate(authorizationId);
    jdbcTemplate.update(
        """
        update payments
           set provider_subscription_id = ?, updated_at = now()
         where id = ? and company_id = ? and subscription_id = ?
        """,
        authorization.providerSubscriptionId(),
        paymentId,
        authorization.companyId(),
        authorization.subscriptionId());
  }

  Prepared active(Long companyId, Long subscriptionId) {
    List<Prepared> rows =
        jdbcTemplate.query(
            """
            select id, company_id, subscription_id, provider_subscription_id,
                   provider_subscription_session_id, authorization_payment_id,
                   authorization_status, max_amount, currency
              from payment_provider_authorizations
             where company_id = ? and subscription_id = ? and provider = 'CASHFREE'
               and authorization_status = 'ACTIVE'
            """,
            (rs, rowNum) ->
                new Prepared(
                    rs.getLong("id"),
                    rs.getLong("company_id"),
                    rs.getLong("subscription_id"),
                    rs.getString("provider_subscription_id"),
                    rs.getString("provider_subscription_session_id"),
                    rs.getString("authorization_payment_id"),
                    rs.getString("authorization_status"),
                    rs.getBigDecimal("max_amount"),
                    rs.getString("currency"),
                    false),
            companyId,
            subscriptionId);
    return rows.isEmpty() ? null : rows.getFirst();
  }

  private Prepared requireForUpdate(long id) {
    List<Prepared> rows =
        jdbcTemplate.query(
            """
            select id, company_id, subscription_id, provider_subscription_id,
                   provider_subscription_session_id, authorization_payment_id,
                   authorization_status, max_amount, currency
              from payment_provider_authorizations
             where id = ?
             for update
            """,
            (rs, rowNum) ->
                new Prepared(
                    rs.getLong("id"),
                    rs.getLong("company_id"),
                    rs.getLong("subscription_id"),
                    rs.getString("provider_subscription_id"),
                    rs.getString("provider_subscription_session_id"),
                    rs.getString("authorization_payment_id"),
                    rs.getString("authorization_status"),
                    rs.getBigDecimal("max_amount"),
                    rs.getString("currency"),
                    false),
            id);
    if (rows.isEmpty()) throw new ConflictException("Recurring authorization no longer exists");
    return rows.getFirst();
  }

  record Prepared(
      long id,
      Long companyId,
      Long subscriptionId,
      String providerSubscriptionId,
      String subscriptionSessionId,
      String authorizationPaymentId,
      String status,
      BigDecimal maxAmount,
      String currency,
      boolean newlyCreated) {}
}
