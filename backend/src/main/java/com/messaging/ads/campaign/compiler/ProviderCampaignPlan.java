package com.messaging.ads.campaign.compiler;

import com.messaging.ads.capability.AdFeatureCapability;
import com.messaging.ads.campaign.enums.AudienceMappingStatus;
import com.messaging.ads.campaign.enums.BudgetPeriod;
import com.messaging.ads.campaign.enums.CampaignChannelType;
import com.messaging.ads.enums.AdProvider;
import java.util.List;
import java.util.Set;

public record ProviderCampaignPlan(
    int planVersion,
    String campaignId,
    String channelId,
    AdProvider provider,
    CampaignChannelType channelType,
    String adAccountId,
    boolean deployable,
    Long effectiveBudgetMinor,
    BudgetPeriod budgetPeriod,
    String creativePackId,
    String audienceDefinitionId,
    AudienceMappingStatus audienceMappingStatus,
    String audienceMappingNote,
    String conversionGoalMappingId,
    Set<AdFeatureCapability> requiredCapabilities,
    List<ProviderPlanIssue> issues,
    List<ProviderPlanOperation> operations) {}
