package com.messaging.channel.provider;

import com.messaging.channel.enums.ProviderType;
import com.messaging.common.exception.BadRequestException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ChannelProviderRegistry {

  private final Map<ProviderType, ChannelProvider> providers;

  public ChannelProviderRegistry(List<ChannelProvider> providerList) {
    Map<ProviderType, ChannelProvider> registry = new EnumMap<>(ProviderType.class);
    for (ChannelProvider provider : providerList) {
      ChannelProvider existing = registry.put(provider.providerType(), provider);
      if (existing != null) {
        throw new IllegalStateException(
            "Duplicate channel provider registered for " + provider.providerType());
      }
    }
    this.providers = Map.copyOf(registry);
  }

  public ChannelProvider get(ProviderType providerType) {
    ChannelProvider provider = providers.get(providerType);
    if (provider == null) {
      throw new BadRequestException("Provider is not configured: " + providerType);
    }
    return provider;
  }

  public boolean isConfigured(ProviderType providerType) {
    return providers.containsKey(providerType);
  }
}
