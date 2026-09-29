package com.messaging.channel.meta.dto;

import com.messaging.channel.meta.control.MetaWhatsAppConnectionControl.ProductState;

public record MetaWhatsAppConnectionResponse(
    String connectionId,
    ProductState status,
    String wabaId,
    String whatsappAccountId,
    String messagingAccountId,
    Phone phone,
    NextAction nextAction) {

  public enum NextAction {
    NONE,
    WAIT,
    REAUTHORIZE,
    OPEN_META,
    RETRY
  }

  public record Phone(
      String senderIdentityId,
      String phoneNumberId,
      String displayPhoneNumber,
      String verifiedName,
      String qualityRating) {}
}
