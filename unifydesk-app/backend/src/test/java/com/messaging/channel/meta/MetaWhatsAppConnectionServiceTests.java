package com.messaging.channel.meta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.entity.SenderIdentity;
import com.messaging.channel.enums.ProviderType;
import com.messaging.channel.meta.MetaGraphClient.GranularScope;
import com.messaging.channel.meta.MetaGraphClient.PhoneNumber;
import com.messaging.channel.meta.MetaGraphClient.TokenInspection;
import com.messaging.channel.meta.MetaGraphClient.TokenResponse;
import com.messaging.channel.meta.MetaGraphClient.WabaDetails;
import com.messaging.channel.meta.MetaWhatsAppPersistenceService.PendingProvisioning;
import com.messaging.channel.meta.MetaWhatsAppPersistenceService.StoredMetaToken;
import com.messaging.channel.meta.MetaWhatsAppSignupStateService.ConsumedSignupState;
import com.messaging.channel.meta.MetaWhatsAppSignupStateService.SignupMode;
import com.messaging.channel.meta.control.MetaWhatsAppConnectionControl;
import com.messaging.channel.meta.control.MetaWhatsAppConnectionControl.AuthState;
import com.messaging.channel.meta.control.MetaWhatsAppConnectionControl.CoexistenceState;
import com.messaging.channel.meta.control.MetaWhatsAppConnectionControl.ProductState;
import com.messaging.channel.meta.control.MetaWhatsAppConnectionControl.RegistrationState;
import com.messaging.channel.meta.control.MetaWhatsAppConnectionControl.SubscriptionState;
import com.messaging.channel.meta.control.MetaWhatsAppConnectionControlRepository;
import com.messaging.channel.meta.control.MetaGraphException;
import com.messaging.channel.meta.control.MetaGraphException.SafeCategory;
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
    org.mockito.Mockito.lenient()
        .when(
            persistenceService.tryClaimProvisioning(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString()))
        .thenReturn(true);
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

  @Test
  void freshConnectCannotSilentlySwitchExistingConnectionPhone() {
    CompleteMetaWhatsAppSignupRequest request = request("waba-1", "phone-2");
    ChannelConnection existingConnection = new ChannelConnection();
    existingConnection.setId(500L);
    existingConnection.setCompanyId(COMPANY_ID);
    existingConnection.setProviderType(ProviderType.META_WHATSAPP);
    MetaWhatsAppConnectionControl existingControl = activeControl(COMPANY_ID, 500L, "phone-1");

    when(stateService.consume("state", COMPANY_ID, USER_ID))
        .thenReturn(new ConsumedSignupState(SignupMode.CONNECT, null, null));
    when(graphClient.exchangeCode("code"))
        .thenReturn(new TokenResponse("access-token", "bearer", 3600L));
    when(graphClient.inspectToken("access-token")).thenReturn(validInspection("waba-1"));
    when(graphClient.getWaba("waba-1", "access-token"))
        .thenReturn(new WabaDetails("waba-1", "Acme", "INR", "5"));
    when(graphClient.getPhoneNumbers("waba-1", "access-token"))
        .thenReturn(List.of(new PhoneNumber("phone-2", "+919111111111", "Acme", "GREEN")));
    when(controlRepository.findFirstBySelectedPhoneNumberIdAndProductStateNot(
            "phone-2", ProductState.DISCONNECTED))
        .thenReturn(Optional.empty());
    when(connectionRepository.findByCompanyIdAndProviderTypeAndExternalAccountId(
            COMPANY_ID, ProviderType.META_WHATSAPP, "waba-1"))
        .thenReturn(Optional.of(existingConnection));
    when(controlRepository.findById(500L)).thenReturn(Optional.of(existingControl));

    assertThatThrownBy(() -> service.complete(COMPANY_ID, USER_ID, request))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("bound to another phone");
  }

  @Test
  void multipleGrantedAccountsRequireExplicitSelection() {
    CompleteMetaWhatsAppSignupRequest request = request(null, "phone-1");
    when(stateService.consume("state", COMPANY_ID, USER_ID))
        .thenReturn(new ConsumedSignupState(SignupMode.CONNECT, null, null));
    when(graphClient.exchangeCode("code"))
        .thenReturn(new TokenResponse("access-token", "bearer", 3600L));
    when(graphClient.inspectToken("access-token"))
        .thenReturn(
            new TokenInspection(
                true,
                Set.of("whatsapp_business_management", "whatsapp_business_messaging"),
                List.of(
                    new GranularScope(
                        "whatsapp_business_management", List.of("waba-1", "waba-2")),
                    new GranularScope(
                        "whatsapp_business_messaging", List.of("waba-1", "waba-2"))),
                null));

    assertThatThrownBy(() -> service.complete(COMPANY_ID, USER_ID, request))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("multiple WhatsApp accounts");
  }

  @Test
  void reauthorizeSessionBindsExistingConnectionAndPhone() {
    MetaWhatsAppConnectionControl existing = activeControl(COMPANY_ID, 600L, "phone-1");
    when(controlRepository.findByCompanyIdOrderByUpdatedAtDesc(COMPANY_ID))
        .thenReturn(List.of(existing));
    var session =
        new MetaWhatsAppSignupStateService.SignupSession(
            "fresh-state", java.time.Instant.now().plusSeconds(600));
    when(stateService.createReauthorize(COMPANY_ID, USER_ID, 600L, "phone-1"))
        .thenReturn(session);

    var response = service.reauthorizeSession(COMPANY_ID, USER_ID);

    assertThat(response.state()).isEqualTo("fresh-state");
    assertThat(response.embeddedSignupVersion()).isEqualTo("4");
    verify(stateService).createReauthorize(COMPANY_ID, USER_ID, 600L, "phone-1");
  }

  @Test
  void reconnectSameAssetConvergesWithoutTransfer() {
    CompleteMetaWhatsAppSignupRequest request = request("waba-1", "phone-1");
    MetaWhatsAppConnectionControl existing = activeControl(COMPANY_ID, 500L, "phone-1");
    ChannelConnection connection = new ChannelConnection();
    connection.setId(500L);
    connection.setCompanyId(COMPANY_ID);
    connection.setProviderType(ProviderType.META_WHATSAPP);
    SenderIdentity sender = new SenderIdentity();
    sender.setId(700L);
    sender.setConnection(connection);
    sender.setExternalId("phone-1");
    sender.setValue("+919000000000");

    when(stateService.consume("state", COMPANY_ID, USER_ID))
        .thenReturn(new ConsumedSignupState(SignupMode.REAUTHORIZE, 500L, "phone-1"));
    when(graphClient.exchangeCode("code"))
        .thenReturn(new TokenResponse("new-token", "bearer", 3600L));
    when(graphClient.inspectToken("new-token")).thenReturn(validInspection("waba-1"));
    when(graphClient.getWaba("waba-1", "new-token"))
        .thenReturn(new WabaDetails("waba-1", "Acme", "INR", "5"));
    when(graphClient.getPhoneNumbers("waba-1", "new-token"))
        .thenReturn(List.of(new PhoneNumber("phone-1", "+919000000000", "Acme", "GREEN")));
    when(controlRepository.findFirstBySelectedPhoneNumberIdAndProductStateNot(
            "phone-1", ProductState.DISCONNECTED))
        .thenReturn(Optional.of(existing));
    when(connectionRepository.findByIdAndCompanyId(500L, COMPANY_ID))
        .thenReturn(Optional.of(connection));
    when(senderIdentityRepository.findByConnectionIdAndIdentityTypeAndExternalId(
            500L, com.messaging.channel.enums.SenderIdentityType.PHONE_NUMBER, "phone-1"))
        .thenReturn(Optional.of(sender));
    when(persistenceService.requireControl(COMPANY_ID, 500L)).thenReturn(existing);
    when(persistenceService.persistPending(
            org.mockito.ArgumentMatchers.eq(COMPANY_ID),
            org.mockito.ArgumentMatchers.eq(USER_ID),
            org.mockito.ArgumentMatchers.eq(500L),
            org.mockito.ArgumentMatchers.eq("waba-1"),
            org.mockito.ArgumentMatchers.isNull(),
            org.mockito.ArgumentMatchers.isNull(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()))
        .thenReturn(
            new PendingProvisioning(500L));
    when(persistenceService.readToken(500L))
        .thenReturn(new StoredMetaToken("new-token", "waba-1", "phone-1", "v26.0", null, null));
    when(graphClient.isAppSubscribed("waba-1", "new-token")).thenReturn(true);

    var response = service.complete(COMPANY_ID, USER_ID, request);

    assertThat(response.connectionId()).isEqualTo("500");
    assertThat(response.wabaId()).isEqualTo("waba-1");
    assertThat(response.phone().phoneNumberId()).isEqualTo("phone-1");
    verify(persistenceService).markHealthy(500L);
  }

  @Test
  void registrationRetryReusesStableStoredPin() {
    MetaWhatsAppConnectionControl pending =
        control(COMPANY_ID, 600L, "phone-1", RegistrationState.PENDING, SubscriptionState.PENDING);
    MetaWhatsAppConnectionControl registered =
        control(
            COMPANY_ID,
            600L,
            "phone-1",
            RegistrationState.REGISTERED,
            SubscriptionState.PENDING);
    when(persistenceService.requireControl(COMPANY_ID, 600L))
        .thenReturn(pending, pending, pending, pending, registered);
    when(persistenceService.readToken(600L))
        .thenReturn(new StoredMetaToken("token", "waba-1", "phone-1", "v26.0", null, null));
    when(persistenceService.readPin(600L)).thenReturn("123456");
    org.mockito.Mockito.doThrow(
            new MetaGraphException(
                "register_phone", 503, null, null, null, null, SafeCategory.TRANSIENT, true))
        .doNothing()
        .when(graphClient)
        .registerPhoneNumber("phone-1", "token", "123456");
    when(graphClient.inspectToken("token")).thenReturn(validInspection("waba-1"));
    when(graphClient.getWaba("waba-1", "token"))
        .thenReturn(new WabaDetails("waba-1", "Acme", "INR", "5"));
    when(graphClient.getPhoneNumbers("waba-1", "token"))
        .thenReturn(List.of(new PhoneNumber("phone-1", "+919000000000", "Acme", "GREEN")));
    when(graphClient.isAppSubscribed("waba-1", "token")).thenReturn(true);

    service.reconcileDue(COMPANY_ID, 600L);
    service.reconcileDue(COMPANY_ID, 600L);

    verify(graphClient, times(2)).registerPhoneNumber("phone-1", "token", "123456");
    verify(persistenceService).markRegistrationSucceeded(600L);
  }

  @Test
  void providerRegistrationSuccessFollowedByLocalWriteFailureConvergesOnRetry() {
    MetaWhatsAppConnectionControl pending =
        control(COMPANY_ID, 600L, "phone-1", RegistrationState.PENDING, SubscriptionState.PENDING);
    MetaWhatsAppConnectionControl registered =
        control(
            COMPANY_ID,
            600L,
            "phone-1",
            RegistrationState.REGISTERED,
            SubscriptionState.PENDING);
    when(persistenceService.requireControl(COMPANY_ID, 600L))
        .thenReturn(pending, pending, pending, pending, registered);
    when(persistenceService.readToken(600L))
        .thenReturn(new StoredMetaToken("token", "waba-1", "phone-1", "v26.0", null, null));
    when(persistenceService.readPin(600L)).thenReturn("123456");
    org.mockito.Mockito.doThrow(new IllegalStateException("local write failed"))
        .doNothing()
        .when(persistenceService)
        .markRegistrationSucceeded(600L);
    when(graphClient.inspectToken("token")).thenReturn(validInspection("waba-1"));
    when(graphClient.getWaba("waba-1", "token"))
        .thenReturn(new WabaDetails("waba-1", "Acme", "INR", "5"));
    when(graphClient.getPhoneNumbers("waba-1", "token"))
        .thenReturn(List.of(new PhoneNumber("phone-1", "+919000000000", "Acme", "GREEN")));
    when(graphClient.isAppSubscribed("waba-1", "token")).thenReturn(true);

    assertThatThrownBy(() -> service.reconcileDue(COMPANY_ID, 600L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("local write failed");

    service.reconcileDue(COMPANY_ID, 600L);

    verify(graphClient, times(2)).registerPhoneNumber("phone-1", "token", "123456");
    verify(persistenceService, times(2)).markRegistrationSucceeded(600L);
    verify(persistenceService).markHealthy(600L);
  }

  @Test
  void subscriptionRetryDoesNotRepeatSuccessfulRegistration() {
    MetaWhatsAppConnectionControl pending =
        control(COMPANY_ID, 600L, "phone-1", RegistrationState.PENDING, SubscriptionState.PENDING);
    MetaWhatsAppConnectionControl registered =
        control(
            COMPANY_ID,
            600L,
            "phone-1",
            RegistrationState.REGISTERED,
            SubscriptionState.PENDING);
    when(persistenceService.requireControl(COMPANY_ID, 600L))
        .thenReturn(pending, pending, registered, registered, registered, registered);
    when(persistenceService.readToken(600L))
        .thenReturn(new StoredMetaToken("token", "waba-1", "phone-1", "v26.0", null, null));
    when(persistenceService.readPin(600L)).thenReturn("123456");
    org.mockito.Mockito.doThrow(
            new MetaGraphException(
                "subscribe_app", 503, null, null, null, null, SafeCategory.TRANSIENT, true))
        .doNothing()
        .when(graphClient)
        .subscribeApp("waba-1", "token");
    when(graphClient.inspectToken("token")).thenReturn(validInspection("waba-1"));
    when(graphClient.getWaba("waba-1", "token"))
        .thenReturn(new WabaDetails("waba-1", "Acme", "INR", "5"));
    when(graphClient.getPhoneNumbers("waba-1", "token"))
        .thenReturn(List.of(new PhoneNumber("phone-1", "+919000000000", "Acme", "GREEN")));
    when(graphClient.isAppSubscribed("waba-1", "token")).thenReturn(true);

    service.reconcileDue(COMPANY_ID, 600L);
    service.reconcileDue(COMPANY_ID, 600L);

    verify(graphClient, times(1)).registerPhoneNumber("phone-1", "token", "123456");
    verify(graphClient, times(2)).subscribeApp("waba-1", "token");
    verify(persistenceService).markRegistrationSucceeded(600L);
    verify(persistenceService).markHealthy(600L);
  }

  @Test
  void transientProviderFailureIsRecordedForDegradedReconciliation() {
    MetaWhatsAppConnectionControl active =
        control(
            COMPANY_ID,
            600L,
            "phone-1",
            RegistrationState.REGISTERED,
            SubscriptionState.SUBSCRIBED);
    when(persistenceService.requireControl(COMPANY_ID, 600L)).thenReturn(active);
    when(persistenceService.readToken(600L))
        .thenReturn(new StoredMetaToken("token", "waba-1", "phone-1", "v26.0", null, null));
    MetaGraphException failure =
        new MetaGraphException(
            "debug_token", 503, null, null, null, null, SafeCategory.TRANSIENT, true);
    when(graphClient.inspectToken("token")).thenThrow(failure);

    service.reconcileDue(COMPANY_ID, 600L);

    verify(persistenceService).markFailure(600L, failure);
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

  private MetaWhatsAppConnectionControl control(
      Long companyId,
      Long connectionId,
      String phoneNumberId,
      RegistrationState registrationState,
      SubscriptionState subscriptionState) {
    MetaWhatsAppConnectionControl control = activeControl(companyId, connectionId, phoneNumberId);
    control.setProductState(ProductState.VALIDATING);
    control.setRegistrationState(registrationState);
    control.setSubscriptionState(subscriptionState);
    return control;
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
