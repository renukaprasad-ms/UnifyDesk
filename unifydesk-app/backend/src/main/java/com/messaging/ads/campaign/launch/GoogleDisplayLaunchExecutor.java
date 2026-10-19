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
import com.messaging.ads.google.GoogleAdsCampaignTargetingService;
import com.messaging.ads.google.GoogleAdsDisplayCampaignService;
import com.messaging.ads.google.GoogleAdsVisualAdService;
import com.messaging.ads.google.GoogleAdsVisualAudienceService;
import com.messaging.ads.google.dto.CreateGoogleDisplayCampaignRequest;
import com.messaging.ads.google.dto.CreateGoogleResponsiveDisplayAdRequest;
import com.messaging.ads.repository.AdAccountRepository;
import com.messaging.ads.repository.AdCampaignRepository;
import com.messaging.ads.repository.AdRepository;
import com.messaging.ads.repository.AdSetRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import com.messaging.media.enums.MediaType;
import com.messaging.media.service.MediaService;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GoogleDisplayLaunchExecutor implements LaunchProviderExecutor {

  private static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;

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
  private final GoogleAdsDisplayCampaignService displayCampaignService;
  private final GoogleAdsVisualAdService visualAdService;
  private final GoogleAdsVisualAudienceService visualAudienceService;
  private final GoogleAdsCampaignTargetingService campaignTargetingService;
  private final GoogleAdsCampaignConversionGoalService conversionGoalService;
  private final ProviderDeploymentService deploymentService;
  private final MediaService mediaService;
  private final ObjectMapper objectMapper;

  @Override
  public Set<CampaignChannelType> supportedChannels() {
    return Set.of(CampaignChannelType.GOOGLE_DISPLAY);
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
              "Display requires an explicit EU political advertising declaration."));
    }

    String bidStrategy =
        firstText(text(override, "bidStrategy"), defaultBidStrategy(campaign.getBusinessGoal()));
    if (!Set.of("MANUAL_CPC", "MAXIMIZE_CONVERSIONS", "TARGET_CPA").contains(bidStrategy)) {
      issues.add(
          error(
              "DISPLAY_BID_STRATEGY_INVALID",
              "providerOverride.bidStrategy",
              "Display bid strategy must be Manual CPC, Maximize Conversions, or Target CPA."));
    }
    if ("TARGET_CPA".equals(bidStrategy) && positiveLong(override, "targetCpaMinor") == null) {
      issues.add(
          error(
              "DISPLAY_TARGET_CPA_REQUIRED",
              "providerOverride.targetCpaMinor",
              "Display Target CPA requires a positive target CPA."));
    }
    if ("MANUAL_CPC".equals(bidStrategy) && positiveLong(override, "adGroupCpcBidMinor") == null) {
      issues.add(
          warning(
              "DISPLAY_CPC_BID_DERIVED",
              "providerOverride.adGroupCpcBidMinor",
              "No Display CPC bid was supplied. UnifyDesk will derive a conservative ad-group CPC from the daily budget."));
    }

    if (dailyBudget(plan, campaign, account, false) == null) {
      issues.add(
          error(
              "GOOGLE_DAILY_BUDGET_UNRESOLVED",
              "budget",
              "Display needs a daily budget, or a total budget with a valid start/end schedule."));
    }

    CreativePack pack = creativePack(companyId, campaign.getId(), plan.creativePackId(), false);
    if (pack == null) {
      issues.add(
          error(
              "DISPLAY_CREATIVE_PACK_REQUIRED",
              "creativePack",
              "Display requires a Creative Pack."));
    } else {
      List<String> headlines = strings(pack.getHeadlinesJson());
      List<String> descriptions = strings(pack.getDescriptionsJson());
      if (headlines.isEmpty()
          || headlines.size() > 5
          || headlines.stream().anyMatch(value -> value.length() > 30)) {
        issues.add(
            error(
                "DISPLAY_HEADLINES_INVALID",
                "creativePack.headlines",
                "Display requires 1 to 5 headlines, each at most 30 characters."));
      }
      if (descriptions.isEmpty()
          || descriptions.size() > 5
          || descriptions.stream().anyMatch(value -> value.length() > 90)) {
        issues.add(
            error(
                "DISPLAY_DESCRIPTIONS_INVALID",
                "creativePack.descriptions",
                "Display requires 1 to 5 descriptions, each at most 90 characters."));
      }
      if (!hasText(pack.getBusinessName()) || pack.getBusinessName().trim().length() > 25) {
        issues.add(
            error(
                "DISPLAY_BUSINESS_NAME_INVALID",
                "creativePack.businessName",
                "Display requires a business name of at most 25 characters."));
      }
      String longHeadline = displayLongHeadline(pack, headlines, false);
      if (!hasText(longHeadline) || longHeadline.length() > 90) {
        issues.add(
            error(
                "DISPLAY_LONG_HEADLINE_INVALID",
                "creativePack.primaryMessage",
                "Display requires a long headline of at most 90 characters."));
      }
      if (!hasText(firstText(pack.getDestinationUrl(), campaign.getLandingPageUrl()))) {
        issues.add(
            error(
                "DISPLAY_FINAL_URL_REQUIRED",
                "creativePack.destinationUrl",
                "Display requires a destination URL."));
      }

      validateImageAsset(
          companyId,
          pack.getId(),
          CreativeAssetRole.MARKETING_IMAGE,
          "Landscape marketing image",
          600,
          314,
          1.91d,
          0.04d,
          true,
          issues);
      validateImageAsset(
          companyId,
          pack.getId(),
          CreativeAssetRole.SQUARE_MARKETING_IMAGE,
          "Square marketing image",
          300,
          300,
          1.0d,
          0.02d,
          true,
          issues);
      validateImageAsset(
          companyId,
          pack.getId(),
          CreativeAssetRole.LOGO,
          "Square logo",
          128,
          128,
          1.0d,
          0.02d,
          false,
          issues);
    }

    try {
      visualAudienceService.validateUserLists(
          companyId,
          account.getId(),
          longsFromOverride(override, "googleAudienceIds"),
          longsFromOverride(override, "googleExcludedAudienceIds"));
    } catch (RuntimeException exception) {
      issues.add(
          error(
              "DISPLAY_PROVIDER_AUDIENCE_INVALID",
              "providerOverride.googleAudienceIds",
              exception.getMessage()));
    }

    AudienceDefinition audience =
        audience(companyId, campaign.getId(), plan.audienceDefinitionId(), false);
    if (audience != null) {
      try {
        campaignTargetingService.resolve(
            companyId, account.getId(), geoLanguageOnly(audience), objectMapper.createObjectNode());
      } catch (RuntimeException exception) {
        issues.add(error("DISPLAY_GEO_LANGUAGE_INVALID", "audience", exception.getMessage()));
      }
      if (audience.getAgeMin() != null
          || audience.getAgeMax() != null
          || hasText(audience.getGendersJson())
          || hasText(audience.getInterestsJson())
          || hasText(audience.getSearchIntentJson())
          || hasText(audience.getRetargetingJson())
          || hasText(audience.getCustomerSegmentsJson())) {
        issues.add(
            warning(
                "DISPLAY_AUDIENCE_PARTIAL",
                "audience",
                "Unified Display maps geo/language plus selected provider-backed Google user lists. Canonical demographics, free-text interests, search intent and unbound canonical retargeting/customer-segment fields remain approximate."));
      }
    }

    if (hasText(plan.conversionGoalMappingId())) {
      try {
        conversionGoalService.validate(
            companyId,
            account.getId(),
            conversion(companyId, campaign.getId(), plan.conversionGoalMappingId()));
      } catch (RuntimeException exception) {
        issues.add(
            error("GOOGLE_CONVERSION_MAPPING_INVALID", "conversion", exception.getMessage()));
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
      case "create-display-paused" -> createDisplay(companyId, run, plan, step, campaign, channel);
      case "configure-display-targeting" ->
          configureTargeting(companyId, plan, step, campaign, channel);
      case "create-responsive-display-ad" -> createCreative(companyId, run, plan, step, campaign);
      case "configure-conversion-goal" -> configureConversion(companyId, run, plan, step, campaign);
      default ->
          throw new BadRequestException(
              "Unsupported Display launch operation: " + step.getOperationKey());
    };
  }

  private LaunchStepExecutionResult createDisplay(
      Long companyId,
      LaunchRun run,
      ProviderCampaignPlan plan,
      LaunchStep step,
      ReachlyoCampaign campaign,
      CampaignChannel channel) {
    AdAccount account = account(companyId, step.getAdAccountId());
    String name = launchName(campaign.getName(), run.getId(), plan.channelId());
    String adGroupName =
        launchName(campaign.getName() + " Display Audience", run.getId(), plan.channelId());

    if (step.getAttemptCount() > 1) {
      campaignSyncService.sync(companyId, account.getId());
    }

    AdCampaign existing =
        adCampaignRepository
            .findFirstByCompanyIdAndAdAccountIdAndProviderAndNameOrderByIdAsc(
                companyId, account.getId(), AdProvider.GOOGLE_ADS, name)
            .filter(value -> "DISPLAY".equalsIgnoreCase(value.getObjective()))
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
              .orElseThrow(
                  () ->
                      new BadRequestException(
                          "Display campaign exists but its launch ad group could not be resolved"));
    } else {
      JsonNode override = override(channel);
      String bidStrategy =
          firstText(text(override, "bidStrategy"), defaultBidStrategy(campaign.getBusinessGoal()));
      var result =
          displayCampaignService.create(
              companyId,
              account.getId(),
              new CreateGoogleDisplayCampaignRequest(
                  name,
                  dailyBudget(plan, campaign, account, true),
                  adGroupName,
                  bidStrategy,
                  positiveLong(override, "targetCpaMinor"),
                  "MANUAL_CPC".equals(bidStrategy)
                      ? firstPositive(
                          positiveLong(override, "adGroupCpcBidMinor"),
                          Math.max(1L, dailyBudget(plan, campaign, account, true) / 20L))
                      : null,
                  override.path("containsEuPoliticalAdvertising").booleanValue(),
                  localDate(campaign.getStartAt(), account),
                  localDate(campaign.getEndAt(), account)),
              run.getRequestedByUserId());
      localCampaign =
          adCampaignRepository
              .findByIdAndCompanyId(Long.valueOf(result.campaign().id()), companyId)
              .orElseThrow(() -> new NotFoundException("Created Display campaign not found"));
      localAdGroup =
          adSetRepository
              .findByIdAndCompanyId(Long.valueOf(result.adGroup().id()), companyId)
              .orElseThrow(() -> new NotFoundException("Created Display ad group not found"));
    }

    deploymentService.bindProviderCampaign(
        companyId, step.getProviderDeploymentId(), localCampaign.getId());

    return LaunchStepExecutionResult.succeeded(
        localCampaign.getId(),
        localCampaign.getExternalCampaignId(),
        Map.of(
            "reused",
            existing != null,
            "adGroupId",
            localAdGroup.getId(),
            "externalAdGroupId",
            localAdGroup.getExternalAdSetId()));
  }

  private LaunchStepExecutionResult configureTargeting(
      Long companyId,
      ProviderCampaignPlan plan,
      LaunchStep step,
      ReachlyoCampaign campaign,
      CampaignChannel channel) {
    AudienceDefinition audience =
        audience(companyId, campaign.getId(), plan.audienceDefinitionId(), false);
    JsonNode override = override(channel);
    List<Long> includedAudienceIds = longsFromOverride(override, "googleAudienceIds");
    List<Long> excludedAudienceIds = longsFromOverride(override, "googleExcludedAudienceIds");

    if (audience == null && includedAudienceIds.isEmpty() && excludedAudienceIds.isEmpty()) {
      return LaunchStepExecutionResult.skipped(
          "No canonical or provider-backed Display audience was selected.");
    }

    AdCampaign providerCampaign = providerCampaign(companyId, step.getProviderDeploymentId());

    var geoResult =
        audience == null
            ? null
            : campaignTargetingService.apply(
                companyId,
                providerCampaign.getId(),
                geoLanguageOnly(audience),
                objectMapper.createObjectNode());

    AdSet adGroup =
        providerAdGroup(
            companyId,
            step.getProviderDeploymentId(),
            launchName(
                campaign.getName() + " Display Audience", step.getLaunchRunId(), plan.channelId()));

    var audienceResult =
        visualAudienceService.applyDisplayUserLists(
            companyId, adGroup.getId(), includedAudienceIds, excludedAudienceIds);

    return LaunchStepExecutionResult.succeeded(
        adGroup.getId(),
        adGroup.getExternalAdSetId(),
        Map.of(
            "geoCreatedCount", geoResult == null ? 0 : geoResult.createdCount(),
            "geoReusedCount", geoResult == null ? 0 : geoResult.reusedCount(),
            "locationCount", geoResult == null ? 0 : geoResult.locationCount(),
            "languageCount", geoResult == null ? 0 : geoResult.languageCount(),
            "audienceCriteriaCreated", audienceResult.createdCount(),
            "audienceCriteriaReused", audienceResult.reusedCount(),
            "audienceCriteriaRequested", audienceResult.requestedCount()));
  }

  private LaunchStepExecutionResult createCreative(
      Long companyId,
      LaunchRun run,
      ProviderCampaignPlan plan,
      LaunchStep step,
      ReachlyoCampaign campaign) {
    AdSet adGroup =
        providerAdGroup(
            companyId,
            step.getProviderDeploymentId(),
            launchName(campaign.getName() + " Display Audience", run.getId(), plan.channelId()));
    String adName = launchName(campaign.getName() + " Display Ad", run.getId(), plan.channelId());

    if (step.getAttemptCount() > 1) {
      adSyncService.sync(companyId, adGroup.getId());
    }

    Ad existing =
        adRepository
            .findFirstByCompanyIdAndAdSetIdAndNameOrderByIdAsc(companyId, adGroup.getId(), adName)
            .orElse(null);
    if (existing != null) {
      return LaunchStepExecutionResult.succeeded(
          existing.getId(), existing.getExternalAdId(), Map.of("reused", true));
    }

    CreativePack pack = creativePack(companyId, campaign.getId(), plan.creativePackId(), true);
    List<String> headlines = strings(pack.getHeadlinesJson());

    AdResponse created =
        visualAdService.createResponsiveDisplay(
            companyId,
            adGroup.getId(),
            new CreateGoogleResponsiveDisplayAdRequest(
                adName,
                firstText(pack.getDestinationUrl(), campaign.getLandingPageUrl()),
                pack.getBusinessName(),
                headlines,
                displayLongHeadline(pack, headlines, true),
                strings(pack.getDescriptionsJson()),
                imageBase64(companyId, pack.getId(), CreativeAssetRole.MARKETING_IMAGE, true),
                imageBase64(
                    companyId, pack.getId(), CreativeAssetRole.SQUARE_MARKETING_IMAGE, true),
                imageBase64(companyId, pack.getId(), CreativeAssetRole.LOGO, false)),
            run.getRequestedByUserId());

    return LaunchStepExecutionResult.succeeded(
        Long.valueOf(created.id()),
        created.externalAdId(),
        Map.of("reused", false, "creativeType", "RESPONSIVE_DISPLAY_AD"));
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
            "created",
            result.created(),
            "updated",
            result.updated(),
            "disabledCampaignGoals",
            result.disabledCampaignGoals()));
  }

  private void validateImageAsset(
      Long companyId,
      Long packId,
      CreativeAssetRole role,
      String label,
      int minWidth,
      int minHeight,
      double ratio,
      double tolerance,
      boolean required,
      List<ProviderPlanIssue> issues) {
    CreativePackAsset asset = asset(companyId, packId, role, false);
    if (asset == null) {
      if (required) {
        issues.add(
            error(
                "DISPLAY_REQUIRED_ASSET_MISSING", "creativePack.assets", label + " is required."));
      }
      return;
    }

    try {
      var media = mediaService.requireActiveCompanyMedia(asset.getMediaId(), companyId);
      if (media.getMediaType() != MediaType.IMAGE) {
        issues.add(
            error(
                "DISPLAY_IMAGE_INVALID",
                "creativePack.assets",
                label + " must reference image media."));
        return;
      }

      byte[] bytes = mediaService.readTrustedObject(asset.getMediaId(), companyId);
      if (bytes.length == 0 || bytes.length > MAX_IMAGE_BYTES) {
        issues.add(
            error(
                "DISPLAY_IMAGE_SIZE_INVALID",
                "creativePack.assets",
                label + " must be non-empty and at most 5 MB."));
        return;
      }
      BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
      if (image == null) {
        issues.add(
            error(
                "DISPLAY_IMAGE_FORMAT_INVALID",
                "creativePack.assets",
                label + " must be PNG, JPEG, or GIF."));
        return;
      }
      if (image.getWidth() < minWidth || image.getHeight() < minHeight) {
        issues.add(
            error(
                "DISPLAY_IMAGE_DIMENSIONS_INVALID",
                "creativePack.assets",
                label + " must be at least " + minWidth + "x" + minHeight + " pixels."));
        return;
      }
      double actual = (double) image.getWidth() / (double) image.getHeight();
      if (Math.abs(actual - ratio) > tolerance) {
        issues.add(
            error(
                "DISPLAY_IMAGE_RATIO_INVALID",
                "creativePack.assets",
                label + " has an invalid aspect ratio."));
      }
    } catch (RuntimeException exception) {
      issues.add(error("DISPLAY_IMAGE_INVALID", "creativePack.assets", exception.getMessage()));
    } catch (Exception exception) {
      issues.add(
          error(
              "DISPLAY_IMAGE_INVALID",
              "creativePack.assets",
              "Unable to read " + label.toLowerCase() + "."));
    }
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
      throw new BadRequestException("Display requires " + role.name() + " media");
    }
    return found;
  }

  private String displayLongHeadline(CreativePack pack, List<String> headlines, boolean required) {
    String primary = pack.getPrimaryMessage();
    String value =
        hasText(primary) && primary.trim().length() <= 90
            ? primary.trim()
            : headlines.isEmpty() ? null : headlines.getFirst();
    if (!hasText(value)) {
      if (required) {
        throw new BadRequestException("Display long headline is required");
      }
      return null;
    }
    return value.trim();
  }

  private AudienceDefinition geoLanguageOnly(AudienceDefinition source) {
    AudienceDefinition copy = new AudienceDefinition();
    copy.setLocationsJson(source.getLocationsJson());
    copy.setLanguagesJson(source.getLanguagesJson());
    return copy;
  }

  private String defaultBidStrategy(CampaignBusinessGoal goal) {
    return switch (goal) {
      case PURCHASE, LEAD, QUALIFIED_LEAD, SIGN_UP, PHONE_CALL, WHATSAPP_CONVERSATION ->
          "MAXIMIZE_CONVERSIONS";
      default -> "MANUAL_CPC";
    };
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
      if (required) {
        throw new BadRequestException("Creative Pack is required");
      }
      return null;
    }
    return creativePackRepository
        .findByIdAndCompanyIdAndReachlyoCampaignId(Long.valueOf(packId), companyId, campaignId)
        .orElseThrow(() -> new NotFoundException("Creative pack not found"));
  }

  private AudienceDefinition audience(
      Long companyId, Long campaignId, String audienceId, boolean required) {
    if (!hasText(audienceId)) {
      if (required) {
        throw new BadRequestException("Audience definition is required");
      }
      return null;
    }
    return audienceDefinitionRepository
        .findByIdAndCompanyIdAndReachlyoCampaignId(Long.valueOf(audienceId), companyId, campaignId)
        .orElseThrow(() -> new NotFoundException("Audience definition not found"));
  }

  private ConversionGoalMapping conversion(Long companyId, Long campaignId, String mappingId) {
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

  private AdSet providerAdGroup(Long companyId, Long deploymentId, String expectedName) {
    AdCampaign providerCampaign = providerCampaign(companyId, deploymentId);
    return adSetRepository
        .findFirstByCompanyIdAndCampaignIdAndNameOrderByIdAsc(
            companyId, providerCampaign.getId(), expectedName)
        .orElseThrow(
            () -> new BadRequestException("Display launch ad group has not been created yet"));
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
      ProviderCampaignPlan plan, ReachlyoCampaign campaign, AdAccount account, boolean required) {
    Long budget = plan.effectiveBudgetMinor();
    if (budget == null || budget <= 0) {
      return required ? failBudget() : null;
    }
    if (plan.budgetPeriod() == BudgetPeriod.DAILY) return budget;

    LocalDate start = localDate(campaign.getStartAt(), account);
    LocalDate end = localDate(campaign.getEndAt(), account);
    if (start == null || end == null || end.isBefore(start)) {
      return required ? failBudget() : null;
    }
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

  private List<Long> longsFromOverride(JsonNode node, String key) {
    JsonNode value = node.get(key);
    if (value == null || value.isNull() || !value.isArray()) return List.of();
    List<Long> result = new ArrayList<>();
    for (JsonNode item : value) {
      if (item.canConvertToLong()) {
        result.add(item.longValue());
      } else if (item.isTextual() && item.asText().matches("\\d+")) {
        result.add(Long.valueOf(item.asText()));
      }
    }
    return result.stream().distinct().toList();
  }

  private List<String> strings(String json) {
    if (!hasText(json)) return List.of();
    try {
      return objectMapper.readValue(json, new TypeReference<List<String>>() {});
    } catch (Exception exception) {
      throw new BadRequestException("Creative/audience text list is invalid JSON");
    }
  }

  private Long firstPositive(Long preferred, Long fallback) {
    return preferred != null && preferred > 0 ? preferred : fallback;
  }

  private Long positiveLong(JsonNode node, String key) {
    JsonNode value = node.get(key);
    if (value == null || value.isNull() || !value.canConvertToLong()) {
      return null;
    }
    long parsed = value.longValue();
    return parsed > 0 ? parsed : null;
  }

  private String text(JsonNode node, String key) {
    JsonNode value = node.get(key);
    if (value == null || value.isNull()) return null;
    String result = value.asText(null);
    return result == null || result.isBlank() ? null : result.trim();
  }

  private String firstText(String... values) {
    for (String value : values) {
      if (hasText(value)) return value.trim();
    }
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
