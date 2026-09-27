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

  private final NotificationEventRecorder recorder;

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
        message.getCreatedAt());
  }
}
