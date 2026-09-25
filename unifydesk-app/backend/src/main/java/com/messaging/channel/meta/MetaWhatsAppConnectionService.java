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
import com.messaging.channel.service.ChannelConnectionService;
import com.messaging.channel.service.ChannelEntitlementService;
import com.messaging.common.exception.BadRequestException;
import java.security.SecureRandom;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
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
  private final ChannelConnectionService channelConnectionService;
  private final MetaWhatsAppPersistenceService persistenceService;

  public MetaWhatsAppSignupConfigResponse signupConfig(Long companyId, Long userId) {
    requireConfigured();
    log.info(
        "meta_whatsapp_signup_config_start companyId={} userId={} appId={} configurationId={} graphApiVersion={}",
        companyId,
        userId,
        properties.getAppId(),
        properties.getConfigurationId(),
        properties.getGraphApiVersion());

    String state = stateService.create(companyId, userId);

    log.info(
        "meta_whatsapp_signup_config_ready companyId={} userId={} stateCreated=true",
        companyId,
        userId);

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
    log.info(
        "meta_whatsapp_complete_start companyId={} userId={} wabaId={} phoneNumberId={} hasCode={} hasState={}",
        companyId,
        userId,
        request.wabaId(),
        request.phoneNumberId(),
        request.code() != null && !request.code().isBlank(),
        request.state() != null && !request.state().isBlank());

    stateService.consume(request.state(), companyId, userId);
    log.info("meta_whatsapp_state_validated companyId={} userId={}", companyId, userId);

    log.info("meta_whatsapp_exchange_code_start companyId={}", companyId);
    TokenResponse token = graphClient.exchangeCode(request.code());
    if (token == null || token.accessToken() == null || token.accessToken().isBlank()) {
      log.warn("meta_whatsapp_exchange_code_missing_token companyId={}", companyId);
      throw new BadRequestException("Meta did not return an access token");
    }
    log.info("meta_whatsapp_exchange_code_success companyId={}", companyId);

    String wabaId = resolveWabaId(request.wabaId(), token.accessToken());
    List<PhoneNumber> phoneNumbers = graphClient.getPhoneNumbers(wabaId, token.accessToken());
    PhoneNumber phone = resolvePhoneNumber(request.phoneNumberId(), phoneNumbers);

    ChannelConnection existing =
        connectionRepository
            .findByCompanyIdAndProviderTypeAndExternalAccountId(
                companyId, ProviderType.META_WHATSAPP, wabaId)
            .orElse(null);

    if (existing == null) {
      entitlementService.assertCanCreateConnection(companyId, ChannelType.WHATSAPP);
      entitlementService.assertCanCreateSender(companyId, SenderIdentityType.PHONE_NUMBER);
    } else {
      boolean senderExists =
          senderIdentityRepository
              .findByConnectionIdAndIdentityTypeAndExternalId(
                  existing.getId(), SenderIdentityType.PHONE_NUMBER, phone.id())
              .isPresent();
      if (!senderExists) {
        entitlementService.assertCanCreateSender(companyId, SenderIdentityType.PHONE_NUMBER);
      }
    }

    WabaDetails waba = graphClient.getWaba(wabaId, token.accessToken());

    log.info(
        "meta_whatsapp_assets_resolved companyId={} wabaId={} phoneNumberId={}",
        companyId,
        wabaId,
        phone.id());

    String pin = sixDigitPin();
    graphClient.registerPhoneNumber(phone.id(), token.accessToken(), pin);
    log.info(
        "meta_whatsapp_phone_registered companyId={} wabaId={} phoneNumberId={}",
        companyId,
        wabaId,
        phone.id());

    graphClient.subscribeApp(wabaId, token.accessToken());
    log.info("meta_whatsapp_app_subscribed companyId={} wabaId={}", companyId, wabaId);

    MetaWhatsAppConnectionResponse response =
        persistenceService.persist(companyId, userId, wabaId, waba, phone, token, pin);
    channelConnectionService.cancelPendingPurge(
        companyId, Long.valueOf(response.connectionId()));

    log.info(
        "meta_whatsapp_complete_success companyId={} connectionId={} wabaId={} phoneNumberId={}",
        companyId,
        response.connectionId(),
        wabaId,
        phone.id());

    return response;
  }

  private String resolveWabaId(String requestedWabaId, String accessToken) {
    if (requestedWabaId != null && !requestedWabaId.isBlank()) {
      return requestedWabaId.trim();
    }

    List<String> grantedWabaIds = graphClient.getGrantedWabaIds(accessToken);
    if (grantedWabaIds.isEmpty()) {
      throw new BadRequestException(
          "Meta login succeeded, but no WhatsApp Business Account was granted to this connection");
    }
    if (grantedWabaIds.size() > 1) {
      throw new BadRequestException(
          "Meta granted multiple WhatsApp Business Accounts. Complete Embedded Signup again and select one account");
    }

    log.info("meta_whatsapp_waba_discovered_from_token wabaId={}", grantedWabaIds.getFirst());
    return grantedWabaIds.getFirst();
  }

  private PhoneNumber resolvePhoneNumber(
      String requestedPhoneNumberId, List<PhoneNumber> phoneNumbers) {
    if (requestedPhoneNumberId != null && !requestedPhoneNumberId.isBlank()) {
      return phoneNumbers.stream()
          .filter(phone -> requestedPhoneNumberId.trim().equals(phone.id()))
          .findFirst()
          .orElseThrow(
              () ->
                  new BadRequestException(
                      "Selected WhatsApp phone number does not belong to this WABA"));
    }

    if (phoneNumbers.isEmpty()) {
      throw new BadRequestException("The selected WhatsApp Business Account has no phone number");
    }
    if (phoneNumbers.size() > 1) {
      throw new BadRequestException(
          "The selected WhatsApp Business Account has multiple phone numbers. Complete Embedded Signup again and select one phone number");
    }

    log.info("meta_whatsapp_phone_discovered_from_waba phoneNumberId={}", phoneNumbers.getFirst().id());
    return phoneNumbers.getFirst();
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
