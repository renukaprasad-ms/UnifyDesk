package com.messaging.channel.meta.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.channel.credential.ProviderCredentialService;
import com.messaging.channel.enums.CredentialType;
import com.messaging.common.exception.ServiceUnavailableException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MetaWhatsAppCredentialResolver {
  private final ProviderCredentialService credentialService;
  private final ObjectMapper objectMapper;

  public StoredCredential require(long connectionId) {
    try {
      JsonNode root =
          objectMapper.readTree(
              credentialService.read(connectionId, CredentialType.OAUTH_TOKEN_SET));
      String token = text(root, "accessToken");
      String wabaId = text(root, "wabaId");
      String phoneNumberId = text(root, "phoneNumberId");
      String graphApiVersion = text(root, "graphApiVersion");
      if (token == null || phoneNumberId == null || graphApiVersion == null) {
        throw new ServiceUnavailableException("WHATSAPP_REAUTH_REQUIRED");
      }
      return new StoredCredential(token, wabaId, phoneNumberId, graphApiVersion);
    } catch (ServiceUnavailableException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new ServiceUnavailableException("WHATSAPP_REAUTH_REQUIRED", exception);
    }
  }

  private String text(JsonNode root, String field) {
    JsonNode value = root == null ? null : root.get(field);
    return value == null || !value.isTextual() || value.asText().isBlank()
        ? null
        : value.asText().trim();
  }

  public record StoredCredential(
      String accessToken, String wabaId, String phoneNumberId, String graphApiVersion) {}
}
