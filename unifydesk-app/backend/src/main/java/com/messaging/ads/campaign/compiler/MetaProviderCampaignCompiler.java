package com.messaging.ads.campaign.compiler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.capability.AdFeatureCapability;
import com.messaging.ads.campaign.entity.CreativeVariant;
import com.messaging.ads.campaign.enums.AudienceMappingStatus;
import com.messaging.ads.campaign.enums.CampaignChannelType;
import com.messaging.ads.campaign.enums.ConversionTargetType;
import com.messaging.ads.campaign.enums.CreativeAssetRole;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class MetaProviderCampaignCompiler implements ProviderCampaignCompiler {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Override
  public Set<CampaignChannelType> supportedChannels() {
    return Set.of(CampaignChannelType.META_ADS);
  }

  @Override
  public ProviderCampaignPlan compile(ProviderCampaignCompileContext context) {
    List<ProviderPlanIssue> issues = new ArrayList<>();
    Set<AdFeatureCapability> required = new LinkedHashSet<>();
    List<ProviderPlanOperation> operations = new ArrayList<>();

    ProviderCampaignPlanSupport.requireCapability(
        context, AdFeatureCapability.CAMPAIGN_CREATE, required, issues);
    ProviderCampaignPlanSupport.requireCapability(
        context, AdFeatureCapability.META_CAMPAIGN_HIERARCHY, required, issues);
    ProviderCampaignPlanSupport.requireCapability(
        context, AdFeatureCapability.CREATIVE_CREATE, required, issues);
    ProviderCampaignPlanSupport.requireCapability(
        context, AdFeatureCapability.META_IMAGE_VIDEO_CAROUSEL, required, issues);

    if (context.account().getCurrency() != null
        && context.campaign().getCurrency() != null
        && !context.account().getCurrency().equalsIgnoreCase(context.campaign().getCurrency())) {
      issues.add(
          ProviderCampaignPlanSupport.error(
              "CURRENCY_MISMATCH",
              "currency",
              "Reachlyo campaign currency must match the Meta ad account currency."));
    }

    Long effectiveBudget =
        ProviderCampaignPlanSupport.effectiveBudget(
            context.budgetPlan(), context.channel(), context.allChannels(), issues);

    validateExistingPost(context, required, issues);
    validateCreative(context, issues);
    validatePlacements(context, required, issues);
    validateAdvantageAudience(context, required, issues);
    validateAdvantageCreative(context, required, issues);
    validateGenerativeText(context, required, issues);
    validateImageExpansion(context, required, issues);
    validateCatalog(context, required, issues);
    validateBackgroundGeneration(context, required, issues);

    AudienceMappingStatus audienceMappingStatus = null;
    String audienceMappingNote = null;
    if (context.audienceDefinition() == null) {
      issues.add(
          ProviderCampaignPlanSupport.warning(
              "AUDIENCE_NOT_SELECTED",
              "audience",
              "No canonical audience is selected; broad Meta targeting will be used."));
    } else {
      boolean hasUnmappedAudienceDetails =
          !isBlank(context.audienceDefinition().getLanguagesJson())
              || !isBlank(context.audienceDefinition().getInterestsJson())
              || !isBlank(context.audienceDefinition().getSearchIntentJson())
              || !isBlank(context.audienceDefinition().getRetargetingJson())
              || !isBlank(context.audienceDefinition().getCustomerSegmentsJson());
      audienceMappingStatus =
          hasUnmappedAudienceDetails
              ? AudienceMappingStatus.APPROXIMATE
              : AudienceMappingStatus.EXACT;
      audienceMappingNote =
          hasUnmappedAudienceDetails
              ? "Meta geo, age, and gender map directly; language, interest, search-intent, retargeting, or customer-segment controls still require later provider mapping."
              : "Canonical geo, age, and gender targeting maps directly to Meta.";
      if (hasUnmappedAudienceDetails) {
        issues.add(
            ProviderCampaignPlanSupport.warning(
                "AUDIENCE_MAPPING_APPROXIMATE", "audience", audienceMappingNote));
      }
    }

    if (ProviderCampaignPlanSupport.requiresConversion(context)) {
      ProviderCampaignPlanSupport.requireCapability(
          context, AdFeatureCapability.CONVERSION_CREATE, required, issues);
      if (context.conversionGoalMapping() == null) {
        issues.add(
            ProviderCampaignPlanSupport.error(
                "CONVERSION_MAPPING_MISSING",
                "conversion",
                "This business goal requires a conversion mapping for the selected Meta ad account."));
      } else if (context.conversionGoalMapping().getTargetType()
              == ConversionTargetType.PROVIDER_CONVERSION
          && !context.conversionGoalMapping().isValidated()) {
        issues.add(
            ProviderCampaignPlanSupport.error(
                "CONVERSION_MAPPING_UNVALIDATED",
                "conversion",
                "The selected Meta conversion mapping is not validated."));
      } else if (context.conversionGoalMapping().getTargetType()
          == ConversionTargetType.STANDARD_EVENT) {
        issues.add(
            ProviderCampaignPlanSupport.warning(
                "STANDARD_EVENT_REMOTE_VALIDATION_PENDING",
                "conversion",
                "The Meta standard event is validated against the current provider enum; Pixel/account eligibility will be verified during deployment preflight."));
      }

      if (context.conversionGoalMapping() != null
          && ConversionGoalCompatibility.shouldWarn(
              context.campaign().getBusinessGoal(),
              context.channel().getProvider(),
              context.conversionGoalMapping().getProviderEventName())) {
        issues.add(
            ProviderCampaignPlanSupport.warning(
                "CONVERSION_GOAL_MISMATCH",
                "conversion",
                "The selected Meta conversion event "
                    + context.conversionGoalMapping().getProviderEventName()
                    + " does not closely match business goal "
                    + context.campaign().getBusinessGoal()
                    + ". Typical matches: "
                    + ConversionGoalCompatibility.recommendedLabel(
                        context.campaign().getBusinessGoal(),
                        context.channel().getProvider())
                    + "."));
      }
    }

    addOperations(context, effectiveBudget, audienceMappingStatus, operations);

    return new ProviderCampaignPlan(
        1,
        context.campaign().getId().toString(),
        context.channel().getId().toString(),
        context.channel().getProvider(),
        context.channel().getChannelType(),
        context.account().getId().toString(),
        !ProviderCampaignPlanSupport.hasErrors(issues),
        effectiveBudget,
        context.budgetPlan() == null ? null : context.budgetPlan().getBudgetPeriod(),
        context.creativePack() == null ? null : context.creativePack().getId().toString(),
        context.audienceDefinition() == null
            ? null
            : context.audienceDefinition().getId().toString(),
        audienceMappingStatus,
        audienceMappingNote,
        context.conversionGoalMapping() == null
            ? null
            : context.conversionGoalMapping().getId().toString(),
        Set.copyOf(required),
        List.copyOf(issues),
        List.copyOf(operations));
  }

  private void validateCatalog(
      ProviderCampaignCompileContext context,
      Set<AdFeatureCapability> required,
      List<ProviderPlanIssue> issues) {
    JsonNode override = providerOverride(context.channel().getProviderOverrideJson());
    String productSetId = text(override, "productSetId");
    if (productSetId == null || productSetId.isBlank()) return;

    String existingPostId = text(override, "existingPostId");
    if (existingPostId != null && !existingPostId.isBlank()) {
      issues.add(
          ProviderCampaignPlanSupport.error(
              "META_CREATIVE_SOURCE_CONFLICT",
              "providerOverride",
              "Meta catalog ads and existing-post ads are mutually exclusive."));
      return;
    }

    ProviderCampaignPlanSupport.requireCapability(
        context, AdFeatureCapability.META_CATALOG, required, issues);
    issues.add(
        ProviderCampaignPlanSupport.warning(
            "META_CATALOG_PRODUCT_SET_SELECTED",
            "providerOverride.productSetId",
            "A Meta product set is selected. Launch will use Meta's catalog/dynamic-product ad path and keep the ad paused for provider review."));
  }

  private void validateBackgroundGeneration(
      ProviderCampaignCompileContext context,
      Set<AdFeatureCapability> required,
      List<ProviderPlanIssue> issues) {
    JsonNode override = providerOverride(context.channel().getProviderOverrideJson());
    if (!booleanValue(override, "backgroundGeneration", false)) return;

    ProviderCampaignPlanSupport.requireCapability(
        context, AdFeatureCapability.META_BACKGROUND_GENERATION, required, issues);

    String productSetId = text(override, "productSetId");
    if (productSetId == null || productSetId.isBlank()) {
      issues.add(
          ProviderCampaignPlanSupport.error(
              "META_BACKGROUND_GENERATION_PRODUCT_SET_REQUIRED",
              "providerOverride.productSetId",
              "Meta background generation requires an eligible product set because the feature is limited to dynamic product or Advantage+ catalog ads."));
      return;
    }

    issues.add(
        ProviderCampaignPlanSupport.warning(
            "META_BACKGROUND_GENERATION_ELIGIBILITY",
            "providerOverride.backgroundGeneration",
            "Meta background generation is account/product eligible only. The selected product set must be eligible and the generated backgrounds should be reviewed in provider preview before activation."));
  }

  private void validateImageExpansion(
      ProviderCampaignCompileContext context,
      Set<AdFeatureCapability> required,
      List<ProviderPlanIssue> issues) {
    JsonNode override = providerOverride(context.channel().getProviderOverrideJson());
    if (!booleanValue(override, "imageExpansion", false)) return;

    ProviderCampaignPlanSupport.requireCapability(
        context, AdFeatureCapability.META_IMAGE_UNCROP, required, issues);
    boolean hasImage =
        context.creativeAssets() != null
            && context.creativeAssets().stream()
                .anyMatch(asset -> asset.getRole() == CreativeAssetRole.IMAGE);
    if (!hasImage) {
      issues.add(
          ProviderCampaignPlanSupport.error(
              "META_IMAGE_EXPANSION_IMAGE_REQUIRED",
              "creativePack.assets",
              "Meta image expansion requires at least one image creative asset."));
    } else {
      issues.add(
          ProviderCampaignPlanSupport.warning(
              "META_IMAGE_EXPANSION_OPT_IN",
              "providerOverride.imageExpansion",
              "Meta image expansion is enabled. Meta may extend image boundaries for eligible placements; review provider previews before activation."));
    }
  }

  private void validateGenerativeText(
      ProviderCampaignCompileContext context,
      Set<AdFeatureCapability> required,
      List<ProviderPlanIssue> issues) {
    JsonNode override = providerOverride(context.channel().getProviderOverrideJson());
    if (!booleanValue(override, "generativeText", false)) return;

    ProviderCampaignPlanSupport.requireCapability(
        context, AdFeatureCapability.META_GENERATIVE_TEXT, required, issues);
    CreativePack pack = context.creativePack();
    String primaryText = pack == null ? null : pack.getPrimaryMessage();
    if (primaryText == null || primaryText.trim().split("\\s+").length < 5) {
      issues.add(
          ProviderCampaignPlanSupport.error(
              "META_GENERATIVE_TEXT_PRIMARY_TEXT_TOO_SHORT",
              "creativePack.primaryMessage",
              "Meta generative text requires enough source primary text for provider generation. Add a fuller primary message before enabling it."));
    } else {
      issues.add(
          ProviderCampaignPlanSupport.warning(
              "META_GENERATIVE_TEXT_OPT_IN",
              "providerOverride.generativeText",
              "Meta AI text generation is enabled. Generated variations remain provider-generated suggestions and must be reviewed in preview/read-back before activation."));
    }
  }

  private void validateAdvantageCreative(
      ProviderCampaignCompileContext context,
      Set<AdFeatureCapability> required,
      List<ProviderPlanIssue> issues) {
    JsonNode override = providerOverride(context.channel().getProviderOverrideJson());
    if (!booleanValue(override, "advantagePlusCreative", false)) return;

    ProviderCampaignPlanSupport.requireCapability(
        context, AdFeatureCapability.META_ADVANTAGE_CREATIVE, required, issues);
    issues.add(
        ProviderCampaignPlanSupport.warning(
            "META_ADVANTAGE_CREATIVE_STANDARD_ENHANCEMENTS",
            "providerOverride.advantagePlusCreative",
            "Meta standard creative enhancements are enabled. Meta may adapt eligible creative presentation; text generation, image expansion and background generation remain separate opt-in features."));
  }

  private void validateAdvantageAudience(
      ProviderCampaignCompileContext context,
      Set<AdFeatureCapability> required,
      List<ProviderPlanIssue> issues) {
    JsonNode override = providerOverride(context.channel().getProviderOverrideJson());
    boolean enabled = booleanValue(override, "advantageAudience", false);
    boolean ageSuggestion = booleanValue(override, "advantageAgeSuggestion", false);
    boolean genderSuggestion = booleanValue(override, "advantageGenderSuggestion", false);

    if (!enabled && (ageSuggestion || genderSuggestion)) {
      issues.add(
          ProviderCampaignPlanSupport.error(
              "META_ADVANTAGE_AUDIENCE_INVALID",
              "providerOverride.advantageAudience",
              "Advantage+ age/gender suggestions require Advantage+ audience to be enabled."));
      return;
    }
    if (!enabled) return;

    ProviderCampaignPlanSupport.requireCapability(
        context, AdFeatureCapability.META_ADVANTAGE_AUDIENCE, required, issues);
    issues.add(
        ProviderCampaignPlanSupport.warning(
            "META_ADVANTAGE_AUDIENCE_EXPANSION",
            "providerOverride.advantageAudience",
            "Meta may expand beyond audience suggestions. Location and explicit exclusions remain controls; enabled age/gender settings are suggestions."));
  }

  private void validatePlacements(
      ProviderCampaignCompileContext context,
      Set<AdFeatureCapability> required,
      List<ProviderPlanIssue> issues) {
    JsonNode override = providerOverride(context.channel().getProviderOverrideJson());
    List<String> platforms = stringArray(override, "publisherPlatforms");
    List<String> facebookPositions = stringArray(override, "facebookPositions");
    List<String> instagramPositions = stringArray(override, "instagramPositions");
    List<String> messengerPositions = stringArray(override, "messengerPositions");
    List<String> audienceNetworkPositions = stringArray(override, "audienceNetworkPositions");

    boolean placementModeExplicit = override.has("advantagePlacements");
    boolean advantagePlacements = booleanValue(override, "advantagePlacements", false);
    boolean hasManualSelection =
        !platforms.isEmpty()
            || !facebookPositions.isEmpty()
            || !instagramPositions.isEmpty()
            || !messengerPositions.isEmpty()
            || !audienceNetworkPositions.isEmpty();

    if (placementModeExplicit && advantagePlacements) {
      if (hasManualSelection) {
        issues.add(
            ProviderCampaignPlanSupport.error(
                "META_ADVANTAGE_PLACEMENTS_CONFLICT",
                "providerOverride.advantagePlacements",
                "Advantage+ placements cannot be combined with manual publisher-platform or position restrictions."));
        return;
      }
      ProviderCampaignPlanSupport.requireCapability(
          context, AdFeatureCapability.META_ADVANTAGE_PLACEMENTS, required, issues);
      return;
    }

    if (placementModeExplicit && !advantagePlacements && platforms.isEmpty()) {
      issues.add(
          ProviderCampaignPlanSupport.error(
              "META_MANUAL_PLACEMENTS_EMPTY",
              "providerOverride.publisherPlatforms",
              "Manual Meta placements require at least one publisher platform."));
      return;
    }

    if (platforms.isEmpty()) return;

    ProviderCampaignPlanSupport.requireCapability(
        context, AdFeatureCapability.META_PLACEMENT_TREE, required, issues);

    Set<com.messaging.ads.campaign.enums.CreativeVariantType> needed = new LinkedHashSet<>();
    if (containsAny(facebookPositions, "feed", "marketplace", "video_feeds", "search")
        || containsAny(instagramPositions, "stream", "explore", "explore_home", "profile_feed")) {
      needed.add(com.messaging.ads.campaign.enums.CreativeVariantType.META_FEED);
    }
    if (containsAny(facebookPositions, "story")
        || containsAny(instagramPositions, "story")
        || containsAny(messengerPositions, "story")) {
      needed.add(com.messaging.ads.campaign.enums.CreativeVariantType.META_STORY);
    }
    if (containsAny(facebookPositions, "reels")
        || containsAny(instagramPositions, "reels")) {
      needed.add(com.messaging.ads.campaign.enums.CreativeVariantType.META_REEL);
    }

    java.util.Map<com.messaging.ads.campaign.enums.CreativeVariantType, CreativeVariant> available =
        context.creativeVariants().stream()
            .filter(variant -> variant.getProvider() == context.channel().getProvider())
            .collect(
                java.util.stream.Collectors.toMap(
                    CreativeVariant::getVariantType,
                    variant -> variant,
                    (first, ignored) -> first));

    for (com.messaging.ads.campaign.enums.CreativeVariantType type : needed) {
      CreativeVariant variant = available.get(type);
      if (variant == null) {
        issues.add(
            ProviderCampaignPlanSupport.warning(
                "META_PLACEMENT_CREATIVE_VARIANT_MISSING",
                "creativePack.variants",
                "Selected Meta placements would benefit from a " + type.name()
                    + " creative variant; the source creative will be reused unless a placement-specific variant is added."));
      } else if (!hasPlacementMedia(variant)) {
        issues.add(
            ProviderCampaignPlanSupport.warning(
                "META_PLACEMENT_CREATIVE_MEDIA_MISSING",
                "creativePack.variants",
                type.name()
                    + " exists but has no placement-specific image/video; the canonical campaign media will be used where placement customization cannot be assembled."));
      }
    }
  }

  private boolean hasPlacementMedia(CreativeVariant variant) {
    if (isBlank(variant.getAssetSpecJson())) return false;
    try {
      JsonNode node = OBJECT_MAPPER.readTree(variant.getAssetSpecJson());
      JsonNode mediaId = node == null ? null : node.get("mediaId");
      return mediaId != null && !mediaId.isNull() && !mediaId.asText("").isBlank();
    } catch (Exception ignored) {
      return false;
    }
  }

  private JsonNode providerOverride(String value) {
    if (isBlank(value)) return OBJECT_MAPPER.createObjectNode();
    try {
      JsonNode node = OBJECT_MAPPER.readTree(value);
      return node != null && node.isObject() ? node : OBJECT_MAPPER.createObjectNode();
    } catch (Exception ignored) {
      return OBJECT_MAPPER.createObjectNode();
    }
  }

  private List<String> stringArray(JsonNode node, String field) {
    JsonNode values = node.path(field);
    if (!values.isArray()) return List.of();
    List<String> result = new ArrayList<>();
    values.forEach(value -> {
      if (value.isTextual() && !value.asText().isBlank()) result.add(value.asText().trim().toLowerCase());
    });
    return result;
  }

  private boolean booleanValue(JsonNode node, String field, boolean defaultValue) {
    JsonNode value = node.get(field);
    if (value == null || value.isNull()) return defaultValue;
    if (value.isBoolean()) return value.booleanValue();
    if (value.isIntegralNumber()) return value.asInt() != 0;
    if (value.isTextual()) {
      String normalized = value.asText("").trim().toLowerCase();
      if ("true".equals(normalized) || "1".equals(normalized)) return true;
      if ("false".equals(normalized) || "0".equals(normalized)) return false;
    }
    return defaultValue;
  }

  private boolean containsAny(List<String> values, String... expected) {
    Set<String> wanted = Set.of(expected);
    return values.stream().anyMatch(wanted::contains);
  }

  private void validateExistingPost(
      ProviderCampaignCompileContext context,
      Set<AdFeatureCapability> required,
      List<ProviderPlanIssue> issues) {
    JsonNode override = providerOverride(context.channel().getProviderOverrideJson());
    String existingPostId = text(override, "existingPostId");
    if (existingPostId == null || existingPostId.isBlank()) return;

    ProviderCampaignPlanSupport.requireCapability(
        context, AdFeatureCapability.META_EXISTING_POST, required, issues);
    if (existingPostId.length() > 255) {
      issues.add(
          ProviderCampaignPlanSupport.error(
              "META_EXISTING_POST_ID_INVALID",
              "providerOverride.existingPostId",
              "Meta existing post ID is too long."));
    } else {
      issues.add(
          ProviderCampaignPlanSupport.warning(
              "META_EXISTING_POST_REUSE",
              "providerOverride.existingPostId",
              "This campaign will reuse the selected Meta post as the creative source. Provider ownership and ad-eligibility are verified by Meta at creation time."));
    }
  }

  private void validateCreative(
      ProviderCampaignCompileContext context, List<ProviderPlanIssue> issues) {
    JsonNode override = providerOverride(context.channel().getProviderOverrideJson());
    if (!isBlank(text(override, "existingPostId"))) return;

    if (context.creativePack() == null) {
      issues.add(
          ProviderCampaignPlanSupport.error(
              "CREATIVE_PACK_MISSING",
              "creativePack",
              "A Creative Pack is required for Meta deployment."));
      return;
    }

    boolean hasMetaVariant =
        context.creativeVariants().stream()
            .map(CreativeVariant::getProvider)
            .anyMatch(provider -> provider == context.channel().getProvider());
    if (!hasMetaVariant) {
      if (isBlank(context.creativePack().getPrimaryMessage())
          || (isBlank(context.creativePack().getDestinationUrl())
              && isBlank(context.campaign().getLandingPageUrl()))) {
        issues.add(
            ProviderCampaignPlanSupport.error(
                "CREATIVE_VARIANT_MISSING",
                "creativePack",
                "No Meta creative variant exists and the source Creative Pack is incomplete."));
      } else {
        issues.add(
            ProviderCampaignPlanSupport.warning(
                "CREATIVE_VARIANT_ADAPTATION_REQUIRED",
                "creativePack",
                "No Meta-specific variant exists; the deployment compiler must adapt the source Creative Pack."));
      }
    }

    if (context.creativeAssets().isEmpty()) {
      issues.add(
          ProviderCampaignPlanSupport.warning(
              "CREATIVE_MEDIA_MISSING",
              "creativePack.assets",
              "The selected Creative Pack has no image or video media."));
    }
  }

  private void addOperations(
      ProviderCampaignCompileContext context,
      Long effectiveBudget,
      AudienceMappingStatus audienceMappingStatus,
      List<ProviderPlanOperation> operations) {
    int sequence = 1;
    operations.add(
        operation(
            sequence++,
            "create-campaign-paused",
            ProviderPlanAction.CREATE,
            ProviderPlanResource.CAMPAIGN,
            true,
            Set.of(
                AdFeatureCapability.CAMPAIGN_CREATE,
                AdFeatureCapability.META_CAMPAIGN_HIERARCHY),
            ProviderCampaignPlanSupport.config(
                "name",
                context.campaign().getName(),
                "businessGoal",
                context.campaign().getBusinessGoal(),
                "providerOverrideJson",
                context.channel().getProviderOverrideJson())));
    operations.add(
        operation(
            sequence++,
            "create-ad-set-paused",
            ProviderPlanAction.CREATE,
            ProviderPlanResource.AD_SET,
            true,
            Set.of(AdFeatureCapability.META_CAMPAIGN_HIERARCHY),
            ProviderCampaignPlanSupport.config(
                "budgetMinor",
                effectiveBudget,
                "budgetPeriod",
                context.budgetPlan() == null ? null : context.budgetPlan().getBudgetPeriod(),
                "startAt",
                context.campaign().getStartAt(),
                "endAt",
                context.campaign().getEndAt())));
    if (context.audienceDefinition() != null) {
      operations.add(
          operation(
              sequence++,
              "configure-ad-set-targeting",
              ProviderPlanAction.CONFIGURE,
              ProviderPlanResource.CAMPAIGN_TARGETING,
              false,
              Set.of(),
              ProviderCampaignPlanSupport.config(
                  "audienceDefinitionId",
                  context.audienceDefinition().getId().toString(),
                  "mappingStatus",
                  audienceMappingStatus)));
    }
    JsonNode providerOverride = providerOverride(context.channel().getProviderOverrideJson());
    String productSetId = text(providerOverride, "productSetId");
    String existingPostId = text(providerOverride, "existingPostId");
    boolean catalogAd = productSetId != null && !productSetId.isBlank();
    boolean existingPost = existingPostId != null && !existingPostId.isBlank();
    if (!catalogAd) {
      operations.add(
          operation(
              sequence++,
              "create-creative",
              ProviderPlanAction.CREATE,
              ProviderPlanResource.CREATIVE,
              false,
              existingPost
                  ? Set.of(
                      AdFeatureCapability.CREATIVE_CREATE,
                      AdFeatureCapability.META_EXISTING_POST)
                  : Set.of(
                      AdFeatureCapability.CREATIVE_CREATE,
                      AdFeatureCapability.META_IMAGE_VIDEO_CAROUSEL),
              ProviderCampaignPlanSupport.config(
                  "creativePackId",
                  context.creativePack() == null
                      ? null
                      : context.creativePack().getId().toString())));
    }
    operations.add(
        operation(
            sequence++,
            "create-ad-paused",
            ProviderPlanAction.CREATE,
            ProviderPlanResource.AD,
            true,
            catalogAd
                ? Set.of(
                    AdFeatureCapability.META_CAMPAIGN_HIERARCHY,
                    AdFeatureCapability.META_CATALOG)
                : Set.of(AdFeatureCapability.META_CAMPAIGN_HIERARCHY),
            ProviderCampaignPlanSupport.config(
                "status",
                "PAUSED",
                "catalogAd",
                catalogAd,
                "productSetId",
                productSetId)));

    if (context.conversionGoalMapping() != null) {
      operations.add(
          operation(
              sequence,
              "configure-conversion-goal",
              ProviderPlanAction.CONFIGURE,
              ProviderPlanResource.CONVERSION_GOAL,
              false,
              Set.of(AdFeatureCapability.CONVERSION_CREATE),
              ProviderCampaignPlanSupport.config(
                  "mappingId",
                  context.conversionGoalMapping().getId().toString(),
                  "targetType",
                  context.conversionGoalMapping().getTargetType(),
                  "providerEventName",
                  context.conversionGoalMapping().getProviderEventName())));
    }
  }

  private ProviderPlanOperation operation(
      int sequence,
      String key,
      ProviderPlanAction action,
      ProviderPlanResource resource,
      boolean createPaused,
      Set<AdFeatureCapability> capabilities,
      java.util.Map<String, Object> configuration) {
    return new ProviderPlanOperation(
        sequence, key, action, resource, createPaused, capabilities, configuration);
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
