package com.messaging.channel.gmail.dto;

import com.messaging.channel.enums.ChannelConnectionStatus;
import com.messaging.channel.enums.SenderIdentityStatus;

public record GmailConnectionResponse(
    String connectionId,
    String emailAddress,
    ChannelConnectionStatus status,
    SenderIdentity sender) {

  public record Sender(
      String senderIdentityId,
      String emailAddress,
      SenderIdentityStatus status,
      boolean defaultIdentity) {}
}
