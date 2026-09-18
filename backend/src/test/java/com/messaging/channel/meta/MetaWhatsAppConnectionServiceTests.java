package com.messaging.channel.meta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.enums.ChannelConnectionStatus;
import com.messaging.channel.enums.ChannelType;
import com.messaging.channel.enums.ProviderType;
import com.messaging.channel.enums.SenderIdentityStatus;
import com.messaging.channel.enums.SenderIdentityType;
import com.messaging.channel.meta.MetaGraphClient.PhoneNumber;
import com.messaging.channel.meta.MetaGraphClient.TokenResponse;
import com.messaging.channel.meta.MetaGraphClient.WabaDetails;
import com.messaging.channel.meta.dto.CompleteMetaWhatsAppSignupRequest;
import com.messaging.channel.meta.dto.MetaWhatsAppConnectionResponse;
import com.messaging.channel.repository.ChannelConnectionRepository;
import com.messaging.channel.repository.SenderIdentityRepository;
import com.messaging.channel.service.ChannelEntitlementService;
import com.messaging.common.exception.BadRequestException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MetaWhatsAppConnectionServiceTests {

  private static final Long COMPANY_ID = 101L;
  private static final Long USER_ID = 202L;

  @Mock private MetaWhatsAppSignupStateService stateService;
  @Mock private MetaGraphClient graphClient;
  @Mock private ChannelConnectionRepository connectionRepository;
  @Mock private SenderIdentityRepository senderIdentityRepository;
  @Mock private ChannelEntitlementService entitlementService;
  @Mock private MetaWhatsAppPersistenceService persistenceService;

  private MetaWhatsAppConnectionService service;

  @BeforeEach
  void setUp() {
    MetaWhatsAppProperties properties = new MetaWhatsAppProperties();
    properties.setEnabled(true);
    properties.setAppId("app-id");
    properties.setAppSecret("app-secret");
    properties.setConfigurationId("config-id");
    properties.setGraphApiVersion("v26.0");
    properties.setRegisterPhoneNumber(true);

    service =
        new MetaWhatsAppConnectionService(
            properties,
            stateService,
            graphClient,
            connectionRepository,
            senderIdentityRepository,
            entitlementService,
            persistenceService);
  }

  @Test
  void completesEmbeddedSignupAndPersistsValidatedWabaAndPhone() {
    CompleteMetaWhatsAppSignupRequest request =
        new CompleteMetaWhatsAppSignupRequest("state", "code", "waba-1", "phone-1");
    TokenResponse token = new TokenResponse("access-token", "bearer", null);
    WabaDetails waba = new WabaDetails("waba-1", "Acme WhatsApp", "INR", "5");
    PhoneNumber phone = new PhoneNumber("phone-1", "+91 90000 00000", "Acme", "GREEN");
    MetaWhatsAppConnectionResponse expected =
        new MetaWhatsAppConnectionResponse(
            "connection-1",
            "waba-1",
            "Acme WhatsApp",
            ChannelConnectionStatus.ACTIVE,
            new MetaWhatsAppConnectionResponse.PhoneNumber(
                "sender-1",
                "phone-1",
                "+91 90000 00000",
                "Acme",
                "GREEN",
                SenderIdentityStatus.ACTIVE));

    when(connectionRepository.findByCompanyIdAndProviderTypeAndExternalAccountId(
            COMPANY_ID, ProviderType.META_WHATSAPP, "waba-1"))
        .thenReturn(Optional.empty());
    when(graphClient.exchangeCode("code")).thenReturn(token);
    when(graphClient.getWaba("waba-1", "access-token")).thenReturn(waba);
    when(graphClient.getPhoneNumbers("waba-1", "access-token")).thenReturn(List.of(phone));
    when(persistenceService.persist(
            COMPANY_ID,
            USER_ID,
            "waba-1",
            waba,
            phone,
            token,
            org.mockito.ArgumentMatchers.anyString()))
        .thenReturn(expected);

    MetaWhatsAppConnectionResponse actual = service.complete(COMPANY_ID, USER_ID, request);

    assertThat(actual).isSameAs(expected);
    verify(stateService).consume("state", COMPANY_ID, USER_ID);
    verify(entitlementService).assertCanCreateConnection(COMPANY_ID, ChannelType.WHATSAPP);
    verify(entitlementService).assertCanCreateSender(COMPANY_ID, SenderIdentityType.PHONE_NUMBER);

    ArgumentCaptor<String> pinCaptor = ArgumentCaptor.forClass(String.class);
    verify(graphClient).registerPhoneNumber("phone-1", "access-token", pinCaptor.capture());
    assertThat(pinCaptor.getValue()).matches("\\d{6}");
    verify(graphClient).subscribeApp("waba-1", "access-token");
  }

  @Test
  void rejectsPhoneNumberThatDoesNotBelongToReturnedWaba() {
    CompleteMetaWhatsAppSignupRequest request =
        new CompleteMetaWhatsAppSignupRequest("state", "code", "waba-1", "phone-1");
    TokenResponse token = new TokenResponse("access-token", "bearer", null);
    WabaDetails waba = new WabaDetails("waba-1", "Acme WhatsApp", "INR", "5");
    PhoneNumber differentPhone = new PhoneNumber("phone-2", "+91 91111 11111", "Other", "GREEN");

    when(connectionRepository.findByCompanyIdAndProviderTypeAndExternalAccountId(
            COMPANY_ID, ProviderType.META_WHATSAPP, "waba-1"))
        .thenReturn(Optional.empty());
    when(graphClient.exchangeCode("code")).thenReturn(token);
    when(graphClient.getWaba("waba-1", "access-token")).thenReturn(waba);
    when(graphClient.getPhoneNumbers("waba-1", "access-token")).thenReturn(List.of(differentPhone));

    assertThatThrownBy(() -> service.complete(COMPANY_ID, USER_ID, request))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("does not belong");

    verify(graphClient, never()).registerPhoneNumber(anyString(), anyString(), anyString());
    verify(graphClient, never()).subscribeApp(anyString(), anyString());
    verify(persistenceService, never())
        .persist(
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyLong(),
            anyString(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            anyString());
  }

  @Test
  void existingWabaOnlyChecksPhoneLimitWhenSelectedPhoneIsNew() {
    CompleteMetaWhatsAppSignupRequest request =
        new CompleteMetaWhatsAppSignupRequest("state", "code", "waba-1", "phone-1");
    ChannelConnection existing = new ChannelConnection();
    existing.setId(500L);

    when(connectionRepository.findByCompanyIdAndProviderTypeAndExternalAccountId(
            COMPANY_ID, ProviderType.META_WHATSAPP, "waba-1"))
        .thenReturn(Optional.of(existing));
    when(senderIdentityRepository.findByConnectionIdAndIdentityTypeAndExternalId(
            500L, SenderIdentityType.PHONE_NUMBER, "phone-1"))
        .thenReturn(Optional.empty());

    TokenResponse token = new TokenResponse("access-token", "bearer", null);
    WabaDetails waba = new WabaDetails("waba-1", "Acme WhatsApp", "INR", "5");
    PhoneNumber phone = new PhoneNumber("phone-1", "+91 90000 00000", "Acme", "GREEN");
    MetaWhatsAppConnectionResponse expected =
        new MetaWhatsAppConnectionResponse(
            "connection-1",
            "waba-1",
            "Acme WhatsApp",
            ChannelConnectionStatus.ACTIVE,
            new MetaWhatsAppConnectionResponse.PhoneNumber(
                "sender-1",
                "phone-1",
                "+91 90000 00000",
                "Acme",
                "GREEN",
                SenderIdentityStatus.ACTIVE));

    when(graphClient.exchangeCode("code")).thenReturn(token);
    when(graphClient.getWaba("waba-1", "access-token")).thenReturn(waba);
    when(graphClient.getPhoneNumbers("waba-1", "access-token")).thenReturn(List.of(phone));
    when(persistenceService.persist(
            COMPANY_ID,
            USER_ID,
            "waba-1",
            waba,
            phone,
            token,
            org.mockito.ArgumentMatchers.anyString()))
        .thenReturn(expected);

    service.complete(COMPANY_ID, USER_ID, request);

    verify(entitlementService, never()).assertCanCreateConnection(COMPANY_ID, ChannelType.WHATSAPP);
    verify(entitlementService).assertCanCreateSender(COMPANY_ID, SenderIdentityType.PHONE_NUMBER);
  }
}
