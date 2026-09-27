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
  void recordsOnlyInboundUnreadMessageUsingLocalMessageId() {
    NotificationEventRecorder recorder = org.mockito.Mockito.mock(NotificationEventRecorder.class);
    InboxActivityNotificationPostProcessor processor =
        new InboxActivityNotificationPostProcessor(recorder);
    InboxMessage message = message(InboxMessageDirection.INBOUND, false);

    processor.afterPersist(message);

    verify(recorder).recordInboxActivity(44L, 22L, 33L, message.getCreatedAt());
  }

  @Test
  void ignoresOutboundAndAlreadyReadMessages() {
    NotificationEventRecorder recorder = org.mockito.Mockito.mock(NotificationEventRecorder.class);
    InboxActivityNotificationPostProcessor processor =
        new InboxActivityNotificationPostProcessor(recorder);

    processor.afterPersist(message(InboxMessageDirection.OUTBOUND, false));
    processor.afterPersist(message(InboxMessageDirection.INBOUND, true));

    verify(recorder, never())
        .recordInboxActivity(
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.any());
  }

  private InboxMessage message(InboxMessageDirection direction, boolean read) {
    InboxConversation conversation = org.mockito.Mockito.mock(InboxConversation.class);
    ChannelConnection connection = org.mockito.Mockito.mock(ChannelConnection.class);
    when(conversation.getCompanyId()).thenReturn(22L);
    when(connection.getId()).thenReturn(33L);
    InboxMessage message = new InboxMessage();
    message.setId(44L);
    message.setConversation(conversation);
    message.setConnection(connection);
    message.setDirection(direction);
    message.setRead(read);
    message.setCreatedAt(Instant.parse("2026-09-27T06:30:00Z"));
    return message;
  }
}
