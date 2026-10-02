package com.messaging.broadcast.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record BroadcastWhatsAppTemplateParameters(
    @Size(max = 20) List<@NotNull @Size(max = 1024) String> header,
    @Size(max = 100) List<@NotNull @Size(max = 1024) String> body,
    @Size(max = 20) List<@NotNull @Size(max = 1024) String> buttons) {

  public BroadcastWhatsAppTemplateParameters {
    header = header == null ? List.of() : List.copyOf(header);
    body = body == null ? List.of() : List.copyOf(body);
    buttons = buttons == null ? List.of() : List.copyOf(buttons);
  }

  public static BroadcastWhatsAppTemplateParameters empty() {
    return new BroadcastWhatsAppTemplateParameters(List.of(), List.of(), List.of());
  }
}
