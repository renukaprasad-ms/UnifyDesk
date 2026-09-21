package com.messaging.delivery.service;

import com.messaging.delivery.enums.EmailBounceType;
import com.messaging.delivery.model.DetectedDeliveryReport;
import com.messaging.inbox.entity.InboxMessage;
import com.messaging.inbox.enums.InboxMessageDirection;
import jakarta.mail.internet.InternetAddress;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class EmailDeliveryReportParser {

  private static final Pattern RECIPIENT =
      Pattern.compile(
          "(?im)^(?:Final-Recipient|Original-Recipient)\s*:\s*(?:rfc822\s*;\s*)?([^\s;<>]+@[^\s;<>]+)");
  private static final Pattern ORIGINAL_MESSAGE_ID =
      Pattern.compile(
          "(?im)^(?:Original-Message-ID|X-Original-Message-ID)\s*:\s*(<[^>\r\n]+>|[^\r\n]+)");
  private static final Pattern CORRELATION =
      Pattern.compile(
          "(?im)^X-Messaging-Broadcast-Recipient\s*:\s*([A-Za-z0-9._-]{8,128})");
  private static final Pattern STATUS =
      Pattern.compile("(?im)^Status\s*:\s*([245]\.\d{1,3}\.\d{1,3})");
  private static final Pattern DIAGNOSTIC =
      Pattern.compile("(?im)^Diagnostic-Code\s*:\s*([^\r\n]{1,1000})");

  public Optional<DetectedDeliveryReport> parse(InboxMessage message) {
    if (message == null || message.getDirection() != InboxMessageDirection.INBOUND) {
      return Optional.empty();
    }

    String subject = value(message.getSubject());
    String from = value(message.getFromAddress());
    String body = combine(message.getBodyText(), message.getSnippet());
    String searchable = subject + "\n" + body;

    String failedRecipient = firstNonBlank(match(RECIPIENT, searchable), match(FALLBACK_RECIPIENT, searchable));
    String originalMessageId = trim(match(ORIGINAL_MESSAGE_ID, searchable));
    String correlationId = trim(match(CORRELATION, searchable));
    String status = firstNonBlank(trim(match(STATUS, searchable)), trim(match(FALLBACK_STATUS, searchable)));
    String diagnostic = trim(match(DIAGNOSTIC, searchable));

    boolean machineDsn =
        failedRecipient != null
            || originalMessageId != null
            || status != null
            || diagnostic != null;
    boolean bounceSender =
        lower(from).contains("mailer-daemon")
            || lower(from).contains("postmaster")
            || lower(from).contains("mail-daemon");
    boolean bounceSubject =
        containsAny(
            lower(subject),
            "delivery status notification",
            "undeliverable",
            "mail delivery failed",
            "delivery failure",
            "returned mail",
            "delivery has failed",
            "couldn't be delivered",
            "could not be delivered");

    if (!machineDsn || (!bounceSender && !bounceSubject && status == null)) {
      return Optional.empty();
    }

    String normalized = normalizeEmail(failedRecipient);
    EmailBounceType type =
        status == null
            ? EmailBounceType.UNKNOWN
            : status.startsWith("5.")
                ? EmailBounceType.HARD
                : status.startsWith("4.")
                    ? EmailBounceType.SOFT
                    : EmailBounceType.UNKNOWN;

    return Optional.of(
        new DetectedDeliveryReport(
            failedRecipient,
            normalized,
            originalMessageId,
            correlationId,
            status,
            limit(diagnostic, 1000),
            type));
  }

  private String normalizeEmail(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      InternetAddress address = new InternetAddress(value.trim(), true);
      address.validate();
      return address.getAddress().trim().toLowerCase(Locale.ROOT);
    } catch (Exception exception) {
      return null;
    }
  }

  private String match(Pattern pattern, String value) {
    Matcher matcher = pattern.matcher(value);
    return matcher.find() ? matcher.group(1).trim() : null;
  }

  private String combine(String body, String snippet) {
    String first = value(body);
    String second = value(snippet);
    if (first.isEmpty()) return second;
    if (second.isEmpty() || first.contains(second)) return first;
    return first + "\n" + second;
  }

  private boolean containsAny(String value, String... tokens) {
    for (String token : tokens) {
      if (value.contains(token)) return true;
    }
    return false;
  }

  private String lower(String value) {
    return value == null ? "" : value.toLowerCase(Locale.ROOT);
  }

  private String value(String value) {
    return value == null ? "" : value;
  }

  private String firstNonBlank(String first, String second) {
    return first != null && !first.isBlank() ? first : second;
  }

  private String trim(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private String limit(String value, int max) {
    if (value == null || value.length() <= max) return value;
    return value.substring(0, max);
  }
}
