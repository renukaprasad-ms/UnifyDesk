package com.messaging.inbox.service;

import com.messaging.channel.enums.ChannelType;
import com.messaging.inbox.entity.InboxMessage;
import com.messaging.inbox.enums.InboxMessageDirection;
import com.messaging.inbox.enums.InboxParticipantType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InboxParticipantContactLinkPostProcessor implements InboxMessagePostProcessor {

  private final InboxParticipantContactLinkService linkService;

  @Override
  public void afterPersist(InboxMessage message) {
    if (message == null
        || message.getDirection() != InboxMessageDirection.INBOUND
        || message.getConversation() == null
        || message.getConnection() == null
        || message.getFromAddress() == null
        || message.getFromAddress().isBlank()) {
      return;
    }

    InboxParticipantType participantType = participantType(message.getConnection().getChannelType());
    if (participantType == null) return;

    linkService.resolve(
        message.getConversation().getCompanyId(),
        message.getConversation().getId(),
        participantType,
        message.getFromAddress());
  }

  private InboxParticipantType participantType(ChannelType channelType) {
    if (channelType == ChannelType.EMAIL) return InboxParticipantType.EMAIL_ADDRESS;
    if (channelType == ChannelType.WHATSAPP) return InboxParticipantType.PHONE_NUMBER;
    return null;
  }
}
