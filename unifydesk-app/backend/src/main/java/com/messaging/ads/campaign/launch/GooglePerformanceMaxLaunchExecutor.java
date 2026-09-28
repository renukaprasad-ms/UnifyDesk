package com.messaging.ads.campaign.launch;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.campaign.compiler.PlanIssueSeverity;
import com.messaging.ads.campaign.compiler.ProviderCampaignPlan;
import com.messaging.ads.campaign.compiler.ProviderPlanIssue;
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
import com.messaging.ads.dto.AdCampaignResponse;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdCampaign;
import com.messaging.ads.google.GoogleAdsAssetGroupSignalService;
import com.messaging.ads.google.GoogleAdsCampaignConversionGoalService;
import com.messaging.ads.google.GoogleAdsCampaignSyncService;
import com.messaging.ads.google.GoogleAdsCampaignTargetingService;
import com.messaging.ads.google.GoogleAdsPerformanceMaxService;
import com.messaging.ads.google.dto.CreateGooglePerformanceMaxCampaignRequest;
import com.messaging.ads.google.dto.GoogleAssetGroupSignalsRequest;
import com.messaging.ads.repository.AdAccountRepository;
import com.messaging.ads.repository.AdCampaignRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import com.messaging.media.enums.MediaType;
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
public class GooglePerformanceMaxLaunchExecutor implements LaunchProviderExecutor {

  private final ReachlyoCampaignRepository campaignRepository;
  private final CampaignChannelRepository channelRepository;
  private final CreativePackRepository creativePackRepository;
  private final CreativePackAssetRepository creativePackAssetRepository;
  private final AudienceDefinitionRepository audienceDefinitionRepository;
  private final ConversionGoalMappingRepository conversionGoalMappingRepository;
  private final AdAccountRepository accountRepository;
  private final AdCampaignRepository adCampaignRepository;
  private final GoogleAdsCampaignSyncService campaignSyncService;
  private final GoogleAdsPerformanceMaxService performanceMaxService;
  private final GoogleAdsCampaignTargetingService campaignTargetingService;
  private final GoogleAdsAssetGroupSignalService signalService;
  private final GoogleAdsCampaignConversionGoalService conversionGoalService;
  private final ProviderDeploymentService deploymentService;
  private final MediaService mediaService;
  private final ObjectMapper objectMapper;

  @Override
  public Set<CampaignChannelType> supportedChannels() {
    return Set.of(CampaignChannelType.GOOGLE_PERFORMANCE_MAX);
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
          "Performance Max requires an explicit EU political advertising declaration."));
    }
    if (dailyBudget(plan, campaign, account, false) == null) {
      issues.add(error(
          "GOOGLE_DAILY_BUDGET_UNRESOLVED",
          "budget",
          "Performance Max needs a daily budget, or a total budget with a valid start/end schedule."));
    }

    CreativePack pack = creativePack(companyId, campaign.getId(), plan.creativePackId(), false);
    if (pack == null) {
      issues.add(error("PMAX_CREATIVE_PACK_REQUIRED", "creativePack", "Performance Max requires a Creative Pack."));
    } else {
      List<String> headlines = strings(pack.getHeadlinesJson());
      List<String> descriptions = strings(pack.getDescriptionsJson());
      if (headlines.size() < 3) {
        issues.add(error("PMAX_HEADLINES_INSUFFICIENT", "creativePack.headlines", "Performance Max requires at least 3 headlines."));
      }
      if (descriptions.size() < 2) {
        issues.add(error("PMAX_DESCRIPTIONS_INSUFFICIENT", "creativePack.descriptions", "Performance Max requires at least 2 descriptions."));
      }
      if (!hasText(pack.getBusinessName())) {
        issues.add(error("PMAX_BUSINESS_NAME_REQUIRED", "creativePack.businessName", "Performance Max requires a business name."));
      }
      if (!hasText(firstText(pack.getDestinationUrl(), campaign.getLandingPageUrl()))) {
        issues.add(error("PMAX_FINAL_URL_REQUIRED", "creativePack.destinationUrl", "Performance Max requires a destination URL."));
      }
      for (CreativeAssetRole role :
          List.of(CreativeAssetRole.MARKETING_IMAGE, CreativeAssetRole.SQUARE_MARKETING_IMAGE, CreativeAssetRole.LOGO)) {
        CreativePackAsset asset = asset(companyId, pack.getId(), role, false);
        if (asset == null) {
          issues.add(error("PMAX_REQUIRED_ASSET_MISSING", "creativePack.assets", "Missing Performance Max " + role.name() + " asset."));
        } else {
          try {
            var media = mediaService.requireActiveCompanyMedia(asset.getMediaId(), companyId);
            if (media.getMediaType() != MediaType.IMAGE) {
              issues.add(error("PMAX_IMAGE_ASSET_INVALID", "creativePack.assets", role.name() + " must reference image media."));
            }
          } catch (RuntimeException exception) {
            issues.add(error("PMAX_IMAGE_ASSET_INVALID", "creativePack.assets", exception.getMessage()));
          }
        }
      }
    }

    AudienceDefinition audience = audience(companyId, campaign.getId(), plan.audienceDefinitionId(), false);
    if (audience != null) {
      try {
        campaignTargetingService.resolve(companyId, account.getId(), geoLanguageOnly(audience), objectMapper.createObjectNode());
      } catch (RuntimeException exception) {
        issues.add(error("PMAX_GEO_LANGUAGE_INVALID", "audience", exception.getMessage()));
      }
      List<String> themes = strings(audience.getSearchIntentJson());
      if (themes.size() > 25 || themes.stream().anyMatch(value -> value.length() > 80)) {
        issues.add(error(
            "PMAX_SEARCH_THEMES_INVALID",
            "audience.searchIntents",
            "Performance Max supports at most 25 search themes and each theme must be at most 80 characters."));
      }
      if (audience.getAgeMin() != null
          || audience.getAgeMax() != null
          || hasText(audience.getGendersJson())
          || hasText(audience.getInterestsJson())) {
        issues.add(warning(
            "PMAX_DEMOGRAPHICS_ARE_SIGNALS",
            "audience",
            "Performance Max does not apply the shared age/gender/interests as hard Search-style criteria. Geo/language are targeting controls; search intent is mapped as asset-group search-theme signals."));
      }
    }

    if (hasText(plan.conversionGoalMappingId())) {
      try {
        ConversionGoalMapping mapping = conversion(companyId, campaign.getId(), plan.conversionGoalMappingId());
        conversionGoalService.validate(companyId, account.getId(), mapping);
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
      case "create-performance-max-paused" ->
          createPerformanceMax(companyId, run, plan, step, campaign, channel);
      case "configure-pmax-targeting" ->
          configureTargeting(companyId, plan, step, campaign);
      case "configure-audience-signals" ->
          configureSignals(companyId, run, plan, step, campaign);
      case "configure-conversion-goal" ->
          configureConversionGoal(companyId, run, plan, step, campaign);
      default -> throw new BadRequestException(
          "Unsupported Performance Max launch operation: " + step.getOperationKey());
    };
  }

  private LaunchStepExecutionResult createPerformanceMax(
      Long companyId,
      LaunchRun run,
      ProviderCampaignPlan plan,
      LaunchStep step,
      ReachlyoCampaign campaign,
      CampaignChannel channel) {
    AdAccount account = account(companyId, step.getAdAccountId());
    String name = launchName(campaign.getName(), run.getId(), plan.channelId());

    if (step.getAttemptCount() > 1) {
      campaignSyncService.sync(companyId, account.getId());
    }

    AdCampaign existing =
        adCampaignRepository
            .findFirstByCompanyIdAndAdAccountIdAndProviderAndNameOrderByIdAsc(
                companyId, account.getId(), com.messaging.ads.enums.AdProvider.GOOGLE_ADS, name)
            .filter(value -> "PERFORMANCE_MAX".equalsIgnoreCase(value.getObjective()))
            .orElse(null);

    AdCampaignResponse result;
    if (existing != null) {
      result = AdCampaignResponse.from(existing);
    } else {
      CreativePack pack = creativePack(companyId, campaign.getId(), plan.creativePackId(), true);
      JsonNode override = override(channel);
      List<String> headlines = strings(pack.getHeadlinesJson());
      List<String> descriptions = strings(pack.getDescriptionsJson());

      result =
          performanceMaxService.create(
              companyId,
              account.getId(),
              new CreateGooglePerformanceMaxCampaignRequest(
                  name,
                  dailyBudget(plan, campaign, account, true),
                  firstText(pack.getDestinationUrl(), campaign.getLandingPageUrl()),
                  pack.getBusinessName(),
                  headlines,
                  pmaxLongHeadline(pack, headlines),
                  descriptions,
                  imageBase64(companyId, pack.getId(), CreativeAssetRole.MARKETING_IMAGE, true),
                  imageBase64(companyId, pack.getId(), CreativeAssetRole.SQUARE_MARKETING_IMAGE, true),
                  imageBase64(companyId, pack.getId(), CreativeAssetRole.LOGO, true),
                  imageBase64(companyId, pack.getId(), CreativeAssetRole.PORTRAIT_MARKETING_IMAGE, false),
                  imageBase64(companyId, pack.getId(), CreativeAssetRole.LANDSCAPE_LOGO, false),
                  stringsFromOverride(override, "pmaxYoutubeVideoIds"),
                  firstText(text(override, "bidStrategy"), defaultBidStrategy(campaign.getBusinessGoal())),
                  override.path("containsEuPoliticalAdvertising").booleanValue(),
                  localDate(campaign.getStartAt(), account),
                  localDate(campaign.getEndAt(), account)),
              run.getRequestedByUserId());
    }

    deploymentService.bindProviderCampaign(
        companyId, step.getProviderDeploymentId(), Long.valueOf(result.id()));

    return LaunchStepExecutionResult.succeeded(
        Long.valueOf(result.id()),
        result.externalCampaignId(),
        Map.of("reused", existing != null, "channel", "PERFORMANCE_MAX"));
  }

  private LaunchStepExecutionResult configureTargeting(
      Long companyId,
      ProviderCampaignPlan plan,
      LaunchStep step,
      ReachlyoCampaign campaign) {
    AudienceDefinition audience =
        audience(companyId, campaign.getId(), plan.audienceDefinitionId(), false);
    if (audience == null) {
      return LaunchStepExecutionResult.skipped("No canonical audience was selected.");
    }
    AdCampaign providerCampaign = providerCampaign(companyId, step.getProviderDeploymentId());
    var result =
        campaignTargetingService.apply(
            companyId,
            providerCampaign.getId(),
            geoLanguageOnly(audience),
            objectMapper.createObjectNode());
    return LaunchStepExecutionResult.succeeded(
        null,
        result.firstExternalResource(),
        Map.of(
            "createdCount", result.createdCount(),
            "reusedCount", result.reusedCount(),
            "locationCount", result.locationCount(),
            "languageCount", result.languageCount()));
  }

  private LaunchStepExecutionResult configureSignals(
      Long companyId,
      LaunchRun run,
      ProviderCampaignPlan plan,
      LaunchStep step,
      ReachlyoCampaign campaign) {
    AudienceDefinition audience =
        audience(companyId, campaign.getId(), plan.audienceDefinitionId(), false);
    if (audience == null) {
      return LaunchStepExecutionResult.skipped("No canonical audience signals were selected.");
    }
    List<String> themes = strings(audience.getSearchIntentJson());
    if (themes.isEmpty()) {
      return LaunchStepExecutionResult.skipped("No search-intent terms were supplied for Performance Max search themes.");
    }

    AdCampaign providerCampaign = providerCampaign(companyId, step.getProviderDeploymentId());
    var groups = performanceMaxService.assetGroups(companyId, providerCampaign.getId());
    if (groups.isEmpty() || !hasText(groups.getFirst().id())) {
      throw new BadRequestException("Performance Max asset group could not be resolved after creation");
    }

    var after =
        signalService.add(
            companyId,
            providerCampaign.getId(),
            groups.getFirst().id(),
            new GoogleAssetGroupSignalsRequest(themes, List.of()),
            run.getRequestedByUserId());

    return LaunchStepExecutionResult.succeeded(
        null,
        after.isEmpty() ? null : after.getFirst().resourceName(),
        Map.of("searchThemeCount", themes.size()));
  }

  private LaunchStepExecutionResult configureConversionGoal(
      Long companyId,
      LaunchRun run,
      ProviderCampaignPlan plan,
      LaunchStep step,
      ReachlyoCampaign campaign) {
    if (!hasText(plan.conversionGoalMappingId())) {
      return LaunchStepExecutionResult.skipped("No Google conversion mapping is configured.");
    }
    ConversionGoalMapping mapping =
        conversion(companyId, campaign.getId(), plan.conversionGoalMappingId());
    AdCampaign providerCampaign = providerCampaign(companyId, step.getProviderDeploymentId());
    var result =
        conversionGoalService.apply(
            companyId,
            step.getAdAccountId(),
            providerCampaign.getExternalCampaignId(),
            run.getReachlyoCampaignId(),
            mapping);

    return LaunchStepExecutionResult.succeeded(
        null,
        result.customConversionGoalResource(),
        Map.of(
            "created", result.created(),
            "updated", result.updated(),
            "disabledCampaignGoals", result.disabledCampaignGoals()));
  }

  private AudienceDefinition geoLanguageOnly(AudienceDefinition source) {
    AudienceDefinition copy = new AudienceDefinition();
    copy.setLocationsJson(source.getLocationsJson());
    copy.setLanguagesJson(source.getLanguagesJson());
    return copy;
  }

  private String imageBase64(
      Long companyId, Long packId, CreativeAssetRole role, boolean required) {
    CreativePackAsset asset = asset(companyId, packId, role, required);
    if (asset == null) return null;
    return Base64.getEncoder()
        .encodeToString(mediaService.readTrustedObject(asset.getMediaId(), companyId));
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
      throw new BadRequestException("Performance Max requires " + role.name() + " media");
    }
    return found;
  }

  private String pmaxLongHeadline(CreativePack pack, List<String> headlines) {
    String value = firstText(pack.getPrimaryMessage(), headlines.isEmpty() ? null : headlines.getFirst());
    if (!hasText(value)) throw new BadRequestException("Performance Max long headline is required");
    value = value.trim();
    return value.length() <= 90 ? value : value.substring(0, 90);
  }

  private String defaultBidStrategy(CampaignBusinessGoal goal) {
    return goal == CampaignBusinessGoal.PURCHASE
        ? "MAXIMIZE_CONVERSION_VALUE"
        : "MAXIMIZE_CONVERSIONS";
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
