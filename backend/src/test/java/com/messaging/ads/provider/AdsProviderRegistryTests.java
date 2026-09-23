package com.messaging.ads.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.messaging.ads.enums.AdCapability;
import com.messaging.ads.enums.AdProvider;
import com.messaging.common.exception.BadRequestException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AdsProviderRegistryTests {

  @Test
  void resolvesConfiguredProviderAndCapabilities() {
    AdsProvider provider =
        provider(AdProvider.META_ADS, true, Set.of(AdCapability.ACCOUNT_DISCOVERY));
    AdsProviderRegistry registry = new AdsProviderRegistry(List.of(provider));

    assertThat(registry.get(AdProvider.META_ADS)).isSameAs(provider);
    assertThat(registry.isConfigured(AdProvider.META_ADS)).isTrue();
    assertThat(registry.capabilities(AdProvider.META_ADS))
        .containsExactly(AdCapability.ACCOUNT_DISCOVERY);
  }

  @Test
  void hidesRuntimeUnconfiguredProvider() {
    AdsProvider provider =
        provider(AdProvider.META_ADS, false, Set.of(AdCapability.ACCOUNT_DISCOVERY));
    AdsProviderRegistry registry = new AdsProviderRegistry(List.of(provider));

    assertThat(registry.isConfigured(AdProvider.META_ADS)).isFalse();
    assertThat(registry.capabilities(AdProvider.META_ADS)).isEmpty();
    assertThatThrownBy(() -> registry.get(AdProvider.META_ADS))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("not configured");
  }

  @Test
  void rejectsDuplicateProviders() {
    assertThatThrownBy(
            () ->
                new AdsProviderRegistry(
                    List.of(
                        provider(AdProvider.META_ADS, true, Set.of()),
                        provider(AdProvider.META_ADS, true, Set.of()))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Duplicate advertising provider");
  }

  private AdsProvider provider(
      AdProvider provider, boolean configured, Set<AdCapability> capabilities) {
    return new AdsProvider() {
      @Override
      public AdProvider provider() {
        return provider;
      }

      @Override
      public boolean configured() {
        return configured;
      }

      @Override
      public Set<AdCapability> capabilities() {
        return capabilities;
      }
    };
  }
}
