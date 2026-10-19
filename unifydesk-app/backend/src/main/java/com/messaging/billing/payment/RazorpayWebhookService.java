package com.messaging.billing.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.billing.entity.Payment;
import com.messaging.billing.enums.PaymentAttemptStatus;
import com.messaging.billing.repository.PaymentRepository;
import com.messaging.common.id.IdGenerator;
import com.messaging.realtime.service.RealtimeService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RazorpayWebhookService {

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;
  private final PaymentRepository paymentRepository;
  private final PaymentConfirmationService confirmationService;
  private final RealtimeService realtimeService;
  private final RazorpayProperties properties;
  private final com.messaging.billing.service.WalletService walletService;
  private final PaymentProviderGateway gateway;

  public void receiveVerified(byte[] rawBody, Instant verifiedAt) {
    String body = new String(rawBody, StandardCharsets.UTF_8);
    String hash = sha256(rawBody);
    String eventKey = "rzp:" + hash;
    jdbcTemplate.update(
        """
        insert into payment_provider_events (
          id, provider, provider_event_key, payload_hash, raw_payload,
          signature_verified_at, received_at, processing_status,
          attempt_count, next_attempt_at
        ) values (?, 'RAZORPAY', ?, ?, ?, ?, now(), 'RECEIVED', 0, now())
        on conflict (provider, provider_event_key) do nothing
        """,
        IdGenerator.nextId(), eventKey, hash, body,
        Timestamp.from(verifiedAt == null ? Instant.now() : verifiedAt));
    String status = jdbcTemplate.queryForObject(
        "select processing_status from payment_provider_events "
            + "where provider='RAZORPAY' and provider_event_key=?",
        String.class, eventKey);
    if ("PROCESSED".equals(status)) return;
    try {
      processPayload(body);
      markEventProcessed(eventKey);
    } catch (RuntimeException ex) {
      markEventRetry(eventKey);
      throw ex; // Return 5xx so Razorpay can redeliver the signed webhook.
    }
  }

  @org.springframework.scheduling.annotation.Scheduled(
      fixedDelayString = "${app.billing.razorpay.webhook-retry-interval-ms:60000}",
      initialDelayString = "${app.billing.razorpay.webhook-retry-initial-delay-ms:60000}")
  public void replayUnprocessedEvents() {
    record Pending(String key, String body) {}
    var pending = jdbcTemplate.query(
        """
        with due as (
          select id from payment_provider_events
           where provider='RAZORPAY'
             and (
               (processing_status in ('RECEIVED','RETRY') and next_attempt_at<=now())
               or (processing_status='PROCESSING' and claimed_until < now())
             )
           order by received_at
           limit 50 for update skip locked
        )
        update payment_provider_events e
           set processing_status='PROCESSING',
               claim_token=null,
               claimed_until=now()+interval '120 seconds'
          from due where e.id=due.id
        returning e.provider_event_key, e.raw_payload
        """,
        (rs, row) -> new Pending(
            rs.getString("provider_event_key"), rs.getString("raw_payload")));
    for (Pending event : pending) {
      try {
        processPayload(event.body());
        markEventProcessed(event.key());
      } catch (RuntimeException ex) {
        log.warn("Razorpay event {} will be retried: {}", event.key(), ex.getClass().getSimpleName());
        markEventRetry(event.key());
      }
    }
  }

  private void markEventProcessed(String eventKey) {
    jdbcTemplate.update(
        "update payment_provider_events set processing_status='PROCESSED', "
            + "claimed_until=null, next_attempt_at=null where provider='RAZORPAY' "
            + "and provider_event_key=?",
        eventKey);
  }

  private void markEventRetry(String eventKey) {
    jdbcTemplate.update(
        "update payment_provider_events "
            + "set processing_status='RETRY', attempt_count=attempt_count+1, "
            + "claimed_until=null, next_attempt_at=now()+interval '5 minutes' "
            + "where provider='RAZORPAY' and provider_event_key=?",
        eventKey);
  }

  public void processPayload(String body) {
    try {
      JsonNode root = objectMapper.readTree(body);
      String event = root.path("event").asText();
      JsonNode payload = root.path("payload");

      log.info("Processing Razorpay webhook event: {}", event);

      if ("order.paid".equalsIgnoreCase(event) || "payment.captured".equalsIgnoreCase(event)) {
        processPaymentSuccess(payload);
      } else if ("payment.failed".equalsIgnoreCase(event)) {
        processPaymentFailure(payload);
      }
    } catch (Exception e) {
      throw new IllegalStateException("Razorpay webhook could not be processed", e);
    }
  }

  private void processPaymentSuccess(JsonNode payload) {
    JsonNode paymentEntity = payload.path("payment").path("entity");
    JsonNode orderEntity = payload.path("order").path("entity");

    String rzpOrderId = paymentEntity.path("order_id").asText(null);
    if (rzpOrderId == null || rzpOrderId.isBlank()) {
      rzpOrderId = orderEntity.path("id").asText(null);
    }

    String rzpPaymentId = paymentEntity.path("id").asText(null);
    long amountPaise = paymentEntity.path("amount").asLong(0);
    BigDecimal amount = new BigDecimal(amountPaise).divide(new BigDecimal(100), 2, RoundingMode.HALF_UP);
    String currency = paymentEntity.path("currency").asText("INR");
    long createdAtSeconds = paymentEntity.path("created_at").asLong(0);
    Instant createdAt = createdAtSeconds > 0 ? Instant.ofEpochSecond(createdAtSeconds) : Instant.now();

    if (rzpOrderId == null && rzpPaymentId == null) {
      log.warn("Razorpay payment success event missing order/payment ID");
      return;
    }

    Optional<Payment> match = rzpOrderId == null ? Optional.empty()
        : paymentRepository.findByProviderAndProviderCheckoutSessionId("RAZORPAY", rzpOrderId);
    if (match.isEmpty() && rzpOrderId != null) {
      match = paymentRepository.findByProviderAndProviderOrderId("RAZORPAY", rzpOrderId);
    }

    if (match.isPresent()) {
      Payment p = match.get();
      if (rzpOrderId == null || !rzpOrderId.startsWith("order_")) {
        throw new IllegalArgumentException("Captured payment is missing its Razorpay order");
      }
      PaymentProviderGateway.Checkout order = gateway.fetchOrder(rzpOrderId);
      if (!"paid".equalsIgnoreCase(order.status())
          || !rzpOrderId.equals(order.paymentSessionId())) {
        throw new IllegalStateException("Razorpay order is not paid");
      }
      var capturedPayments = gateway.fetchOrderPayments(rzpOrderId).stream()
          .filter(x -> "SUCCESS".equalsIgnoreCase(x.status())).toList();
      if (capturedPayments.size() != 1
          || capturedPayments.getFirst().providerPaymentId() == null
          || (rzpPaymentId != null
              && !rzpPaymentId.equals(capturedPayments.getFirst().providerPaymentId()))
          || capturedPayments.getFirst().amount().compareTo(p.getAmount()) != 0
          || !p.getCurrency().equalsIgnoreCase(capturedPayments.getFirst().currency())) {
        throw new IllegalStateException("Captured Razorpay payment does not match the canonical invoice");
      }
      rzpPaymentId = capturedPayments.getFirst().providerPaymentId();
      confirmationService.confirmSuccessByPaymentId(
          p.getId(), rzpPaymentId, p.getAmount(), p.getCurrency(), createdAt);

      // Broadcast real-time websocket event to the workspace
      realtimeService.sendToCompany(
          p.getCompany().getId(),
          "billing.payment.updated",
          Map.of(
              "companyId", p.getCompany().getId(),
              "paymentId", p.getId(),
              "orderId", rzpOrderId != null ? rzpOrderId : "",
              "providerPaymentId", rzpPaymentId != null ? rzpPaymentId : "",
              "status", "SUCCEEDED",
              "amount", p.getAmount()));
      log.info("Payment confirmed via Razorpay webhook for payment {}", p.getId());
    } else {
      String internalOrderId = paymentEntity.path("notes").path("internal_order_id").asText(null);
      if (internalOrderId == null && payload.has("order")) {
        internalOrderId = payload.path("order").path("entity").path("notes").path("internal_order_id").asText(null);
      }

      if (internalOrderId != null && internalOrderId.startsWith("TOPUP-")) {
        try {
          String[] parts = internalOrderId.split("-");
          if (parts.length >= 2) {
            Long companyId = Long.valueOf(parts[1]);
            walletService.confirmTopup(companyId, internalOrderId, amount, null);
            realtimeService.sendToCompany(
                companyId,
                "billing.wallet.updated",
                Map.of(
                    "companyId", companyId,
                    "orderId", internalOrderId,
                    "amount", amount,
                    "status", "SUCCEEDED"));
            log.info("Wallet top-up confirmed via Razorpay webhook for company {} order {}", companyId, internalOrderId);
            return;
          }
        } catch (Exception topupEx) {
          log.warn("Could not auto-credit wallet from top-up webhook {}: {}", internalOrderId, topupEx.getMessage());
        }
      }

      log.info("Razorpay webhook event recorded for order {} payment {}", rzpOrderId, rzpPaymentId);
    }
  }

  private void processPaymentFailure(JsonNode payload) {
    JsonNode paymentEntity = payload.path("payment").path("entity");
    String rzpOrderId = paymentEntity.path("order_id").asText(null);
    String rzpPaymentId = paymentEntity.path("id").asText(null);
    String errorCode = paymentEntity.path("error_code").asText("PAYMENT_FAILED");

    Optional<Payment> match = Optional.empty();
    if (rzpOrderId != null) {
      match = paymentRepository.findByProviderAndProviderOrderId("RAZORPAY", rzpOrderId);
    }

    match.ifPresent(p -> {
      confirmationService.recordFailureByPaymentId(
          p.getId(),
          rzpPaymentId,
          p.getAmount(),
          p.getCurrency(),
          errorCode,
          PaymentAttemptStatus.FAILED,
          Instant.now());
      realtimeService.sendToCompany(
          p.getCompany().getId(),
          "billing.payment.updated",
          Map.of(
              "companyId", p.getCompany().getId(),
              "paymentId", p.getId(),
              "orderId", rzpOrderId != null ? rzpOrderId : "",
              "status", "FAILED",
              "errorCode", errorCode));
    });
  }

  private String sha256(byte[] data) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(md.digest(data));
    } catch (Exception ex) {
      return String.valueOf(System.currentTimeMillis());
    }
  }
}
