package com.messaging.ads.campaign.compiler;

import com.messaging.ads.capability.AdFeatureCapability;
import com.messaging.ads.capability.AdFeatureCapabilityResponse;
import com.messaging.ads.campaign.entity.AudienceDefinition;
import com.messaging.ads.campaign.entity.BudgetPlan;
import com.messaging.ads.campaign.entity.CampaignChannel;
import com.messaging.ads.campaign.entity.ConversionGoalMapping;
import com.messaging.ads.campaign.entity.CreativePack;
import com.messaging.ads.campaign.entity.CreativePackAsset;
import com.messaging.ads.campaign.entity.CreativeVariant;
import com.messaging.ads.campaign.entity.ReachlyoCampaign;
import com.messaging.ads.entity.AdAccount;
import java.util.List;
import java.util.Map;

public record ProviderCampaignCompileContext(
    ReachlyoCampaign campaign,
    CampaignChannel channel,
    List<CampaignChannel> allChannels,
    AdAccount account,
    BudgetPlan budgetPlan,
    CreativePack creativePack,
    List<CreativePackAsset> creativeAssets,
    List<CreativeVariant> creativeVariants,
    AudienceDefinition audienceDefinition,
    ConversionGoalMapping conversionGoalMapping,
    Map<AdFeatureCapability, AdFeatureCapabilityResponse> capabilities) {}
