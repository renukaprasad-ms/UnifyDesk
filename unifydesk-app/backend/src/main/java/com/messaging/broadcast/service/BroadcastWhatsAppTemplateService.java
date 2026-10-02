package com.messaging.broadcast.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.broadcast.dto.BroadcastWhatsAppTemplateParameters;
import com.messaging.channel.meta.runtime.MetaWhatsAppTemplateService;
import com.messaging.channel.meta.runtime.dto.MetaWhatsAppSendRequest;
import com.messaging.common.exception.BadRequestException;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BroadcastWhatsAppTemplateService {

  private final MetaWhatsAppTemplateService templateService;
  private final ObjectMapper objectMapper;

  public Prepared validate(
      long companyId,
      long channelConnectionId,
      long templateId,
      BroadcastWhatsAppTemplateParameters parameters) {
    MetaWhatsAppTemplateService.PreparedTemplate prepared =
        templateService.prepare(
            companyId,
            channelConnectionId,
            Long.toString(templateId),
            toProviderParameters(parameters));
    return new Prepared(
        prepared.name(),
        prepared.language(),
        writeProviderComponents(prepared.providerComponents()));
  }

  public String writeParameters(BroadcastWhatsAppTemplateParameters parameters) {
    try {
      return objectMapper.writeValueAsString(
          parameters == null ? BroadcastWhatsAppTemplateParameters.empty() : parameters);
    } catch (Exception exception) {
      throw new BadRequestException("WHATSAPP_TEMPLATE_PARAMETERS_INVALID");
    }
  }

  public BroadcastWhatsAppTemplateParameters readParameters(String json) {
    if (json == null || json.isBlank()) return BroadcastWhatsAppTemplateParameters.empty();
    try {
      return objectMapper.readValue(json, BroadcastWhatsAppTemplateParameters.class);
    } catch (Exception exception) {
      throw new BadRequestException("WHATSAPP_TEMPLATE_PARAMETERS_INVALID");
    }
  }

  private MetaWhatsAppSendRequest.Parameters toProviderParameters(
      BroadcastWhatsAppTemplateParameters parameters) {
    BroadcastWhatsAppTemplateParameters safe =
        parameters == null ? BroadcastWhatsAppTemplateParameters.empty() : parameters;
    return new MetaWhatsAppSendRequest.Parameters(
        safe.header(), safe.body(), safe.buttons());
  }

  private String writeProviderComponents(List<Map<String, Object>> components) {
    try {
      return objectMapper.writeValueAsString(components == null ? List.of() : components);
    } catch (Exception exception) {
      throw new BadRequestException("WHATSAPP_TEMPLATE_PARAMETERS_INVALID");
    }
  }

  public record Prepared(String name, String language, String providerComponentsJson) {}
}
