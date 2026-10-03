package com.messaging.billing.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.billing.enums.PaymentAttemptStatus;
import com.messaging.common.id.IdGenerator;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CashfreeWebhookService {

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;
  private final PaymentConfirmationService confirmationService;

  @Transactional
  public void receive(byte[] rawBody) {
    String body = new String(rawBody, StandardCharsets.UTF_8);
    JsonNode root = read(body);
    JsonNode data = root.path("data");
    JsonNode order = data.path("order");
    JsonNode payment = data.path("payment");
    String type = text(root, "type");
    String orderId = text(order, "order_id");
    String providerPaymentId = text(payment, "cf_payment_id");
    String status = text(payment, "payment_status");
    String hash = sha256(rawBody);
    String eventKey =
        String.join(
            ":",
            safe(orderId),
            safe(providerPaymentId),
            safe(type),
            safe(status),
            hash.substring(0, 16));

    int inserted =
        jdbcTemplate.update(
            """
            insert into payment_provider_events (
              id, provider, provider_event_key, provider_object_id, event_type,
              payload_hash, raw_payload, provider_created_at, received_at,
              processing_status, attempt_count
            ) values (?, 'CASHFREE', ?, ?, ?, ?, ?, ?, now(), 'RECEIVED', 0)
            on conflict (provider, provider_event_key) do nothing
            """,
            IdGenerator.nextId(),
            eventKey,
            providerPaymentId == null ? orderId : providerPaymentId,
            type,
            hash,
            body,
            timestamp(payment.path("payment_time").asText(null)));
    if (inserted == 0) return;

    try {
      process(orderId, providerPaymentId, status, payment);
      jdbcTemplate.update(
          """
          update payment_provider_events
             set processing_status = 'PROCESSED', attempt_count = attempt_count + 1
           where provider = 'CASHFREE' and provider_event_key = ?
          """,
          eventKey);
    } catch (RuntimeException exception) {
      jdbcTemplate.update(
          """
          update payment_provider_events
             set processing_status = 'RETRY',
                 attempt_count = attempt_count + 1,
                 next_attempt_at = now() + interval '5 minutes',
                 last_error_category = ?
           where provider = 'CASHFREE' and provider_event_key = ?
          """,
          exception.getClass().getSimpleName(),
          eventKey);
      throw exception;
    }
  }

  private void process(
      String orderId, String providerPaymentId, String status, JsonNode payment) {
    if (orderId == null || status == null) return;
    BigDecimal amount =
        payment.path("payment_amount").isNumber()
            ? payment.path("payment_amount").decimalValue()
            : null;
    String currency = text(payment, "payment_currency");
    Instant createdAt = timestamp(payment.path("payment_time").asText(null));

    if ("SUCCESS".equalsIgnoreCase(status)) {
      confirmationService.confirmSuccess(orderId, providerPaymentId, amount, currency, createdAt);
      return;
    }
    PaymentAttemptStatus local =
        switch (status.toUpperCase(java.util.Locale.ROOT)) {
          case "PENDING" -> PaymentAttemptStatus.PENDING;
          case "USER_DROPPED" -> PaymentAttemptStatus.USER_DROPPED;
          case "CANCELLED" -> PaymentAttemptStatus.CANCELLED;
          case "EXPIRED" -> PaymentAttemptStatus.EXPIRED;
          default -> PaymentAttemptStatus.FAILED;
        };
    String error = payment.path("error_details").path("error_code").asText(null);
    confirmationService.recordFailure(
        orderId, providerPaymentId, amount, currency, error, local, createdAt);
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

  private String text(JsonNode node, String field) {
    String value = node.path(field).asText(null);
    return value == null || value.isBlank() ? null : value;
  }

  private String safe(String value) {
    return value == null ? "-" : value.replace(":", "_");
  }

  private Instant timestamp(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return java.time.OffsetDateTime.parse(value).toInstant();
    } catch (RuntimeException ignored) {
      return null;
    }
  }
}
