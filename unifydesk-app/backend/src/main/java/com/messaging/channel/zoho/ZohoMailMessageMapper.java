package com.messaging.channel.zoho;

import com.messaging.channel.email.EmailFolder;
import com.messaging.channel.zoho.ZohoMailApiClient.ZohoAttachment;
import com.messaging.channel.zoho.ZohoMailApiClient.ZohoMessage;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

@Component
public class ZohoMailMessageMapper {

  private static final Pattern EMAIL_PATTERN =
      Pattern.compile("(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}");

  public ZohoMailMessageSnapshot map(
      ZohoMessage message, String content, EmailFolder folder, List<ZohoAttachment> attachments) {
    if (message == null || !hasText(message.messageId())) {
      throw new IllegalArgumentException("Zoho message id is missing");
    }

    String html = trim(content);
    String summary = trim(message.summary());
    String thread =
        hasText(message.threadId()) ? message.threadId().trim() : message.messageId().trim();

    return new ZohoMailMessageSnapshot(
        message.messageId().trim(),
        null,
        thread,
        message.folderId(),
        normalize(message.fromAddress()),
        trim(message.sender()),
        addresses(message.toAddress()),
        addresses(message.ccAddress()),
        addresses(message.bccAddress()),
        trim(message.subject()),
        summary,
        html,
        timestamp(message),
        read(message.status()),
        folder,
        attachmentSnapshots(attachments));
  }

  private List<ZohoMailAttachmentSnapshot> attachmentSnapshots(List<ZohoAttachment> attachments) {
    if (attachments == null || attachments.isEmpty()) return List.of();
    return attachments.stream()
        .filter(java.util.Objects::nonNull)
        .filter(item -> hasText(item.attachmentId()))
        .map(
            item ->
                new ZohoMailAttachmentSnapshot(
                    item.attachmentId().trim(),
                    trim(item.attachmentName()),
                    item.attachmentSize(),
                    item.isInline()))
        .toList();
  }

  private Instant timestamp(ZohoMessage message) {
    for (String candidate : new String[] {message.receivedTime(), message.sentDateInGMT()}) {
      if (!hasText(candidate)) continue;
      try {
        return Instant.ofEpochMilli(Long.parseLong(candidate.trim()));
      } catch (NumberFormatException ignored) {
        try {
          return Instant.parse(candidate.trim());
        } catch (RuntimeException ignoredAgain) {
          // Try the next provider timestamp.
        }
      }
    }
    return Instant.now();
  }

  private boolean read(String status) {
    if (!hasText(status)) return false;
    String normalized = status.trim().toLowerCase(Locale.ROOT);
    return "1".equals(normalized) || "read".equals(normalized);
  }

  private List<String> addresses(String raw) {
    if (!hasText(raw) || "not provided".equalsIgnoreCase(raw.trim())) return List.of();
    String decoded = HtmlUtils.htmlUnescape(raw);
    Matcher matcher = EMAIL_PATTERN.matcher(decoded);
    LinkedHashSet<String> values = new LinkedHashSet<>();
    while (matcher.find()) {
      values.add(matcher.group().toLowerCase(Locale.ROOT));
    }
    return List.copyOf(values);
  }

  private String normalize(String value) {
    if (!hasText(value)) return null;
    List<String> parsed = addresses(value);
    return parsed.isEmpty() ? value.trim().toLowerCase(Locale.ROOT) : parsed.getFirst();
  }

  private String trim(String value) {
    return hasText(value) ? value.trim() : null;
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
