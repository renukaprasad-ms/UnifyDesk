package com.messaging.ads.campaign.launch;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.campaign.compiler.PlanIssueSeverity;
import com.messaging.ads.campaign.compiler.ProviderCampaignPlan;
import com.messaging.ads.campaign.compiler.ProviderPlanIssue;
import com.messaging.ads.campaign.dto.AudienceLocationInput;
import com.messaging.ads.campaign.entity.AudienceDefinition;
import com.messaging.ads.campaign.entity.CampaignChannel;
import com.messaging.ads.campaign.entity.ConversionGoalMapping;
import com.messaging.ads.campaign.entity.CreativePack;
import com.messaging.ads.campaign.entity.CreativePackAsset;
import com.messaging.ads.campaign.entity.ReachlyoCampaign;
import com.messaging.ads.campaign.enums.BudgetPeriod;
import com.messaging.ads.campaign.enums.CampaignBusinessGoal;
import com.messaging.ads.campaign.enums.CampaignChannelType;
import com.messaging.ads.campaign.enums.CreativeAssetRole;
import com.messaging.ads.campaign.repository.AudienceDefinitionRepository;
import com.messaging.ads.campaign.repository.CampaignChannelRepository;
import com.messaging.ads.campaign.repository.ConversionGoalMappingRepository;
import com.messaging.ads.campaign.repository.CreativePackAssetRepository;
import com.messaging.ads.campaign.repository.CreativePackRepository;
import com.messaging.ads.campaign.repository.ReachlyoCampaignRepository;
import com.messaging.ads.campaign.service.ProviderDeploymentService;
import com.messaging.ads.dto.AdResponse;
import com.messaging.ads.entity.Ad;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdCampaign;
import com.messaging.ads.entity.AdSet;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.GoogleAdsAdGroupSyncService;
import com.messaging.ads.google.GoogleAdsAdSyncService;
import com.messaging.ads.google.GoogleAdsCampaignConversionGoalService;
import com.messaging.ads.google.GoogleAdsCampaignSyncService;
import com.messaging.ads.google.GoogleAdsDemandGenCampaignService;
import com.messaging.ads.google.GoogleAdsDemandGenCreativeService;
import com.messaging.ads.google.GoogleAdsDemandGenTargetingService;
import com.messaging.ads.google.dto.CreateGoogleDemandGenCampaignRequest;
import com.messaging.ads.google.dto.CreateGoogleDemandGenMultiAssetAdRequest;
import com.messaging.ads.google.dto.GoogleDemandGenTargetingRequest;
import com.messaging.ads.repository.AdAccountRepository;
import com.messaging.ads.repository.AdCampaignRepository;
import com.messaging.ads.repository.AdRepository;
import com.messaging.ads.repository.AdSetRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import com.messaging.media.service.MediaService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GoogleDemandGenLaunchExecutor implements LaunchProviderExecutor {

  private final ReachlyoCampaignRepository campaignRepository;
  private final CampaignChannelRepository channelRepository;
  private final CreativePackRepository creativePackRepository;
  private final CreativePackAssetRepository creativePackAssetRepository;
  private final AudienceDefinitionRepository audienceDefinitionRepository;
  private final ConversionGoalMappingRepository conversionGoalMappingRepository;
  private final AdAccountRepository accountRepository;
  private final AdCampaignRepository adCampaignRepository;
  private final AdSetRepository adSetRepository;
  private final AdRepository adRepository;
  private final GoogleAdsCampaignSyncService campaignSyncService;
  private final GoogleAdsAdGroupSyncService adGroupSyncService;
  private final GoogleAdsAdSyncService adSyncService;
  private final GoogleAdsDemandGenCampaignService demandGenCampaignService;
  private final GoogleAdsDemandGenTargetingService targetingService;
  private final GoogleAdsDemandGenCreativeService creativeService;
  private final GoogleAdsCampaignConversionGoalService conversionGoalService;
  private final ProviderDeploymentService deploymentService;
  private final MediaService mediaService;
  private final ObjectMapper objectMapper;

  @Override
  public Set<CampaignChannelType> supportedChannels() {
    return Set.of(CampaignChannelType.GOOGLE_DEMAND_GEN);
  }

  @Override
  public List<ProviderPlanIssue> preflight(
      Long companyId, LaunchRun run, ProviderCampaignPlan plan) {
    List<ProviderPlanIssue> issues = new ArrayList<>();
    ReachlyoCampaign campaign = campaign(companyId, run.getReachlyoCampaignId());
    CampaignChannel channel = channel(companyId, campaign.getId(), Long.valueOf(plan.channelId()));
    AdAccount account = account(companyId, Long.valueOf(plan.adAccountId()));
    JsonNode override = override(channel);

    if (!override.path("containsEuPoliticalAdvertising").isBoolean()) {
      issues.add(error(
          "GOOGLE_POLITICAL_DECLARATION_REQUIRED",
          "providerOverride.containsEuPoliticalAdvertising",
          "Demand Gen requires an explicit EU political advertising declaration."));
    }
    if (dailyBudget(plan, campaign, account, false) == null) {
      issues.add(error(
          "GOOGLE_DAILY_BUDGET_UNRESOLVED",
          "budget",
          "Demand Gen needs a daily budget, or a total budget with a valid start/end schedule."));
    }

    CreativePack pack = creativePack(companyId, campaign.getId(), plan.creativePackId(), false);
    if (pack == null) {
      issues.add(error("DEMAND_GEN_CREATIVE_PACK_REQUIRED", "creativePack", "Demand Gen requires a Creative Pack."));
    } else {
      List<String> headlines = strings(pack.getHeadlinesJson());
      List<String> descriptions = strings(pack.getDescriptionsJson());
      if (headlines.isEmpty() || headlines.size() > 5 || headlines.stream().anyMatch(v -> v.length() > 30)) {
        issues.add(error("DEMAND_GEN_HEADLINES_INVALID", "creativePack.headlines", "Demand Gen requires 1 to 5 headlines, each at most 30 characters."));
      }
      if (descriptions.isEmpty() || descriptions.size() > 5 || descriptions.stream().anyMatch(v -> v.length() > 90)) {
        issues.add(error("DEMAND_GEN_DESCRIPTIONS_INVALID", "creativePack.descriptions", "Demand Gen requires 1 to 5 descriptions, each at most 90 characters."));
      }
      if (!hasText(pack.getBusinessName()) || pack.getBusinessName().trim().length() > 25) {
        issues.add(error("DEMAND_GEN_BUSINESS_NAME_INVALID", "creativePack.businessName", "Demand Gen requires a business name of at most 25 characters."));
      }
      if (asset(companyId, pack.getId(), CreativeAssetRole.LOGO, false) == null) {
        issues.add(error("DEMAND_GEN_LOGO_MISSING", "creativePack.assets", "Demand Gen requires a square logo."));
      }
      if (asset(companyId, pack.getId(), CreativeAssetRole.MARKETING_IMAGE, false) == null
          && asset(companyId, pack.getId(), CreativeAssetRole.SQUARE_MARKETING_IMAGE, false) == null) {
        issues.add(error("DEMAND_GEN_IMAGE_MISSING", "creativePack.assets", "Demand Gen requires a landscape or square marketing image."));
      }
    }

    AudienceDefinition audience = audience(companyId, campaign.getId(), plan.audienceDefinitionId(), false);
    if (audience != null) {
      List<AudienceLocationInput> locations = locations(audience.getLocationsJson());
      if (locations.stream().anyMatch(value ->
          value.radiusKm() != null || value.latitude() != null || value.longitude() != null)) {
        issues.add(error(
            "DEMAND_GEN_PROXIMITY_UNSUPPORTED",
            "audience.locations",
            "Demand Gen unified launch currently supports country/region/city/postal locations, not proximity radius."));
      }
      if (hasText(audience.getInterestsJson()) || hasText(audience.getSearchIntentJson())) {
        issues.add(warning(
            "DEMAND_GEN_AUDIENCE_MAPPING_APPROXIMATE",
            "audience",
            "Canonical interests/search intent are not guessed into Google Audience resources. Add provider-backed Demand Gen Audience resources in advanced settings when needed."));
      }
    }

    if (hasText(plan.conversionGoalMappingId())) {
      try {
        conversionGoalService.validate(
            companyId,
            account.getId(),
            conversion(companyId, campaign.getId(), plan.conversionGoalMappingId()));
      } catch (RuntimeException exception) {
        issues.add(error("GOOGLE_CONVERSION_MAPPING_INVALID", "conversion", exception.getMessage()));
      }
    }
    return List.copyOf(issues);
  }

  @Override
  public LaunchStepExecutionResult execute(
      Long companyId, LaunchRun run, ProviderCampaignPlan plan, LaunchStep step) {
    ReachlyoCampaign campaign = campaign(companyId, run.getReachlyoCampaignId());
    CampaignChannel channel = channel(companyId, campaign.getId(), step.getCampaignChannelId());

    return switch (step.getOperationKey()) {
      case "create-demand-gen-paused" ->
          createDemandGen(companyId, run, plan, step, campaign, channel);
      case "configure-demand-gen-targeting" ->
          configureTargeting(companyId, run, plan, step, campaign, channel);
      case "create-demand-gen-multi-asset-ad" ->
          createCreative(companyId, run, plan, step, campaign);
      case "configure-conversion-goal" ->
          configureConversion(companyId, run, plan, step, campaign);
      default -> throw new BadRequestException(
          "Unsupported Demand Gen launch operation: " + step.getOperationKey());
    };
  }

  private LaunchStepExecutionResult createDemandGen(
      Long companyId,
      LaunchRun run,
      ProviderCampaignPlan plan,
      LaunchStep step,
      ReachlyoCampaign campaign,
      CampaignChannel channel) {
    AdAccount account = account(companyId, step.getAdAccountId());
    String name = launchName(campaign.getName(), run.getId(), plan.channelId());
    String adGroupName = launchName(campaign.getName() + " Audience", run.getId(), plan.channelId());

    if (step.getAttemptCount() > 1) {
      campaignSyncService.sync(companyId, account.getId());
    }

    AdCampaign existing =
        adCampaignRepository
            .findFirstByCompanyIdAndAdAccountIdAndProviderAndNameOrderByIdAsc(
                companyId, account.getId(), AdProvider.GOOGLE_ADS, name)
            .filter(value -> "DEMAND_GEN".equalsIgnoreCase(value.getObjective()))
            .orElse(null);

    AdCampaign localCampaign;
    AdSet localAdGroup;
    if (existing != null) {
      localCampaign = existing;
      adGroupSyncService.sync(companyId, existing.getId());
      localAdGroup =
          adSetRepository
              .findFirstByCompanyIdAndCampaignIdAndNameOrderByIdAsc(
                  companyId, existing.getId(), adGroupName)
              .orElseThrow(() -> new BadRequestException(
                  "Demand Gen campaign exists but its launch ad group could not be resolved"));
    } else {
      JsonNode override = override(channel);
      var result =
          demandGenCampaignService.create(
              companyId,
              account.getId(),
              new CreateGoogleDemandGenCampaignRequest(
                  name,
                  dailyBudget(plan, campaign, account, true),
                  adGroupName,
                  firstText(text(override, "bidStrategy"), defaultBidStrategy(campaign.getBusinessGoal())),
                  override.path("containsEuPoliticalAdvertising").booleanValue(),
                  localDate(campaign.getStartAt(), account),
                  localDate(campaign.getEndAt(), account)),
              run.getRequestedByUserId());
      localCampaign =
          adCampaignRepository
              .findByIdAndCompanyId(Long.valueOf(result.campaign().id()), companyId)
              .orElseThrow(() -> new NotFoundException("Created Demand Gen campaign not found"));
      localAdGroup =
          adSetRepository
              .findByIdAndCompanyId(Long.valueOf(result.adGroup().id()), companyId)
              .orElseThrow(() -> new NotFoundException("Created Demand Gen ad group not found"));
    }

    deploymentService.bindProviderCampaign(
        companyId, step.getProviderDeploymentId(), localCampaign.getId());

    return LaunchStepExecutionResult.succeeded(
        localCampaign.getId(),
        localCampaign.getExternalCampaignId(),
        Map.of(
            "reused", existing != null,
            "adGroupId", localAdGroup.getId(),
            "externalAdGroupId", localAdGroup.getExternalAdSetId()));
  }

  private LaunchStepExecutionResult configureTargeting(
      Long companyId,
      LaunchRun run,
      ProviderCampaignPlan plan,
      LaunchStep step,
      ReachlyoCampaign campaign,
      CampaignChannel channel) {
    AdSet adGroup = providerAdGroup(companyId, step.getProviderDeploymentId());
    AudienceDefinition audience =
        audience(companyId, campaign.getId(), plan.audienceDefinitionId(), false);
    JsonNode override = override(channel);

    var response =
        targetingService.update(
            companyId,
            adGroup.getId(),
            new GoogleDemandGenTargetingRequest(
                firstText(text(override, "demandGenChannelStrategy"), "ALL_CHANNELS"),
                null,
                audience == null ? List.of() : locations(audience.getLocationsJson()),
                List.of(),
                List.of(),
                List.of(),
                audience == null ? List.of() : strings(audience.getLanguagesJson()),
                List.of(),
                stringsFromOverride(override, "demandGenAudienceResourceNames")),
            run.getRequestedByUserId());

    return LaunchStepExecutionResult.succeeded(
        adGroup.getId(),
        adGroup.getExternalAdSetId(),
        Map.of(
            "channelStrategy", response.channelStrategy() == null ? "ALL_CHANNELS" : response.channelStrategy(),
            "locationCount", response.locationResources().size(),
            "languageCount", response.languageResources().size(),
            "audienceCount", response.audienceResources().size()));
  }

  private LaunchStepExecutionResult createCreative(
      Long companyId,
      LaunchRun run,
      ProviderCampaignPlan plan,
      LaunchStep step,
      ReachlyoCampaign campaign) {
    AdSet adGroup = providerAdGroup(companyId, step.getProviderDeploymentId());
    String name = launchName(campaign.getName() + " Ad", run.getId(), plan.channelId());

    if (step.getAttemptCount() > 1) {
      adSyncService.sync(companyId, adGroup.getId());
    }
    Ad existing =
        adRepository
            .findFirstByCompanyIdAndAdSetIdAndNameOrderByIdAsc(companyId, adGroup.getId(), name)
            .orElse(null);
    if (existing != null) {
      return LaunchStepExecutionResult.succeeded(
          existing.getId(), existing.getExternalAdId(), Map.of("reused", true));
    }

    CreativePack pack = creativePack(companyId, campaign.getId(), plan.creativePackId(), true);
    AdResponse created =
        creativeService.createMultiAsset(
            companyId,
            adGroup.getId(),
            new CreateGoogleDemandGenMultiAssetAdRequest(
                name,
                firstText(pack.getDestinationUrl(), campaign.getLandingPageUrl()),
                pack.getBusinessName(),
                strings(pack.getHeadlinesJson()),
                strings(pack.getDescriptionsJson()),
                pack.getCallToAction(),
                imageBase64(companyId, pack.getId(), CreativeAssetRole.MARKETING_IMAGE, false),
                imageBase64(companyId, pack.getId(), CreativeAssetRole.SQUARE_MARKETING_IMAGE, false),
                imageBase64(companyId, pack.getId(), CreativeAssetRole.PORTRAIT_MARKETING_IMAGE, false),
                null,
                imageBase64(companyId, pack.getId(), CreativeAssetRole.LOGO, true)),
            run.getRequestedByUserId());

    return LaunchStepExecutionResult.succeeded(
        Long.valueOf(created.id()),
        created.externalAdId(),
        Map.of("reused", false, "creativeType", "DEMAND_GEN_MULTI_ASSET"));
  }

  private LaunchStepExecutionResult configureConversion(
      Long companyId,
      LaunchRun run,
      ProviderCampaignPlan plan,
      LaunchStep step,
      ReachlyoCampaign campaign) {
    if (!hasText(plan.conversionGoalMappingId())) {
      return LaunchStepExecutionResult.skipped("No Google conversion mapping is configured.");
    }
    AdCampaign providerCampaign = providerCampaign(companyId, step.getProviderDeploymentId());
    var result =
        conversionGoalService.apply(
            companyId,
            step.getAdAccountId(),
            providerCampaign.getExternalCampaignId(),
            run.getReachlyoCampaignId(),
            conversion(companyId, campaign.getId(), plan.conversionGoalMappingId()));

    return LaunchStepExecutionResult.succeeded(
        null,
        result.customConversionGoalResource(),
        Map.of(
            "created", result.created(),
            "updated", result.updated(),
            "disabledCampaignGoals", result.disabledCampaignGoals()));
  }

  private String imageBase64(
      Long companyId, Long packId, CreativeAssetRole role, boolean required) {
    CreativePackAsset asset = asset(companyId, packId, role, required);
    if (asset == null) return null;
    return Base64.getEncoder().encodeToString(
        mediaService.readTrustedObject(asset.getMediaId(), companyId));
  }

  private CreativePackAsset asset(
      Long companyId, Long packId, CreativeAssetRole role, boolean required) {
    CreativePackAsset found =
        creativePackAssetRepository
            .findByCompanyIdAndCreativePackIdOrderBySortOrderAscIdAsc(companyId, packId)
            .stream()
            .filter(value -> value.getRole() == role)
            .findFirst()
            .orElse(null);
    if (found == null && required) {
      throw new BadRequestException("Demand Gen requires " + role.name() + " media");
    }
    return found;
  }

  private ReachlyoCampaign campaign(Long companyId, Long campaignId) {
    return campaignRepository.findByIdAndCompanyId(campaignId, companyId)
        .orElseThrow(() -> new NotFoundException("Reachlyo campaign not found"));
  }

  private CampaignChannel channel(Long companyId, Long campaignId, Long channelId) {
    return channelRepository
        .findByIdAndCompanyIdAndReachlyoCampaignId(channelId, companyId, campaignId)
        .orElseThrow(() -> new NotFoundException("Campaign channel not found"));
  }

  private AdAccount account(Long companyId, Long accountId) {
    return accountRepository.findByIdAndCompanyId(accountId, companyId)
        .orElseThrow(() -> new NotFoundException("Advertising account not found"));
  }

  private CreativePack creativePack(
      Long companyId, Long campaignId, String packId, boolean required) {
    if (!hasText(packId)) {
      if (required) throw new BadRequestException("Creative Pack is required");
      return null;
    }
    return creativePackRepository
        .findByIdAndCompanyIdAndReachlyoCampaignId(Long.valueOf(packId), companyId, campaignId)
        .orElseThrow(() -> new NotFoundException("Creative pack not found"));
  }

  private AudienceDefinition audience(
      Long companyId, Long campaignId, String audienceId, boolean required) {
    if (!hasText(audienceId)) {
      if (required) throw new BadRequestException("Audience definition is required");
      return null;
    }
    return audienceDefinitionRepository
        .findByIdAndCompanyIdAndReachlyoCampaignId(Long.valueOf(audienceId), companyId, campaignId)
        .orElseThrow(() -> new NotFoundException("Audience definition not found"));
  }

  private ConversionGoalMapping conversion(
      Long companyId, Long campaignId, String mappingId) {
    return conversionGoalMappingRepository
        .findByIdAndCompanyIdAndReachlyoCampaignId(Long.valueOf(mappingId), companyId, campaignId)
        .orElseThrow(() -> new NotFoundException("Conversion goal mapping not found"));
  }

  private AdCampaign providerCampaign(Long companyId, Long deploymentId) {
    var deployment = deploymentService.get(companyId, deploymentId);
    if (deployment.getLocalCampaignId() == null) {
      throw new BadRequestException("Provider campaign has not been created yet");
    }
    return adCampaignRepository
        .findByIdAndCompanyId(deployment.getLocalCampaignId(), companyId)
        .orElseThrow(() -> new NotFoundException("Advertising campaign not found"));
  }

  private AdSet providerAdGroup(Long companyId, Long deploymentId) {
    AdCampaign providerCampaign = providerCampaign(companyId, deploymentId);
    return adSetRepository
        .findByCompanyIdAndCampaignIdOrderByProviderUpdatedAtDesc(companyId, providerCampaign.getId())
        .stream()
        .findFirst()
        .orElseThrow(() -> new BadRequestException("Demand Gen ad group has not been created yet"));
  }

  private JsonNode override(CampaignChannel channel) {
    try {
      return hasText(channel.getProviderOverrideJson())
          ? objectMapper.readTree(channel.getProviderOverrideJson())
          : objectMapper.createObjectNode();
    } catch (Exception exception) {
      throw new BadRequestException("Campaign channel provider override is invalid JSON");
    }
  }

  private Long dailyBudget(
      ProviderCampaignPlan plan,
      ReachlyoCampaign campaign,
      AdAccount account,
      boolean required) {
    Long budget = plan.effectiveBudgetMinor();
    if (budget == null || budget <= 0) return required ? failBudget() : null;
    if (plan.budgetPeriod() == BudgetPeriod.DAILY) return budget;
    LocalDate start = localDate(campaign.getStartAt(), account);
    LocalDate end = localDate(campaign.getEndAt(), account);
    if (start == null || end == null || end.isBefore(start)) return required ? failBudget() : null;
    long days = ChronoUnit.DAYS.between(start, end) + 1;
    return Math.max(1L, (budget + days - 1) / days);
  }

  private Long failBudget() {
    throw new BadRequestException(
        "Google total budget requires a valid campaign schedule so UnifyDesk can derive the daily budget");
  }

  private LocalDate localDate(Instant instant, AdAccount account) {
    if (instant == null) return null;
    ZoneId zone = ZoneId.of("UTC");
    if (hasText(account.getTimezone())) {
      try {
        zone = ZoneId.of(account.getTimezone());
      } catch (Exception ignored) {
        zone = ZoneId.of("UTC");
      }
    }
    return instant.atZone(zone).toLocalDate();
  }

  private List<String> strings(String json) {
    if (!hasText(json)) return List.of();
    try {
      return objectMapper.readValue(json, new TypeReference<List<String>>() {});
    } catch (Exception exception) {
      throw new BadRequestException("Creative/audience text list is invalid JSON");
    }
  }

  private List<AudienceLocationInput> locations(String json) {
    if (!hasText(json)) return List.of();
    try {
      return objectMapper.readValue(json, new TypeReference<List<AudienceLocationInput>>() {});
    } catch (Exception exception) {
      throw new BadRequestException("Audience locations are invalid JSON");
    }
  }

  private List<String> stringsFromOverride(JsonNode node, String key) {
    JsonNode value = node.get(key);
    if (value == null || value.isNull() || !value.isArray()) return List.of();
    List<String> result = new ArrayList<>();
    for (JsonNode item : value) {
      if (item.isTextual() && !item.asText().isBlank()) result.add(item.asText().trim());
    }
    return List.copyOf(result);
  }

  private String text(JsonNode node, String key) {
    JsonNode value = node.get(key);
    return value == null || value.isNull() ? null : value.asText(null);
  }

  private String defaultBidStrategy(CampaignBusinessGoal goal) {
    return switch (goal) {
      case TRAFFIC, AWARENESS -> "MAXIMIZE_CLICKS";
      case PURCHASE -> "MAXIMIZE_CONVERSION_VALUE";
      default -> "MAXIMIZE_CONVERSIONS";
    };
  }

  private String firstText(String... values) {
    for (String value : values) if (hasText(value)) return value.trim();
    return null;
  }

  private String launchName(String base, Long runId, String channelId) {
    String suffix = " [RLY-" + runId + "-" + channelId + "]";
    String normalized = hasText(base) ? base.trim() : "UnifyDesk";
    int maxBase = Math.max(1, 255 - suffix.length());
    return (normalized.length() > maxBase ? normalized.substring(0, maxBase) : normalized) + suffix;
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private ProviderPlanIssue error(String code, String field, String message) {
    return new ProviderPlanIssue(PlanIssueSeverity.ERROR, code, field, message);
  }

  private ProviderPlanIssue warning(String code, String field, String message) {
    return new ProviderPlanIssue(PlanIssueSeverity.WARNING, code, field, message);
  }
}
