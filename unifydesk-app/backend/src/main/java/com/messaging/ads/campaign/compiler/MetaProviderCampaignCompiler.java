package com.messaging.ads.campaign.compiler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.capability.AdFeatureCapability;
import com.messaging.ads.campaign.entity.CreativeVariant;
import com.messaging.ads.campaign.enums.AudienceMappingStatus;
import com.messaging.ads.campaign.enums.CampaignChannelType;
import com.messaging.ads.campaign.enums.ConversionTargetType;
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

    validateCreative(context, issues);
    validatePlacements(context, required, issues);

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
                "The Meta standard event name will be validated against the provider during deployment preflight."));
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

  private void validatePlacements(
      ProviderCampaignCompileContext context,
      Set<AdFeatureCapability> required,
      List<ProviderPlanIssue> issues) {
    JsonNode override = providerOverride(context.channel().getProviderOverrideJson());
    List<String> platforms = stringArray(override, "publisherPlatforms");
    if (platforms.isEmpty()) return;

    ProviderCampaignPlanSupport.requireCapability(
        context, AdFeatureCapability.META_PLACEMENT_TREE, required, issues);

    Set<com.messaging.ads.campaign.enums.CreativeVariantType> needed = new LinkedHashSet<>();
    if (containsAny(stringArray(override, "facebookPositions"), "feed", "marketplace", "video_feeds", "search")
        || containsAny(stringArray(override, "instagramPositions"), "stream", "explore", "explore_home", "profile_feed")) {
      needed.add(com.messaging.ads.campaign.enums.CreativeVariantType.META_FEED);
    }
    if (containsAny(stringArray(override, "facebookPositions"), "story")
        || containsAny(stringArray(override, "instagramPositions"), "story")
        || containsAny(stringArray(override, "messengerPositions"), "story")) {
      needed.add(com.messaging.ads.campaign.enums.CreativeVariantType.META_STORY);
    }
    if (containsAny(stringArray(override, "facebookPositions"), "reels")
        || containsAny(stringArray(override, "instagramPositions"), "reels")) {
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

  private boolean containsAny(List<String> values, String... expected) {
    Set<String> wanted = Set.of(expected);
    return values.stream().anyMatch(wanted::contains);
  }

  private void validateCreative(
      ProviderCampaignCompileContext context, List<ProviderPlanIssue> issues) {
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
    operations.add(
        operation(
            sequence++,
            "create-creative",
            ProviderPlanAction.CREATE,
            ProviderPlanResource.CREATIVE,
            false,
            Set.of(
                AdFeatureCapability.CREATIVE_CREATE,
                AdFeatureCapability.META_IMAGE_VIDEO_CAROUSEL),
            ProviderCampaignPlanSupport.config(
                "creativePackId",
                context.creativePack() == null
                    ? null
                    : context.creativePack().getId().toString())));
    operations.add(
        operation(
            sequence++,
            "create-ad-paused",
            ProviderPlanAction.CREATE,
            ProviderPlanResource.AD,
            true,
            Set.of(AdFeatureCapability.META_CAMPAIGN_HIERARCHY),
            ProviderCampaignPlanSupport.config("status", "PAUSED")));

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
