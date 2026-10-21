package com.messaging.notification.platform.core.event;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.messaging.channel.entity.ChannelConnection;
import com.messaging.inbox.entity.InboxConversation;
import com.messaging.inbox.entity.InboxMessage;
import com.messaging.inbox.enums.InboxMessageDirection;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class InboxActivityNotificationPostProcessorTests {

  @Test
  void recordsOnlyInboundUnreadMessageWithUsefulEmailContext() {
    NotificationEventService recorder = org.mockito.Mockito.mock(NotificationEventService.class);
    InboxActivityNotificationPostProcessor processor =
        new InboxActivityNotificationPostProcessor(recorder);
    InboxMessage message = message(InboxMessageDirection.INBOUND, false);

    processor.afterPersist(message);

    verify(recorder)
        .recordInboxActivity(
            44L,
            22L,
            33L,
            "Alice <alice@example.com>",
            "Quarterly update",
            "Here is the latest update",
            "Sales Inbox",
            message.getCreatedAt());
  }

  @Test
  void ignoresOutboundAndAlreadyReadMessages() {
    NotificationEventService recorder = org.mockito.Mockito.mock(NotificationEventService.class);
    InboxActivityNotificationPostProcessor processor =
        new InboxActivityNotificationPostProcessor(recorder);

    processor.afterPersist(message(InboxMessageDirection.OUTBOUND, false));
    processor.afterPersist(message(InboxMessageDirection.INBOUND, true));

    verify(recorder, never())
        .recordInboxActivity(
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any());
  }

  private InboxMessage message(InboxMessageDirection direction, boolean read) {
    InboxConversation conversation = new InboxConversation();
    conversation.setCompanyId(22L);
    conversation.setSubject("Quarterly update");
    conversation.setLastMessagePreview("Here is the latest update");

    ChannelConnection connection = org.mockito.Mockito.mock(ChannelConnection.class);
    when(connection.getId()).thenReturn(33L);
    when(connection.getName()).thenReturn("Sales Inbox");

    InboxMessage message = new InboxMessage();
    message.setId(44L);
    message.setConversation(conversation);
    message.setConnection(connection);
    message.setDirection(direction);
    message.setRead(read);
    message.setFromAddress("Alice <alice@example.com>");
    message.setSubject("Quarterly update");
    message.setSnippet("Here is the latest update");
    message.setCreatedAt(Instant.parse("2026-09-27T06:30:00Z"));
    return message;
  }
}
