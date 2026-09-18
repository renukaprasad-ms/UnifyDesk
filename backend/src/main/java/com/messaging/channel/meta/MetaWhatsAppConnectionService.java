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
import com.messaging.channel.meta.dto.CompleteMetaWhatsAppSignupRequest;
import com.messaging.channel.meta.dto.MetaWhatsAppConnectionResponse;
import com.messaging.channel.meta.dto.MetaWhatsAppSignupConfigResponse;
import com.messaging.channel.repository.ChannelConnectionRepository;
import com.messaging.channel.repository.SenderIdentityRepository;
import com.messaging.channel.service.ChannelEntitlementService;
import com.messaging.common.exception.BadRequestException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MetaWhatsAppConnectionService {

  private static final SecureRandom RANDOM = new SecureRandom();

  private final MetaWhatsAppProperties properties;
  private final MetaWhatsAppSignupStateService stateService;
  private final MetaGraphClient graphClient;
  private final ChannelConnectionRepository connectionRepository;
  private final SenderIdentityRepository senderIdentityRepository;
  private final ChannelEntitlementService entitlementService;
  private final ProviderCredentialService credentialService;
  private final ObjectMapper objectMapper;

  public MetaWhatsAppSignupConfigResponse signupConfig(Long companyId, Long userId) {
    requireConfigured();
    String state = stateService.create(companyId, userId);
    return new MetaWhatsAppSignupConfigResponse(
        properties.getAppId(),
        properties.getConfigurationId(),
        properties.getGraphApiVersion(),
        state,
        false);
  }

  public MetaWhatsAppConnectionResponse complete(
      Long companyId, Long userId, CompleteMetaWhatsAppSignupRequest request) {
    requireConfigured();
    stateService.consume(request.state(), companyId, userId);

    ChannelConnection existing =
        connectionRepository
            .findByCompanyIdAndProviderTypeAndExternalAccountId(
                companyId, ProviderType.META_WHATSAPP, request.wabaId())
            .orElse(null);

    if (existing == null) {
      entitlementService.assertCanCreateConnection(companyId, ChannelType.WHATSAPP);
      entitlementService.assertCanCreateSender(companyId, SenderIdentityType.PHONE_NUMBER);
    } else {
      boolean senderExists =
          senderIdentityRepository
              .findByConnectionIdAndIdentityTypeAndExternalId(
                  existing.getId(), SenderIdentityType.PHONE_NUMBER, request.phoneNumberId())
              .isPresent();
      if (!senderExists) {
        entitlementService.assertCanCreateSender(companyId, SenderIdentityType.PHONE_NUMBER);
      }
    }

    TokenResponse token = graphClient.exchangeCode(request.code());
    if (token == null || token.accessToken() == null || token.accessToken().isBlank()) {
      throw new BadRequestException("Meta did not return an access token");
    }

    WabaDetails waba = graphClient.getWaba(request.wabaId(), token.accessToken());
    PhoneNumber phone = requirePhoneNumber(request, graphClient.getPhoneNumbers(request.wabaId(), token.accessToken()));

    String pin = sixDigitPin();
    graphClient.registerPhoneNumber(phone.id(), token.accessToken(), pin);
    graphClient.subscribeApp(request.wabaId(), token.accessToken());

    return persist(companyId, userId, request.wabaId(), waba, phone, token, pin);
  }

  @Transactional
  protected MetaWhatsAppConnectionResponse persist(
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

    boolean newConnection = connection.getId() == null;
    if (newConnection) {
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
                token.accessToken(),
                wabaId,
                phone.id(),
                properties.getGraphApiVersion())),
        expiresAt);

    if (properties.isRegisterPhoneNumber()) {
      credentialService.upsert(
          connection,
          CredentialType.PROVIDER_SECRET,
          json(new StoredMetaSecret(pin)),
          null);
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

  private PhoneNumber requirePhoneNumber(
      CompleteMetaWhatsAppSignupRequest request, List<PhoneNumber> phoneNumbers) {
    return phoneNumbers.stream()
        .filter(phone -> request.phoneNumberId().equals(phone.id()))
        .findFirst()
        .orElseThrow(
            () -> new BadRequestException("Selected WhatsApp phone number does not belong to this WABA"));
  }

  private String sixDigitPin() {
    return String.format("%06d", RANDOM.nextInt(1_000_000));
  }

  private String json(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to serialize Meta credential", exception);
    }
  }

  private void requireConfigured() {
    if (!properties.configured()) {
      throw new BadRequestException(
          "Meta WhatsApp is not configured. Configure the Meta app before connecting WhatsApp");
    }
  }

  private record StoredMetaToken(
      String accessToken, String wabaId, String phoneNumberId, String graphApiVersion) {}

  private record StoredMetaSecret(String twoStepPin) {}
}
