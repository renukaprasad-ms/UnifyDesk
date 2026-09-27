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
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
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

  private static final ZoneId LEGACY_MSG91_ZONE = ZoneId.of("Asia/Kolkata");
  private static final DateTimeFormatter LEGACY_MSG91_TIME =
      new DateTimeFormatterBuilder()
          .appendPattern("yyyy-MM-dd HH:mm:ss")
          .optionalStart()
          .appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true)
          .optionalEnd()
          .toFormatter(Locale.ROOT);

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;
  private final ManagedSmsRuntimeRepository runtimeRepository;
  private final ManagedSmsRuntimeProperties properties;
  private final ManagedSmsWebhookTokenService tokenService;

  @Transactional
  public void receive(byte[] rawBody, String suppliedToken) {
    if (rawBody == null || rawBody.length == 0 || rawBody.length > properties.getWebhookMaxBytes()) {
      throw invalidEvent();
    }

    JsonNode payload;
    try {
      payload = objectMapper.readTree(rawBody);
    } catch (Exception exception) {
      throw invalidEvent();
    }

    String correlation = text(payload, "CRQID", "crqid");
    if (correlation == null
        || correlation.length() > 52
        || !correlation.matches("[A-Za-z0-9]+")) {
      throw invalidEvent();
    }

    ManagedSmsRuntimeRepository.CorrelationRuntime runtime =
        runtimeRepository.findCorrelation(correlation).orElseThrow(this::invalidEvent);

    if (!tokenService.matches(runtime.providerAccountKey(), suppliedToken)) {
      throw invalidEvent();
    }

    String requestId = text(payload, "requestId", "request_id");
    if (requestId == null) throw invalidEvent();
    if (runtime.providerRequestId() != null
        && !runtime.providerRequestId().equals(requestId)) {
      throw invalidEvent();
    }

    String status = text(payload, "upperCaseEventName", "eventName", "event", "desc", "status");
    if (status == null) throw invalidEvent();
    String rawTs = text(payload, "ts");
    if (rawTs == null) throw invalidEvent();

    String eventKey =
        sha256(
            (runtime.providerAccountKey()
                    + "|"
                    + correlation
                    + "|"
                    + requestId
                    + "|"
                    + status.trim().toUpperCase(Locale.ROOT)
                    + "|"
                    + rawTs)
                .getBytes(StandardCharsets.UTF_8));

    if (eventExists(runtime.providerAccountKey(), eventKey)) {
      return;
    }

    Instant providerEventAt = parseProviderEventAt(rawTs);
    Duration replayWindow = properties.getEventReplayWindow();
    if (replayWindow == null || replayWindow.isZero() || replayWindow.isNegative()) {
      throw invalidEvent();
    }
    Instant now = Instant.now();
    if (providerEventAt.isBefore(now.minus(replayWindow))
        || providerEventAt.isAfter(now.plus(replayWindow))) {
      throw invalidEvent();
    }

    jdbcTemplate.update(
        """
        insert into managed_sms_provider_events (
          id, provider, provider_account_key, provider_event_key, raw_hash, event_type,
          correlation_token, provider_request_id, payload_json, trust_status,
          processing_status, provider_event_at, received_at, processed_at
        ) values (?, 'MSG91_SMS', ?, ?, ?, ?, ?, ?, ?, 'TRUSTED', 'PENDING', ?, now(), null)
        on conflict (provider, provider_account_key, provider_event_key) do nothing
        """,
        IdGenerator.nextId(),
        runtime.providerAccountKey(),
        eventKey,
        sha256(rawBody),
        mapType(payload).name(),
        correlation,
        requestId,
        new String(rawBody, StandardCharsets.UTF_8),
        java.sql.Timestamp.from(providerEventAt));
  }

  private boolean eventExists(String providerAccountKey, String eventKey) {
    Integer count =
        jdbcTemplate.queryForObject(
            """
            select count(*)
              from managed_sms_provider_events
             where provider = 'MSG91_SMS'
               and provider_account_key = ?
               and provider_event_key = ?
            """,
            Integer.class,
            providerAccountKey,
            eventKey);
    return count != null && count > 0;
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

  static Instant parseProviderEventAt(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new BadRequestException("Invalid Managed SMS provider event");
    }
    try {
      return OffsetDateTime.parse(raw).toInstant();
    } catch (RuntimeException ignored) {
      try {
        return Instant.parse(raw);
      } catch (RuntimeException ignoredAgain) {
        try {
          return LocalDateTime.parse(raw, LEGACY_MSG91_TIME)
              .atZone(LEGACY_MSG91_ZONE)
              .toInstant();
        } catch (RuntimeException invalid) {
          throw new BadRequestException("Invalid Managed SMS provider event");
        }
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

  private BadRequestException invalidEvent() {
    return new BadRequestException("Invalid Managed SMS provider event");
  }
}
