package com.messaging.managedsms.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.id.IdGenerator;
import com.messaging.managedsms.runtime.ManagedSmsRuntimeProperties;
import com.messaging.managedsms.runtime.ManagedSmsRuntimeRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ManagedSmsEventInboxService {

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;
  private final ManagedSmsRuntimeRepository runtimeRepository;
  private final ManagedSmsRuntimeProperties properties;

  @Transactional
  public void receive(byte[] rawBody, String suppliedSecret) {
    if (rawBody == null || rawBody.length == 0 || rawBody.length > properties.getWebhookMaxBytes()) {
      throw new BadRequestException("Invalid Managed SMS provider event");
    }
    if (!trustedSecret(suppliedSecret)) {
      throw new BadRequestException("Invalid Managed SMS provider event");
    }

    JsonNode payload;
    try {
      payload = objectMapper.readTree(rawBody);
    } catch (Exception exception) {
      throw new BadRequestException("Invalid Managed SMS provider event");
    }
    String correlation = text(payload, "CRQID", "crqid");
    if (correlation == null
        || correlation.length() > 52
        || !correlation.matches("[A-Za-z0-9]+")) {
      throw new BadRequestException("Invalid Managed SMS provider event");
    }

    Optional<ManagedSmsRuntimeRepository.CorrelationRuntime> runtimeOpt =
        runtimeRepository.findCorrelation(correlation);
    if (runtimeOpt.isEmpty()) {
      throw new BadRequestException("Invalid Managed SMS provider event");
    }
    ManagedSmsRuntimeRepository.CorrelationRuntime runtime = runtimeOpt.get();
    String requestId = text(payload, "requestId", "request_id");
    boolean requestMatches =
        runtime.providerRequestId() == null
            || requestId == null
            || runtime.providerRequestId().equals(requestId);
    String rawHash = sha256(rawBody);
    String status = text(payload, "upperCaseEventName", "eventName", "event", "desc", "status");
    String eventKey =
        sha256(
            (runtime.providerAccountKey()
                    + "|"
                    + correlation
                    + "|"
                    + String.valueOf(requestId)
                    + "|"
                    + String.valueOf(status)
                    + "|"
                    + String.valueOf(text(payload, "ts", "deliveryTime", "requestedAt")))
                .getBytes(StandardCharsets.UTF_8));
    String trust = requestMatches ? "TRUSTED" : "REJECTED";
    String processing = requestMatches ? "PENDING" : "REJECTED";
    jdbcTemplate.update(
        """
        insert into managed_sms_provider_events (
          id, provider, provider_account_key, provider_event_key, raw_hash, event_type,
          correlation_token, provider_request_id, payload_json, trust_status,
          processing_status, received_at, processed_at
        ) values (?, 'MSG91_SMS', ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), ?)
        on conflict (provider, provider_account_key, provider_event_key) do nothing
        """,
        IdGenerator.nextId(),
        runtime.providerAccountKey(),
        eventKey,
        rawHash,
        mapType(payload).name(),
        correlation,
        requestId,
        new String(rawBody, StandardCharsets.UTF_8),
        trust,
        processing,
        requestMatches ? null : java.sql.Timestamp.from(Instant.now()));
  }

  private boolean trustedSecret(String supplied) {
    String expected = properties.getWebhookSecret();
    if (expected == null || expected.isBlank() || supplied == null) return false;
    return MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8));
  }

  ManagedSmsProviderEventType mapType(JsonNode payload) {
    String raw =
        Optional.ofNullable(
                text(payload, "upperCaseEventName", "eventName", "event", "desc", "status"))
            .orElse("")
            .toUpperCase(Locale.ROOT);
    return switch (raw) {
      case "1", "DELIVERED" -> ManagedSmsProviderEventType.DELIVERED;
      case "2", "FAILED" -> ManagedSmsProviderEventType.FAILED;
      case "9", "16", "17", "20", "25", "REJECTED", "NDNC", "BLOCKED" ->
          ManagedSmsProviderEventType.REJECTED;
      case "0", "SENT" -> ManagedSmsProviderEventType.SENT;
      default -> ManagedSmsProviderEventType.UNKNOWN;
    };
  }

  static Instant eventAt(JsonNode payload) {
    String raw = text(payload, "ts", "deliveryTime", "requestedAt");
    if (raw == null) return Instant.now();
    try {
      if (raw.matches("[0-9]{10,13}")) {
        long value = Long.parseLong(raw);
        return raw.length() == 10 ? Instant.ofEpochSecond(value) : Instant.ofEpochMilli(value);
      }
      return OffsetDateTime.parse(raw).toInstant();
    } catch (Exception ignored) {
      try {
        return java.time.LocalDateTime.parse(
                raw, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            .toInstant(java.time.ZoneOffset.UTC);
      } catch (Exception ignoredAgain) {
        return Instant.now();
      }
    }
  }

  static String text(JsonNode node, String... keys) {
    for (String key : keys) {
      JsonNode value = node.path(key);
      if (!value.isMissingNode() && !value.isNull()) {
        String text = value.asText("").trim();
        if (!text.isBlank()) return text;
      }
    }
    return null;
  }

  static BigDecimal decimal(JsonNode node, String key) {
    String value = text(node, key);
    if (value == null) return null;
    try {
      return new BigDecimal(value);
    } catch (NumberFormatException exception) {
      return null;
    }
  }

  private String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to hash managed SMS event", exception);
    }
  }
}
