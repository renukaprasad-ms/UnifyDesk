package com.messaging.channel.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.messaging.channel.email.EmailReadStateMessage;
import com.messaging.channel.email.EmailReadStateProvider;
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
  void resolvesSupportedCapability() {
    EmailReadStateProvider provider = readStateProvider(ProviderType.GMAIL);
    ChannelProviderRegistry registry = new ChannelProviderRegistry(List.of(provider));

    assertThat(registry.requireCapability(ProviderType.GMAIL, EmailReadStateProvider.class))
        .isSameAs(provider);
    assertThat(registry.supportsCapability(ProviderType.GMAIL, EmailReadStateProvider.class))
        .isTrue();
  }

  @Test
  void rejectsUnsupportedCapability() {
    ChannelProvider provider = provider(ProviderType.META_WHATSAPP);
    ChannelProviderRegistry registry = new ChannelProviderRegistry(List.of(provider));

    assertThatThrownBy(
            () ->
                registry.requireCapability(
                    ProviderType.META_WHATSAPP, EmailReadStateProvider.class))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("does not support capability");
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
      public ProviderAccountHealth health(ChannelConnection connection) {
        return new ProviderAccountHealth(true, "healthy", null, Instant.now());
      }
    };
  }

  private EmailReadStateProvider readStateProvider(ProviderType type) {
    return new EmailReadStateProvider() {
      @Override
      public ProviderType providerType() {
        return type;
      }

      @Override
      public void updateReadState(
          ChannelConnection connection,
          List<EmailReadStateMessage> messages,
          boolean read,
          boolean background) {}

      @Override
      public ProviderAccountHealth health(ChannelConnection connection) {
        return new ProviderAccountHealth(true, "healthy", null, Instant.now());
      }
    };
  }
}
