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

  public void receiveVerified(byte[] rawBody, Instant verifiedAt) {
    String body = new String(rawBody, StandardCharsets.UTF_8);
    String hash = sha256(rawBody);
    String eventKey = "rzp:" + hash;
    long id = IdGenerator.nextId();

    try {
      jdbcTemplate.update(
          """
          insert into payment_provider_events (
            id, provider, provider_event_key, payload_hash, raw_payload,
            signature_verified_at, received_at, processing_status,
            attempt_count, next_attempt_at
          ) values (?, 'RAZORPAY', ?, ?, ?, ?, now(), 'RECEIVED', 0, now())
          on conflict (provider, provider_event_key) do nothing
          """,
          id,
          eventKey,
          hash,
          body,
          Timestamp.from(verifiedAt == null ? Instant.now() : verifiedAt));
    } catch (Exception ex) {
      log.debug("Event already recorded: {}", ex.getMessage());
    }

    try {
      processPayload(body);
    } catch (Exception ex) {
      log.error("Error processing Razorpay webhook payload: {}", ex.getMessage(), ex);
    }
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
      log.error("Failed to parse Razorpay webhook event: {}", e.getMessage());
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

    Optional<Payment> match = Optional.empty();
    if (rzpOrderId != null) {
      match = paymentRepository.findByProviderAndProviderOrderId("RAZORPAY", rzpOrderId);
      if (match.isEmpty()) {
        match = paymentRepository.findByProviderAndProviderOrderId("CASHFREE", rzpOrderId);
      }
    }

    if (match.isEmpty() && rzpOrderId != null) {
      // Find by provider_checkout_session_id
      var all = paymentRepository.findAll();
      for (Payment p : all) {
        if (rzpOrderId.equals(p.getProviderCheckoutSessionId()) || rzpOrderId.equals(p.getProviderOrderId())) {
          match = Optional.of(p);
          break;
        }
      }
    }

    if (match.isPresent()) {
      Payment p = match.get();
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
            walletService.confirmTopup(companyId, internalOrderId, amount, 1L);
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
