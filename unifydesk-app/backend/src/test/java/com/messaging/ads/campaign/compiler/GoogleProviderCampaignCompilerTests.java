package com.messaging.ads.campaign.compiler;

import static org.assertj.core.api.Assertions.assertThat;

import com.messaging.ads.capability.AdFeatureAvailability;
import com.messaging.ads.capability.AdFeatureCapability;
import com.messaging.ads.capability.AdFeatureCapabilityResponse;
import com.messaging.ads.campaign.entity.AudienceDefinition;
import com.messaging.ads.campaign.entity.BudgetPlan;
import com.messaging.ads.campaign.entity.CampaignChannel;
import com.messaging.ads.campaign.entity.CreativePack;
import com.messaging.ads.campaign.entity.ReachlyoCampaign;
import com.messaging.ads.campaign.enums.AudienceMappingStatus;
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

class GoogleProviderCampaignCompilerTests {

  @Test
  void compilesDeployablePausedSearchPlanWithoutRemoteMutation() {
    GoogleProviderCampaignCompiler compiler = new GoogleProviderCampaignCompiler();

    ReachlyoCampaign campaign = new ReachlyoCampaign();
    campaign.setId(10L);
    campaign.setName("Search leads");
    campaign.setBusinessGoal(CampaignBusinessGoal.TRAFFIC);
    campaign.setCurrency("INR");

    CampaignChannel channel = new CampaignChannel();
    channel.setId(20L);
    channel.setReachlyoCampaignId(10L);
    channel.setProvider(AdProvider.GOOGLE_ADS);
    channel.setChannelType(CampaignChannelType.GOOGLE_SEARCH);
    channel.setEnabled(true);
    channel.setBudgetAllocationMinor(5_000L);

    AdAccount account = new AdAccount();
    account.setId(30L);
    account.setProvider(AdProvider.GOOGLE_ADS);
    account.setCurrency("INR");

    BudgetPlan budget = new BudgetPlan();
    budget.setTotalBudgetMinor(5_000L);
    budget.setBudgetPeriod(BudgetPeriod.DAILY);
    budget.setAllocationMode(BudgetAllocationMode.FIXED);

    CreativePack pack = new CreativePack();
    pack.setId(40L);
    pack.setHeadlinesJson("[\"Headline one\",\"Headline two\",\"Headline three\"]");
    pack.setDescriptionsJson("[\"Description one\",\"Description two\"]");
    pack.setDestinationUrl("https://example.com");

    AudienceDefinition audience = new AudienceDefinition();
    audience.setId(50L);
    audience.setSearchIntentJson("[\"crm software\"]");

    Map<AdFeatureCapability, AdFeatureCapabilityResponse> capabilities =
        new EnumMap<>(AdFeatureCapability.class);
    capabilities.put(
        AdFeatureCapability.CAMPAIGN_CREATE, available(AdFeatureCapability.CAMPAIGN_CREATE));
    capabilities.put(
        AdFeatureCapability.GOOGLE_SEARCH, available(AdFeatureCapability.GOOGLE_SEARCH));
    capabilities.put(
        AdFeatureCapability.CREATIVE_CREATE, available(AdFeatureCapability.CREATIVE_CREATE));

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
                audience,
                null,
                Map.copyOf(capabilities)));

    assertThat(plan.deployable()).isTrue();
    assertThat(plan.effectiveBudgetMinor()).isEqualTo(5_000L);
    assertThat(plan.audienceMappingStatus()).isEqualTo(AudienceMappingStatus.EXACT);
    assertThat(plan.operations())
        .anyMatch(
            operation ->
                operation.resource() == ProviderPlanResource.CAMPAIGN
                    && operation.createPaused());
    assertThat(plan.operations())
        .anyMatch(
            operation ->
                operation.resource() == ProviderPlanResource.NEGATIVE_KEYWORDS
                    && "configure-negative-keywords".equals(operation.key()));
  }

  @Test
  void keepsSearchAudienceApproximateWhenUnsupportedAudienceControlsRemain() {
    GoogleProviderCampaignCompiler compiler = new GoogleProviderCampaignCompiler();

    ReachlyoCampaign campaign = new ReachlyoCampaign();
    campaign.setId(11L);
    campaign.setName("Search leads");
    campaign.setBusinessGoal(CampaignBusinessGoal.TRAFFIC);
    campaign.setCurrency("INR");

    CampaignChannel channel = new CampaignChannel();
    channel.setId(21L);
    channel.setReachlyoCampaignId(11L);
    channel.setProvider(AdProvider.GOOGLE_ADS);
    channel.setChannelType(CampaignChannelType.GOOGLE_SEARCH);
    channel.setEnabled(true);
    channel.setBudgetAllocationMinor(5_000L);

    AdAccount account = new AdAccount();
    account.setId(31L);
    account.setProvider(AdProvider.GOOGLE_ADS);
    account.setCurrency("INR");

    BudgetPlan budget = new BudgetPlan();
    budget.setTotalBudgetMinor(5_000L);
    budget.setBudgetPeriod(BudgetPeriod.DAILY);
    budget.setAllocationMode(BudgetAllocationMode.FIXED);

    CreativePack pack = new CreativePack();
    pack.setId(41L);
    pack.setHeadlinesJson("[\"Headline one\",\"Headline two\",\"Headline three\"]");
    pack.setDescriptionsJson("[\"Description one\",\"Description two\"]");
    pack.setDestinationUrl("https://example.com");

    AudienceDefinition audience = new AudienceDefinition();
    audience.setId(51L);
    audience.setLocationsJson("[{\"countryCode\":\"IN\"}]");
    audience.setLanguagesJson("[\"en\"]");
    audience.setInterestsJson("[\"software\"]");
    audience.setSearchIntentJson("[\"crm software\"]");

    Map<AdFeatureCapability, AdFeatureCapabilityResponse> capabilities =
        new EnumMap<>(AdFeatureCapability.class);
    capabilities.put(
        AdFeatureCapability.CAMPAIGN_CREATE, available(AdFeatureCapability.CAMPAIGN_CREATE));
    capabilities.put(
        AdFeatureCapability.GOOGLE_SEARCH, available(AdFeatureCapability.GOOGLE_SEARCH));
    capabilities.put(
        AdFeatureCapability.CREATIVE_CREATE, available(AdFeatureCapability.CREATIVE_CREATE));

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
                audience,
                null,
                Map.copyOf(capabilities)));

    assertThat(plan.deployable()).isTrue();
    assertThat(plan.audienceMappingStatus()).isEqualTo(AudienceMappingStatus.APPROXIMATE);
    assertThat(plan.issues())
        .anyMatch(issue -> "AUDIENCE_MAPPING_APPROXIMATE".equals(issue.code()));
  }

  @Test
  void requiresAiMaxCapabilityAndWarnsWhenKeywordMatchTypesWillBeOverridden() {
    GoogleProviderCampaignCompiler compiler = new GoogleProviderCampaignCompiler();

    ReachlyoCampaign campaign = new ReachlyoCampaign();
    campaign.setId(12L);
    campaign.setName("AI Max search");
    campaign.setBusinessGoal(CampaignBusinessGoal.TRAFFIC);
    campaign.setCurrency("INR");
    campaign.setLandingPageUrl("https://example.com");

    CampaignChannel channel = new CampaignChannel();
    channel.setId(22L);
    channel.setReachlyoCampaignId(12L);
    channel.setProvider(AdProvider.GOOGLE_ADS);
    channel.setChannelType(CampaignChannelType.GOOGLE_SEARCH);
    channel.setEnabled(true);
    channel.setBudgetAllocationMinor(5_000L);
    channel.setProviderOverrideJson(
        "{\"aiMaxEnabled\":true,\"aiMaxTextCustomizationEnabled\":true,"
            + "\"aiMaxFinalUrlExpansionEnabled\":true,"
            + "\"aiMaxDisableSearchTermMatching\":false,"
            + "\"keywordMatchType\":\"EXACT\"}");

    AdAccount account = new AdAccount();
    account.setId(32L);
    account.setProvider(AdProvider.GOOGLE_ADS);
    account.setCurrency("INR");

    BudgetPlan budget = new BudgetPlan();
    budget.setTotalBudgetMinor(5_000L);
    budget.setBudgetPeriod(BudgetPeriod.DAILY);
    budget.setAllocationMode(BudgetAllocationMode.FIXED);

    CreativePack pack = new CreativePack();
    pack.setId(42L);
    pack.setHeadlinesJson("[\"Headline one\",\"Headline two\",\"Headline three\"]");
    pack.setDescriptionsJson("[\"Description one\",\"Description two\"]");
    pack.setDestinationUrl("https://example.com");

    AudienceDefinition audience = new AudienceDefinition();
    audience.setId(52L);
    audience.setSearchIntentJson("[\"crm software\"]");

    Map<AdFeatureCapability, AdFeatureCapabilityResponse> capabilities =
        new EnumMap<>(AdFeatureCapability.class);
    capabilities.put(
        AdFeatureCapability.CAMPAIGN_CREATE, available(AdFeatureCapability.CAMPAIGN_CREATE));
    capabilities.put(
        AdFeatureCapability.GOOGLE_SEARCH, available(AdFeatureCapability.GOOGLE_SEARCH));
    capabilities.put(
        AdFeatureCapability.CREATIVE_CREATE, available(AdFeatureCapability.CREATIVE_CREATE));
    capabilities.put(
        AdFeatureCapability.GOOGLE_AI_MAX, available(AdFeatureCapability.GOOGLE_AI_MAX));

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
                audience,
                null,
                Map.copyOf(capabilities)));

    assertThat(plan.deployable()).isTrue();
    assertThat(plan.requiredCapabilities()).contains(AdFeatureCapability.GOOGLE_AI_MAX);
    assertThat(plan.issues())
        .anyMatch(issue -> "AI_MAX_KEYWORD_MATCH_TYPE_OVERRIDDEN".equals(issue.code()));
    assertThat(plan.operations())
        .anyMatch(operation -> "configure-ai-max-ad-group".equals(operation.key()));
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
        "v25",
        Set.of());
  }
}
