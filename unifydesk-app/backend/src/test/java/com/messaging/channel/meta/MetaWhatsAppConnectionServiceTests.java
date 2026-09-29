package com.messaging.channel.meta;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.enums.ProviderType;
import com.messaging.channel.meta.MetaGraphClient.GranularScope;
import com.messaging.channel.meta.MetaGraphClient.PhoneNumber;
import com.messaging.channel.meta.MetaGraphClient.TokenInspection;
import com.messaging.channel.meta.MetaGraphClient.TokenResponse;
import com.messaging.channel.meta.MetaGraphClient.WabaDetails;
import com.messaging.channel.meta.MetaWhatsAppSignupStateService.ConsumedSignupState;
import com.messaging.channel.meta.MetaWhatsAppSignupStateService.SignupMode;
import com.messaging.channel.meta.control.MetaWhatsAppConnectionControl;
import com.messaging.channel.meta.control.MetaWhatsAppConnectionControl.AuthState;
import com.messaging.channel.meta.control.MetaWhatsAppConnectionControl.CoexistenceState;
import com.messaging.channel.meta.control.MetaWhatsAppConnectionControl.ProductState;
import com.messaging.channel.meta.control.MetaWhatsAppConnectionControl.RegistrationState;
import com.messaging.channel.meta.control.MetaWhatsAppConnectionControl.SubscriptionState;
import com.messaging.channel.meta.control.MetaWhatsAppConnectionControlRepository;
import com.messaging.channel.meta.dto.CompleteMetaWhatsAppSignupRequest;
import com.messaging.channel.repository.ChannelConnectionRepository;
import com.messaging.channel.repository.SenderIdentityRepository;
import com.messaging.channel.service.ChannelConnectionService;
import com.messaging.channel.service.ChannelEntitlementService;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.ConflictException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
  @Mock private MetaWhatsAppConnectionControlRepository controlRepository;
  @Mock private ChannelEntitlementService entitlementService;
  @Mock private ChannelConnectionService channelConnectionService;
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

    service =
        new MetaWhatsAppConnectionService(
            properties,
            stateService,
            graphClient,
            connectionRepository,
            senderIdentityRepository,
            controlRepository,
            entitlementService,
            channelConnectionService,
            persistenceService);
  }

  @Test
  void rejectsTokenMissingRequiredScopeBeforeAssetPersistence() {
    CompleteMetaWhatsAppSignupRequest request = request("waba-1", "phone-1");
    when(stateService.consume("state", COMPANY_ID, USER_ID))
        .thenReturn(new ConsumedSignupState(SignupMode.CONNECT, null, null));
    when(graphClient.exchangeCode("code"))
        .thenReturn(new TokenResponse("access-token", "bearer", 3600L));
    when(graphClient.inspectToken("access-token"))
        .thenReturn(
            new TokenInspection(
                true,
                Set.of("whatsapp_business_management"),
                List.of(
                    new GranularScope(
                        "whatsapp_business_management", List.of("waba-1"))),
                null));

    assertThatThrownBy(() -> service.complete(COMPANY_ID, USER_ID, request))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("WHATSAPP_PERMISSION_MISSING");

    verify(graphClient, never()).getWaba("waba-1", "access-token");
  }

  @Test
  void rejectsTamperedWabaNotGrantedByExchangedToken() {
    CompleteMetaWhatsAppSignupRequest request = request("waba-tampered", "phone-1");
    when(stateService.consume("state", COMPANY_ID, USER_ID))
        .thenReturn(new ConsumedSignupState(SignupMode.CONNECT, null, null));
    when(graphClient.exchangeCode("code"))
        .thenReturn(new TokenResponse("access-token", "bearer", 3600L));
    when(graphClient.inspectToken("access-token"))
        .thenReturn(validInspection("waba-1"));

    assertThatThrownBy(() -> service.complete(COMPANY_ID, USER_ID, request))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("WHATSAPP_ASSET_NOT_GRANTED");

    verify(graphClient, never()).getPhoneNumbers("waba-tampered", "access-token");
  }

  @Test
  void rejectsTamperedPhoneNotReturnedByValidatedWaba() {
    CompleteMetaWhatsAppSignupRequest request = request("waba-1", "phone-tampered");
    when(stateService.consume("state", COMPANY_ID, USER_ID))
        .thenReturn(new ConsumedSignupState(SignupMode.CONNECT, null, null));
    when(graphClient.exchangeCode("code"))
        .thenReturn(new TokenResponse("access-token", "bearer", 3600L));
    when(graphClient.inspectToken("access-token"))
        .thenReturn(validInspection("waba-1"));
    when(graphClient.getWaba("waba-1", "access-token"))
        .thenReturn(new WabaDetails("waba-1", "Acme", "INR", "5"));
    when(graphClient.getPhoneNumbers("waba-1", "access-token"))
        .thenReturn(List.of(new PhoneNumber("phone-1", "+919000000000", "Acme", "GREEN")));

    assertThatThrownBy(() -> service.complete(COMPANY_ID, USER_ID, request))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("WHATSAPP_ASSET_NOT_GRANTED");

    verify(persistenceService, never())
        .persistPending(
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.nullable(Long.class),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.nullable(String.class),
            org.mockito.ArgumentMatchers.nullable(String.class),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.nullable(java.time.Instant.class),
            org.mockito.ArgumentMatchers.any());
  }

  @Test
  void rejectsReauthorizationThatAttemptsToSwitchPhone() {
    CompleteMetaWhatsAppSignupRequest request = request("waba-1", "phone-2");
    when(stateService.consume("state", COMPANY_ID, USER_ID))
        .thenReturn(new ConsumedSignupState(SignupMode.REAUTHORIZE, 500L, "phone-1"));
    when(graphClient.exchangeCode("code"))
        .thenReturn(new TokenResponse("access-token", "bearer", 3600L));
    when(graphClient.inspectToken("access-token"))
        .thenReturn(validInspection("waba-1"));
    when(graphClient.getWaba("waba-1", "access-token"))
        .thenReturn(new WabaDetails("waba-1", "Acme", "INR", "5"));
    when(graphClient.getPhoneNumbers("waba-1", "access-token"))
        .thenReturn(List.of(new PhoneNumber("phone-2", "+919111111111", "Acme", "GREEN")));

    assertThatThrownBy(() -> service.complete(COMPANY_ID, USER_ID, request))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("cannot transfer");
  }

  @Test
  void rejectsPhoneAlreadyOwnedByAnotherCompanyBeforePersistence() {
    CompleteMetaWhatsAppSignupRequest request = request("waba-1", "phone-1");
    when(stateService.consume("state", COMPANY_ID, USER_ID))
        .thenReturn(new ConsumedSignupState(SignupMode.CONNECT, null, null));
    when(graphClient.exchangeCode("code"))
        .thenReturn(new TokenResponse("access-token", "bearer", 3600L));
    when(graphClient.inspectToken("access-token"))
        .thenReturn(validInspection("waba-1"));
    when(graphClient.getWaba("waba-1", "access-token"))
        .thenReturn(new WabaDetails("waba-1", "Acme", "INR", "5"));
    when(graphClient.getPhoneNumbers("waba-1", "access-token"))
        .thenReturn(List.of(new PhoneNumber("phone-1", "+919000000000", "Acme", "GREEN")));

    MetaWhatsAppConnectionControl foreign = activeControl(999L, 900L, "phone-1");
    when(controlRepository.findFirstBySelectedPhoneNumberIdAndProductStateNot(
            "phone-1", ProductState.DISCONNECTED))
        .thenReturn(Optional.of(foreign));

    assertThatThrownBy(() -> service.complete(COMPANY_ID, USER_ID, request))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("WHATSAPP_PHONE_OWNED_BY_OTHER_COMPANY");
  }

  private CompleteMetaWhatsAppSignupRequest request(String wabaId, String phoneNumberId) {
    return new CompleteMetaWhatsAppSignupRequest(
        "state", "code", wabaId, phoneNumberId, null, null);
  }

  private TokenInspection validInspection(String wabaId) {
    return new TokenInspection(
        true,
        Set.of("whatsapp_business_management", "whatsapp_business_messaging"),
        List.of(
            new GranularScope("whatsapp_business_management", List.of(wabaId)),
            new GranularScope("whatsapp_business_messaging", List.of(wabaId))),
        null);
  }

  private MetaWhatsAppConnectionControl activeControl(
      Long companyId, Long connectionId, String phoneNumberId) {
    MetaWhatsAppConnectionControl control = new MetaWhatsAppConnectionControl();
    control.setCompanyId(companyId);
    control.setConnectionId(connectionId);
    control.setLegacyWabaId("waba-1");
    control.setSelectedPhoneNumberId(phoneNumberId);
    control.setProductState(ProductState.ACTIVE);
    control.setAuthState(AuthState.VALID);
    control.setRegistrationState(RegistrationState.REGISTERED);
    control.setSubscriptionState(SubscriptionState.SUBSCRIBED);
    control.setCoexistenceState(CoexistenceState.DISABLED);
    return control;
  }
}
