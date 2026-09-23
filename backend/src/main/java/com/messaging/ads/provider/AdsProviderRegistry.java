package com.messaging.ads.provider;

import com.messaging.ads.enums.AdCapability;
import com.messaging.ads.enums.AdProvider;
import com.messaging.common.exception.BadRequestException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class AdsProviderRegistry {

  private final Map<AdProvider, AdsProvider> providers;

  public AdsProviderRegistry(List<AdsProvider> providerList) {
    Map<AdProvider, AdsProvider> registry = new EnumMap<>(AdProvider.class);
    for (AdsProvider provider : providerList) {
      AdsProvider existing = registry.put(provider.provider(), provider);
      if (existing != null) {
        throw new IllegalStateException(
            "Duplicate advertising provider registered for " + provider.provider());
      }
    }
    this.providers = Map.copyOf(registry);
  }

  public AdsProvider get(AdProvider provider) {
    AdsProvider implementation = providers.get(provider);
    if (implementation == null || !implementation.configured()) {
      throw new BadRequestException("Advertising provider is not configured: " + provider);
    }
    return implementation;
  }

  public boolean isConfigured(AdProvider provider) {
    AdsProvider implementation = providers.get(provider);
    return implementation != null && implementation.configured();
  }

  public Set<AdCapability> capabilities(AdProvider provider) {
    AdsProvider implementation = providers.get(provider);
    return implementation == null || !implementation.configured()
        ? Set.of()
        : Set.copyOf(implementation.capabilities());
  }

  public <T extends AdsProvider> T requireCapability(
      AdProvider provider, Class<T> capabilityType) {
    AdsProvider implementation = get(provider);
    if (!capabilityType.isInstance(implementation)) {
      throw new BadRequestException(
          "Advertising provider "
              + provider
              + " does not support capability "
              + capabilityType.getSimpleName());
    }
    return capabilityType.cast(implementation);
  }
}
