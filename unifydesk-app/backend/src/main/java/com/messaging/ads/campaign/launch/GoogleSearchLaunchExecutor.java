package com.messaging.ads.campaign.launch;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.campaign.compiler.PlanIssueSeverity;
import com.messaging.ads.campaign.compiler.ProviderCampaignPlan;
import com.messaging.ads.campaign.compiler.ProviderPlanIssue;
import com.messaging.ads.campaign.entity.AudienceDefinition;
import com.messaging.ads.campaign.entity.CampaignChannel;
import com.messaging.ads.campaign.entity.CreativePack;
import com.messaging.ads.campaign.entity.ConversionGoalMapping;
import com.messaging.ads.campaign.entity.CreativeVariant;
import com.messaging.ads.campaign.entity.ReachlyoCampaign;
import com.messaging.ads.campaign.enums.BudgetPeriod;
import com.messaging.ads.campaign.enums.CampaignChannelType;
import com.messaging.ads.campaign.enums.CreativeVariantType;
import com.messaging.ads.campaign.repository.AudienceDefinitionRepository;
import com.messaging.ads.campaign.repository.CampaignChannelRepository;
import com.messaging.ads.campaign.repository.CreativePackRepository;
import com.messaging.ads.campaign.repository.ConversionGoalMappingRepository;
import com.messaging.ads.campaign.repository.CreativeVariantRepository;
import com.messaging.ads.campaign.repository.ReachlyoCampaignRepository;
import com.messaging.ads.campaign.service.ProviderDeploymentService;
import com.messaging.ads.dto.AdCampaignResponse;
import com.messaging.ads.dto.AdResponse;
import com.messaging.ads.dto.AdSetResponse;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdCampaign;
import com.messaging.ads.entity.AdKeyword;
import com.messaging.ads.entity.AdSet;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.GoogleAdsAdGroupMutationService;
import com.messaging.ads.google.GoogleAdsAiMaxControlService;
import com.messaging.ads.google.GoogleAdsAiMaxBrandService;
import com.messaging.ads.google.GoogleAdsAdGroupSyncService;
import com.messaging.ads.google.GoogleAdsAdMutationService;
import com.messaging.ads.google.GoogleAdsAdSyncService;
import com.messaging.ads.google.GoogleAdsCampaignMutationService;
import com.messaging.ads.google.GoogleAdsCampaignConversionGoalService;
import com.messaging.ads.google.GoogleAdsCampaignTargetingService;
import com.messaging.ads.google.GoogleAdsCampaignSyncService;
import com.messaging.ads.google.GoogleAdsKeywordMutationService;
import com.messaging.ads.google.GoogleAdsNegativeKeywordService;
import com.messaging.ads.google.GoogleAdsKeywordSyncService;
import com.messaging.ads.google.dto.CreateGoogleAdGroupRequest;
import com.messaging.ads.google.dto.CreateGoogleCampaignRequest;
import com.messaging.ads.google.dto.CreateGoogleKeywordRequest;
import com.messaging.ads.google.dto.CreateGoogleResponsiveSearchAdRequest;
import com.messaging.ads.repository.AdAccountRepository;
import com.messaging.ads.repository.AdCampaignRepository;
import com.messaging.ads.repository.AdKeywordRepository;
import com.messaging.ads.repository.AdRepository;
import com.messaging.ads.repository.AdSetRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GoogleSearchLaunchExecutor implements LaunchProviderExecutor {

  private final ReachlyoCampaignRepository campaignRepository;
  private final CampaignChannelRepository channelRepository;
  private final CreativePackRepository creativePackRepository;
  private final CreativeVariantRepository creativeVariantRepository;
  private final AudienceDefinitionRepository audienceDefinitionRepository;
  private final ConversionGoalMappingRepository conversionGoalMappingRepository;
  private final AdAccountRepository accountRepository;
  private final AdCampaignRepository adCampaignRepository;
  private final AdSetRepository adSetRepository;
  private final AdKeywordRepository keywordRepository;
  private final AdRepository adRepository;
  private final GoogleAdsCampaignMutationService campaignMutationService;
  private final GoogleAdsCampaignTargetingService campaignTargetingService;
  private final GoogleAdsCampaignSyncService campaignSyncService;
  private final GoogleAdsCampaignConversionGoalService campaignConversionGoalService;
  private final GoogleAdsAdGroupMutationService adGroupMutationService;
  private final GoogleAdsAiMaxControlService aiMaxControlService;
  private final GoogleAdsAiMaxBrandService aiMaxBrandService;
  private final GoogleAdsAdGroupSyncService adGroupSyncService;
  private final GoogleAdsKeywordMutationService keywordMutationService;
  private final GoogleAdsKeywordSyncService keywordSyncService;
  private final GoogleAdsNegativeKeywordService negativeKeywordService;
  private final GoogleAdsAdMutationService adMutationService;
  private final GoogleAdsAdSyncService adSyncService;
  private final ProviderDeploymentService deploymentService;
  private final ObjectMapper objectMapper;

  @Override
  public Set<CampaignChannelType> supportedChannels() {
    return Set.of(CampaignChannelType.GOOGLE_SEARCH);
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
      issues.add(
          error(
              "GOOGLE_POLITICAL_DECLARATION_REQUIRED",
              "providerOverride.containsEuPoliticalAdvertising",
              "Google Search launch requires an explicit containsEuPoliticalAdvertising boolean."));
    }
    if (dailyBudget(plan, campaign, account, false) == null) {
      issues.add(
          error(
              "GOOGLE_DAILY_BUDGET_UNRESOLVED",
              "budget",
              "Google Search requires a resolvable positive daily budget."));
    }

    CreativePack pack = creativePack(companyId, campaign.getId(), plan.creativePackId(), false);
    if (pack == null) {
      issues.add(
          error(
              "CREATIVE_PACK_MISSING",
              "creativePack",
              "Google Search launch requires a Creative Pack."));
      return issues;
    }

    CreativeVariant variant =
        variant(companyId, pack.getId(), CreativeVariantType.GOOGLE_RSA);
    List<String> headlines =
        strings(
            variant != null && hasText(variant.getHeadlinesJson())
                ? variant.getHeadlinesJson()
                : pack.getHeadlinesJson());
    List<String> descriptions =
        strings(
            variant != null && hasText(variant.getDescriptionsJson())
                ? variant.getDescriptionsJson()
                : pack.getDescriptionsJson());
    String finalUrl =
        firstText(
            variant == null ? null : variant.getDestinationUrl(),
            pack.getDestinationUrl(),
            campaign.getLandingPageUrl());

    if (headlines.size() < 3 || headlines.size() > 15) {
      issues.add(
          error(
              "GOOGLE_RSA_HEADLINES_INVALID",
              "creativePack.headlines",
              "Google Search requires 3 to 15 headlines."));
    }
    if (headlines.stream().anyMatch(value -> !hasText(value) || value.trim().length() > 30)) {
      issues.add(
          error(
              "GOOGLE_RSA_HEADLINE_LENGTH_INVALID",
              "creativePack.headlines",
              "Each Google Search headline must be non-blank and no longer than 30 characters."));
    }
    if (descriptions.size() < 2 || descriptions.size() > 4) {
      issues.add(
          error(
              "GOOGLE_RSA_DESCRIPTIONS_INVALID",
              "creativePack.descriptions",
              "Google Search requires 2 to 4 descriptions."));
    }
    if (descriptions.stream().anyMatch(value -> !hasText(value) || value.trim().length() > 90)) {
      issues.add(
          error(
              "GOOGLE_RSA_DESCRIPTION_LENGTH_INVALID",
              "creativePack.descriptions",
              "Each Google Search description must be non-blank and no longer than 90 characters."));
    }
    if (!hasText(finalUrl)) {
      issues.add(
          error(
              "GOOGLE_FINAL_URL_MISSING",
              "creativePack.destinationUrl",
              "Google Search requires a destination URL."));
    } else if (!isHttpUrl(finalUrl)) {
      issues.add(
          error(
              "GOOGLE_FINAL_URL_INVALID",
              "creativePack.destinationUrl",
              "Google Search destination URL must be a valid HTTP or HTTPS URL."));
    }

    String path1 = text(override, "path1");
    String path2 = text(override, "path2");
    if (hasText(path1) && path1.trim().length() > 15) {
      issues.add(error("GOOGLE_RSA_PATH1_TOO_LONG", "providerOverride.path1", "Google RSA path1 may contain at most 15 characters."));
    }
    if (hasText(path2) && path2.trim().length() > 15) {
      issues.add(error("GOOGLE_RSA_PATH2_TOO_LONG", "providerOverride.path2", "Google RSA path2 may contain at most 15 characters."));
    }
    if (hasText(path2) && !hasText(path1)) {
      issues.add(error("GOOGLE_RSA_PATH2_REQUIRES_PATH1", "providerOverride.path2", "Google RSA path2 requires path1."));
    }

    String bidStrategy = text(override, "bidStrategy");
    if (hasText(bidStrategy)
        && !Set.of("MANUAL_CPC", "MAXIMIZE_CLICKS", "MAXIMIZE_CONVERSIONS", "MAXIMIZE_CONVERSION_VALUE")
            .contains(bidStrategy.trim().toUpperCase(Locale.ROOT))) {
      issues.add(error("GOOGLE_BID_STRATEGY_INVALID", "providerOverride.bidStrategy", "Unsupported Google Search bid strategy."));
    }
    String keywordMatchType = text(override, "keywordMatchType");
    if (hasText(keywordMatchType)
        && !Set.of("BROAD", "PHRASE", "EXACT").contains(keywordMatchType.trim().toUpperCase(Locale.ROOT))) {
      issues.add(error("GOOGLE_KEYWORD_MATCH_TYPE_INVALID", "providerOverride.keywordMatchType", "Keyword match type must be BROAD, PHRASE, or EXACT."));
    }
    Long adGroupCpc = longValue(override, "cpcBidMinor");
    if (adGroupCpc != null && adGroupCpc <= 0) {
      issues.add(error("GOOGLE_CPC_BID_INVALID", "providerOverride.cpcBidMinor", "Ad-group CPC bid must be positive."));
    }
    Long keywordCpc = longValue(override, "keywordCpcBidMinor");
    if (keywordCpc != null && keywordCpc <= 0) {
      issues.add(error("GOOGLE_KEYWORD_CPC_BID_INVALID", "providerOverride.keywordCpcBidMinor", "Keyword CPC bid must be positive."));
    }

    AudienceDefinition audience =
        plan.audienceDefinitionId() == null
            ? null
            : audience(companyId, campaign.getId(), plan.audienceDefinitionId(), false);
    if (audience != null && hasText(audience.getSearchIntentJson())) {
      List<String> terms = strings(audience.getSearchIntentJson());
      if (terms.stream().anyMatch(value -> !hasText(value) || value.trim().length() > 500)) {
        issues.add(
            error(
                "GOOGLE_KEYWORD_LENGTH_INVALID",
                "audience.searchIntents",
                "Each Google Search keyword seed must be non-blank and no longer than 500 characters."));
      }
    }

    try {
      campaignTargetingService.resolve(companyId, account.getId(), audience, override);
    } catch (BadRequestException exception) {
      issues.add(
          error(
              "GOOGLE_TARGETING_INVALID",
              "audience",
              exception.getMessage()));
    }

    try {
      negativeKeywordService.validateLaunchConfiguration(
          companyId, account.getId(), override);
    } catch (BadRequestException exception) {
      issues.add(
          error(
              "GOOGLE_NEGATIVE_KEYWORDS_INVALID",
              "providerOverride.negativeKeywords",
              exception.getMessage()));
    }

    if (override.path("aiMaxEnabled").asBoolean(false)) {
      try {
        aiMaxControlService.validateUrlExclusions(
            stringsFromOverrideArray(override, "aiMaxUrlExclusions"));
        aiMaxControlService.validateUrlInclusions(
            stringsFromOverrideArray(override, "aiMaxUrlInclusions"));
        aiMaxBrandService.validate(
            objectIdsFromOverrideArray(override, "aiMaxBrandInclusions"),
            objectIdsFromOverrideArray(override, "aiMaxBrandExclusions"));
      } catch (BadRequestException exception) {
        issues.add(
            error(
                "GOOGLE_AI_MAX_URL_EXCLUSIONS_INVALID",
                "providerOverride.aiMaxUrlExclusions",
                exception.getMessage()));
      }
    }

    if (plan.conversionGoalMappingId() != null
        && !plan.conversionGoalMappingId().isBlank()) {
      try {
        ConversionGoalMapping mapping =
            conversionGoalMappingRepository
                .findByIdAndCompanyIdAndReachlyoCampaignId(
                    Long.valueOf(plan.conversionGoalMappingId()),
                    companyId,
                    campaign.getId())
                .orElseThrow(
                    () -> new NotFoundException("Conversion goal mapping not found"));
        campaignConversionGoalService.validate(
            companyId, account.getId(), mapping);
      } catch (RuntimeException exception) {
        issues.add(
            error(
                "GOOGLE_CONVERSION_MAPPING_INVALID",
                "conversion",
                exception.getMessage()));
      }
    }
    return issues;
  }

  @Override
  public LaunchStepExecutionResult execute(
      Long companyId, LaunchRun run, ProviderCampaignPlan plan, LaunchStep step) {
    ReachlyoCampaign campaign = campaign(companyId, run.getReachlyoCampaignId());
    CampaignChannel channel = channel(companyId, campaign.getId(), step.getCampaignChannelId());
    AdAccount account = account(companyId, step.getAdAccountId());

    return switch (step.getOperationKey()) {
      case "create-budget" ->
          LaunchStepExecutionResult.skipped(
              "GoogleAdsCampaignMutationService creates CampaignBudget atomically with the paused campaign.");
      case "create-campaign-paused" ->
          createCampaign(companyId, run, plan, step, campaign, channel, account);
      case "configure-targeting" ->
          configureTargeting(companyId, run, plan, step, campaign, channel);
      case "create-ad-group" ->
          createAdGroup(companyId, run, plan, step, campaign, channel);
      case "configure-ai-max-ad-group" ->
          configureAiMaxAdGroup(companyId, run, step, channel);
      case "configure-ai-max-url-exclusions" ->
          configureAiMaxUrlExclusions(companyId, run, step, channel);
      case "configure-ai-max-url-inclusions" ->
          configureAiMaxUrlInclusions(companyId, run, step, channel);
      case "configure-ai-max-brands" ->
          configureAiMaxBrands(companyId, run, step, channel);
      case "configure-keywords" ->
          configureKeywords(companyId, run, plan, step);
      case "configure-negative-keywords" ->
          configureNegativeKeywords(companyId, run, step, campaign, channel);
      case "create-provider-creative" ->
          createSearchAd(companyId, run, plan, step, campaign);
      case "configure-conversion-goal" ->
          configureConversionGoal(companyId, run, plan, step, campaign);
      default -> throw new BadRequestException("Unsupported Google Search launch operation: " + step.getOperationKey());
    };
  }

  private LaunchStepExecutionResult configureConversionGoal(
      Long companyId,
      LaunchRun run,
      ProviderCampaignPlan plan,
      LaunchStep step,
      ReachlyoCampaign campaign) {
    if (plan.conversionGoalMappingId() == null || plan.conversionGoalMappingId().isBlank()) {
      return LaunchStepExecutionResult.skipped(
          "No Google conversion mapping is configured for this campaign.");
    }

    ConversionGoalMapping mapping =
        conversionGoalMappingRepository
            .findByIdAndCompanyIdAndReachlyoCampaignId(
                Long.valueOf(plan.conversionGoalMappingId()),
                companyId,
                campaign.getId())
            .orElseThrow(() -> new NotFoundException("Conversion goal mapping not found"));

    var deployment = deploymentService.get(companyId, step.getProviderDeploymentId());
    if (deployment.getExternalCampaignId() == null
        || deployment.getExternalCampaignId().isBlank()) {
      throw new BadRequestException(
          "Google campaign must exist before configuring its conversion goal");
    }

    var result =
        campaignConversionGoalService.apply(
            companyId,
            step.getAdAccountId(),
            deployment.getExternalCampaignId(),
            run.getReachlyoCampaignId(),
            mapping);

    return LaunchStepExecutionResult.succeeded(
        null,
        result.customConversionGoalResource(),
        Map.of(
            "created", result.created(),
            "updated", result.updated(),
            "disabledCampaignGoals", result.disabledCampaignGoals(),
            "customConversionGoalResource", result.customConversionGoalResource()));
  }

  private LaunchStepExecutionResult createCampaign(
      Long companyId,
      LaunchRun run,
      ProviderCampaignPlan plan,
      LaunchStep step,
      ReachlyoCampaign campaign,
      CampaignChannel channel,
      AdAccount account) {
    String name = launchName(campaign.getName(), run.getId(), plan.channelId());
    if (step.getAttemptCount() > 1) {
      campaignSyncService.sync(companyId, account.getId());
    }
    AdCampaign existing =
        adCampaignRepository
            .findFirstByCompanyIdAndAdAccountIdAndProviderAndNameOrderByIdAsc(
                companyId, account.getId(), AdProvider.GOOGLE_ADS, name)
            .orElse(null);

    AdCampaignResponse result;
    if (existing != null) {
      result = AdCampaignResponse.from(existing);
    } else {
      JsonNode override = override(channel);
      Long budget = dailyBudget(plan, campaign, account, true);
      result =
          campaignMutationService.create(
              companyId,
              account.getId(),
              new CreateGoogleCampaignRequest(
                  name,
                  budget,
                  text(override, "bidStrategy"),
                  override.path("containsEuPoliticalAdvertising").booleanValue(),
                  localDate(campaign.getStartAt(), account),
                  localDate(campaign.getEndAt(), account)),
              campaignTargetingService.searchCampaignSettings(override),
              run.getRequestedByUserId());
    }

    deploymentService.bindProviderCampaign(
        companyId, step.getProviderDeploymentId(), Long.valueOf(result.id()));
    return LaunchStepExecutionResult.succeeded(
        Long.valueOf(result.id()),
        result.externalCampaignId(),
        Map.of(
            "reused", existing != null,
            "providerStatus", result.providerStatus() == null ? "PAUSED" : result.providerStatus()));
  }

  private LaunchStepExecutionResult configureTargeting(
      Long companyId,
      LaunchRun run,
      ProviderCampaignPlan plan,
      LaunchStep step,
      ReachlyoCampaign campaign,
      CampaignChannel channel) {
    AdCampaign providerCampaign = providerCampaign(companyId, step.getProviderDeploymentId());
    AudienceDefinition audience =
        audience(companyId, campaign.getId(), plan.audienceDefinitionId(), false);
    var result =
        campaignTargetingService.apply(
            companyId,
            providerCampaign.getId(),
            audience,
            override(channel));
    return LaunchStepExecutionResult.succeeded(
        null,
        result.firstExternalResource(),
        Map.of(
            "createdCount", result.createdCount(),
            "reusedCount", result.reusedCount(),
            "locationCount", result.locationCount(),
            "languageCount", result.languageCount(),
            "scheduleCount", result.scheduleCount()));
  }

  private LaunchStepExecutionResult createAdGroup(
      Long companyId,
      LaunchRun run,
      ProviderCampaignPlan plan,
      LaunchStep step,
      ReachlyoCampaign campaign,
      CampaignChannel channel) {
    AdCampaign providerCampaign = providerCampaign(companyId, step.getProviderDeploymentId());
    if (step.getAttemptCount() > 1) {
      adGroupSyncService.sync(companyId, providerCampaign.getId());
    }
    String name = launchName(campaign.getName() + " Ad Group", run.getId(), plan.channelId());
    AdSet existing =
        adSetRepository
            .findFirstByCompanyIdAndCampaignIdAndNameOrderByIdAsc(
                companyId, providerCampaign.getId(), name)
            .orElse(null);

    AdSetResponse result =
        existing != null
            ? AdSetResponse.from(existing)
            : adGroupMutationService.create(
                companyId,
                providerCampaign.getId(),
                new CreateGoogleAdGroupRequest(
                    name, longValue(override(channel), "cpcBidMinor")),
                run.getRequestedByUserId());
    return LaunchStepExecutionResult.succeeded(
        Long.valueOf(result.id()),
        result.externalAdSetId(),
        Map.of("reused", existing != null));
  }

  private LaunchStepExecutionResult configureAiMaxAdGroup(
      Long companyId,
      LaunchRun run,
      LaunchStep step,
      CampaignChannel channel) {
    AdSet adGroup = providerAdGroup(companyId, step.getProviderDeploymentId());
    JsonNode override = override(channel);
    boolean disableSearchTermMatching =
        override.path("aiMaxDisableSearchTermMatching").asBoolean(false);
    adGroupMutationService.setAiMaxSearchTermMatching(
        companyId,
        adGroup.getId(),
        disableSearchTermMatching,
        run.getRequestedByUserId());
    return LaunchStepExecutionResult.succeeded(
        adGroup.getId(),
        adGroup.getExternalAdSetId(),
        Map.of("disableSearchTermMatching", disableSearchTermMatching));
  }

  private LaunchStepExecutionResult configureAiMaxBrands(
      Long companyId,
      LaunchRun run,
      LaunchStep step,
      CampaignChannel channel) {
    AdCampaign providerCampaign =
        providerCampaign(companyId, step.getProviderDeploymentId());
    JsonNode override = override(channel);
    List<String> inclusions =
        objectIdsFromOverrideArray(override, "aiMaxBrandInclusions");
    List<String> exclusions =
        objectIdsFromOverrideArray(override, "aiMaxBrandExclusions");
    var result =
        aiMaxBrandService.apply(
            companyId,
            providerCampaign.getId(),
            inclusions,
            exclusions,
            run.getRequestedByUserId());
    return LaunchStepExecutionResult.succeeded(
        null,
        null,
        Map.of(
            "createdLists", result.createdLists(),
            "createdCriteria", result.createdCriteria()));
  }

  private LaunchStepExecutionResult configureAiMaxUrlInclusions(
      Long companyId,
      LaunchRun run,
      LaunchStep step,
      CampaignChannel channel) {
    AdCampaign providerCampaign =
        providerCampaign(companyId, step.getProviderDeploymentId());
    AdSet adGroup = providerAdGroup(companyId, step.getProviderDeploymentId());
    List<String> inclusions =
        stringsFromOverrideArray(override(channel), "aiMaxUrlInclusions");
    var result =
        aiMaxControlService.applyUrlInclusions(
            companyId,
            providerCampaign.getId(),
            Long.valueOf(adGroup.getExternalAdSetId()),
            inclusions,
            run.getRequestedByUserId());
    return LaunchStepExecutionResult.succeeded(
        adGroup.getId(),
        adGroup.getExternalAdSetId(),
        Map.of(
            "createdAssets", result.createdAssets(),
            "createdCriteria", result.createdCriteria(),
            "reusedCriteria", result.reusedCriteria()));
  }

  private LaunchStepExecutionResult configureAiMaxUrlExclusions(
      Long companyId,
      LaunchRun run,
      LaunchStep step,
      CampaignChannel channel) {
    AdCampaign providerCampaign =
        providerCampaign(companyId, step.getProviderDeploymentId());
    List<String> exclusions =
        stringsFromOverrideArray(override(channel), "aiMaxUrlExclusions");
    var result =
        aiMaxControlService.applyUrlExclusions(
            companyId,
            providerCampaign.getId(),
            exclusions,
            run.getRequestedByUserId());
    return LaunchStepExecutionResult.succeeded(
        null,
        null,
        Map.of(
            "createdCount", result.createdCount(),
            "reusedCount", result.reusedCount()));
  }

  private LaunchStepExecutionResult configureKeywords(
      Long companyId, LaunchRun run, ProviderCampaignPlan plan, LaunchStep step) {
    AdSet adGroup = providerAdGroup(companyId, step.getProviderDeploymentId());
    if (step.getAttemptCount() > 1) {
      keywordSyncService.sync(companyId, adGroup.getId());
    }
    AudienceDefinition audience =
        audience(companyId, run.getReachlyoCampaignId(), plan.audienceDefinitionId(), false);
    if (audience == null || !hasText(audience.getSearchIntentJson())) {
      return LaunchStepExecutionResult.skipped(
          "No Search intent terms were supplied; keyword creation was not requested.");
    }

    CampaignChannel channel =
        channel(companyId, run.getReachlyoCampaignId(), step.getCampaignChannelId());
    JsonNode override = override(channel);
    String matchType =
        hasText(text(override, "keywordMatchType"))
            ? text(override, "keywordMatchType").trim().toUpperCase(Locale.ROOT)
            : "BROAD";
    Long cpcBid = longValue(override, "keywordCpcBidMinor");
    List<String> terms = strings(audience.getSearchIntentJson());
    List<AdKeyword> existing =
        keywordRepository.findByCompanyIdAndAdSetIdOrderByUpdatedAtDesc(companyId, adGroup.getId());

    int created = 0;
    Long firstId = null;
    String firstExternal = null;
    for (String term : terms.stream().map(String::trim).filter(value -> !value.isBlank()).distinct().toList()) {
      AdKeyword current =
          existing.stream()
              .filter(value -> value.isAccessible() && !value.isNegative())
              .filter(value -> term.equalsIgnoreCase(value.getKeywordText()))
              .filter(value -> matchType.equalsIgnoreCase(value.getMatchType()))
              .findFirst()
              .orElse(null);
      if (current != null) {
        if (firstId == null) {
          firstId = current.getId();
          firstExternal = current.getExternalKeywordId();
        }
        continue;
      }
      var createdKeyword =
          keywordMutationService.create(
              companyId,
              adGroup.getId(),
              new CreateGoogleKeywordRequest(term, matchType, cpcBid, false),
              run.getRequestedByUserId());
      created++;
      if (firstId == null) {
        firstId = Long.valueOf(createdKeyword.id());
        firstExternal = createdKeyword.externalKeywordId();
      }
    }
    return LaunchStepExecutionResult.succeeded(
        firstId,
        firstExternal,
        Map.of("createdCount", created, "requestedCount", terms.size(), "matchType", matchType));
  }

  private LaunchStepExecutionResult configureNegativeKeywords(
      Long companyId,
      LaunchRun run,
      LaunchStep step,
      ReachlyoCampaign campaign,
      CampaignChannel channel) {
    AdCampaign providerCampaign = providerCampaign(companyId, step.getProviderDeploymentId());
    var result =
        negativeKeywordService.applyLaunchConfiguration(
            companyId,
            providerCampaign.getId(),
            override(channel),
            run.getRequestedByUserId());
    return LaunchStepExecutionResult.succeeded(
        null,
        null,
        Map.of(
            "campaignNegativeKeywordsCreated",
            result.campaignNegativeKeywordsCreated(),
            "campaignNegativeKeywordsReused",
            result.campaignNegativeKeywordsReused(),
            "sharedListsAttached",
            result.sharedListsAttached(),
            "sharedListsReused",
            result.sharedListsReused()));
  }

  private LaunchStepExecutionResult createSearchAd(
      Long companyId,
      LaunchRun run,
      ProviderCampaignPlan plan,
      LaunchStep step,
      ReachlyoCampaign campaign) {
    AdSet adGroup = providerAdGroup(companyId, step.getProviderDeploymentId());
    if (step.getAttemptCount() > 1) {
      adSyncService.sync(companyId, adGroup.getId());
    }
    CreativePack pack =
        creativePack(companyId, campaign.getId(), plan.creativePackId(), true);
    CreativeVariant variant =
        variant(companyId, pack.getId(), CreativeVariantType.GOOGLE_RSA);
    List<String> headlines =
        strings(
            variant != null && hasText(variant.getHeadlinesJson())
                ? variant.getHeadlinesJson()
                : pack.getHeadlinesJson());
    List<String> descriptions =
        strings(
            variant != null && hasText(variant.getDescriptionsJson())
                ? variant.getDescriptionsJson()
                : pack.getDescriptionsJson());
    String finalUrl =
        firstText(
            variant == null ? null : variant.getDestinationUrl(),
            pack.getDestinationUrl(),
            campaign.getLandingPageUrl());

    String name = launchName(campaign.getName() + " RSA", run.getId(), plan.channelId());
    var existing =
        adRepository
            .findFirstByCompanyIdAndAdSetIdAndNameOrderByIdAsc(companyId, adGroup.getId(), name)
            .orElse(null);
    AdResponse result =
        existing != null
            ? AdResponse.from(existing)
            : adMutationService.create(
                companyId,
                adGroup.getId(),
                new CreateGoogleResponsiveSearchAdRequest(
                    name,
                    finalUrl,
                    headlines,
                    descriptions,
                    text(override(channel(companyId, campaign.getId(), step.getCampaignChannelId())), "path1"),
                    text(override(channel(companyId, campaign.getId(), step.getCampaignChannelId())), "path2")),
                run.getRequestedByUserId());
    return LaunchStepExecutionResult.succeeded(
        Long.valueOf(result.id()), result.externalAdId(), Map.of("reused", existing != null));
  }

  private ReachlyoCampaign campaign(Long companyId, Long campaignId) {
    return campaignRepository
        .findByIdAndCompanyId(campaignId, companyId)
        .orElseThrow(() -> new NotFoundException("Reachlyo campaign not found"));
  }

  private CampaignChannel channel(Long companyId, Long campaignId, Long channelId) {
    return channelRepository
        .findByIdAndCompanyIdAndReachlyoCampaignId(channelId, companyId, campaignId)
        .orElseThrow(() -> new NotFoundException("Campaign channel not found"));
  }

  private AdAccount account(Long companyId, Long accountId) {
    return accountRepository
        .findByIdAndCompanyId(accountId, companyId)
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

  private CreativeVariant variant(Long companyId, Long packId, CreativeVariantType type) {
    return creativeVariantRepository
        .findByCompanyIdAndCreativePackIdOrderByCreatedAtAsc(companyId, packId)
        .stream()
        .filter(value -> value.getVariantType() == type)
        .findFirst()
        .orElse(null);
  }

  private AudienceDefinition audience(
      Long companyId, Long campaignId, String audienceId, boolean required) {
    if (!hasText(audienceId)) {
      if (required) throw new BadRequestException("Audience definition is required");
      return null;
    }
    return audienceDefinitionRepository
        .findByIdAndCompanyIdAndReachlyoCampaignId(
            Long.valueOf(audienceId), companyId, campaignId)
        .orElseThrow(() -> new NotFoundException("Audience definition not found"));
  }

  private AdCampaign providerCampaign(Long companyId, Long deploymentId) {
    var deployment =
        deploymentService.get(companyId, deploymentId);
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
        .findByCompanyIdAndCampaignIdOrderByProviderUpdatedAtDesc(
            companyId, providerCampaign.getId())
        .stream()
        .findFirst()
        .orElseThrow(() -> new BadRequestException("Google ad group has not been created yet"));
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
        "Google total budget requires a valid campaign schedule so Reachlyo can derive the daily budget");
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

  private String text(JsonNode node, String key) {
    JsonNode value = node.get(key);
    return value == null || value.isNull() ? null : value.asText(null);
  }

  private List<String> objectIdsFromOverrideArray(JsonNode node, String key) {
    JsonNode value = node.get(key);
    if (value == null || value.isNull() || !value.isArray()) return List.of();
    List<String> result = new java.util.ArrayList<>();
    for (JsonNode item : value) {
      String id = item.isObject() ? item.path("id").asText(null) : null;
      if (id != null && !id.isBlank()) result.add(id.trim());
    }
    return List.copyOf(result);
  }

  private List<String> stringsFromOverrideArray(JsonNode node, String key) {
    JsonNode value = node.get(key);
    if (value == null || value.isNull() || !value.isArray()) return List.of();
    List<String> result = new java.util.ArrayList<>();
    for (JsonNode item : value) {
      if (item.isTextual() && !item.asText().isBlank()) {
        result.add(item.asText().trim());
      }
    }
    return List.copyOf(result);
  }

  private Long longValue(JsonNode node, String key) {
    JsonNode value = node.get(key);
    return value == null || value.isNull() || !value.canConvertToLong() ? null : value.longValue();
  }

  private boolean isHttpUrl(String value) {
    try {
      java.net.URI uri = java.net.URI.create(value.trim());
      return "http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme());
    } catch (RuntimeException exception) {
      return false;
    }
  }

  private String firstText(String... values) {
    for (String value : values) if (hasText(value)) return value.trim();
    return null;
  }

  private String launchName(String base, Long runId, String channelId) {
    String suffix = " [RLY-" + runId + "-" + channelId + "]";
    String normalized = hasText(base) ? base.trim() : "Reachlyo";
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
