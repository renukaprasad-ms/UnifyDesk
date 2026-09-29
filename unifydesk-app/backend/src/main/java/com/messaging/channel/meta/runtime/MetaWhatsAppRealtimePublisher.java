package com.messaging.channel.meta.runtime;

import com.messaging.channel.enums.ProviderType;
import com.messaging.inbox.dto.InboxEmailChangedEvent;
import com.messaging.realtime.service.RealtimeService;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MetaWhatsAppRealtimePublisher {

  private final RealtimeService realtimeService;

  public void inboxChanged(
      long companyId,
      long connectionId,
      Collection<Long> conversationIds,
      Collection<Long> messageIds) {
    try {
      realtimeService.sendToCompany(
          companyId,
          "inbox.email.changed",
          InboxEmailChangedEvent.delta(
              connectionId,
              ProviderType.META_WHATSAPP,
              conversationIds == null ? List.of() : List.copyOf(conversationIds),
              messageIds == null ? List.of() : List.copyOf(messageIds),
              List.of()));
    } catch (RuntimeException ignored) {
      // Realtime is advisory; the durable Inbox remains authoritative.
    }
  }

  public void statusChanged(
      long companyId, long connectionId, long conversationId, long messageId) {
    try {
      realtimeService.sendToCompany(
          companyId,
          "inbox.whatsapp.status.changed",
          Map.of(
              "connectionId", String.valueOf(connectionId),
              "conversationId", String.valueOf(conversationId),
              "messageId", String.valueOf(messageId)));
    } catch (RuntimeException ignored) {
      // Realtime is advisory; provider/runtime state remains authoritative.
    }
  }
}
