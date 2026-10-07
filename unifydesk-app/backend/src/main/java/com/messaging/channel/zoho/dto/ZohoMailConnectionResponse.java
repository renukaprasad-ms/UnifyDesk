package com.messaging.channel.zoho.dto;

import com.messaging.channel.enums.ChannelConnectionStatus;
import com.messaging.channel.enums.SenderIdentityStatus;

public record ZohoMailConnectionResponse(
    String connectionId,
    String accountId,
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
