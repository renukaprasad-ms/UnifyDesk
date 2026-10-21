package com.messaging.notification.platform.core.event;

import com.messaging.inbox.entity.InboxMessage;
import com.messaging.inbox.enums.InboxMessageDirection;
import com.messaging.inbox.service.InboxMessagePostProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class InboxActivityNotificationPostProcessor implements InboxMessagePostProcessor {

  private final NotificationEventService recorder;

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void afterPersist(InboxMessage message) {
    if (message.getId() == null
        || message.getDirection() != InboxMessageDirection.INBOUND
        || message.isRead()
        || message.getConversation() == null
        || message.getConnection() == null) {
      return;
    }

    recorder.recordInboxActivity(
        message.getId(),
        message.getConversation().getCompanyId(),
        message.getConnection().getId(),
        compact(message.getFromAddress(), "Unknown sender", 160),
        compact(
            firstNonBlank(message.getSubject(), message.getConversation().getSubject()),
            "(No subject)",
            240),
        compact(
            firstNonBlank(message.getSnippet(), message.getConversation().getLastMessagePreview()),
            "",
            320),
        compact(message.getConnection().getName(), "Inbox", 120),
        message.getCreatedAt());
  }

  private String firstNonBlank(String primary, String fallback) {
    return primary != null && !primary.isBlank() ? primary : fallback;
  }

  private String compact(String value, String fallback, int maxLength) {
    String normalized =
        value == null
            ? ""
            : value.replaceAll("[\\p{Cntrl}]+", " ").replaceAll("\\s+", " ").trim();
    if (normalized.isBlank()) {
      normalized = fallback;
    }
    if (normalized.length() <= maxLength) {
      return normalized;
    }
    return normalized.substring(0, Math.max(1, maxLength - 1)).trim() + "…";
  }
}
