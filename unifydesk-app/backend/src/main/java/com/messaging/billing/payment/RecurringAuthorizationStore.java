package com.messaging.billing.payment;

import com.messaging.common.exception.ConflictException;
import com.messaging.common.id.IdGenerator;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
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
  Prepared prepare(Long companyId, Long subscriptionId, BigDecimal maxAmount, String currency) {
    List<Prepared> existing =
        queryForUpdate(
            """
            select id, company_id, subscription_id, checkout_intent_id,
                   provider_subscription_id, provider_subscription_session_id,
                   authorization_payment_id, authorization_status, max_amount, currency
              from payment_provider_authorizations
             where company_id = ? and subscription_id = ? and provider = 'RAZORPAY'
             for update
            """,
            companyId,
            subscriptionId);
    if (!existing.isEmpty()) {
      return requireCoverage(existing.getFirst(), maxAmount, currency);
    }
    return insert(
        companyId,
        subscriptionId,
        null,
        "UDS-" + subscriptionId,
        maxAmount,
        currency);
  }

  @Transactional
  Prepared prepareInitial(
      Long companyId, Long checkoutIntentId, BigDecimal maxAmount, String currency) {
    List<Prepared> existing =
        queryForUpdate(
            """
            select id, company_id, subscription_id, checkout_intent_id,
                   provider_subscription_id, provider_subscription_session_id,
                   authorization_payment_id, authorization_status, max_amount, currency
              from payment_provider_authorizations
             where company_id = ? and checkout_intent_id = ? and provider = 'RAZORPAY'
             for update
            """,
            companyId,
            checkoutIntentId);
    if (!existing.isEmpty()) {
      return requireCoverage(existing.getFirst(), maxAmount, currency);
    }
    return insert(
        companyId,
        null,
        checkoutIntentId,
        "UDS-I-" + checkoutIntentId,
        maxAmount,
        currency);
  }

  @Transactional
  Prepared prepareCompanyAutopay(Long companyId, BigDecimal maxAmount, String currency) {
    List<Prepared> existing =
        queryForUpdate(
            """
            select id, company_id, subscription_id, checkout_intent_id,
                   provider_subscription_id, provider_subscription_session_id,
                   authorization_payment_id, authorization_status, max_amount, currency,
                   provider_authorization_payment_id, payment_method_type
              from payment_provider_authorizations
             where company_id = ? and is_company_autopay = true and provider = 'RAZORPAY'
             order by case when authorization_status = 'ACTIVE' then 0 else 1 end, id desc
             for update
            """,
            companyId);
    if (!existing.isEmpty()) {
      Prepared current = existing.getFirst();
      if ("ACTIVE".equalsIgnoreCase(current.status())) {
        return current;
      }
      String newSubId = "UDS-AP-" + companyId + "-" + IdGenerator.nextId();
      jdbcTemplate.update(
          """
          update payment_provider_authorizations
             set provider_subscription_id = ?,
                 authorization_status = 'CREATE_PENDING',
                 provider_status = 'CREATE_PENDING',
                 max_amount = ?,
                 currency = ?,
                 updated_at = now()
           where id = ?
          """,
          newSubId,
          maxAmount,
          currency,
          current.id());
      return requireForUpdate(current.id());
    }
    return insertCompanyAutopay(
        companyId,
        "UDS-AP-" + companyId + "-" + IdGenerator.nextId(),
        maxAmount,
        currency);
  }

  @Transactional(readOnly = true)
  Prepared findActiveCompanyAutopay(Long companyId) {
    List<Prepared> rows =
        query(
            """
            select id, company_id, subscription_id, checkout_intent_id,
                   provider_subscription_id, provider_subscription_session_id,
                   authorization_payment_id, authorization_status, max_amount, currency,
                   provider_authorization_payment_id, payment_method_type
              from payment_provider_authorizations
             where company_id = ? and is_company_autopay = true and provider = 'RAZORPAY'
               and authorization_status = 'ACTIVE'
             order by id desc
            """,
            companyId);
    return rows.isEmpty() ? null : rows.getFirst();
  }

  @Transactional(readOnly = true)
  Prepared findCompanyAutopay(Long companyId) {
    List<Prepared> rows =
        query(
            """
            select id, company_id, subscription_id, checkout_intent_id,
                   provider_subscription_id, provider_subscription_session_id,
                   authorization_payment_id, authorization_status, max_amount, currency,
                   provider_authorization_payment_id, payment_method_type
              from payment_provider_authorizations
             where company_id = ? and is_company_autopay = true and provider = 'RAZORPAY'
             order by case when authorization_status = 'ACTIVE' then 0 else 1 end, id desc
            """,
            companyId);
    return rows.isEmpty() ? null : rows.getFirst();
  }

  @Transactional
  boolean cancelCompanyAutopay(Long companyId) {
    int updated =
        jdbcTemplate.update(
            """
            update payment_provider_authorizations
               set authorization_status = 'CANCELLED',
                   updated_at = now()
             where company_id = ? and is_company_autopay = true
            """,
            companyId);
    return updated > 0;
  }

  @Transactional
  Prepared activateCompanyAutopay(
      long id, String tokenId, String paymentId, String customerId) {
    Prepared local = requireForUpdate(id);
    String effectiveSubId = (tokenId != null && !tokenId.isBlank()) ? tokenId : local.providerSubscriptionId();
    jdbcTemplate.update(
        """
        update payment_provider_authorizations
           set provider_subscription_id = coalesce(cast(? as varchar), provider_subscription_id),
               provider_authorization_payment_id = coalesce(cast(? as varchar), provider_authorization_payment_id),
               payment_method_type = coalesce(cast(? as varchar), payment_method_type),
               authorization_status = 'ACTIVE',
               provider_status = 'ACTIVE',
               provider_updated_at = now(),
               updated_at = now()
         where id = ?
        """,
        effectiveSubId,
        paymentId,
        customerId,
        id);
    return requireForUpdate(id);
  }

  @Transactional
  Prepared applySubscription(long id, PaymentProviderGateway.RecurringAuthorization remote) {
    Prepared local = requireForUpdate(id);
    if (!local.providerSubscriptionId().equals(remote.subscriptionId())) {
      throw new ConflictException("Provider returned a conflicting subscription identity");
    }
    String status =
        "ACTIVE".equalsIgnoreCase(remote.authorizationStatus()) ? "ACTIVE" : "PENDING_AUTH";
    jdbcTemplate.update(
        """
        update payment_provider_authorizations
           set provider_subscription_session_id = coalesce(cast(? as varchar), provider_subscription_session_id),
               payment_method_type = coalesce(cast(? as varchar), payment_method_type),
               authorization_status = ?,
               provider_status = ?,
               provider_updated_at = now(),
               updated_at = now()
         where id = ?
        """,
        remote.subscriptionSessionId(),
        remote.providerSubscriptionReference(),
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
    if (local.status().equals("ACTIVE") && !"SUCCESS".equalsIgnoreCase(remote.status())) {
      return local;
    }
    String status =
        "SUCCESS".equalsIgnoreCase(remote.status())
            ? "ACTIVE"
            : ("FAILED".equalsIgnoreCase(remote.status()) ? "FAILED" : "PENDING_AUTH");
    jdbcTemplate.update(
        """
        update payment_provider_authorizations
           set provider_authorization_payment_id = coalesce(cast(? as varchar), provider_authorization_payment_id),
               authorization_status = ?,
               provider_status = ?,
               provider_created_at = coalesce(provider_created_at, cast(? as timestamp)),
               provider_updated_at = now(),
               updated_at = now()
         where id = ?
        """,
        remote.providerPaymentId(),
        status,
        remote.status(),
        remote.providerCreatedAt() == null ? null : Timestamp.from(remote.providerCreatedAt()),
        id);
    Prepared updated = requireForUpdate(id);
    if (remote.providerPaymentId() != null
        && updated.providerAuthorizationPaymentId() != null
        && !updated.providerAuthorizationPaymentId().equals(remote.providerPaymentId())) {
      throw new ConflictException("Provider authorization payment identity conflicts locally");
    }
    return updated;
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
  void bindInitialActivation(Long companyId, Long checkoutIntentId, Long subscriptionId) {
    List<Prepared> rows =
        queryForUpdate(
            """
            select id, company_id, subscription_id, checkout_intent_id,
                   provider_subscription_id, provider_subscription_session_id,
                   authorization_payment_id, authorization_status, max_amount, currency,
                   provider_authorization_payment_id
              from payment_provider_authorizations
             where company_id = ? and checkout_intent_id = ? and provider = 'RAZORPAY'
             for update
            """,
            companyId,
            checkoutIntentId);
    if (rows.isEmpty()) {
      return;
    }
    Prepared authorization = rows.getFirst();
    if (authorization.subscriptionId() != null) {
      if (!authorization.subscriptionId().equals(subscriptionId)) {
        throw new ConflictException("Initial recurring authorization is bound to another subscription");
      }
      return;
    }

    Integer conflicting =
        jdbcTemplate.queryForObject(
            """
            select count(*)
              from payment_provider_authorizations
             where company_id=? and subscription_id=? and provider='RAZORPAY' and id<>?
            """,
            Integer.class,
            companyId,
            subscriptionId,
            authorization.id());
    if (conflicting != null && conflicting > 0) {
      throw new ConflictException("Subscription already has another recurring authorization");
    }

    jdbcTemplate.update(
        """
        update payment_provider_authorizations
           set subscription_id=?, updated_at=now()
         where id=? and company_id=? and checkout_intent_id=?
        """,
        subscriptionId,
        authorization.id(),
        companyId,
        checkoutIntentId);
  }

  @Transactional
  void bindPayment(long authorizationId, Long paymentId) {
    Prepared authorization = requireForUpdate(authorizationId);
    if (authorization.subscriptionId() == null) {
      throw new ConflictException("Recurring authorization is not bound to a subscription");
    }
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

  @Transactional(readOnly = true)
  Prepared active(Long companyId, Long subscriptionId) {
    List<Prepared> rows =
        query(
            """
            select id, company_id, subscription_id, checkout_intent_id,
                   provider_subscription_id, provider_subscription_session_id,
                   authorization_payment_id, authorization_status, max_amount, currency,
                   provider_authorization_payment_id
              from payment_provider_authorizations
             where company_id = ? and subscription_id = ? and provider = 'RAZORPAY'
               and authorization_status = 'ACTIVE'
            """,
            companyId,
            subscriptionId);
    return rows.isEmpty() ? null : rows.getFirst();
  }

  @Transactional(readOnly = true)
  Prepared bound(Long companyId, Long subscriptionId) {
    List<Prepared> rows =
        query(
            """
            select id, company_id, subscription_id, checkout_intent_id,
                   provider_subscription_id, provider_subscription_session_id,
                   authorization_payment_id, authorization_status, max_amount, currency,
                   provider_authorization_payment_id
              from payment_provider_authorizations
             where company_id = ? and subscription_id = ? and provider = 'RAZORPAY'
            """,
            companyId,
            subscriptionId);
    if (!rows.isEmpty()) {
      return rows.getFirst();
    }
    return findActiveCompanyAutopay(companyId);
  }

  private Prepared insertCompanyAutopay(
      Long companyId,
      String providerSubscriptionId,
      BigDecimal maxAmount,
      String currency) {
    long id = IdGenerator.nextId();
    String authorizationPaymentId = "UDA-" + id;
    jdbcTemplate.update(
        """
        insert into payment_provider_authorizations (
          id, company_id, subscription_id, checkout_intent_id, is_company_autopay, provider,
          provider_subscription_id, authorization_payment_id,
          authorization_status, max_amount, currency, expires_at, created_at, updated_at
        ) values (?, ?, null, null, true, 'RAZORPAY', ?, ?, 'CREATE_PENDING', ?, ?, now() + interval '20 years', now(), now())
        """,
        id,
        companyId,
        providerSubscriptionId,
        authorizationPaymentId,
        maxAmount,
        currency);
    return new Prepared(
        id,
        companyId,
        null,
        null,
        providerSubscriptionId,
        null,
        authorizationPaymentId,
        null,
        "CREATE_PENDING",
        maxAmount,
        currency,
        true);
  }

  private Prepared insert(
      Long companyId,
      Long subscriptionId,
      Long checkoutIntentId,
      String providerSubscriptionId,
      BigDecimal maxAmount,
      String currency) {
    long id = IdGenerator.nextId();
    String authorizationPaymentId = "UDA-" + id;
    jdbcTemplate.update(
        """
        insert into payment_provider_authorizations (
          id, company_id, subscription_id, checkout_intent_id, provider,
          provider_subscription_id, authorization_payment_id,
          authorization_status, max_amount, currency, created_at, updated_at
        ) values (?, ?, ?, ?, 'RAZORPAY', ?, ?, 'CREATE_PENDING', ?, ?, now(), now())
        """,
        id,
        companyId,
        subscriptionId,
        checkoutIntentId,
        providerSubscriptionId,
        authorizationPaymentId,
        maxAmount,
        currency);
    return new Prepared(
        id,
        companyId,
        subscriptionId,
        checkoutIntentId,
        providerSubscriptionId,
        null,
        authorizationPaymentId,
        null,
        "CREATE_PENDING",
        maxAmount,
        currency,
        true);
  }

  private Prepared requireCoverage(Prepared prior, BigDecimal maxAmount, String currency) {
    if (!prior.currency().equalsIgnoreCase(currency)) {
      throw new ConflictException("Recurring authorization currency mismatch");
    }
    if (prior.maxAmount().compareTo(maxAmount) < 0) {
      throw new ConflictException(
          "Existing recurring authorization does not cover the canonical invoice amount");
    }
    return prior;
  }

  private Prepared requireForUpdate(long id) {
    List<Prepared> rows =
        queryForUpdate(
            """
            select id, company_id, subscription_id, checkout_intent_id,
                   provider_subscription_id, provider_subscription_session_id,
                   authorization_payment_id, authorization_status, max_amount, currency,
                   provider_authorization_payment_id
              from payment_provider_authorizations
             where id = ?
             for update
            """,
            id);
    if (rows.isEmpty()) {
      throw new ConflictException("Recurring authorization no longer exists");
    }
    return rows.getFirst();
  }

  private List<Prepared> query(String sql, Object... args) {
    return jdbcTemplate.query(sql, this::map, args);
  }

  private List<Prepared> queryForUpdate(String sql, Object... args) {
    return jdbcTemplate.query(sql, this::map, args);
  }

  private Prepared map(ResultSet rs, int rowNum) throws SQLException {
    return new Prepared(
        rs.getLong("id"),
        rs.getLong("company_id"),
        nullableLong(rs, "subscription_id"),
        nullableLong(rs, "checkout_intent_id"),
        rs.getString("provider_subscription_id"),
        rs.getString("provider_subscription_session_id"),
        rs.getString("authorization_payment_id"),
        hasColumn(rs, "provider_authorization_payment_id")
            ? rs.getString("provider_authorization_payment_id")
            : null,
        rs.getString("authorization_status"),
        rs.getBigDecimal("max_amount"),
        rs.getString("currency"),
        false,
        hasColumn(rs, "payment_method_type")
            ? rs.getString("payment_method_type")
            : null);
  }

  private Long nullableLong(ResultSet rs, String column) throws SQLException {
    long value = rs.getLong(column);
    return rs.wasNull() ? null : value;
  }

  private boolean hasColumn(ResultSet rs, String column) {
    try {
      rs.findColumn(column);
      return true;
    } catch (SQLException ignored) {
      return false;
    }
  }

  record Prepared(
      long id,
      Long companyId,
      Long subscriptionId,
      Long checkoutIntentId,
      String providerSubscriptionId,
      String subscriptionSessionId,
      String authorizationPaymentId,
      String providerAuthorizationPaymentId,
      String status,
      BigDecimal maxAmount,
      String currency,
      boolean newlyCreated,
      String customerId) {
    public Prepared(
        long id,
        Long companyId,
        Long subscriptionId,
        Long checkoutIntentId,
        String providerSubscriptionId,
        String subscriptionSessionId,
        String authorizationPaymentId,
        String providerAuthorizationPaymentId,
        String status,
        BigDecimal maxAmount,
        String currency,
        boolean newlyCreated) {
      this(id, companyId, subscriptionId, checkoutIntentId, providerSubscriptionId, subscriptionSessionId, authorizationPaymentId, providerAuthorizationPaymentId, status, maxAmount, currency, newlyCreated, null);
    }
  }
}
