package com.messaging.channel.meta.dto;

import com.messaging.channel.enums.ChannelConnectionStatus;
import com.messaging.channel.enums.SenderIdentityStatus;

public record MetaWhatsAppConnectionResponse(
    String connectionId,
    String wabaId,
    String wabaName,
    ChannelConnectionStatus status,
    PhoneNumber phoneNumber) {

  public record PhoneNumber(
      String senderIdentityId,
      String phoneNumberId,
      String displayPhoneNumber,
      String verifiedName,
      String qualityRating,
      SenderIdentityStatus status) {}
}
