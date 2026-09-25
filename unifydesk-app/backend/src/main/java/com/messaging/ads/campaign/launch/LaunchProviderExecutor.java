package com.messaging.ads.campaign.launch;

import com.messaging.ads.campaign.compiler.ProviderCampaignPlan;
import com.messaging.ads.campaign.compiler.ProviderPlanIssue;
import com.messaging.ads.campaign.enums.CampaignChannelType;
import java.util.List;
import java.util.Set;

public interface LaunchProviderExecutor {

  Set<CampaignChannelType> supportedChannels();

  List<ProviderPlanIssue> preflight(
      Long companyId, LaunchRun run, ProviderCampaignPlan plan);

  LaunchStepExecutionResult execute(
      Long companyId, LaunchRun run, ProviderCampaignPlan plan, LaunchStep step);
}
