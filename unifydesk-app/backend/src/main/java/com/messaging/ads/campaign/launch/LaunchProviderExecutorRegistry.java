package com.messaging.ads.campaign.launch;

import com.messaging.ads.campaign.enums.CampaignChannelType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class LaunchProviderExecutorRegistry {

  private final Map<CampaignChannelType, LaunchProviderExecutor> executors;

  public LaunchProviderExecutorRegistry(List<LaunchProviderExecutor> executorList) {
    Map<CampaignChannelType, LaunchProviderExecutor> registry =
        new EnumMap<>(CampaignChannelType.class);
    for (LaunchProviderExecutor executor : executorList) {
      for (CampaignChannelType channelType : executor.supportedChannels()) {
        LaunchProviderExecutor existing = registry.put(channelType, executor);
        if (existing != null) {
          throw new IllegalStateException(
              "Duplicate launch executor registered for " + channelType);
        }
      }
    }
    this.executors = Map.copyOf(registry);
  }

  public Optional<LaunchProviderExecutor> find(CampaignChannelType channelType) {
    return Optional.ofNullable(executors.get(channelType));
  }
}
