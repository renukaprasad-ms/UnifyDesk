package com.messaging.billing.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.billing.enums.PaymentAttemptStatus;
import com.messaging.common.exception.ConflictException;
import com.messaging.common.id.IdGenerator;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CashfreeWebhookService {

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;
  private final PaymentConfirmationService confirmationService;
  private final RefundStore refundStore;
  private final RecurringAuthorizationStore authorizationStore;
  private final CashfreeProperties properties;

  public void receiveVerified(byte[] rawBody, Instant verifiedAt) {
    String body = new String(rawBody, StandardCharsets.UTF_8);
    String hash = sha256(rawBody);
    String eventKey = "raw:" + hash;
    long id = IdGenerator.nextId();
    int inserted =
        jdbcTemplate.update(
            """
            insert into payment_provider_events (
              id, provider, provider_event_key, payload_hash, raw_payload,
              signature_verified_at, received_at, processing_status,
              attempt_count, next_attempt_at
            ) values (?, 'CASHFREE', ?, ?, ?, ?, now(), 'RECEIVED', 0, now())
            on conflict (provider, provider_event_key) do nothing
            """,
            id,
            eventKey,
            hash,
            body,
            Timestamp.from(verifiedAt == null ? Instant.now() : verifiedAt));
    if (inserted == 0) return;
    processAndRecord(id, body, hash);
  }

  @Scheduled(
      fixedDelayString = "${app.billing.cashfree.webhook-retry-interval-ms:60000}",
      initialDelayString = "${app.billing.cashfree.webhook-retry-initial-delay-ms:60000}")
  public void retryDueEvents() {
    for (Claimed event : claimDue()) {
      processAndRecord(event.id(), event.rawPayload(), event.payloadHash());
    }
  }

  private List<Claimed> claimDue() {
    String token = UUID.randomUUID().toString();
    long leaseSeconds = Math.max(30L, properties.getWebhookClaimSeconds());
    return jdbcTemplate.query(
        """
        with due as (
          select id
            from payment_provider_events
           where signature_verified_at is not null
             and next_attempt_at <= now()
             and (
               processing_status = 'RETRY'
               or (processing_status = 'PROCESSING' and claimed_until < now())
             )
           order by next_attempt_at, id
           for update skip locked
           limit 50
        )
        update payment_provider_events e
           set processing_status='PROCESSING',
               claim_token=?,
               claimed_until=now() + (? * interval '1 second')
          from due
         where e.id=due.id
        returning e.id, e.raw_payload, e.payload_hash
        """,
        (rs, rowNum) ->
            new Claimed(
                rs.getLong("id"),
                rs.getString("raw_payload"),
                rs.getString("payload_hash")),
        token,
        leaseSeconds);
  }

  private void processAndRecord(long eventId, String body, String expectedHash) {
    try {
      String actualHash = sha256(body.getBytes(StandardCharsets.UTF_8));
      if (!MessageDigest.isEqual(
          expectedHash.getBytes(StandardCharsets.US_ASCII),
          actualHash.getBytes(StandardCharsets.US_ASCII))) {
        throw new ConflictException("Stored Cashfree webhook payload hash mismatch");
      }

      JsonNode root = read(body);
      Correlation correlation = process(root);
      jdbcTemplate.update(
          """
          update payment_provider_events
             set payment_id=coalesce(?, payment_id),
                 refund_id=coalesce(?, refund_id),
                 provider_object_id=coalesce(?, provider_object_id),
                 event_type=coalesce(?, event_type),
                 provider_created_at=coalesce(?, provider_created_at),
                 processing_status='PROCESSED',
                 attempt_count=attempt_count+1,
                 next_attempt_at=null,
                 last_error_category=null,
                 claim_token=null,
                 claimed_until=null
           where id=?
          """,
          correlation.paymentId(),
          correlation.refundId(),
          correlation.providerObjectId(),
          correlation.eventType(),
          correlation.providerCreatedAt() == null
              ? null
              : Timestamp.from(correlation.providerCreatedAt()),
          eventId);
    } catch (RuntimeException exception) {
      fail(eventId, exception);
    }
  }

  private Correlation process(JsonNode root) {
    String type = text(root, "type");
    JsonNode data = root.path("data");

    JsonNode refund = data.path("refund");
    if (!refund.isMissingNode() && !refund.isNull() && refund.isObject()) {
      return processRefund(type, refund, root);
    }

    if (type != null && type.startsWith("SUBSCRIPTION_")) {
      return processSubscription(type, data, root);
    }

    JsonNode order = data.path("order");
    JsonNode payment = data.path("payment");
    if (order.isObject() && payment.isObject()) {
      return processOrderPayment(type, order, payment);
    }

    throw new UnboundProviderEventException("Cashfree event type is not bound to a known financial object");
  }

  private Correlation processOrderPayment(
      String type, JsonNode order, JsonNode payment) {
    String orderId = text(order, "order_id");
    String providerPaymentId = text(payment, "cf_payment_id");
    String status = text(payment, "payment_status");
    if (orderId == null || status == null) {
      throw new UnboundProviderEventException("Cashfree payment event has no bound order/status");
    }
    Long paymentId = localPaymentId(orderId);
    if (paymentId == null) {
      throw new UnboundProviderEventException("Cashfree order is not yet bound locally");
    }
    BigDecimal amount = decimal(payment, "payment_amount");
    String currency = text(payment, "payment_currency");
    Instant createdAt = timestamp(text(payment, "payment_time"));

    if ("SUCCESS".equalsIgnoreCase(status)) {
      confirmationService.confirmSuccessByPaymentId(
          paymentId, providerPaymentId, amount, currency, createdAt);
    } else {
      confirmationService.recordFailureByPaymentId(
          paymentId,
          providerPaymentId,
          amount,
          currency,
          text(payment.path("error_details"), "error_code"),
          paymentStatus(status),
          createdAt);
    }
    return new Correlation(paymentId, null, providerPaymentId, type, createdAt);
  }

  private Correlation processRefund(String type, JsonNode refund, JsonNode root) {
    String refundId = text(refund, "refund_id");
    if (refundId == null) {
      throw new UnboundProviderEventException("Cashfree refund event has no merchant refund identity");
    }
    Long localRefundId =
        firstLong(
            "select id from refunds where provider_refund_id = ?",
            refundId);
    if (localRefundId == null) {
      throw new UnboundProviderEventException("Cashfree refund is not yet bound locally");
    }
    Long paymentId =
        firstLong(
            "select payment_id from refunds where id = ?",
            localRefundId);
    refundStore.applyRemote(
        localRefundId,
        new PaymentProviderGateway.RemoteRefund(
            refundId,
            text(refund, "cf_refund_id"),
            text(refund, "refund_status"),
            decimal(refund, "refund_amount"),
            text(refund, "refund_currency"),
            timestamp(text(refund, "created_at")),
            timestamp(text(refund, "processed_at"))));
    return new Correlation(
        paymentId,
        localRefundId,
        text(refund, "cf_refund_id"),
        type,
        timestamp(text(root, "event_time")));
  }

  private Correlation processSubscription(String type, JsonNode data, JsonNode root) {
    String commandId = text(data, "payment_id");
    String subscriptionId = text(data, "subscription_id");
    String providerPaymentId = text(data, "cf_payment_id");
    String status = text(data, "payment_status");
    String paymentType = text(data, "payment_type");
    Instant createdAt = timestamp(text(data, "payment_initiated_date"));

    if (commandId == null || subscriptionId == null || status == null) {
      throw new UnboundProviderEventException("Subscription event has no stable local identity");
    }

    if ("AUTH".equalsIgnoreCase(paymentType)) {
      Long authorizationId =
          firstLong(
              """
              select id from payment_provider_authorizations
               where provider='CASHFREE' and provider_subscription_id=?
                 and authorization_payment_id=?
              """,
              subscriptionId,
              commandId);
      if (authorizationId == null) {
        throw new UnboundProviderEventException("Subscription authorization is not yet bound locally");
      }
      authorizationStore.applyAuthorizationPayment(
          authorizationId,
          new PaymentProviderGateway.RemoteSubscriptionPayment(
              commandId,
              providerPaymentId,
              status,
              decimal(data, "payment_amount"),
              text(data, "payment_currency"),
              createdAt,
              null,
              text(data.path("failure_details"), "failure_reason")));
      return new Correlation(
          null,
          null,
          providerPaymentId == null ? subscriptionId : providerPaymentId,
          type,
          timestamp(text(root, "event_time")));
    }

    Long paymentId =
        firstLong(
            """
            select a.payment_id
              from payment_attempts a
             where a.provider_command_id = ?
            """,
            commandId);
    if (paymentId == null) {
      throw new UnboundProviderEventException("Subscription charge is not yet bound locally");
    }

    BigDecimal amount = decimal(data, "payment_amount");
    String currency = text(data, "payment_currency");
    if ("SUCCESS".equalsIgnoreCase(status)) {
      confirmationService.confirmSuccessByPaymentId(
          paymentId, providerPaymentId, amount, currency, createdAt);
    } else {
      confirmationService.recordFailureByPaymentId(
          paymentId,
          providerPaymentId,
          amount,
          currency,
          text(data.path("failure_details"), "failure_reason"),
          paymentStatus(status),
          createdAt);
    }
    return new Correlation(
        paymentId,
        null,
        providerPaymentId == null ? commandId : providerPaymentId,
        type,
        timestamp(text(root, "event_time")));
  }

  private PaymentAttemptStatus paymentStatus(String status) {
    if (status == null) return PaymentAttemptStatus.FAILED;
    return switch (status.toUpperCase(Locale.ROOT)) {
      case "PENDING", "INITIALIZED" -> PaymentAttemptStatus.PENDING;
      case "USER_DROPPED" -> PaymentAttemptStatus.USER_DROPPED;
      case "CANCELLED" -> PaymentAttemptStatus.CANCELLED;
      case "EXPIRED" -> PaymentAttemptStatus.EXPIRED;
      default -> PaymentAttemptStatus.FAILED;
    };
  }

  private void fail(long eventId, RuntimeException exception) {
    Integer attempts =
        jdbcTemplate.queryForObject(
            "select attempt_count from payment_provider_events where id=?",
            Integer.class,
            eventId);
    int nextAttempt = (attempts == null ? 0 : attempts) + 1;
    int max = Math.max(1, properties.getWebhookMaxAttempts());
    if (nextAttempt >= max) {
      jdbcTemplate.update(
          """
          update payment_provider_events
             set processing_status='DEAD',
                 attempt_count=?,
                 next_attempt_at=null,
                 last_error_category=?,
                 claim_token=null,
                 claimed_until=null
           where id=?
          """,
          nextAttempt,
          exception.getClass().getSimpleName(),
          eventId);
      return;
    }
    long base = Math.max(1L, properties.getWebhookRetryBaseSeconds());
    long seconds = Math.min(86400L, base * (1L << Math.min(10, nextAttempt - 1)));
    jdbcTemplate.update(
        """
        update payment_provider_events
           set processing_status='RETRY',
               attempt_count=?,
               next_attempt_at=now() + (? * interval '1 second'),
               last_error_category=?,
               claim_token=null,
               claimed_until=null
         where id=?
        """,
        nextAttempt,
        seconds,
        exception.getClass().getSimpleName(),
        eventId);
  }

  private Long localPaymentId(String providerOrderId) {
    return firstLong(
        "select id from payments where provider='CASHFREE' and provider_order_id=?",
        providerOrderId);
  }

  private Long firstLong(String sql, Object... args) {
    List<Long> rows = jdbcTemplate.queryForList(sql, Long.class, args);
    return rows.isEmpty() ? null : rows.getFirst();
  }

  private JsonNode read(String value) {
    try {
      return objectMapper.readTree(value);
    } catch (Exception exception) {
      throw new IllegalArgumentException("Invalid Cashfree webhook JSON", exception);
    }
  }

  private String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }

  private BigDecimal decimal(JsonNode node, String field) {
    return node.path(field).isNumber() ? node.path(field).decimalValue() : null;
  }

  private String text(JsonNode node, String field) {
    String value = node.path(field).asText(null);
    return value == null || value.isBlank() ? null : value;
  }

  private Instant timestamp(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return OffsetDateTime.parse(value).toInstant();
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  private record Claimed(long id, String rawPayload, String payloadHash) {}

  private record Correlation(
      Long paymentId,
      Long refundId,
      String providerObjectId,
      String eventType,
      Instant providerCreatedAt) {}

  private static final class UnboundProviderEventException extends RuntimeException {
    private UnboundProviderEventException(String message) {
      super(message);
    }
  }
}
