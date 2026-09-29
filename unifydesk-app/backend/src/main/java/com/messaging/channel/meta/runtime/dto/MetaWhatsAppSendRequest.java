package com.messaging.channel.meta.runtime.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record MetaWhatsAppSendRequest(
    @NotBlank @Size(max = 160) String clientRequestId,
    @NotBlank String type,
    @Size(max = 4096) String text,
    String replyToMessageId,
    String templateId,
    @Valid Parameters parameters,
    String mediaId,
    @Size(max = 1024) String caption) {

  public record Parameters(
      List<@Size(max = 1024) String> header,
      List<@Size(max = 1024) String> body,
      List<@Size(max = 1024) String> buttons) {
    public Parameters {
      header = header == null ? List.of() : List.copyOf(header);
      body = body == null ? List.of() : List.copyOf(body);
      buttons = buttons == null ? List.of() : List.copyOf(buttons);
    }
  }
}
