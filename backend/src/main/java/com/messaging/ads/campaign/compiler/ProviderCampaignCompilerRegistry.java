package com.messaging.ads.campaign.compiler;

import com.messaging.ads.campaign.enums.CampaignChannelType;
import com.messaging.common.exception.BadRequestException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ProviderCampaignCompilerRegistry {

  private final Map<CampaignChannelType, ProviderCampaignCompiler> compilers;

  public ProviderCampaignCompilerRegistry(List<ProviderCampaignCompiler> compilerList) {
    Map<CampaignChannelType, ProviderCampaignCompiler> registry =
        new EnumMap<>(CampaignChannelType.class);
    for (ProviderCampaignCompiler compiler : compilerList) {
      for (CampaignChannelType channelType : compiler.supportedChannels()) {
        ProviderCampaignCompiler existing = registry.put(channelType, compiler);
        if (existing != null) {
          throw new IllegalStateException(
              "Duplicate campaign compiler registered for " + channelType);
        }
      }
    }
    this.compilers = Map.copyOf(registry);
  }

  public ProviderCampaignCompiler require(CampaignChannelType channelType) {
    ProviderCampaignCompiler compiler = compilers.get(channelType);
    if (compiler == null) {
      throw new BadRequestException("No provider compiler is registered for " + channelType);
    }
    return compiler;
  }
}
