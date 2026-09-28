package com.messaging.ads.campaign.compiler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.capability.AdFeatureCapability;
import com.messaging.ads.campaign.entity.CreativeVariant;
import com.messaging.ads.campaign.enums.AudienceMappingStatus;
import com.messaging.ads.campaign.enums.BudgetPeriod;
import com.messaging.ads.campaign.enums.CampaignChannelType;
import com.messaging.ads.campaign.enums.ConversionTargetType;
import com.messaging.ads.campaign.enums.CreativeAssetRole;
import com.messaging.ads.campaign.enums.CreativeVariantType;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class GoogleProviderCampaignCompiler implements ProviderCampaignCompiler {

  private static final ObjectMapper JSON = new ObjectMapper();

  private static final Set<CampaignChannelType> CHANNELS =
      EnumSet.of(
          CampaignChannelType.GOOGLE_SEARCH,
          CampaignChannelType.GOOGLE_PERFORMANCE_MAX,
          CampaignChannelType.GOOGLE_DEMAND_GEN,
          CampaignChannelType.GOOGLE_DISPLAY,
          CampaignChannelType.GOOGLE_SHOPPING);

  @Override
  public Set<CampaignChannelType> supportedChannels() {
    return CHANNELS;
  }

  @Override
  public ProviderCampaignPlan compile(ProviderCampaignCompileContext context) {
    List<ProviderPlanIssue> issues = new ArrayList<>();
    Set<AdFeatureCapability> required = new LinkedHashSet<>();
    List<ProviderPlanOperation> operations = new ArrayList<>();

    AdFeatureCapability channelCapability = channelCapability(context.channel().getChannelType());
    ProviderCampaignPlanSupport.requireCapability(
        context, AdFeatureCapability.CAMPAIGN_CREATE, required, issues);
    ProviderCampaignPlanSupport.requireCapability(context, channelCapability, required, issues);

    if (context.account().getCurrency() != null
        && context.campaign().getCurrency() != null
        && !context.account().getCurrency().equalsIgnoreCase(context.campaign().getCurrency())) {
      issues.add(
          ProviderCampaignPlanSupport.error(
              "CURRENCY_MISMATCH",
              "currency",
              "Reachlyo campaign currency must match the Google Ads account currency."));
    }

    Long effectiveBudget =
        ProviderCampaignPlanSupport.effectiveBudget(
            context.budgetPlan(), context.channel(), context.allChannels(), issues);

    if (context.budgetPlan() != null
        && context.budgetPlan().getBudgetPeriod() == BudgetPeriod.TOTAL) {
      issues.add(
          ProviderCampaignPlanSupport.warning(
              "TOTAL_BUDGET_DAILY_DERIVATION",
              "budget",
              "Google campaign deployment will need to derive a daily budget from this total budget and schedule."));
    }

    boolean shopping = context.channel().getChannelType() == CampaignChannelType.GOOGLE_SHOPPING;
    if (!shopping) {
      ProviderCampaignPlanSupport.requireCapability(
          context, AdFeatureCapability.CREATIVE_CREATE, required, issues);
      validateCreative(context, issues);
      if (context.channel().getChannelType() == CampaignChannelType.GOOGLE_PERFORMANCE_MAX) {
        validatePerformanceMaxCreative(context, issues);
      }
      if (context.channel().getChannelType() == CampaignChannelType.GOOGLE_DEMAND_GEN) {
        validateDemandGenCreative(context, issues);
      }
      if (context.channel().getChannelType() == CampaignChannelType.GOOGLE_DISPLAY) {
        validateDisplayCreative(context, issues);
      }
    } else {
      ProviderCampaignPlanSupport.requireCapability(
          context, AdFeatureCapability.GOOGLE_MERCHANT, required, issues);
      issues.add(
          ProviderCampaignPlanSupport.warning(
              "MERCHANT_PREFLIGHT_PENDING",
              "merchant",
              "Linked Merchant Center resources will be validated during deployment preflight."));
    }

    AudienceMappingStatus audienceMappingStatus = null;
    String audienceMappingNote = null;
    if (context.audienceDefinition() == null) {
      if (!hasProviderAudienceSelection(context)) {
        issues.add(
            ProviderCampaignPlanSupport.warning(
                "AUDIENCE_NOT_SELECTED",
                "audience",
                "No canonical or provider-backed audience is selected; provider defaults/broad targeting will be used where valid."));
      }
    } else {
      audienceMappingStatus = googleAudienceMappingStatus(context);
      if (audienceMappingStatus == AudienceMappingStatus.EXACT) {
        audienceMappingNote =
            "Canonical geo, language, demographic, and Search intent targeting can be represented directly for Google Search.";
      } else {
        audienceMappingNote =
            context.channel().getChannelType() == CampaignChannelType.GOOGLE_SEARCH
                ? "Geo, language, and demographic targeting are mapped, but this audience also contains audience controls not yet applied to Google Search."
                : "Canonical audience mapping remains partial for this Google campaign type.";
        issues.add(
            ProviderCampaignPlanSupport.warning(
                "AUDIENCE_MAPPING_APPROXIMATE", "audience", audienceMappingNote));
      }
    }

    if (context.channel().getChannelType() == CampaignChannelType.GOOGLE_SEARCH
        && (context.audienceDefinition() == null
            || isBlank(context.audienceDefinition().getSearchIntentJson()))) {
      issues.add(
          ProviderCampaignPlanSupport.warning(
              "SEARCH_INTENT_MISSING",
              "audience.searchIntents",
              "No search-intent terms are available to seed Search keywords."));
    }

    if (context.channel().getChannelType() == CampaignChannelType.GOOGLE_SEARCH
        && aiMaxEnabled(context)) {
      ProviderCampaignPlanSupport.requireCapability(
          context, AdFeatureCapability.GOOGLE_AI_MAX, required, issues);
      if (providerText(context, "keywordMatchType") != null
          && !providerBoolean(context, "aiMaxDisableSearchTermMatching", false)) {
        issues.add(
            ProviderCampaignPlanSupport.warning(
                "AI_MAX_KEYWORD_MATCH_TYPE_OVERRIDDEN",
                "providerOverride.keywordMatchType",
                "With AI Max search-term matching enabled, Google treats keywords as broad match. Disable AI Max search-term matching at the ad-group level if individual keyword match types must apply."));
      }
      if (providerBoolean(context, "aiMaxFinalUrlExpansionEnabled", false)
          && isBlank(context.campaign().getLandingPageUrl())
          && (context.creativePack() == null
              || isBlank(context.creativePack().getDestinationUrl()))) {
        issues.add(
            ProviderCampaignPlanSupport.error(
                "AI_MAX_FINAL_URL_REQUIRED",
                "providerOverride.aiMaxFinalUrlExpansionEnabled",
                "AI Max final URL expansion requires a campaign or Creative Pack landing URL."));
      }
      if (!providerStrings(context, "aiMaxUrlInclusions").isEmpty()
          && (!providerBoolean(context, "aiMaxTextCustomizationEnabled", false)
              || !providerBoolean(context, "aiMaxFinalUrlExpansionEnabled", false)
              || providerBoolean(context, "aiMaxDisableSearchTermMatching", false))) {
        issues.add(
            ProviderCampaignPlanSupport.error(
                "AI_MAX_URL_INCLUSIONS_REQUIRE_AUTOMATION",
                "providerOverride.aiMaxUrlInclusions",
                "AI Max URL inclusions require text customization, final URL expansion, and AI Max search-term matching to remain enabled."));
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
                "This business goal requires a conversion mapping for the selected Google Ads account."));
      } else if (context.conversionGoalMapping().getTargetType()
          != ConversionTargetType.PROVIDER_CONVERSION) {
        issues.add(
            ProviderCampaignPlanSupport.error(
                "GOOGLE_CONVERSION_ACTION_REQUIRED",
                "conversion",
                "Google deployment requires a synced Google conversion action rather than a standard event name."));
      } else if (!context.conversionGoalMapping().isValidated()) {
        issues.add(
            ProviderCampaignPlanSupport.error(
                "CONVERSION_MAPPING_UNVALIDATED",
                "conversion",
                "The selected Google conversion mapping is not validated."));
      } else if (ConversionGoalCompatibility.shouldWarn(
          context.campaign().getBusinessGoal(),
          context.channel().getProvider(),
          context.conversionGoalMapping().getProviderEventName())) {
        issues.add(
            ProviderCampaignPlanSupport.warning(
                "CONVERSION_GOAL_MISMATCH",
                "conversion",
                "The selected Google conversion category "
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

  private void validateCreative(
      ProviderCampaignCompileContext context, List<ProviderPlanIssue> issues) {
    if (context.creativePack() == null) {
      issues.add(
          ProviderCampaignPlanSupport.error(
              "CREATIVE_PACK_MISSING",
              "creativePack",
              "A Creative Pack is required for this Google channel."));
      return;
    }

    CreativeVariantType expected = expectedVariant(context.channel().getChannelType());
    boolean matchingVariant =
        context.creativeVariants().stream()
            .map(CreativeVariant::getVariantType)
            .anyMatch(type -> type == expected);
    if (!matchingVariant) {
      if (isBlank(context.creativePack().getHeadlinesJson())
          || isBlank(context.creativePack().getDescriptionsJson())
          || (isBlank(context.creativePack().getDestinationUrl())
              && isBlank(context.campaign().getLandingPageUrl()))) {
        issues.add(
            ProviderCampaignPlanSupport.error(
                "CREATIVE_VARIANT_MISSING",
                "creativePack",
                "No matching provider variant exists and the source Creative Pack is incomplete."));
      } else {
        issues.add(
            ProviderCampaignPlanSupport.warning(
                "CREATIVE_VARIANT_ADAPTATION_REQUIRED",
                "creativePack",
                "No matching provider variant exists; the deployment compiler must adapt the source Creative Pack."));
      }
    }

    if (context.channel().getChannelType() != CampaignChannelType.GOOGLE_SEARCH
        && context.creativeAssets().isEmpty()) {
      issues.add(
          ProviderCampaignPlanSupport.warning(
              "CREATIVE_MEDIA_MISSING",
              "creativePack.assets",
              "This visual Google channel has no media assets in the selected Creative Pack."));
    }
  }

  private void validateDisplayCreative(
      ProviderCampaignCompileContext context, List<ProviderPlanIssue> issues) {
    if (context.creativePack() == null) return;

    List<String> headlines = jsonStrings(context.creativePack().getHeadlinesJson());
    List<String> descriptions = jsonStrings(context.creativePack().getDescriptionsJson());
    if (headlines.isEmpty() || headlines.size() > 5) {
      issues.add(
          ProviderCampaignPlanSupport.error(
              "DISPLAY_HEADLINES_INVALID",
              "creativePack.headlines",
              "Responsive Display ads require 1 to 5 headlines."));
    } else if (headlines.stream().anyMatch(value -> value.length() > 30)) {
      issues.add(
          ProviderCampaignPlanSupport.error(
              "DISPLAY_HEADLINE_TOO_LONG",
              "creativePack.headlines",
              "Responsive Display headlines must be at most 30 characters."));
    }

    if (descriptions.isEmpty() || descriptions.size() > 5) {
      issues.add(
          ProviderCampaignPlanSupport.error(
              "DISPLAY_DESCRIPTIONS_INVALID",
              "creativePack.descriptions",
              "Responsive Display ads require 1 to 5 descriptions."));
    } else if (descriptions.stream().anyMatch(value -> value.length() > 90)) {
      issues.add(
          ProviderCampaignPlanSupport.error(
              "DISPLAY_DESCRIPTION_TOO_LONG",
              "creativePack.descriptions",
              "Responsive Display descriptions must be at most 90 characters."));
    }

    String businessName = context.creativePack().getBusinessName();
    if (isBlank(businessName)) {
      issues.add(
          ProviderCampaignPlanSupport.error(
              "DISPLAY_BUSINESS_NAME_MISSING",
              "creativePack.businessName",
              "Responsive Display ads require a business name."));
    } else if (businessName.trim().length() > 25) {
      issues.add(
          ProviderCampaignPlanSupport.error(
              "DISPLAY_BUSINESS_NAME_TOO_LONG",
              "creativePack.businessName",
              "Responsive Display business name must be at most 25 characters."));
    }

    String primaryMessage = context.creativePack().getPrimaryMessage();
    String longHeadline =
        !isBlank(primaryMessage) && primaryMessage.trim().length() <= 90
            ? primaryMessage.trim()
            : headlines.isEmpty() ? null : headlines.getFirst();
    if (isBlank(longHeadline) || longHeadline.length() > 90) {
      issues.add(
          ProviderCampaignPlanSupport.error(
              "DISPLAY_LONG_HEADLINE_INVALID",
              "creativePack.primaryMessage",
              "Responsive Display requires a long headline of at most 90 characters. Use Primary message or a headline."));
    }

    String finalUrl =
        !isBlank(context.creativePack().getDestinationUrl())
            ? context.creativePack().getDestinationUrl()
            : context.campaign().getLandingPageUrl();
    if (isBlank(finalUrl) || !isHttpUrl(finalUrl)) {
      issues.add(
          ProviderCampaignPlanSupport.error(
              "DISPLAY_FINAL_URL_INVALID",
              "creativePack.destinationUrl",
              "Responsive Display requires a valid HTTP or HTTPS destination URL."));
    }

    Set<CreativeAssetRole> roles =
        context.creativeAssets().stream()
            .map(asset -> asset.getRole())
            .collect(java.util.stream.Collectors.toSet());
    for (CreativeAssetRole required :
        List.of(
            CreativeAssetRole.MARKETING_IMAGE,
            CreativeAssetRole.SQUARE_MARKETING_IMAGE)) {
      if (!roles.contains(required)) {
        issues.add(
            ProviderCampaignPlanSupport.error(
                "DISPLAY_REQUIRED_ASSET_MISSING",
                "creativePack.assets",
                "Responsive Display requires a " + required.name() + " asset."));
      }
    }
  }

  private void validateDemandGenCreative(
      ProviderCampaignCompileContext context, List<ProviderPlanIssue> issues) {
    if (context.creativePack() == null) return;

    List<String> headlines = jsonStrings(context.creativePack().getHeadlinesJson());
    List<String> descriptions = jsonStrings(context.creativePack().getDescriptionsJson());
    if (headlines.isEmpty() || headlines.size() > 5) {
      issues.add(
          ProviderCampaignPlanSupport.error(
              "DEMAND_GEN_HEADLINES_INVALID",
              "creativePack.headlines",
              "Demand Gen multi-asset ads require 1 to 5 headlines."));
    } else if (headlines.stream().anyMatch(value -> value.length() > 30)) {
      issues.add(
          ProviderCampaignPlanSupport.error(
              "DEMAND_GEN_HEADLINE_TOO_LONG",
              "creativePack.headlines",
              "Demand Gen headlines must be at most 30 characters."));
    }
    if (descriptions.isEmpty() || descriptions.size() > 5) {
      issues.add(
          ProviderCampaignPlanSupport.error(
              "DEMAND_GEN_DESCRIPTIONS_INVALID",
              "creativePack.descriptions",
              "Demand Gen multi-asset ads require 1 to 5 descriptions."));
    } else if (descriptions.stream().anyMatch(value -> value.length() > 90)) {
      issues.add(
          ProviderCampaignPlanSupport.error(
              "DEMAND_GEN_DESCRIPTION_TOO_LONG",
              "creativePack.descriptions",
              "Demand Gen descriptions must be at most 90 characters."));
    }

    String businessName = context.creativePack().getBusinessName();
    if (isBlank(businessName)) {
      issues.add(
          ProviderCampaignPlanSupport.error(
              "DEMAND_GEN_BUSINESS_NAME_MISSING",
              "creativePack.businessName",
              "Demand Gen requires a business name."));
    } else if (businessName.trim().length() > 25) {
      issues.add(
          ProviderCampaignPlanSupport.error(
              "DEMAND_GEN_BUSINESS_NAME_TOO_LONG",
              "creativePack.businessName",
              "Demand Gen business name must be at most 25 characters."));
    }

    String finalUrl =
        !isBlank(context.creativePack().getDestinationUrl())
            ? context.creativePack().getDestinationUrl()
            : context.campaign().getLandingPageUrl();
    if (isBlank(finalUrl) || !isHttpUrl(finalUrl)) {
      issues.add(
          ProviderCampaignPlanSupport.error(
              "DEMAND_GEN_FINAL_URL_INVALID",
              "creativePack.destinationUrl",
              "Demand Gen requires a valid HTTP or HTTPS destination URL."));
    }

    Set<CreativeAssetRole> roles =
        context.creativeAssets().stream()
            .map(asset -> asset.getRole())
            .collect(java.util.stream.Collectors.toSet());
    if (!roles.contains(CreativeAssetRole.LOGO)) {
      issues.add(
          ProviderCampaignPlanSupport.error(
              "DEMAND_GEN_LOGO_MISSING",
              "creativePack.assets",
              "Demand Gen requires a square logo asset."));
    }
    if (!roles.contains(CreativeAssetRole.MARKETING_IMAGE)
        && !roles.contains(CreativeAssetRole.SQUARE_MARKETING_IMAGE)) {
      issues.add(
          ProviderCampaignPlanSupport.error(
              "DEMAND_GEN_IMAGE_MISSING",
              "creativePack.assets",
              "Demand Gen requires at least a landscape or square marketing image."));
    }
  }

  private void validatePerformanceMaxCreative(
      ProviderCampaignCompileContext context, List<ProviderPlanIssue> issues) {
    if (context.creativePack() == null) return;

    List<String> headlines = jsonStrings(context.creativePack().getHeadlinesJson());
    List<String> descriptions = jsonStrings(context.creativePack().getDescriptionsJson());
    if (headlines.size() < 3 || headlines.size() > 15) {
      issues.add(
          ProviderCampaignPlanSupport.error(
              "PMAX_HEADLINES_INVALID",
              "creativePack.headlines",
              "Performance Max requires 3 to 15 headlines."));
    } else if (headlines.stream().anyMatch(value -> value.length() > 30)) {
      issues.add(
          ProviderCampaignPlanSupport.error(
              "PMAX_HEADLINE_TOO_LONG",
              "creativePack.headlines",
              "Performance Max headlines must be at most 30 characters."));
    }
    if (descriptions.size() < 2 || descriptions.size() > 5) {
      issues.add(
          ProviderCampaignPlanSupport.error(
              "PMAX_DESCRIPTIONS_INVALID",
              "creativePack.descriptions",
              "Performance Max requires 2 to 5 descriptions."));
    } else {
      if (descriptions.stream().anyMatch(value -> value.length() > 90)) {
        issues.add(
            ProviderCampaignPlanSupport.error(
                "PMAX_DESCRIPTION_TOO_LONG",
                "creativePack.descriptions",
                "Performance Max descriptions must be at most 90 characters."));
      }
      if (descriptions.stream().noneMatch(value -> value.length() <= 60)) {
        issues.add(
            ProviderCampaignPlanSupport.error(
                "PMAX_SHORT_DESCRIPTION_REQUIRED",
                "creativePack.descriptions",
                "Performance Max requires at least one description of 60 characters or fewer."));
      }
    }
    String businessName = context.creativePack().getBusinessName();
    if (isBlank(businessName)) {
      issues.add(
          ProviderCampaignPlanSupport.error(
              "PMAX_BUSINESS_NAME_MISSING",
              "creativePack.businessName",
              "Performance Max requires a business name."));
    } else if (businessName.trim().length() > 25) {
      issues.add(
          ProviderCampaignPlanSupport.error(
              "PMAX_BUSINESS_NAME_TOO_LONG",
              "creativePack.businessName",
              "Performance Max business name must be at most 25 characters."));
    }

    String finalUrl =
        !isBlank(context.creativePack().getDestinationUrl())
            ? context.creativePack().getDestinationUrl()
            : context.campaign().getLandingPageUrl();
    if (isBlank(finalUrl) || !isHttpUrl(finalUrl)) {
      issues.add(
          ProviderCampaignPlanSupport.error(
              "PMAX_FINAL_URL_INVALID",
              "creativePack.destinationUrl",
              "Performance Max requires a valid HTTP or HTTPS destination URL."));
    }

    Set<CreativeAssetRole> roles =
        context.creativeAssets().stream()
            .map(asset -> asset.getRole())
            .collect(java.util.stream.Collectors.toSet());
    for (CreativeAssetRole required :
        List.of(
            CreativeAssetRole.MARKETING_IMAGE,
            CreativeAssetRole.SQUARE_MARKETING_IMAGE,
            CreativeAssetRole.LOGO)) {
      if (!roles.contains(required)) {
        issues.add(
            ProviderCampaignPlanSupport.error(
                "PMAX_REQUIRED_ASSET_MISSING",
                "creativePack.assets",
                "Performance Max requires a " + required.name() + " asset."));
      }
    }
  }

  private List<String> jsonStrings(String value) {
    if (isBlank(value)) return List.of();
    try {
      JsonNode node = JSON.readTree(value);
      if (!node.isArray()) return List.of();
      List<String> result = new ArrayList<>();
      for (JsonNode item : node) {
        if (item.isTextual() && !item.asText().isBlank()) result.add(item.asText().trim());
      }
      return List.copyOf(result);
    } catch (Exception exception) {
      return List.of();
    }
  }

  private void addOperations(
      ProviderCampaignCompileContext context,
      Long effectiveBudget,
      AudienceMappingStatus audienceMappingStatus,
      List<ProviderPlanOperation> operations) {
    int sequence = 1;
    if (context.channel().getChannelType() == CampaignChannelType.GOOGLE_DISPLAY) {
      operations.add(
          operation(
              sequence++,
              "create-display-paused",
              ProviderPlanAction.CREATE,
              ProviderPlanResource.CAMPAIGN,
              true,
              Set.of(
                  AdFeatureCapability.CAMPAIGN_CREATE,
                  AdFeatureCapability.GOOGLE_DISPLAY),
              ProviderCampaignPlanSupport.config(
                  "budgetMinor", effectiveBudget,
                  "budgetPeriod",
                  context.budgetPlan() == null ? null : context.budgetPlan().getBudgetPeriod(),
                  "providerOverrideJson", context.channel().getProviderOverrideJson())));
      if (context.audienceDefinition() != null || hasProviderAudienceLists(context)) {
        operations.add(
            operation(
                sequence++,
                "configure-display-targeting",
                ProviderPlanAction.CONFIGURE,
                ProviderPlanResource.CAMPAIGN_TARGETING,
                false,
                Set.of(),
                ProviderCampaignPlanSupport.config(
                    "audienceDefinitionId", context.audienceDefinition().getId().toString(),
                    "mappingStatus", AudienceMappingStatus.APPROXIMATE)));
      }
      operations.add(
          operation(
              sequence++,
              "create-responsive-display-ad",
              ProviderPlanAction.CREATE,
              ProviderPlanResource.CREATIVE,
              true,
              Set.of(AdFeatureCapability.CREATIVE_CREATE),
              ProviderCampaignPlanSupport.config(
                  "creativePackId",
                  context.creativePack() == null
                      ? null
                      : context.creativePack().getId().toString())));
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
                    "mappingId", context.conversionGoalMapping().getId().toString(),
                    "targetType", context.conversionGoalMapping().getTargetType())));
      }
      return;
    }
    if (context.channel().getChannelType() == CampaignChannelType.GOOGLE_SHOPPING) {
      operations.add(
          operation(
              sequence++,
              "create-shopping-paused",
              ProviderPlanAction.CREATE,
              ProviderPlanResource.CAMPAIGN,
              true,
              Set.of(
                  AdFeatureCapability.CAMPAIGN_CREATE,
                  AdFeatureCapability.GOOGLE_SHOPPING,
                  AdFeatureCapability.GOOGLE_MERCHANT),
              ProviderCampaignPlanSupport.config(
                  "budgetMinor", effectiveBudget,
                  "budgetPeriod",
                  context.budgetPlan() == null ? null : context.budgetPlan().getBudgetPeriod(),
                  "providerOverrideJson", context.channel().getProviderOverrideJson())));
      if (context.audienceDefinition() != null) {
        operations.add(
            operation(
                sequence++,
                "configure-shopping-targeting",
                ProviderPlanAction.CONFIGURE,
                ProviderPlanResource.CAMPAIGN_TARGETING,
                false,
                Set.of(),
                ProviderCampaignPlanSupport.config(
                    "audienceDefinitionId", context.audienceDefinition().getId().toString(),
                    "mappingStatus", AudienceMappingStatus.APPROXIMATE)));
      }
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
                    "mappingId", context.conversionGoalMapping().getId().toString(),
                    "targetType", context.conversionGoalMapping().getTargetType())));
      }
      return;
    }
    if (context.channel().getChannelType() == CampaignChannelType.GOOGLE_DEMAND_GEN) {
      operations.add(
          operation(
              sequence++,
              "create-demand-gen-paused",
              ProviderPlanAction.CREATE,
              ProviderPlanResource.CAMPAIGN,
              true,
              Set.of(
                  AdFeatureCapability.CAMPAIGN_CREATE,
                  AdFeatureCapability.GOOGLE_DEMAND_GEN),
              ProviderCampaignPlanSupport.config(
                  "budgetMinor", effectiveBudget,
                  "budgetPeriod",
                  context.budgetPlan() == null ? null : context.budgetPlan().getBudgetPeriod(),
                  "providerOverrideJson", context.channel().getProviderOverrideJson())));
      operations.add(
          operation(
              sequence++,
              "configure-demand-gen-targeting",
              ProviderPlanAction.CONFIGURE,
              ProviderPlanResource.CAMPAIGN_TARGETING,
              false,
              Set.of(),
              ProviderCampaignPlanSupport.config(
                  "audienceDefinitionId",
                  context.audienceDefinition() == null
                      ? null
                      : context.audienceDefinition().getId().toString(),
                  "mappingStatus", AudienceMappingStatus.APPROXIMATE)));
      operations.add(
          operation(
              sequence++,
              "create-demand-gen-multi-asset-ad",
              ProviderPlanAction.CREATE,
              ProviderPlanResource.CREATIVE,
              true,
              Set.of(AdFeatureCapability.CREATIVE_CREATE),
              ProviderCampaignPlanSupport.config(
                  "creativePackId",
                  context.creativePack() == null
                      ? null
                      : context.creativePack().getId().toString())));
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
                    "mappingId", context.conversionGoalMapping().getId().toString(),
                    "targetType", context.conversionGoalMapping().getTargetType())));
      }
      return;
    }
    if (context.channel().getChannelType() == CampaignChannelType.GOOGLE_PERFORMANCE_MAX) {
      operations.add(
          operation(
              sequence++,
              "create-performance-max-paused",
              ProviderPlanAction.CREATE,
              ProviderPlanResource.CAMPAIGN,
              true,
              Set.of(
                  AdFeatureCapability.CAMPAIGN_CREATE,
                  AdFeatureCapability.GOOGLE_PERFORMANCE_MAX,
                  AdFeatureCapability.CREATIVE_CREATE),
              ProviderCampaignPlanSupport.config(
                  "budgetMinor",
                  effectiveBudget,
                  "budgetPeriod",
                  context.budgetPlan() == null ? null : context.budgetPlan().getBudgetPeriod(),
                  "creativePackId",
                  context.creativePack() == null ? null : context.creativePack().getId().toString(),
                  "brandGuidelinesEnabled",
                  providerBoolean(context, "pmaxBrandGuidelinesEnabled", true),
                  "brandMainColor",
                  providerText(context, "pmaxBrandMainColor"),
                  "brandAccentColor",
                  providerText(context, "pmaxBrandAccentColor"),
                  "brandFontFamily",
                  providerText(context, "pmaxBrandFontFamily"),
                  "providerOverrideJson",
                  context.channel().getProviderOverrideJson())));
      if (context.audienceDefinition() != null) {
        operations.add(
            operation(
                sequence++,
                "configure-pmax-targeting",
                ProviderPlanAction.CONFIGURE,
                ProviderPlanResource.CAMPAIGN_TARGETING,
                false,
                Set.of(),
                ProviderCampaignPlanSupport.config(
                    "audienceDefinitionId", context.audienceDefinition().getId().toString())));
      }
      if (context.audienceDefinition() != null || hasProviderAudienceLists(context)) {
        operations.add(
            operation(
                sequence++,
                "configure-audience-signals",
                ProviderPlanAction.CONFIGURE,
                ProviderPlanResource.AUDIENCE_SIGNALS,
                false,
                Set.of(),
                ProviderCampaignPlanSupport.config(
                    "audienceDefinitionId",
                    context.audienceDefinition() == null
                        ? null
                        : context.audienceDefinition().getId().toString(),
                    "mappingStatus", AudienceMappingStatus.APPROXIMATE)));
      }
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
                    "mappingId", context.conversionGoalMapping().getId().toString(),
                    "targetType", context.conversionGoalMapping().getTargetType())));
      }
      return;
    }
    operations.add(
        operation(
            sequence++,
            "create-budget",
            ProviderPlanAction.CREATE,
            ProviderPlanResource.CAMPAIGN_BUDGET,
            false,
            Set.of(AdFeatureCapability.CAMPAIGN_CREATE),
            ProviderCampaignPlanSupport.config(
                "budgetMinor",
                effectiveBudget,
                "budgetPeriod",
                context.budgetPlan() == null ? null : context.budgetPlan().getBudgetPeriod())));
    operations.add(
        operation(
            sequence++,
            "create-campaign-paused",
            ProviderPlanAction.CREATE,
            ProviderPlanResource.CAMPAIGN,
            true,
            Set.of(
                AdFeatureCapability.CAMPAIGN_CREATE,
                channelCapability(context.channel().getChannelType())),
            ProviderCampaignPlanSupport.config(
                "name",
                context.campaign().getName(),
                "businessGoal",
                context.campaign().getBusinessGoal(),
                "startAt",
                context.campaign().getStartAt(),
                "endAt",
                context.campaign().getEndAt(),
                "providerOverrideJson",
                context.channel().getProviderOverrideJson())));

    if (context.audienceDefinition() != null
        || context.channel().getChannelType() == CampaignChannelType.GOOGLE_SEARCH) {
      operations.add(
          operation(
              sequence++,
              "configure-targeting",
              ProviderPlanAction.CONFIGURE,
              ProviderPlanResource.CAMPAIGN_TARGETING,
              false,
              Set.of(),
              ProviderCampaignPlanSupport.config(
                  "audienceDefinitionId",
                  context.audienceDefinition() == null
                      ? null
                      : context.audienceDefinition().getId().toString(),
                  "mappingStatus",
                  audienceMappingStatus == null
                      ? AudienceMappingStatus.EXACT
                      : audienceMappingStatus)));
    }

    switch (context.channel().getChannelType()) {
      case GOOGLE_SEARCH -> {
        operations.add(
            operation(
                sequence++,
                "create-ad-group",
                ProviderPlanAction.CREATE,
                ProviderPlanResource.AD_GROUP,
                true,
                Set.of(),
                ProviderCampaignPlanSupport.config("status", "PAUSED")));
        if (hasSearchAudienceCriteria(context)) {
          operations.add(
              operation(
                  sequence++,
                  "configure-search-audiences",
                  ProviderPlanAction.CONFIGURE,
                  ProviderPlanResource.AD_GROUP,
                  false,
                  Set.of(),
                  ProviderCampaignPlanSupport.config(
                      "mode",
                      providerText(context, "googleSearchAudienceMode") == null
                          ? "OBSERVATION"
                          : providerText(context, "googleSearchAudienceMode"),
                      "includedCount",
                      providerStrings(context, "googleAudienceIds").size(),
                      "excludedCount",
                      providerStrings(context, "googleExcludedAudienceIds").size())));
        }
        if (!providerStrings(context, "googleSearchInterestResources").isEmpty()
            || !providerStrings(context, "googleSearchExcludedInterestResources").isEmpty()) {
          operations.add(
              operation(
                  sequence++,
                  "configure-search-interests",
                  ProviderPlanAction.CONFIGURE,
                  ProviderPlanResource.AD_GROUP,
                  false,
                  Set.of(),
                  ProviderCampaignPlanSupport.config(
                      "includedCount",
                      providerStrings(context, "googleSearchInterestResources").size(),
                      "excludedCount",
                      providerStrings(context, "googleSearchExcludedInterestResources").size())));
        }
        if (aiMaxEnabled(context)) {
          operations.add(
              operation(
                  sequence++,
                  "configure-ai-max-ad-group",
                  ProviderPlanAction.CONFIGURE,
                  ProviderPlanResource.AD_GROUP,
                  false,
                  Set.of(AdFeatureCapability.GOOGLE_AI_MAX),
                  ProviderCampaignPlanSupport.config(
                      "disableSearchTermMatching",
                      providerBoolean(
                          context, "aiMaxDisableSearchTermMatching", false))));
          if (!providerStrings(context, "aiMaxUrlExclusions").isEmpty()) {
            operations.add(
                operation(
                    sequence++,
                    "configure-ai-max-url-exclusions",
                    ProviderPlanAction.CONFIGURE,
                    ProviderPlanResource.CAMPAIGN_TARGETING,
                    false,
                    Set.of(AdFeatureCapability.GOOGLE_AI_MAX),
                    ProviderCampaignPlanSupport.config(
                        "urlExclusions",
                        providerStrings(context, "aiMaxUrlExclusions"))));
          }
          if (!providerStrings(context, "aiMaxUrlInclusions").isEmpty()) {
            operations.add(
                operation(
                    sequence++,
                    "configure-ai-max-url-inclusions",
                    ProviderPlanAction.CONFIGURE,
                    ProviderPlanResource.AD_GROUP,
                    false,
                    Set.of(AdFeatureCapability.GOOGLE_AI_MAX),
                    ProviderCampaignPlanSupport.config(
                        "urlInclusions",
                        providerStrings(context, "aiMaxUrlInclusions"))));
          }
          if (!providerObjectIds(context, "aiMaxBrandInclusions").isEmpty()
              || !providerObjectIds(context, "aiMaxBrandExclusions").isEmpty()) {
            operations.add(
                operation(
                    sequence++,
                    "configure-ai-max-brands",
                    ProviderPlanAction.CONFIGURE,
                    ProviderPlanResource.CAMPAIGN_TARGETING,
                    false,
                    Set.of(AdFeatureCapability.GOOGLE_AI_MAX),
                    ProviderCampaignPlanSupport.config(
                        "brandInclusionCount",
                        providerObjectIds(context, "aiMaxBrandInclusions").size(),
                        "brandExclusionCount",
                        providerObjectIds(context, "aiMaxBrandExclusions").size())));
          }
        }
        operations.add(
            operation(
                sequence++,
                "configure-keywords",
                ProviderPlanAction.CONFIGURE,
                ProviderPlanResource.KEYWORDS,
                false,
                Set.of(),
                ProviderCampaignPlanSupport.config(
                    "source",
                    "AUDIENCE_SEARCH_INTENT",
                    "audienceDefinitionId",
                    context.audienceDefinition() == null
                        ? null
                        : context.audienceDefinition().getId().toString())));
        operations.add(
            operation(
                sequence++,
                "configure-negative-keywords",
                ProviderPlanAction.CONFIGURE,
                ProviderPlanResource.NEGATIVE_KEYWORDS,
                false,
                Set.of(),
                ProviderCampaignPlanSupport.config(
                    "source",
                    "PROVIDER_OVERRIDE",
                    "providerOverrideJson",
                    context.channel().getProviderOverrideJson())));
        operations.add(creativeOperation(sequence++, context, ProviderPlanResource.CREATIVE));
      }
      case GOOGLE_PERFORMANCE_MAX ->
          throw new IllegalStateException("Performance Max operations must be emitted atomically");
      case GOOGLE_DISPLAY ->
          throw new IllegalStateException("Display operations must use the dedicated plan");
      case GOOGLE_DEMAND_GEN ->
          throw new IllegalStateException("Demand Gen operations must use the dedicated plan");
      case GOOGLE_SHOPPING ->
          throw new IllegalStateException("Shopping operations must use the dedicated plan");
      default -> throw new IllegalStateException("Unsupported Google channel");
    }

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
                  context.conversionGoalMapping().getTargetType())));
    }
  }

  private ProviderPlanOperation creativeOperation(
      int sequence, ProviderCampaignCompileContext context, ProviderPlanResource resource) {
    return operation(
        sequence,
        "create-provider-creative",
        ProviderPlanAction.CREATE,
        resource,
        true,
        Set.of(AdFeatureCapability.CREATIVE_CREATE),
        ProviderCampaignPlanSupport.config(
            "creativePackId",
            context.creativePack() == null ? null : context.creativePack().getId().toString(),
            "variantType",
            expectedVariant(context.channel().getChannelType())));
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

  private AdFeatureCapability channelCapability(CampaignChannelType type) {
    return switch (type) {
      case GOOGLE_SEARCH -> AdFeatureCapability.GOOGLE_SEARCH;
      case GOOGLE_PERFORMANCE_MAX -> AdFeatureCapability.GOOGLE_PERFORMANCE_MAX;
      case GOOGLE_DEMAND_GEN -> AdFeatureCapability.GOOGLE_DEMAND_GEN;
      case GOOGLE_DISPLAY -> AdFeatureCapability.GOOGLE_DISPLAY;
      case GOOGLE_SHOPPING -> AdFeatureCapability.GOOGLE_SHOPPING;
      default -> throw new IllegalArgumentException("Not a Google campaign channel: " + type);
    };
  }

  private AudienceMappingStatus googleAudienceMappingStatus(
      ProviderCampaignCompileContext context) {
    if (context.channel().getChannelType() != CampaignChannelType.GOOGLE_SEARCH) {
      return AudienceMappingStatus.APPROXIMATE;
    }
    var audience = context.audienceDefinition();
    boolean unsupported =
        !isBlank(audience.getCustomerSegmentsJson())
            || !isBlank(audience.getRetargetingJson())
            || !isBlank(audience.getExclusionsJson())
            || !isBlank(audience.getInterestsJson());
    return unsupported ? AudienceMappingStatus.APPROXIMATE : AudienceMappingStatus.EXACT;
  }

  private CreativeVariantType expectedVariant(CampaignChannelType type) {
    return switch (type) {
      case GOOGLE_SEARCH -> CreativeVariantType.GOOGLE_RSA;
      case GOOGLE_PERFORMANCE_MAX -> CreativeVariantType.GOOGLE_PERFORMANCE_MAX;
      case GOOGLE_DEMAND_GEN -> CreativeVariantType.GOOGLE_DEMAND_GEN;
      case GOOGLE_DISPLAY -> CreativeVariantType.GOOGLE_DISPLAY;
      default -> throw new IllegalArgumentException(
          "Channel has no Google creative variant: " + type);
    };
  }

  private boolean hasProviderAudienceLists(
      ProviderCampaignCompileContext context) {
    return !providerStrings(context, "googleAudienceIds").isEmpty()
        || !providerStrings(context, "googleExcludedAudienceIds").isEmpty();
  }

  private boolean hasSearchAudienceCriteria(
      ProviderCampaignCompileContext context) {
    return hasProviderAudienceLists(context)
        || !providerStrings(context, "googleSearchInterestResources").isEmpty()
        || !providerStrings(context, "googleSearchExcludedInterestResources").isEmpty();
  }

  private boolean hasProviderAudienceSelection(
      ProviderCampaignCompileContext context) {
    return context.channel().getChannelType() == CampaignChannelType.GOOGLE_SEARCH
        ? hasSearchAudienceCriteria(context)
        : hasProviderAudienceLists(context);
  }

  private boolean aiMaxEnabled(ProviderCampaignCompileContext context) {
    return providerBoolean(context, "aiMaxEnabled", false);
  }

  private boolean providerBoolean(
      ProviderCampaignCompileContext context, String key, boolean fallback) {
    JsonNode root = providerOverride(context);
    JsonNode value = root.get(key);
    return value == null || value.isNull() ? fallback : value.asBoolean(fallback);
  }

  private List<String> providerObjectIds(
      ProviderCampaignCompileContext context, String key) {
    JsonNode value = providerOverride(context).get(key);
    if (value == null || value.isNull() || !value.isArray()) return List.of();
    List<String> result = new ArrayList<>();
    for (JsonNode item : value) {
      String id = item.isObject() ? item.path("id").asText(null) : null;
      if (id != null && !id.isBlank()) result.add(id.trim());
    }
    return List.copyOf(result);
  }

  private List<String> providerStrings(
      ProviderCampaignCompileContext context, String key) {
    JsonNode value = providerOverride(context).get(key);
    if (value == null || value.isNull() || !value.isArray()) return List.of();
    List<String> result = new ArrayList<>();
    for (JsonNode item : value) {
      if (item.isTextual() && !item.asText().isBlank()) {
        result.add(item.asText().trim());
      }
    }
    return List.copyOf(result);
  }

  private String providerText(ProviderCampaignCompileContext context, String key) {
    JsonNode root = providerOverride(context);
    JsonNode value = root.get(key);
    if (value == null || value.isNull()) return null;
    String text = value.asText(null);
    return text == null || text.isBlank() ? null : text.trim();
  }

  private JsonNode providerOverride(ProviderCampaignCompileContext context) {
    String json = context.channel().getProviderOverrideJson();
    if (json == null || json.isBlank()) return JSON.createObjectNode();
    try {
      return JSON.readTree(json);
    } catch (Exception exception) {
      return JSON.createObjectNode();
    }
  }

  private boolean isHttpUrl(String value) {
    try {
      java.net.URI uri = java.net.URI.create(value.trim());
      return "http".equalsIgnoreCase(uri.getScheme())
          || "https".equalsIgnoreCase(uri.getScheme());
    } catch (RuntimeException exception) {
      return false;
    }
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
