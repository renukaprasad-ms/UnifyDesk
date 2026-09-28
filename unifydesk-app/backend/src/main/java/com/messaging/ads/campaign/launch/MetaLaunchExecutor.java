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
import com.messaging.ads.campaign.entity.CreativeVariant;
import com.messaging.ads.campaign.entity.ReachlyoCampaign;
import com.messaging.ads.campaign.enums.BudgetPeriod;
import com.messaging.ads.campaign.enums.CampaignChannelType;
import com.messaging.ads.campaign.enums.ConversionTargetType;
import com.messaging.ads.campaign.enums.CreativeAssetRole;
import com.messaging.ads.campaign.repository.AudienceDefinitionRepository;
import com.messaging.ads.campaign.repository.CampaignChannelRepository;
import com.messaging.ads.campaign.repository.ConversionGoalMappingRepository;
import com.messaging.ads.campaign.repository.CreativePackAssetRepository;
import com.messaging.ads.campaign.repository.CreativePackRepository;
import com.messaging.ads.campaign.repository.CreativeVariantRepository;
import com.messaging.ads.campaign.repository.ReachlyoCampaignRepository;
import com.messaging.ads.campaign.service.ProviderDeploymentService;
import com.messaging.ads.dto.AdCampaignResponse;
import com.messaging.ads.dto.AdCreativeResponse;
import com.messaging.ads.dto.AdResponse;
import com.messaging.ads.dto.AdSetResponse;
import com.messaging.ads.entity.AdCampaign;
import com.messaging.ads.entity.AdCreative;
import com.messaging.ads.entity.AdSet;
import com.messaging.ads.enums.AdAccountResourceType;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.meta.MetaAdsAdMutationService;
import com.messaging.ads.meta.MetaAdsAdSyncService;
import com.messaging.ads.meta.MetaAdsAdSetMutationService;
import com.messaging.ads.meta.MetaAdsAdSetSyncService;
import com.messaging.ads.meta.MetaAdsCampaignMutationService;
import com.messaging.ads.meta.MetaAdsCampaignSyncService;
import com.messaging.ads.meta.MetaAdsCreativeMutationService;
import com.messaging.ads.meta.MetaAdsCreativeSyncService;
import com.messaging.ads.meta.MetaAdsGeoTargetingService;
import com.messaging.ads.meta.MetaStandardConversionEvent;
import com.messaging.ads.meta.dto.CreateMetaAdRequest;
import com.messaging.ads.meta.dto.CreateMetaCatalogAdRequest;
import com.messaging.ads.meta.dto.CreateMetaAdSetRequest;
import com.messaging.ads.meta.dto.CreateMetaCampaignRequest;
import com.messaging.ads.meta.dto.CreateMetaCreativeRequest;
import com.messaging.ads.meta.dto.CreateMetaPlacementCreativeRequest;
import com.messaging.ads.repository.AdAccountResourceRepository;
import com.messaging.ads.repository.AdCampaignRepository;
import com.messaging.ads.repository.AdCreativeRepository;
import com.messaging.ads.repository.AdRepository;
import com.messaging.ads.repository.AdSetRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import com.messaging.media.entity.Media;
import com.messaging.media.enums.MediaPurpose;
import com.messaging.media.enums.MediaStatus;
import com.messaging.media.enums.MediaType;
import com.messaging.media.service.MediaService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MetaLaunchExecutor implements LaunchProviderExecutor {

  private final ReachlyoCampaignRepository campaignRepository;
  private final CampaignChannelRepository channelRepository;
  private final CreativePackRepository creativePackRepository;
  private final CreativePackAssetRepository creativePackAssetRepository;
  private final CreativeVariantRepository creativeVariantRepository;
  private final AudienceDefinitionRepository audienceDefinitionRepository;
  private final ConversionGoalMappingRepository conversionGoalMappingRepository;
  private final AdAccountResourceRepository accountResourceRepository;
  private final AdCampaignRepository adCampaignRepository;
  private final AdSetRepository adSetRepository;
  private final AdCreativeRepository adCreativeRepository;
  private final AdRepository adRepository;
  private final MetaAdsCampaignMutationService campaignMutationService;
  private final MetaAdsCampaignSyncService campaignSyncService;
  private final MetaAdsAdSetMutationService adSetMutationService;
  private final MetaAdsAdSetSyncService adSetSyncService;
  private final MetaAdsCreativeMutationService creativeMutationService;
  private final MetaAdsCreativeSyncService creativeSyncService;
  private final MetaAdsAdMutationService adMutationService;
  private final MetaAdsAdSyncService adSyncService;
  private final MetaAdsGeoTargetingService geoTargetingService;
  private final ProviderDeploymentService deploymentService;
  private final MediaService mediaService;
  private final ObjectMapper objectMapper;

  @Override
  public Set<CampaignChannelType> supportedChannels() {
    return Set.of(CampaignChannelType.META_ADS);
  }

  @Override
  public List<ProviderPlanIssue> preflight(
      Long companyId, LaunchRun run, ProviderCampaignPlan plan) {
    List<ProviderPlanIssue> issues = new ArrayList<>();
    ReachlyoCampaign campaign = campaign(companyId, run.getReachlyoCampaignId());
    CampaignChannel channel = channel(companyId, campaign.getId(), Long.valueOf(plan.channelId()));
    JsonNode override = override(channel);

    requireTextIssue(
        issues,
        override,
        "pageId",
        "META_PAGE_REQUIRED",
        "providerOverride.pageId",
        "Meta launch requires a Facebook Page identity.");

    String pageId = text(override, "pageId");
    if (hasText(pageId)) {
      var page =
          accountResourceRepository
              .findByCompanyIdAndAdAccountIdAndResourceTypeAndExternalResourceId(
                  companyId,
                  Long.valueOf(plan.adAccountId()),
                  AdAccountResourceType.FACEBOOK_PAGE,
                  pageId.trim())
              .orElse(null);
      if (page == null || !page.isAccessible()) {
        issues.add(
            error(
                "META_PAGE_UNAVAILABLE",
                "providerOverride.pageId",
                "Selected Facebook Page is not available for this Meta ad account."));
      }
    }
    String instagramActorId = text(override, "instagramActorId");
    if (hasText(instagramActorId)) {
      var instagram =
          accountResourceRepository
              .findByCompanyIdAndAdAccountIdAndResourceTypeAndExternalResourceId(
                  companyId,
                  Long.valueOf(plan.adAccountId()),
                  AdAccountResourceType.INSTAGRAM_ACCOUNT,
                  instagramActorId.trim())
              .orElse(null);
      if (instagram == null || !instagram.isAccessible()) {
        issues.add(
            error(
                "META_INSTAGRAM_UNAVAILABLE",
                "providerOverride.instagramActorId",
                "Selected Instagram account is not available for this Meta ad account."));
      }
    }
    String productSetId = text(override, "productSetId");
    if (hasText(productSetId)) {
      var productSet =
          accountResourceRepository
              .findByCompanyIdAndAdAccountIdAndResourceTypeAndExternalResourceId(
                  companyId,
                  Long.valueOf(plan.adAccountId()),
                  AdAccountResourceType.META_PRODUCT_SET,
                  productSetId.trim())
              .orElse(null);
      if (productSet == null || !productSet.isAccessible()) {
        issues.add(
            error(
                "META_PRODUCT_SET_UNAVAILABLE",
                "providerOverride.productSetId",
                "Selected Meta product set is not synchronized or accessible for this ad account."));
      }
    }
    if (booleanValue(override, "backgroundGeneration", false) && !hasText(productSetId)) {
      issues.add(
          error(
              "META_BACKGROUND_GENERATION_PRODUCT_SET_REQUIRED",
              "providerOverride.productSetId",
              "Meta background generation requires a synchronized product set."));
    }

    requireTextIssue(
        issues,
        override,
        "optimizationGoal",
        "META_OPTIMIZATION_GOAL_REQUIRED",
        "providerOverride.optimizationGoal",
        "Meta launch requires an explicit optimizationGoal.");
    requireTextIssue(
        issues,
        override,
        "billingEvent",
        "META_BILLING_EVENT_REQUIRED",
        "providerOverride.billingEvent",
        "Meta launch requires an explicit billingEvent.");

    boolean advantageAudience = booleanValue(override, "advantageAudience", false);
    boolean advantageAgeSuggestion =
        booleanValue(override, "advantageAgeSuggestion", false);
    boolean advantageGenderSuggestion =
        booleanValue(override, "advantageGenderSuggestion", false);
    if (!advantageAudience && (advantageAgeSuggestion || advantageGenderSuggestion)) {
      issues.add(
          error(
              "META_ADVANTAGE_AUDIENCE_INVALID",
              "providerOverride.advantageAudience",
              "Advantage+ age/gender suggestions require Advantage+ audience to be enabled."));
    }

    List<String> specialCategories = stringArray(override, "specialAdCategories");
    if (specialCategories.isEmpty()) {
      issues.add(
          error(
              "META_SPECIAL_AD_CATEGORY_REQUIRED",
              "providerOverride.specialAdCategories",
              "Meta launch requires an explicit specialAdCategories selection; use [\"NONE\"] when none apply."));
    } else if (specialCategories.stream().anyMatch(value -> "NONE".equalsIgnoreCase(value))
        && specialCategories.size() > 1) {
      issues.add(
          error(
              "META_SPECIAL_AD_CATEGORY_INVALID",
              "providerOverride.specialAdCategories",
              "NONE cannot be combined with another Meta special ad category."));
    }

    String buyingType = text(override, "buyingType");
    if (hasText(buyingType)
        && !Set.of("AUCTION", "RESERVED").contains(buyingType.trim().toUpperCase(Locale.ROOT))) {
      issues.add(error("META_BUYING_TYPE_INVALID", "providerOverride.buyingType", "Meta buyingType must be AUCTION or RESERVED."));
    }
    String objectiveOverride = text(override, "objective");
    if (hasText(objectiveOverride)
        && !Set.of(
                "OUTCOME_AWARENESS",
                "OUTCOME_TRAFFIC",
                "OUTCOME_ENGAGEMENT",
                "OUTCOME_LEADS",
                "OUTCOME_APP_PROMOTION",
                "OUTCOME_SALES")
            .contains(objectiveOverride.trim().toUpperCase(Locale.ROOT))) {
      issues.add(error("META_OBJECTIVE_INVALID", "providerOverride.objective", "Unsupported Meta campaign objective."));
    }

    if (plan.effectiveBudgetMinor() == null || plan.effectiveBudgetMinor() <= 0) {
      issues.add(error("META_BUDGET_MISSING", "budget", "Meta launch requires a positive budget."));
    }
    if (plan.budgetPeriod() == BudgetPeriod.TOTAL
        && (campaign.getStartAt() == null || campaign.getEndAt() == null)) {
      issues.add(
          error(
              "META_LIFETIME_SCHEDULE_REQUIRED",
              "schedule",
              "Meta lifetime budget requires both campaign start and end times."));
    }

    String existingPostId = text(override, "existingPostId");
    CreativePack pack = creativePack(companyId, campaign.getId(), plan.creativePackId(), false);
    boolean catalogAd = hasText(productSetId);
    boolean existingPost = hasText(existingPostId);
    if (!existingPost && pack == null) {
      issues.add(error("CREATIVE_PACK_MISSING", "creativePack", "Meta launch requires a Creative Pack unless an existing post is selected."));
    } else if (!existingPost) {
      if (!catalogAd) {
        CreativePackAsset image = imageAsset(companyId, pack.getId());
        if (image == null) {
          issues.add(
              error(
                  "META_IMAGE_MISSING",
                  "creativePack.assets",
                  "The baseline Meta executor requires at least one IMAGE creative asset."));
        } else {
          Media media = mediaService.requireActiveCompanyMedia(image.getMediaId(), companyId);
          if (media.getStatus() != MediaStatus.ACTIVE
              || media.getPurpose() != MediaPurpose.AD_CREATIVE
              || media.getMediaType() != MediaType.IMAGE) {
            issues.add(
                error(
                    "META_IMAGE_INVALID",
                    "creativePack.assets",
                    "Meta launch image must be active AD_CREATIVE image media."));
          }
        }
      }
      if (!hasText(destination(pack, campaign))) {
        issues.add(
            error(
                "META_DESTINATION_MISSING",
                "creativePack.destinationUrl",
                "Meta launch requires a destination URL."));
      }
    }

    AudienceDefinition audience =
        audience(companyId, campaign.getId(), plan.audienceDefinitionId(), false);
    if (audience == null) {
      issues.add(
          error(
              "META_AUDIENCE_REQUIRED",
              "audience",
              "The current Meta executor requires a canonical audience with at least one country."));
    } else {
      List<AudienceLocationInput> locations = locations(audience);
      if (locations.stream().noneMatch(location -> hasText(location.countryCode()))) {
        issues.add(
            error(
                "META_COUNTRY_REQUIRED",
                "audience.locations",
                "Meta launch requires at least one country in the canonical audience."));
      }
      if (audience.getAgeMin() != null && audience.getAgeMin() > 65) {
        issues.add(
            error(
                "META_AGE_MIN_INVALID",
                "audience.ageMin",
                "Meta audience ageMin cannot exceed 65 in the current executor."));
      }
      if (audience.getAgeMax() != null && audience.getAgeMax() > 65) {
        issues.add(
            warning(
                "META_AGE_MAX_CLAMPED",
                "audience.ageMax",
                "Meta represents the upper age bucket as 65+; Reachlyo will send ageMax=65."));
      }
      try {
        geoTargetingService.resolveCanonical(
            companyId, Long.valueOf(plan.adAccountId()), locations);
      } catch (BadRequestException exception) {
        issues.add(
            error(
                "META_GEO_TARGETING_INVALID",
                "audience.locations",
                exception.getMessage()));
      }
      boolean interestsUnmapped =
          hasText(audience.getInterestsJson()) && metaInterests(override).isEmpty();
      if (interestsUnmapped
          || hasText(audience.getRetargetingJson())
          || hasText(audience.getCustomerSegmentsJson())) {
        issues.add(
            warning(
                "META_AUDIENCE_DETAIL_NOT_EXECUTED",
                "audience",
                interestsUnmapped
                    ? "Canonical interests need provider-backed Meta interest selections before launch; retargeting/customer-segment mappings remain separate provider controls."
                    : "Retargeting and customer-segment mappings are not yet executed in the baseline Meta launcher."));
      }
    }

    ConversionGoalMapping conversion = conversion(companyId, campaign.getId(), plan.conversionGoalMappingId());
    if (conversion != null
        && conversion.getTargetType() == ConversionTargetType.STANDARD_EVENT) {
      if (!MetaStandardConversionEvent.supports(conversion.getProviderEventName())) {
        issues.add(
            error(
                "META_STANDARD_EVENT_INVALID",
                "conversion.providerEventName",
                "The stored Meta standard conversion event is no longer supported by the current provider adapter."));
      }
      String pixelId = text(override, "pixelId");
      if (!hasText(pixelId)) {
        issues.add(
            error(
                "META_PIXEL_REQUIRED",
                "providerOverride.pixelId",
                "A Meta pixelId is required when using a standard conversion event."));
      } else {
        var pixel =
            accountResourceRepository
                .findByCompanyIdAndAdAccountIdAndResourceTypeAndExternalResourceId(
                    companyId,
                    Long.valueOf(plan.adAccountId()),
                    AdAccountResourceType.META_PIXEL,
                    pixelId.trim())
                .orElse(null);
        if (pixel == null || !pixel.isAccessible()) {
          issues.add(
              error(
                  "META_PIXEL_UNAVAILABLE",
                  "providerOverride.pixelId",
                  "Selected Meta Pixel is not available for this ad account."));
        }
      }
    }
    return issues;
  }

  @Override
  public LaunchStepExecutionResult execute(
      Long companyId, LaunchRun run, ProviderCampaignPlan plan, LaunchStep step) {
    ReachlyoCampaign campaign = campaign(companyId, run.getReachlyoCampaignId());
    CampaignChannel channel = channel(companyId, campaign.getId(), step.getCampaignChannelId());

    return switch (step.getOperationKey()) {
      case "create-campaign-paused" ->
          createCampaign(companyId, run, plan, step, campaign, channel);
      case "create-ad-set-paused" ->
          createAdSet(companyId, run, plan, step, campaign, channel);
      case "configure-ad-set-targeting" ->
          LaunchStepExecutionResult.skipped(
              "Canonical Meta geo/age/gender targeting was applied atomically while creating the paused Meta ad set.");
      case "create-creative" ->
          createCreative(companyId, run, plan, step, campaign, channel);
      case "create-ad-paused" ->
          createAd(companyId, run, plan, step, campaign, channel);
      case "configure-conversion-goal" ->
          LaunchStepExecutionResult.skipped(
              "Meta promoted-object conversion configuration was applied while creating the paused ad set.");
      default -> throw new BadRequestException("Unsupported Meta launch operation: " + step.getOperationKey());
    };
  }

  private LaunchStepExecutionResult createCampaign(
      Long companyId,
      LaunchRun run,
      ProviderCampaignPlan plan,
      LaunchStep step,
      ReachlyoCampaign campaign,
      CampaignChannel channel) {
    String name = launchName(campaign.getName(), run.getId(), plan.channelId());
    if (step.getAttemptCount() > 1) {
      campaignSyncService.sync(companyId, step.getAdAccountId());
    }
    AdCampaign existing =
        adCampaignRepository
            .findFirstByCompanyIdAndAdAccountIdAndProviderAndNameOrderByIdAsc(
                companyId, step.getAdAccountId(), AdProvider.META_ADS, name)
            .orElse(null);
    JsonNode override = override(channel);

    AdCampaignResponse result =
        existing != null
            ? AdCampaignResponse.from(existing)
            : campaignMutationService.create(
                companyId,
                step.getAdAccountId(),
                new CreateMetaCampaignRequest(
                    name,
                    firstText(text(override, "objective"), metaObjective(campaign)),
                    text(override, "buyingType"),
                    text(override, "bidStrategy"),
                    null,
                    null,
                    stringArray(override, "specialAdCategories")));

    deploymentService.bindProviderCampaign(
        companyId, step.getProviderDeploymentId(), Long.valueOf(result.id()));
    return LaunchStepExecutionResult.succeeded(
        Long.valueOf(result.id()),
        result.externalCampaignId(),
        Map.of("reused", existing != null));
  }

  private LaunchStepExecutionResult createAdSet(
      Long companyId,
      LaunchRun run,
      ProviderCampaignPlan plan,
      LaunchStep step,
      ReachlyoCampaign campaign,
      CampaignChannel channel) {
    AdCampaign providerCampaign = providerCampaign(companyId, step.getProviderDeploymentId());
    if (step.getAttemptCount() > 1) {
      adSetSyncService.sync(companyId, providerCampaign.getId());
    }
    String name = launchName(campaign.getName() + " Ad Set", run.getId(), plan.channelId());
    AdSet existing =
        adSetRepository
            .findFirstByCompanyIdAndCampaignIdAndNameOrderByIdAsc(
                companyId, providerCampaign.getId(), name)
            .orElse(null);
    if (existing != null) {
      return LaunchStepExecutionResult.succeeded(
          existing.getId(), existing.getExternalAdSetId(), Map.of("reused", true));
    }

    JsonNode override = override(channel);
    AudienceDefinition audience =
        audience(companyId, campaign.getId(), plan.audienceDefinitionId(), true);
    List<CreateMetaAdSetRequest.TargetingLocation> targetingLocations =
        geoTargetingService.resolveCanonical(
            companyId, step.getAdAccountId(), locations(audience));
    ConversionGoalMapping conversion =
        conversion(companyId, campaign.getId(), plan.conversionGoalMappingId());

    Long dailyBudget = plan.budgetPeriod() == BudgetPeriod.DAILY ? plan.effectiveBudgetMinor() : null;
    Long lifetimeBudget = plan.budgetPeriod() == BudgetPeriod.TOTAL ? plan.effectiveBudgetMinor() : null;

    String pixelId = null;
    String customEventType = null;
    String customConversionId = null;
    if (conversion != null) {
      if (conversion.getTargetType() == ConversionTargetType.PROVIDER_CONVERSION) {
        customConversionId =
            conversion.getAdConversionId() == null ? null : conversion.getAdConversionId().toString();
      } else {
        pixelId = text(override, "pixelId");
        customEventType = conversion.getProviderEventName();
      }
    }

    AdSetResponse result =
        adSetMutationService.create(
            companyId,
            providerCampaign.getId(),
            new CreateMetaAdSetRequest(
                name,
                text(override, "optimizationGoal"),
                text(override, "billingEvent"),
                text(override, "adSetBidStrategy"),
                longValue(override, "bidAmountMinor"),
                dailyBudget,
                lifetimeBudget,
                campaign.getStartAt(),
                campaign.getEndAt(),
                text(override, "destinationType"),
                targetingLocations,
                audience.getAgeMin(),
                audience.getAgeMax() == null ? null : Math.min(65, audience.getAgeMax()),
                metaGenders(audience),
                stringArray(override, "publisherPlatforms"),
                stringArray(override, "facebookPositions"),
                stringArray(override, "instagramPositions"),
                stringArray(override, "messengerPositions"),
                stringArray(override, "audienceNetworkPositions"),
                List.of(),
                List.of(),
                booleanValue(override, "advantageAudience", false),
                booleanValue(override, "advantageAgeSuggestion", false),
                booleanValue(override, "advantageGenderSuggestion", false),
                pixelId,
                customEventType,
                customConversionId,
                text(override, "productSetId"),
                metaInterests(override)),
            run.getRequestedByUserId());

    return LaunchStepExecutionResult.succeeded(
        Long.valueOf(result.id()), result.externalAdSetId(), Map.of("reused", false));
  }

  private LaunchStepExecutionResult createCreative(
      Long companyId,
      LaunchRun run,
      ProviderCampaignPlan plan,
      LaunchStep step,
      ReachlyoCampaign campaign,
      CampaignChannel channel) {
    JsonNode override = override(channel);
    String existingPostId = text(override, "existingPostId");
    CreativePack pack =
        creativePack(
            companyId,
            campaign.getId(),
            plan.creativePackId(),
            !hasText(existingPostId));
    if (step.getAttemptCount() > 1) {
      creativeSyncService.sync(companyId, step.getAdAccountId());
    }
    String name = launchName(campaign.getName() + " Creative", run.getId(), plan.channelId());
    AdCreative existing =
        adCreativeRepository
            .findFirstByCompanyIdAndAdAccountIdAndProviderAndNameOrderByIdAsc(
                companyId, step.getAdAccountId(), AdProvider.META_ADS, name)
            .orElse(null);
    if (existing != null) {
      return LaunchStepExecutionResult.succeeded(
          existing.getId(), existing.getExternalCreativeId(), Map.of("reused", true));
    }

    if (hasText(existingPostId)) {
      AdCreativeResponse reusedPost =
          creativeMutationService.create(
              companyId,
              step.getAdAccountId(),
              new CreateMetaCreativeRequest(
                  name,
                  "SINGLE_IMAGE",
                  text(override, "pageId"),
                  text(override, "instagramActorId"),
                  null,
                  null,
                  null,
                  null,
                  null,
                  null,
                  null,
                  null,
                  null,
                  null,
                  false,
                  false,
                  false,
                  existingPostId,
                  List.of()),
              run.getRequestedByUserId());
      return LaunchStepExecutionResult.succeeded(
          Long.valueOf(reusedPost.id()),
          reusedPost.externalCreativeId(),
          Map.of("reused", false, "existingPost", true));
    }

    CreativePackAsset image = imageAsset(companyId, pack.getId());
    if (image == null) throw new BadRequestException("Meta IMAGE creative asset is missing");
    Media baselineMedia = mediaService.requireActiveCompanyMedia(image.getMediaId(), companyId);
    if (baselineMedia.getMediaType() != MediaType.IMAGE) {
      throw new BadRequestException("Meta baseline creative requires image media");
    }
    String baselineImageUrl = mediaService.createTrustedAccessUrl(baselineMedia.getId()).url();
    Set<com.messaging.ads.campaign.enums.CreativeVariantType> families =
        MetaPlacementCreativeVariantSelector.selectedFamilies(override);
    if (MetaPlacementCreativeVariantSelector.fullyMapped(override) && families.size() > 1) {
      List<CreateMetaPlacementCreativeRequest.PlacementAsset> placementAssets =
          placementAssets(companyId, pack, campaign, override, families);
      if (placementAssets.size() == families.size()) {
        AdCreativeResponse customized =
            creativeMutationService.createPlacementCustomized(
                companyId,
                step.getAdAccountId(),
                new CreateMetaPlacementCreativeRequest(
                    name,
                    text(override, "pageId"),
                    text(override, "instagramActorId"),
                    firstText(text(override, "callToActionType"), pack.getCallToAction()),
                    text(override, "urlTags"),
                    booleanValue(override, "advantagePlusCreative", false),
                    booleanValue(override, "generativeText", false),
                    booleanValue(override, "imageExpansion", false),
                    placementAssets),
                run.getRequestedByUserId());
        return LaunchStepExecutionResult.succeeded(
            Long.valueOf(customized.id()),
            customized.externalCreativeId(),
            Map.of(
                "reused", false,
                "placementCustomized", true,
                "creativeVariantTypes",
                    families.stream().map(Enum::name).sorted().toList()));
      }
    }

    CreativeVariant variant = placementVariant(companyId, pack.getId(), override);
    List<String> headlines =
        variant == null ? strings(pack.getHeadlinesJson()) : strings(variant.getHeadlinesJson());
    List<String> descriptions =
        variant == null ? strings(pack.getDescriptionsJson()) : strings(variant.getDescriptionsJson());

    String primaryText =
        variant == null ? pack.getPrimaryMessage() : firstText(variant.getPrimaryText(), pack.getPrimaryMessage());
    String callToAction =
        variant == null
            ? firstText(text(override, "callToActionType"), pack.getCallToAction())
            : firstText(variant.getCallToAction(), text(override, "callToActionType"), pack.getCallToAction());
    String creativeDestination =
        variant == null
            ? destination(pack, campaign)
            : firstText(variant.getDestinationUrl(), destination(pack, campaign));

    VariantMedia variantMedia = variant == null ? null : variantMedia(companyId, variant);
    Media selectedMedia = variantMedia == null ? baselineMedia : variantMedia.media();
    String selectedUrl = variantMedia == null ? baselineImageUrl : variantMedia.url();
    String format = selectedMedia.getMediaType() == MediaType.VIDEO ? "VIDEO" : "SINGLE_IMAGE";

    AdCreativeResponse result =
        creativeMutationService.create(
            companyId,
            step.getAdAccountId(),
            new CreateMetaCreativeRequest(
                name,
                format,
                text(override, "pageId"),
                text(override, "instagramActorId"),
                primaryText,
                headlines.isEmpty() ? null : headlines.getFirst(),
                descriptions.isEmpty() ? null : descriptions.getFirst(),
                callToAction,
                creativeDestination,
                null,
                selectedMedia.getMediaType() == MediaType.IMAGE ? selectedUrl : null,
                null,
                selectedMedia.getMediaType() == MediaType.VIDEO ? selectedUrl : null,
                text(override, "urlTags"),
                booleanValue(override, "advantagePlusCreative", false),
                booleanValue(override, "generativeText", false),
                booleanValue(override, "imageExpansion", false),
                null,
                List.of()),
            run.getRequestedByUserId());

    Map<String, Object> metadata = new java.util.LinkedHashMap<>();
    metadata.put("reused", false);
    if (variant != null) metadata.put("creativeVariantType", variant.getVariantType().name());
    if (variantMedia != null) metadata.put("placementMedia", true);
    return LaunchStepExecutionResult.succeeded(
        Long.valueOf(result.id()), result.externalCreativeId(), Map.copyOf(metadata));
  }

  private LaunchStepExecutionResult createAd(
      Long companyId,
      LaunchRun run,
      ProviderCampaignPlan plan,
      LaunchStep step,
      ReachlyoCampaign campaign,
      CampaignChannel channel) {
    AdCampaign providerCampaign = providerCampaign(companyId, step.getProviderDeploymentId());
    AdSet adSet =
        adSetRepository
            .findByCompanyIdAndCampaignIdOrderByProviderUpdatedAtDesc(
                companyId, providerCampaign.getId())
            .stream()
            .findFirst()
            .orElseThrow(() -> new BadRequestException("Meta ad set has not been created yet"));
    if (step.getAttemptCount() > 1) {
      adSyncService.sync(companyId, adSet.getId());
    }
    JsonNode override = override(channel);
    String productSetId = text(override, "productSetId");

    String name = launchName(campaign.getName() + " Ad", run.getId(), plan.channelId());
    var existing =
        adRepository
            .findFirstByCompanyIdAndAdSetIdAndNameOrderByIdAsc(companyId, adSet.getId(), name)
            .orElse(null);

    AdResponse result;
    if (existing != null) {
      result = AdResponse.from(existing);
    } else if (hasText(productSetId)) {
      CreativePack pack =
          creativePack(companyId, campaign.getId(), plan.creativePackId(), true);
      List<String> headlines = strings(pack.getHeadlinesJson());
      result =
          adMutationService.createCatalog(
              companyId,
              adSet.getId(),
              new CreateMetaCatalogAdRequest(
                  name,
                  text(override, "pageId"),
                  text(override, "instagramActorId"),
                  productSetId,
                  destination(pack, campaign),
                  headlines.isEmpty() ? pack.getOfferText() : headlines.get(0),
                  firstText(text(override, "callToActionType"), pack.getCallToAction()),
                  booleanValue(override, "backgroundGeneration", false)),
              run.getRequestedByUserId());
    } else {
      AdCreative creative =
          adCreativeRepository
              .findByCompanyIdAndAdAccountIdOrderByProviderUpdatedAtDesc(
                  companyId, step.getAdAccountId())
              .stream()
              .filter(
                  value ->
                      value
                          .getName()
                          .equals(
                              launchName(
                                  campaign.getName() + " Creative",
                                  run.getId(),
                                  plan.channelId())))
              .findFirst()
              .orElseThrow(
                  () -> new BadRequestException("Meta creative has not been created yet"));
      result =
          adMutationService.create(
              companyId,
              adSet.getId(),
              new CreateMetaAdRequest(name, creative.getId().toString()));
    }

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

  private CreativePackAsset imageAsset(Long companyId, Long packId) {
    return creativePackAssetRepository
        .findByCompanyIdAndCreativePackIdOrderBySortOrderAscIdAsc(companyId, packId)
        .stream()
        .filter(asset -> asset.getRole() == CreativeAssetRole.IMAGE)
        .findFirst()
        .orElse(null);
  }

  private CreativeVariant placementVariant(Long companyId, Long packId, JsonNode override) {
    return placementVariant(
        companyId, packId, MetaPlacementCreativeVariantSelector.select(override));
  }

  private List<CreateMetaPlacementCreativeRequest.PlacementAsset> placementAssets(
      Long companyId,
      CreativePack pack,
      ReachlyoCampaign campaign,
      JsonNode override,
      Set<com.messaging.ads.campaign.enums.CreativeVariantType> families) {
    List<CreateMetaPlacementCreativeRequest.PlacementAsset> result = new ArrayList<>();
    for (var family : families) {
      CreativeVariant variant = placementVariant(companyId, pack.getId(), family);
      VariantMedia media = variant == null ? null : variantMedia(companyId, variant);
      if (variant == null || media == null) continue;

      List<String> headlines = strings(variant.getHeadlinesJson());
      List<String> descriptions = strings(variant.getDescriptionsJson());
      result.add(
          new CreateMetaPlacementCreativeRequest.PlacementAsset(
              MetaPlacementCreativeVariantSelector.publisherPlatforms(override, family),
              MetaPlacementCreativeVariantSelector.facebookPositions(override, family),
              MetaPlacementCreativeVariantSelector.instagramPositions(override, family),
              MetaPlacementCreativeVariantSelector.messengerPositions(override, family),
              MetaPlacementCreativeVariantSelector.audienceNetworkPositions(override, family),
              firstText(variant.getPrimaryText(), pack.getPrimaryMessage()),
              headlines.isEmpty() ? null : headlines.getFirst(),
              descriptions.isEmpty() ? null : descriptions.getFirst(),
              firstText(variant.getDestinationUrl(), destination(pack, campaign)),
              media.media().getMediaType().name(),
              media.url()));
    }
    return List.copyOf(result);
  }

  private CreativeVariant placementVariant(
      Long companyId,
      Long packId,
      com.messaging.ads.campaign.enums.CreativeVariantType type) {
    if (type == null) return null;
    return creativeVariantRepository
        .findByCompanyIdAndCreativePackIdOrderByCreatedAtAsc(companyId, packId)
        .stream()
        .filter(variant -> variant.getProvider() == AdProvider.META_ADS)
        .filter(variant -> variant.getVariantType() == type)
        .findFirst()
        .orElse(null);
  }

  private VariantMedia variantMedia(Long companyId, CreativeVariant variant) {
    if (!hasText(variant.getAssetSpecJson())) return null;
    try {
      JsonNode spec = objectMapper.readTree(variant.getAssetSpecJson());
      JsonNode mediaIdNode = spec.get("mediaId");
      if (mediaIdNode == null || mediaIdNode.isNull()) return null;
      String mediaId = mediaIdNode.asText();
      if (!hasText(mediaId) || !mediaId.matches("\\d+")) {
        throw new BadRequestException("Meta creative variant media ID is invalid");
      }
      Media media = mediaService.requireActiveCompanyMedia(Long.valueOf(mediaId), companyId);
      if (media.getPurpose() != MediaPurpose.AD_CREATIVE
          || (media.getMediaType() != MediaType.IMAGE && media.getMediaType() != MediaType.VIDEO)) {
        throw new BadRequestException(
            "Meta placement creative media must be active AD_CREATIVE image or video media");
      }
      return new VariantMedia(media, mediaService.createTrustedAccessUrl(media.getId()).url());
    } catch (BadRequestException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new BadRequestException("Meta creative variant asset specification is invalid");
    }
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

  private ConversionGoalMapping conversion(
      Long companyId, Long campaignId, String mappingId) {
    if (!hasText(mappingId)) return null;
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

  private List<AudienceLocationInput> locations(AudienceDefinition audience) {
    if (audience == null || !hasText(audience.getLocationsJson())) return List.of();
    try {
      return objectMapper.readValue(
          audience.getLocationsJson(), new TypeReference<List<AudienceLocationInput>>() {});
    } catch (Exception exception) {
      throw new BadRequestException("Audience locations are invalid JSON");
    }
  }

  private List<Integer> metaGenders(AudienceDefinition audience) {
    List<String> genders = strings(audience.getGendersJson());
    List<Integer> result = new ArrayList<>();
    for (String gender : genders) {
      if (!hasText(gender)) continue;
      String value = gender.trim().toUpperCase(Locale.ROOT);
      if ("MALE".equals(value) || "1".equals(value)) result.add(1);
      if ("FEMALE".equals(value) || "2".equals(value)) result.add(2);
    }
    return result.stream().distinct().toList();
  }

  private String metaObjective(ReachlyoCampaign campaign) {
    return switch (campaign.getObjective()) {
      case SALES, PRODUCT_SALES -> "OUTCOME_SALES";
      case LEADS -> "OUTCOME_LEADS";
      case TRAFFIC, LOCAL_PROMOTION -> "OUTCOME_TRAFFIC";
      case AWARENESS -> "OUTCOME_AWARENESS";
      case APP_PROMOTION -> "OUTCOME_APP_PROMOTION";
      case RETARGETING -> "OUTCOME_ENGAGEMENT";
    };
  }

  private String destination(CreativePack pack, ReachlyoCampaign campaign) {
    return firstText(pack.getDestinationUrl(), campaign.getLandingPageUrl());
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

  private List<CreateMetaAdSetRequest.TargetingInterest> metaInterests(JsonNode override) {
    JsonNode value = override == null ? null : override.get("detailedInterests");
    if (value == null || value.isNull() || !value.isArray()) return List.of();

    List<CreateMetaAdSetRequest.TargetingInterest> result = new ArrayList<>();
    for (JsonNode item : value) {
      if (item == null || !item.isObject()) continue;
      String id = item.path("id").asText(null);
      String name = item.path("name").asText(null);
      if (!hasText(id) || !hasText(name)) continue;
      result.add(new CreateMetaAdSetRequest.TargetingInterest(id.trim(), name.trim()));
    }
    return List.copyOf(result);
  }

  private List<String> stringArray(JsonNode node, String key) {
    JsonNode value = node.get(key);
    if (value == null || !value.isArray()) return List.of();
    List<String> result = new ArrayList<>();
    value.forEach(item -> {
      if (item.isTextual() && !item.asText().isBlank()) result.add(item.asText().trim());
    });
    return result;
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

  private boolean booleanValue(JsonNode node, String key, boolean defaultValue) {
    JsonNode value = node.get(key);
    if (value == null || value.isNull()) return defaultValue;
    if (value.isBoolean()) return value.booleanValue();
    if (value.isInt() || value.isLong()) return value.asInt() != 0;
    if (value.isTextual()) {
      String normalized = value.asText("").trim().toLowerCase(Locale.ROOT);
      if ("true".equals(normalized) || "1".equals(normalized)) return true;
      if ("false".equals(normalized) || "0".equals(normalized)) return false;
    }
    throw new BadRequestException("Meta provider override " + key + " must be boolean");
  }

  private Long longValue(JsonNode node, String key) {
    JsonNode value = node.get(key);
    return value == null || value.isNull() || !value.canConvertToLong() ? null : value.longValue();
  }

  private void requireTextIssue(
      List<ProviderPlanIssue> issues,
      JsonNode override,
      String key,
      String code,
      String field,
      String message) {
    if (!hasText(text(override, key))) issues.add(error(code, field, message));
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

  private record VariantMedia(Media media, String url) {}

  private ProviderPlanIssue warning(String code, String field, String message) {
    return new ProviderPlanIssue(PlanIssueSeverity.WARNING, code, field, message);
  }
}
