package com.messaging.channel.meta;

import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.enums.ChannelConnectionStatus;
import com.messaging.channel.enums.ProviderType;
import com.messaging.channel.meta.control.MetaWhatsAppConnectionControl;
import com.messaging.channel.meta.control.MetaWhatsAppConnectionControl.ProductState;
import com.messaging.channel.meta.control.MetaWhatsAppConnectionControlRepository;
import com.messaging.channel.provider.DisconnectableChannelProvider;
import com.messaging.channel.provider.ProviderAccountHealth;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MetaWhatsAppProvider implements DisconnectableChannelProvider {

  private final MetaWhatsAppConnectionControlRepository controlRepository;
  private final MetaWhatsAppPersistenceService persistenceService;

  @Override
  public ProviderType providerType() {
    return ProviderType.META_WHATSAPP;
  }

  @Override
  public ProviderAccountHealth health(ChannelConnection connection) {
    MetaWhatsAppConnectionControl control =
        controlRepository.findById(connection.getId()).orElse(null);
    if (control == null) {
      boolean healthy = connection.getStatus() == ChannelConnectionStatus.ACTIVE;
      return new ProviderAccountHealth(
          healthy,
          connection.getStatus().name(),
          healthy
              ? "Meta WhatsApp connection is active"
              : "Meta WhatsApp connection requires configuration",
          Instant.now());
    }

    boolean healthy = control.getProductState() == ProductState.ACTIVE;
    String message =
        switch (control.getProductState()) {
          case ACTIVE -> "Meta WhatsApp provider health is validated";
          case DEGRADED -> "Meta WhatsApp is temporarily degraded";
          case ACTION_REQUIRED -> "Meta action is required";
          case REAUTH_REQUIRED -> "Meta WhatsApp must be reauthorized";
          case CONNECTING, VALIDATING -> "Meta WhatsApp setup is still being validated";
          case DISCONNECTED -> "Meta WhatsApp is disconnected";
          case ERROR -> "Meta WhatsApp connection has an error";
        };
    Instant checkedAt =
        control.getLastValidatedAt() == null ? Instant.now() : control.getLastValidatedAt();
    return new ProviderAccountHealth(healthy, control.getProductState().name(), message, checkedAt);
  }

  @Override
  public void disconnect(ChannelConnection connection) {
    // Do not delete, deregister, or mutate customer-owned Meta business assets here. The generic
    // channel lifecycle removes the encrypted credentials and disables local sender identities.
    // App subscription is intentionally retained because it may be shared by other connections.
    persistenceService.markDisconnected(connection.getId());
  }
}
