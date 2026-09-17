package com.messaging.channel.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.enums.ProviderType;
import com.messaging.common.exception.BadRequestException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChannelProviderRegistryTests {

  @Test
  void resolvesRegisteredProvider() {
    ChannelProvider provider = provider(ProviderType.META_WHATSAPP);
    ChannelProviderRegistry registry = new ChannelProviderRegistry(List.of(provider));

    assertThat(registry.get(ProviderType.META_WHATSAPP)).isSameAs(provider);
    assertThat(registry.isConfigured(ProviderType.META_WHATSAPP)).isTrue();
    assertThat(registry.isConfigured(ProviderType.GMAIL)).isFalse();
  }

  @Test
  void rejectsDuplicateProviders() {
    assertThatThrownBy(
            () ->
                new ChannelProviderRegistry(
                    List.of(provider(ProviderType.GMAIL), provider(ProviderType.GMAIL))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Duplicate channel provider");
  }

  @Test
  void rejectsProviderThatIsNotConfigured() {
    ChannelProviderRegistry registry = new ChannelProviderRegistry(List.of());

    assertThatThrownBy(() -> registry.get(ProviderType.MSG91))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("Provider is not configured");
  }

  private ChannelProvider provider(ProviderType type) {
    return new ChannelProvider() {
      @Override
      public ProviderType providerType() {
        return type;
      }

      @Override
      public ProviderSendResult send(ProviderSendRequest request) {
        return new ProviderSendResult(true, "message-id", "accepted", Instant.now(), null);
      }

      @Override
      public List<ProviderWebhookMessage> parseWebhook(ProviderWebhookRequest request) {
        return List.of();
      }

      @Override
      public ProviderAccountHealth health(ChannelConnection connection) {
        return new ProviderAccountHealth(true, "healthy", null, Instant.now());
      }
    };
  }
}
