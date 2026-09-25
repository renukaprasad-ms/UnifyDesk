package com.messaging.ads.campaign.compiler;

import static org.assertj.core.api.Assertions.assertThat;

import com.messaging.ads.capability.AdFeatureAvailability;
import com.messaging.ads.capability.AdFeatureCapability;
import com.messaging.ads.capability.AdFeatureCapabilityResponse;
import com.messaging.ads.campaign.entity.BudgetPlan;
import com.messaging.ads.campaign.entity.CampaignChannel;
import com.messaging.ads.campaign.entity.CreativePack;
import com.messaging.ads.campaign.entity.ReachlyoCampaign;
import com.messaging.ads.campaign.enums.BudgetAllocationMode;
import com.messaging.ads.campaign.enums.BudgetPeriod;
import com.messaging.ads.campaign.enums.CampaignBusinessGoal;
import com.messaging.ads.campaign.enums.CampaignChannelType;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.enums.AdProvider;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MetaProviderCampaignCompilerTests {

  @Test
  void blocksLeadPlanWhenConversionMappingIsMissing() {
    MetaProviderCampaignCompiler compiler = new MetaProviderCampaignCompiler();

    ReachlyoCampaign campaign = new ReachlyoCampaign();
    campaign.setId(10L);
    campaign.setName("Meta leads");
    campaign.setBusinessGoal(CampaignBusinessGoal.LEAD);
    campaign.setCurrency("INR");

    CampaignChannel channel = new CampaignChannel();
    channel.setId(20L);
    channel.setReachlyoCampaignId(10L);
    channel.setProvider(AdProvider.META_ADS);
    channel.setChannelType(CampaignChannelType.META_ADS);
    channel.setEnabled(true);
    channel.setBudgetAllocationMinor(3_000L);

    AdAccount account = new AdAccount();
    account.setId(30L);
    account.setProvider(AdProvider.META_ADS);
    account.setCurrency("INR");

    BudgetPlan budget = new BudgetPlan();
    budget.setTotalBudgetMinor(3_000L);
    budget.setBudgetPeriod(BudgetPeriod.DAILY);
    budget.setAllocationMode(BudgetAllocationMode.FIXED);

    CreativePack pack = new CreativePack();
    pack.setId(40L);
    pack.setPrimaryMessage("Talk to our team");
    pack.setDestinationUrl("https://example.com");

    Map<AdFeatureCapability, AdFeatureCapabilityResponse> capabilities =
        new EnumMap<>(AdFeatureCapability.class);
    for (AdFeatureCapability feature :
        List.of(
            AdFeatureCapability.CAMPAIGN_CREATE,
            AdFeatureCapability.META_CAMPAIGN_HIERARCHY,
            AdFeatureCapability.CREATIVE_CREATE,
            AdFeatureCapability.META_IMAGE_VIDEO_CAROUSEL,
            AdFeatureCapability.CONVERSION_CREATE)) {
      capabilities.put(feature, available(feature));
    }

    ProviderCampaignPlan plan =
        compiler.compile(
            new ProviderCampaignCompileContext(
                campaign,
                channel,
                List.of(channel),
                account,
                budget,
                pack,
                List.of(),
                List.of(),
                null,
                null,
                Map.copyOf(capabilities)));

    assertThat(plan.deployable()).isFalse();
    assertThat(plan.issues())
        .anyMatch(issue -> issue.code().equals("CONVERSION_MAPPING_MISSING"));
    assertThat(plan.operations())
        .anyMatch(
            operation ->
                operation.resource() == ProviderPlanResource.AD && operation.createPaused());
  }

  private AdFeatureCapabilityResponse available(AdFeatureCapability feature) {
    return new AdFeatureCapabilityResponse(
        feature,
        AdFeatureAvailability.AVAILABLE,
        true,
        true,
        true,
        true,
        null,
        "v26.0",
        Set.of());
  }
}
