package com.messaging.channel.meta;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.channel.credential.ProviderCredentialService;
import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.entity.SenderIdentity;
import com.messaging.channel.enums.ChannelConnectionStatus;
import com.messaging.channel.enums.ChannelType;
import com.messaging.channel.enums.CredentialType;
import com.messaging.channel.enums.ProviderType;
import com.messaging.channel.enums.SenderIdentityStatus;
import com.messaging.channel.enums.SenderIdentityType;
import com.messaging.channel.meta.MetaGraphClient.PhoneNumber;
import com.messaging.channel.meta.MetaGraphClient.TokenResponse;
import com.messaging.channel.meta.MetaGraphClient.WabaDetails;
import com.messaging.channel.meta.dto.MetaWhatsAppConnectionResponse;
import com.messaging.channel.repository.ChannelConnectionRepository;
import com.messaging.channel.repository.SenderIdentityRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MetaWhatsAppPersistenceService {

  private final ChannelConnectionRepository connectionRepository;
  private final SenderIdentityRepository senderIdentityRepository;
  private final ProviderCredentialService credentialService;
  private final MetaWhatsAppProperties properties;
  private final ObjectMapper objectMapper;

  @Transactional
  public MetaWhatsAppConnectionResponse persist(
      Long companyId,
      Long userId,
      String wabaId,
      WabaDetails waba,
      PhoneNumber phone,
      TokenResponse token,
      String pin) {
    ChannelConnection connection =
        connectionRepository
            .findByCompanyIdAndProviderTypeAndExternalAccountId(
                companyId, ProviderType.META_WHATSAPP, wabaId)
            .orElseGet(ChannelConnection::new);

    if (connection.getId() == null) {
      connection.setCompanyId(companyId);
      connection.setChannelType(ChannelType.WHATSAPP);
      connection.setProviderType(ProviderType.META_WHATSAPP);
      connection.setCreatedByUserId(userId);
    }
    connection.setExternalAccountId(wabaId);
    connection.setName(
        waba.name() == null || waba.name().isBlank() ? "WhatsApp Business" : waba.name().trim());
    connection.setStatus(ChannelConnectionStatus.ACTIVE);
    connection.setLastSyncAt(Instant.now());
    connection = connectionRepository.saveAndFlush(connection);

    Instant expiresAt =
        token.expiresIn() == null || token.expiresIn() <= 0
            ? null
            : Instant.now().plusSeconds(token.expiresIn());

    credentialService.upsert(
        connection,
        CredentialType.OAUTH_TOKEN_SET,
        json(
            new StoredMetaToken(
                token.accessToken(), wabaId, phone.id(), properties.getGraphApiVersion())),
        expiresAt);

    if (properties.isRegisterPhoneNumber()) {
      credentialService.upsert(
          connection, CredentialType.PROVIDER_SECRET, json(new StoredMetaSecret(pin)), null);
    }

    SenderIdentity sender =
        senderIdentityRepository
            .findByConnectionIdAndIdentityTypeAndExternalId(
                connection.getId(), SenderIdentityType.PHONE_NUMBER, phone.id())
            .orElseGet(SenderIdentity::new);

    if (sender.getId() == null) {
      sender.setConnection(connection);
      sender.setIdentityType(SenderIdentityType.PHONE_NUMBER);
      sender.setDefaultIdentity(
          senderIdentityRepository
              .findFirstByConnectionIdAndDefaultIdentityTrue(connection.getId())
              .isEmpty());
    }
    sender.setExternalId(phone.id());
    sender.setValue(
        phone.displayPhoneNumber() == null || phone.displayPhoneNumber().isBlank()
            ? phone.id()
            : phone.displayPhoneNumber().trim());
    sender.setStatus(SenderIdentityStatus.ACTIVE);
    sender = senderIdentityRepository.saveAndFlush(sender);

    return new MetaWhatsAppConnectionResponse(
        connection.getId().toString(),
        wabaId,
        connection.getName(),
        connection.getStatus(),
        new MetaWhatsAppConnectionResponse.PhoneNumber(
            sender.getId().toString(),
            phone.id(),
            sender.getValue(),
            phone.verifiedName(),
            phone.qualityRating(),
            sender.getStatus()));
  }

  private String json(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to serialize Meta credential", exception);
    }
  }

  private record StoredMetaToken(
      String accessToken, String wabaId, String phoneNumberId, String graphApiVersion) {}

  private record StoredMetaSecret(String twoStepPin) {}
}
