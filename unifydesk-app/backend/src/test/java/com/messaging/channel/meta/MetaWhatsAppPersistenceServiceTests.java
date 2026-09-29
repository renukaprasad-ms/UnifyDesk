package com.messaging.channel.meta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.channel.credential.ProviderCredentialService;
import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.entity.SenderIdentity;
import com.messaging.channel.enums.ChannelConnectionStatus;
import com.messaging.channel.enums.CredentialType;
import com.messaging.channel.meta.control.MetaGraphException;
import com.messaging.channel.meta.control.MetaGraphException.SafeCategory;
import com.messaging.channel.meta.MetaGraphClient.PhoneNumber;
import com.messaging.channel.meta.MetaGraphClient.TokenResponse;
import com.messaging.channel.meta.MetaGraphClient.WabaDetails;
import com.messaging.channel.meta.control.MetaWhatsAppConnectionControl;
import com.messaging.channel.meta.control.MetaWhatsAppConnectionControl.AuthState;
import com.messaging.channel.meta.control.MetaWhatsAppConnectionControl.CoexistenceState;
import com.messaging.channel.meta.control.MetaWhatsAppConnectionControl.ProductState;
import com.messaging.channel.meta.control.MetaWhatsAppConnectionControl.RegistrationState;
import com.messaging.channel.meta.control.MetaWhatsAppConnectionControl.SubscriptionState;
import com.messaging.channel.meta.control.MetaWhatsAppConnectionControlRepository;
import com.messaging.channel.meta.control.MetaWhatsAppProvisioningOperationRepository;
import com.messaging.channel.repository.ChannelConnectionRepository;
import com.messaging.channel.repository.SenderIdentityRepository;
import com.messaging.common.exception.ConflictException;
import com.messaging.common.exception.NotFoundException;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MetaWhatsAppPersistenceServiceTests {

  @Mock private ChannelConnectionRepository connectionRepository;
  @Mock private SenderIdentityRepository senderIdentityRepository;
  @Mock private ProviderCredentialService credentialService;
  @Mock private MetaWhatsAppConnectionControlRepository controlRepository;
  @Mock private MetaWhatsAppProvisioningOperationRepository operationRepository;

  private MetaWhatsAppPersistenceService service;

  @BeforeEach
  void setUp() {
    service =
        new MetaWhatsAppPersistenceService(
            connectionRepository,
            senderIdentityRepository,
            credentialService,
            controlRepository,
            operationRepository,
            new MetaWhatsAppProperties(),
            new ObjectMapper());
  }

  @Test
  void revokedTokenMovesConnectionToReauthRequired() {
    MetaWhatsAppConnectionControl control = activeControl();
    ChannelConnection connection = connection(ChannelConnectionStatus.ACTIVE);
    when(controlRepository.findById(600L)).thenReturn(Optional.of(control));
    when(connectionRepository.findById(600L)).thenReturn(Optional.of(connection));
    when(operationRepository.findFirstByConnectionIdOrderByUpdatedAtDesc(600L))
        .thenReturn(Optional.empty());

    service.markFailure(
        600L,
        new MetaGraphException(
            "debug_token", 401, "OAuthException", 190, null, "trace", SafeCategory.AUTH, false));

    assertThat(control.getProductState()).isEqualTo(ProductState.REAUTH_REQUIRED);
    assertThat(control.getAuthState()).isEqualTo(AuthState.REVOKED);
    assertThat(connection.getStatus()).isEqualTo(ChannelConnectionStatus.ERROR);
    verify(controlRepository).save(control);
    verify(connectionRepository).save(connection);
  }

  @Test
  void expiredTokenMovesConnectionToReauthRequiredWithExpiredAuthState() {
    MetaWhatsAppConnectionControl control = activeControl();
    ChannelConnection connection = connection(ChannelConnectionStatus.ACTIVE);
    when(controlRepository.findById(600L)).thenReturn(Optional.of(control));
    when(connectionRepository.findById(600L)).thenReturn(Optional.of(connection));
    when(operationRepository.findFirstByConnectionIdOrderByUpdatedAtDesc(600L))
        .thenReturn(Optional.empty());

    service.markFailure(
        600L,
        new MetaGraphException(
            "debug_token", 401, "OAuthException", 190, 463, "trace", SafeCategory.AUTH, false));

    assertThat(control.getProductState()).isEqualTo(ProductState.REAUTH_REQUIRED);
    assertThat(control.getAuthState()).isEqualTo(AuthState.EXPIRED);
    assertThat(connection.getStatus()).isEqualTo(ChannelConnectionStatus.ERROR);
  }

  @Test
  void databasePhoneUniquenessClosesCrossCompanyClaimRace() {
    MetaWhatsAppProperties properties = new MetaWhatsAppProperties();
    properties.setRegisterPhoneNumber(true);
    service =
        new MetaWhatsAppPersistenceService(
            connectionRepository,
            senderIdentityRepository,
            credentialService,
            controlRepository,
            operationRepository,
            properties,
            new ObjectMapper());

    ChannelConnection savedConnection = new ChannelConnection();
    savedConnection.setId(600L);
    savedConnection.setCompanyId(101L);
    when(connectionRepository.findByCompanyIdAndProviderTypeAndExternalAccountId(
            101L, com.messaging.channel.enums.ProviderType.META_WHATSAPP, "waba-1"))
        .thenReturn(Optional.empty());
    when(connectionRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(ChannelConnection.class)))
        .thenReturn(savedConnection);
    when(credentialService.read(600L, CredentialType.PROVIDER_SECRET))
        .thenThrow(new NotFoundException("Provider credential not found"));
    when(senderIdentityRepository.findByConnectionIdAndIdentityTypeAndExternalId(
            600L, com.messaging.channel.enums.SenderIdentityType.PHONE_NUMBER, "phone-1"))
        .thenReturn(Optional.empty());
    when(senderIdentityRepository.findFirstByConnectionIdAndDefaultIdentityTrue(600L))
        .thenReturn(Optional.empty());
    SenderIdentity savedSender = new SenderIdentity();
    savedSender.setId(700L);
    savedSender.setConnection(savedConnection);
    when(senderIdentityRepository.saveAndFlush(
            org.mockito.ArgumentMatchers.any(SenderIdentity.class)))
        .thenReturn(savedSender);
    when(controlRepository.findById(600L)).thenReturn(Optional.empty());
    when(controlRepository.saveAndFlush(
            org.mockito.ArgumentMatchers.any(MetaWhatsAppConnectionControl.class)))
        .thenThrow(new DataIntegrityViolationException("unique phone"));

    assertThatThrownBy(
            () ->
                service.persistPending(
                    101L,
                    202L,
                    null,
                    "waba-1",
                    null,
                    null,
                    new WabaDetails("waba-1", "Acme", "INR", "5"),
                    new PhoneNumber("phone-1", "+919000000000", "Acme", "GREEN"),
                    new TokenResponse("token", "bearer", 3600L),
                    null,
                    com.messaging.channel.meta.control.MetaWhatsAppProvisioningOperation.OperationType.CONNECT))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("WHATSAPP_PHONE_OWNED_BY_OTHER_COMPANY");
  }

  @Test
  void transientProviderFailureMovesConnectionToDegradedButKeepsGenericActive() {
    MetaWhatsAppConnectionControl control = activeControl();
    ChannelConnection connection = connection(ChannelConnectionStatus.ACTIVE);
    when(controlRepository.findById(600L)).thenReturn(Optional.of(control));
    when(connectionRepository.findById(600L)).thenReturn(Optional.of(connection));
    when(operationRepository.findFirstByConnectionIdOrderByUpdatedAtDesc(600L))
        .thenReturn(Optional.empty());

    service.markFailure(
        600L,
        new MetaGraphException(
            "get_waba", 503, null, null, null, null, SafeCategory.TRANSIENT, true));

    assertThat(control.getProductState()).isEqualTo(ProductState.DEGRADED);
    assertThat(control.getAuthState()).isEqualTo(AuthState.VALID);
    assertThat(connection.getStatus()).isEqualTo(ChannelConnectionStatus.ACTIVE);
  }

  private MetaWhatsAppConnectionControl activeControl() {
    MetaWhatsAppConnectionControl control = new MetaWhatsAppConnectionControl();
    control.setConnectionId(600L);
    control.setCompanyId(101L);
    control.setLegacyWabaId("waba-1");
    control.setSelectedPhoneNumberId("phone-1");
    control.setProductState(ProductState.ACTIVE);
    control.setAuthState(AuthState.VALID);
    control.setRegistrationState(RegistrationState.REGISTERED);
    control.setSubscriptionState(SubscriptionState.SUBSCRIBED);
    control.setCoexistenceState(CoexistenceState.DISABLED);
    return control;
  }

  private ChannelConnection connection(ChannelConnectionStatus status) {
    ChannelConnection connection = new ChannelConnection();
    connection.setId(600L);
    connection.setCompanyId(101L);
    connection.setStatus(status);
    return connection;
  }
}
