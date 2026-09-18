package com.messaging.channel.meta;

import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.enums.ChannelType;
import com.messaging.channel.enums.ProviderType;
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
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
  private final MetaWhatsAppPersistenceService persistenceService;

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
    PhoneNumber phone =
        requirePhoneNumber(
            request, graphClient.getPhoneNumbers(request.wabaId(), token.accessToken()));

    String pin = sixDigitPin();
    graphClient.registerPhoneNumber(phone.id(), token.accessToken(), pin);
    graphClient.subscribeApp(request.wabaId(), token.accessToken());

    return persistenceService.persist(companyId, userId, request.wabaId(), waba, phone, token, pin);
  }

  private PhoneNumber requirePhoneNumber(
      CompleteMetaWhatsAppSignupRequest request, List<PhoneNumber> phoneNumbers) {
    return phoneNumbers.stream()
        .filter(phone -> request.phoneNumberId().equals(phone.id()))
        .findFirst()
        .orElseThrow(
            () ->
                new BadRequestException(
                    "Selected WhatsApp phone number does not belong to this WABA"));
  }

  private String sixDigitPin() {
    return String.format("%06d", RANDOM.nextInt(1_000_000));
  }

  private void requireConfigured() {
    if (!properties.configured()) {
      throw new BadRequestException(
          "Meta WhatsApp is not configured. Configure the Meta app before connecting WhatsApp");
    }
  }
}
