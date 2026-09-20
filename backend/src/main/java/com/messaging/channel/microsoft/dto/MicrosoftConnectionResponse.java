package com.messaging.channel.microsoft.dto;

import com.messaging.channel.enums.ChannelConnectionStatus;
import com.messaging.channel.enums.SenderIdentityStatus;

public record MicrosoftConnectionResponse(
    String connectionId,
    String emailAddress,
    String displayName,
    ChannelConnectionStatus status,
    Sender sender) {

  public record Sender(
      String senderIdentityId,
      String emailAddress,
      SenderIdentityStatus status,
      boolean defaultIdentity) {}
}
