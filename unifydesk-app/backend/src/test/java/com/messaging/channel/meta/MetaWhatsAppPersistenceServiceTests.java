package com.messaging.channel.meta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.channel.credential.ProviderCredentialService;
import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.enums.ChannelConnectionStatus;
import com.messaging.channel.meta.control.MetaGraphException;
import com.messaging.channel.meta.control.MetaGraphException.SafeCategory;
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
import java.util.Optional;
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
            "debug_token", 401, "OAuthException", 190, 463, "trace", SafeCategory.AUTH, false));

    assertThat(control.getProductState()).isEqualTo(ProductState.REAUTH_REQUIRED);
    assertThat(control.getAuthState()).isEqualTo(AuthState.REVOKED);
    assertThat(connection.getStatus()).isEqualTo(ChannelConnectionStatus.ERROR);
    verify(controlRepository).save(control);
    verify(connectionRepository).save(connection);
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
