package com.messaging.channel.zoho.dto;

import com.messaging.channel.enums.ChannelConnectionStatus;
import com.messaging.channel.enums.SenderIdentityStatus;

public record ZohoMailConnectionResponse(
    String connectionId,
    String email,
    String displayName,
    String location,
    ChannelConnectionStatus status,
    Sender sender) {

  public record Sender(
      String senderIdentityId,
      String email,
      SenderIdentityStatus status,
      boolean defaultIdentity) {}
}
