package com.messaging.emailprovider.feedback;

import com.messaging.emailprovider.common.exception.BadRequestException;
import com.messaging.emailprovider.feedback.dto.PostfixFeedbackRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class DsnFeedbackParser {

  private static final Pattern VERP = Pattern.compile("(?i)^bounce\\+(\\d+)\\.(\\d+)@[^@]+$");
  private static final Pattern ACTION = Pattern.compile("(?im)^Action:\\s*([^\\r\\n]+)");
  private static final Pattern STATUS = Pattern.compile("(?im)^Status:\\s*([^\\s\\r\\n]+)");
  private static final Pattern DIAGNOSTIC =
      Pattern.compile("(?im)^Diagnostic-Code:\\s*([^\\r\\n]+)");

  public PostfixFeedbackRequest parse(String envelopeRecipient, String rawMessage) {
    if (envelopeRecipient == null || envelopeRecipient.isBlank()) {
      throw new BadRequestException("Envelope recipient is required");
    }
    if (rawMessage == null || rawMessage.isBlank()) {
      throw new BadRequestException("DSN payload is required");
    }

    Matcher verp = VERP.matcher(envelopeRecipient.trim());
    if (!verp.matches()) {
      throw new BadRequestException("Envelope recipient is not a UnifyDesk VERP address");
    }

    Long recipientId = Long.parseLong(verp.group(2));
    String action = find(ACTION, rawMessage);
    String status = find(STATUS, rawMessage);
    String diagnostic = find(DIAGNOSTIC, rawMessage);

    EmailEventType eventType = eventType(action, status);
    return new PostfixFeedbackRequest(
        recipientId,
        null,
        eventType,
        EmailEventSource.DSN,
        sha256(envelopeRecipient.trim() + "\n" + rawMessage),
        status,
        diagnostic,
        Instant.now());
  }

  private EmailEventType eventType(String action, String status) {
    String normalized = action == null ? "" : action.trim().toLowerCase(Locale.ROOT);
    if ("delivered".equals(normalized)) {
      return EmailEventType.DELIVERED;
    }
    if ("delayed".equals(normalized)) {
      return EmailEventType.DEFERRED;
    }
    if ("failed".equals(normalized)) {
      if (status != null && status.trim().startsWith("4")) {
        return EmailEventType.DEFERRED;
      }
      return EmailEventType.BOUNCED;
    }
    throw new BadRequestException("Unsupported DSN action");
  }

  private String find(Pattern pattern, String value) {
    Matcher matcher = pattern.matcher(value);
    return matcher.find() ? matcher.group(1).trim() : null;
  }

  private String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is not available", exception);
    }
  }
}
