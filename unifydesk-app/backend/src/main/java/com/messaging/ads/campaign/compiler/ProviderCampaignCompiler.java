package com.messaging.ads.campaign.compiler;

import com.messaging.ads.campaign.enums.CampaignChannelType;
import java.util.Set;

public interface ProviderCampaignCompiler {

  Set<CampaignChannelType> supportedChannels();

  ProviderCampaignPlan compile(ProviderCampaignCompileContext context);
}
