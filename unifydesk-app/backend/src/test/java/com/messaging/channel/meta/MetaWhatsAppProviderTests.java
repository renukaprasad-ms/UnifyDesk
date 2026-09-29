package com.messaging.channel.meta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.enums.ChannelConnectionStatus;
import com.messaging.channel.meta.control.MetaWhatsAppConnectionControl;
import com.messaging.channel.meta.control.MetaWhatsAppConnectionControl.AuthState;
import com.messaging.channel.meta.control.MetaWhatsAppConnectionControl.CoexistenceState;
import com.messaging.channel.meta.control.MetaWhatsAppConnectionControl.ProductState;
import com.messaging.channel.meta.control.MetaWhatsAppConnectionControl.RegistrationState;
import com.messaging.channel.meta.control.MetaWhatsAppConnectionControl.SubscriptionState;
import com.messaging.channel.meta.control.MetaWhatsAppConnectionControlRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MetaWhatsAppProviderTests {

  @Mock private MetaWhatsAppConnectionControlRepository controlRepository;
  @Mock private MetaWhatsAppPersistenceService persistenceService;

  @Test
  void safeDisconnectOnlyMarksLocalMetaControlState() {
    MetaWhatsAppProvider provider =
        new MetaWhatsAppProvider(controlRepository, persistenceService);
    ChannelConnection connection = new ChannelConnection();
    connection.setId(600L);

    provider.disconnect(connection);

    verify(persistenceService).markDisconnected(600L);
    verifyNoMoreInteractions(persistenceService);
  }

  @Test
  void healthUsesCachedProviderValidationInsteadOfCallingMeta() {
    MetaWhatsAppProvider provider =
        new MetaWhatsAppProvider(controlRepository, persistenceService);
    ChannelConnection connection = new ChannelConnection();
    connection.setId(600L);
    connection.setStatus(ChannelConnectionStatus.ACTIVE);

    MetaWhatsAppConnectionControl control = new MetaWhatsAppConnectionControl();
    control.setConnectionId(600L);
    control.setCompanyId(101L);
    control.setSelectedPhoneNumberId("phone-1");
    control.setProductState(ProductState.ACTIVE);
    control.setAuthState(AuthState.VALID);
    control.setRegistrationState(RegistrationState.REGISTERED);
    control.setSubscriptionState(SubscriptionState.SUBSCRIBED);
    control.setCoexistenceState(CoexistenceState.DISABLED);
    control.setLastValidatedAt(Instant.parse("2026-09-29T05:00:00Z"));
    when(controlRepository.findById(600L)).thenReturn(Optional.of(control));

    var health = provider.health(connection);

    assertThat(health.healthy()).isTrue();
    assertThat(health.status()).isEqualTo("ACTIVE");
    assertThat(health.checkedAt()).isEqualTo(control.getLastValidatedAt());
  }
}
