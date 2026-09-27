package com.messaging.ads.campaign.compiler;

import static org.assertj.core.api.Assertions.assertThat;

import com.messaging.ads.capability.AdFeatureAvailability;
import com.messaging.ads.capability.AdFeatureCapability;
import com.messaging.ads.capability.AdFeatureCapabilityResponse;
import com.messaging.ads.campaign.entity.AudienceDefinition;
import com.messaging.ads.campaign.entity.BudgetPlan;
import com.messaging.ads.campaign.entity.CampaignChannel;
import com.messaging.ads.campaign.entity.CreativePack;
import com.messaging.ads.campaign.entity.CreativeVariant;
import com.messaging.ads.campaign.entity.ReachlyoCampaign;
import com.messaging.ads.campaign.enums.AudienceMappingStatus;
import com.messaging.ads.campaign.enums.BudgetAllocationMode;
import com.messaging.ads.campaign.enums.BudgetPeriod;
import com.messaging.ads.campaign.enums.CampaignBusinessGoal;
import com.messaging.ads.campaign.enums.CampaignChannelType;
import com.messaging.ads.campaign.enums.CreativeVariantType;
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

  @Test
  void mapsGeoAgeAndGenderAudienceExactly() {
    MetaProviderCampaignCompiler compiler = new MetaProviderCampaignCompiler();

    ReachlyoCampaign campaign = new ReachlyoCampaign();
    campaign.setId(11L);
    campaign.setName("Meta traffic");
    campaign.setBusinessGoal(CampaignBusinessGoal.TRAFFIC);
    campaign.setCurrency("INR");

    CampaignChannel channel = new CampaignChannel();
    channel.setId(21L);
    channel.setReachlyoCampaignId(11L);
    channel.setProvider(AdProvider.META_ADS);
    channel.setChannelType(CampaignChannelType.META_ADS);
    channel.setEnabled(true);
    channel.setBudgetAllocationMinor(4_000L);

    AdAccount account = new AdAccount();
    account.setId(31L);
    account.setProvider(AdProvider.META_ADS);
    account.setCurrency("INR");

    BudgetPlan budget = new BudgetPlan();
    budget.setTotalBudgetMinor(4_000L);
    budget.setBudgetPeriod(BudgetPeriod.DAILY);
    budget.setAllocationMode(BudgetAllocationMode.FIXED);

    CreativePack pack = new CreativePack();
    pack.setId(41L);
    pack.setPrimaryMessage("Visit our site");
    pack.setDestinationUrl("https://example.com");

    AudienceDefinition audience = new AudienceDefinition();
    audience.setId(51L);
    audience.setLocationsJson(
        "[{\"countryCode\":\"IN\",\"region\":\"Karnataka\",\"city\":\"Bengaluru\"}]");
    audience.setAgeMin(21);
    audience.setAgeMax(45);
    audience.setGendersJson("[\"MALE\",\"FEMALE\"]");

    Map<AdFeatureCapability, AdFeatureCapabilityResponse> capabilities =
        baseCapabilities();

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
    assertThat(plan.audienceMappingStatus()).isEqualTo(AudienceMappingStatus.EXACT);
    assertThat(plan.issues())
        .noneMatch(issue -> issue.code().equals("AUDIENCE_MAPPING_APPROXIMATE"));
    assertThat(plan.operations())
        .anyMatch(
            operation ->
                operation.resource() == ProviderPlanResource.CAMPAIGN_TARGETING
                    && AudienceMappingStatus.EXACT.equals(
                        operation.configuration().get("mappingStatus")));
  }

  @Test
  void keepsAudienceApproximateWhenInterestMappingRemains() {
    MetaProviderCampaignCompiler compiler = new MetaProviderCampaignCompiler();

    ReachlyoCampaign campaign = new ReachlyoCampaign();
    campaign.setId(12L);
    campaign.setName("Meta interest traffic");
    campaign.setBusinessGoal(CampaignBusinessGoal.TRAFFIC);
    campaign.setCurrency("INR");

    CampaignChannel channel = new CampaignChannel();
    channel.setId(22L);
    channel.setReachlyoCampaignId(12L);
    channel.setProvider(AdProvider.META_ADS);
    channel.setChannelType(CampaignChannelType.META_ADS);
    channel.setEnabled(true);
    channel.setBudgetAllocationMinor(4_000L);

    AdAccount account = new AdAccount();
    account.setId(32L);
    account.setProvider(AdProvider.META_ADS);
    account.setCurrency("INR");

    BudgetPlan budget = new BudgetPlan();
    budget.setTotalBudgetMinor(4_000L);
    budget.setBudgetPeriod(BudgetPeriod.DAILY);
    budget.setAllocationMode(BudgetAllocationMode.FIXED);

    CreativePack pack = new CreativePack();
    pack.setId(42L);
    pack.setPrimaryMessage("Visit our site");
    pack.setDestinationUrl("https://example.com");

    AudienceDefinition audience = new AudienceDefinition();
    audience.setId(52L);
    audience.setLocationsJson("[{\"countryCode\":\"IN\"}]");
    audience.setInterestsJson("[\"business software\"]");

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
                Map.copyOf(baseCapabilities())));

    assertThat(plan.audienceMappingStatus())
        .isEqualTo(AudienceMappingStatus.APPROXIMATE);
    assertThat(plan.issues())
        .anyMatch(issue -> issue.code().equals("AUDIENCE_MAPPING_APPROXIMATE"));
  }


  @Test
  void requiresAdvantagePlacementsCapabilityForExplicitAutomaticMode() {
    MetaProviderCampaignCompiler compiler = new MetaProviderCampaignCompiler();

    ReachlyoCampaign campaign = new ReachlyoCampaign();
    campaign.setId(12L);
    campaign.setName("Meta Advantage placements");
    campaign.setBusinessGoal(CampaignBusinessGoal.TRAFFIC);
    campaign.setCurrency("INR");

    CampaignChannel channel = new CampaignChannel();
    channel.setId(22L);
    channel.setReachlyoCampaignId(12L);
    channel.setProvider(AdProvider.META_ADS);
    channel.setChannelType(CampaignChannelType.META_ADS);
    channel.setEnabled(true);
    channel.setBudgetAllocationMinor(4_000L);
    channel.setProviderOverrideJson("{\"advantagePlacements\":true}");

    AdAccount account = new AdAccount();
    account.setId(32L);
    account.setProvider(AdProvider.META_ADS);
    account.setCurrency("INR");

    BudgetPlan budget = new BudgetPlan();
    budget.setTotalBudgetMinor(4_000L);
    budget.setBudgetPeriod(BudgetPeriod.DAILY);
    budget.setAllocationMode(BudgetAllocationMode.FIXED);

    CreativePack pack = new CreativePack();
    pack.setId(42L);
    pack.setPrimaryMessage("Visit our site");
    pack.setDestinationUrl("https://example.com");

    Map<AdFeatureCapability, AdFeatureCapabilityResponse> capabilities =
        baseCapabilities();
    capabilities.put(
        AdFeatureCapability.META_ADVANTAGE_PLACEMENTS,
        available(AdFeatureCapability.META_ADVANTAGE_PLACEMENTS));

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

    assertThat(plan.deployable()).isTrue();
    assertThat(plan.requiredCapabilities())
        .contains(AdFeatureCapability.META_ADVANTAGE_PLACEMENTS);
  }

  @Test
  void rejectsAdvantagePlacementsWithManualPlacementRestrictions() {
    MetaProviderCampaignCompiler compiler = new MetaProviderCampaignCompiler();

    ReachlyoCampaign campaign = new ReachlyoCampaign();
    campaign.setId(13L);
    campaign.setName("Meta placement conflict");
    campaign.setBusinessGoal(CampaignBusinessGoal.TRAFFIC);
    campaign.setCurrency("INR");

    CampaignChannel channel = new CampaignChannel();
    channel.setId(23L);
    channel.setReachlyoCampaignId(13L);
    channel.setProvider(AdProvider.META_ADS);
    channel.setChannelType(CampaignChannelType.META_ADS);
    channel.setEnabled(true);
    channel.setBudgetAllocationMinor(4_000L);
    channel.setProviderOverrideJson(
        "{\"advantagePlacements\":true,\"publisherPlatforms\":[\"instagram\"]}");

    AdAccount account = new AdAccount();
    account.setId(33L);
    account.setProvider(AdProvider.META_ADS);
    account.setCurrency("INR");

    BudgetPlan budget = new BudgetPlan();
    budget.setTotalBudgetMinor(4_000L);
    budget.setBudgetPeriod(BudgetPeriod.DAILY);
    budget.setAllocationMode(BudgetAllocationMode.FIXED);

    CreativePack pack = new CreativePack();
    pack.setId(43L);
    pack.setPrimaryMessage("Visit");
    pack.setDestinationUrl("https://example.com");

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
                Map.copyOf(baseCapabilities())));

    assertThat(plan.deployable()).isFalse();
    assertThat(plan.issues())
        .anyMatch(issue -> issue.code().equals("META_ADVANTAGE_PLACEMENTS_CONFLICT"));
  }

  @Test
  void requiresAdvantageAudienceCapabilityAndSurfacesExpansionWarning() {
    MetaProviderCampaignCompiler compiler = new MetaProviderCampaignCompiler();

    ReachlyoCampaign campaign = new ReachlyoCampaign();
    campaign.setId(14L);
    campaign.setName("Meta Advantage audience");
    campaign.setBusinessGoal(CampaignBusinessGoal.TRAFFIC);
    campaign.setCurrency("INR");

    CampaignChannel channel = new CampaignChannel();
    channel.setId(24L);
    channel.setReachlyoCampaignId(14L);
    channel.setProvider(AdProvider.META_ADS);
    channel.setChannelType(CampaignChannelType.META_ADS);
    channel.setEnabled(true);
    channel.setBudgetAllocationMinor(4_000L);
    channel.setProviderOverrideJson(
        "{\"advantageAudience\":true,\"advantageAgeSuggestion\":true}");

    AdAccount account = new AdAccount();
    account.setId(34L);
    account.setProvider(AdProvider.META_ADS);
    account.setCurrency("INR");

    BudgetPlan budget = new BudgetPlan();
    budget.setTotalBudgetMinor(4_000L);
    budget.setBudgetPeriod(BudgetPeriod.DAILY);
    budget.setAllocationMode(BudgetAllocationMode.FIXED);

    CreativePack pack = new CreativePack();
    pack.setId(44L);
    pack.setPrimaryMessage("Visit our site");
    pack.setDestinationUrl("https://example.com");

    AudienceDefinition audience = new AudienceDefinition();
    audience.setId(54L);
    audience.setLocationsJson("[{\"countryCode\":\"IN\"}]");
    audience.setAgeMin(21);
    audience.setAgeMax(45);

    Map<AdFeatureCapability, AdFeatureCapabilityResponse> capabilities =
        baseCapabilities();
    capabilities.put(
        AdFeatureCapability.META_ADVANTAGE_AUDIENCE,
        available(AdFeatureCapability.META_ADVANTAGE_AUDIENCE));

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
    assertThat(plan.requiredCapabilities())
        .contains(AdFeatureCapability.META_ADVANTAGE_AUDIENCE);
    assertThat(plan.issues())
        .anyMatch(issue -> issue.code().equals("META_ADVANTAGE_AUDIENCE_EXPANSION"));
  }

  @Test
  void blocksAdvantageSuggestionWhenAudienceExpansionIsDisabled() {
    MetaProviderCampaignCompiler compiler = new MetaProviderCampaignCompiler();

    ReachlyoCampaign campaign = new ReachlyoCampaign();
    campaign.setId(15L);
    campaign.setName("Meta invalid Advantage audience");
    campaign.setBusinessGoal(CampaignBusinessGoal.TRAFFIC);
    campaign.setCurrency("INR");

    CampaignChannel channel = new CampaignChannel();
    channel.setId(25L);
    channel.setReachlyoCampaignId(15L);
    channel.setProvider(AdProvider.META_ADS);
    channel.setChannelType(CampaignChannelType.META_ADS);
    channel.setEnabled(true);
    channel.setBudgetAllocationMinor(4_000L);
    channel.setProviderOverrideJson(
        "{\"advantageAudience\":false,\"advantageGenderSuggestion\":true}");

    AdAccount account = new AdAccount();
    account.setId(35L);
    account.setProvider(AdProvider.META_ADS);
    account.setCurrency("INR");

    BudgetPlan budget = new BudgetPlan();
    budget.setTotalBudgetMinor(4_000L);
    budget.setBudgetPeriod(BudgetPeriod.DAILY);
    budget.setAllocationMode(BudgetAllocationMode.FIXED);

    CreativePack pack = new CreativePack();
    pack.setId(45L);
    pack.setPrimaryMessage("Visit");
    pack.setDestinationUrl("https://example.com");

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
                Map.copyOf(baseCapabilities())));

    assertThat(plan.deployable()).isFalse();
    assertThat(plan.issues())
        .anyMatch(issue -> issue.code().equals("META_ADVANTAGE_AUDIENCE_INVALID"));
  }

  @Test
  void warnsWhenStoryAndReelPlacementsLackMatchingVariants() {
    MetaProviderCampaignCompiler compiler = new MetaProviderCampaignCompiler();

    ReachlyoCampaign campaign = new ReachlyoCampaign();
    campaign.setId(13L);
    campaign.setName("Meta placement campaign");
    campaign.setBusinessGoal(CampaignBusinessGoal.TRAFFIC);
    campaign.setCurrency("INR");

    CampaignChannel channel = new CampaignChannel();
    channel.setId(23L);
    channel.setReachlyoCampaignId(13L);
    channel.setProvider(AdProvider.META_ADS);
    channel.setChannelType(CampaignChannelType.META_ADS);
    channel.setEnabled(true);
    channel.setBudgetAllocationMinor(4_000L);
    channel.setProviderOverrideJson(
        "{\"publisherPlatforms\":[\"instagram\"],\"instagramPositions\":[\"story\",\"reels\"]}");

    AdAccount account = new AdAccount();
    account.setId(33L);
    account.setProvider(AdProvider.META_ADS);
    account.setCurrency("INR");

    BudgetPlan budget = new BudgetPlan();
    budget.setTotalBudgetMinor(4_000L);
    budget.setBudgetPeriod(BudgetPeriod.DAILY);
    budget.setAllocationMode(BudgetAllocationMode.FIXED);

    CreativePack pack = new CreativePack();
    pack.setId(43L);
    pack.setPrimaryMessage("Visit our site");
    pack.setDestinationUrl("https://example.com");

    CreativeVariant feed = new CreativeVariant();
    feed.setProvider(AdProvider.META_ADS);
    feed.setVariantType(CreativeVariantType.META_FEED);

    Map<AdFeatureCapability, AdFeatureCapabilityResponse> capabilities = baseCapabilities();
    capabilities.put(
        AdFeatureCapability.META_PLACEMENT_TREE,
        available(AdFeatureCapability.META_PLACEMENT_TREE));

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
                List.of(feed),
                null,
                null,
                Map.copyOf(capabilities)));

    assertThat(plan.deployable()).isTrue();
    assertThat(plan.issues())
        .filteredOn(issue -> issue.code().equals("META_PLACEMENT_CREATIVE_VARIANT_MISSING"))
        .hasSize(2);
  }

  private Map<AdFeatureCapability, AdFeatureCapabilityResponse> baseCapabilities() {
    Map<AdFeatureCapability, AdFeatureCapabilityResponse> capabilities =
        new EnumMap<>(AdFeatureCapability.class);
    for (AdFeatureCapability feature :
        List.of(
            AdFeatureCapability.CAMPAIGN_CREATE,
            AdFeatureCapability.META_CAMPAIGN_HIERARCHY,
            AdFeatureCapability.CREATIVE_CREATE,
            AdFeatureCapability.META_IMAGE_VIDEO_CAROUSEL)) {
      capabilities.put(feature, available(feature));
    }
    return capabilities;
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
