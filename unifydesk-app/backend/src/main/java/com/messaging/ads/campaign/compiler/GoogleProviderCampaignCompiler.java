package com.messaging.ads.campaign.compiler;

import com.messaging.ads.capability.AdFeatureCapability;
import com.messaging.ads.campaign.entity.CreativeVariant;
import com.messaging.ads.campaign.enums.AudienceMappingStatus;
import com.messaging.ads.campaign.enums.BudgetPeriod;
import com.messaging.ads.campaign.enums.CampaignChannelType;
import com.messaging.ads.campaign.enums.ConversionTargetType;
import com.messaging.ads.campaign.enums.CreativeVariantType;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class GoogleProviderCampaignCompiler implements ProviderCampaignCompiler {

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
      issues.add(
          ProviderCampaignPlanSupport.warning(
              "AUDIENCE_NOT_SELECTED",
              "audience",
              "No canonical audience is selected; provider defaults/broad targeting will be used where valid."));
    } else {
      audienceMappingStatus = googleAudienceMappingStatus(context);
      if (audienceMappingStatus == AudienceMappingStatus.EXACT) {
        audienceMappingNote =
            "Canonical geo, language, and Search intent targeting can be represented directly for Google Search.";
      } else {
        audienceMappingNote =
            context.channel().getChannelType() == CampaignChannelType.GOOGLE_SEARCH
                ? "Geo and language targeting are mapped, but this audience also contains controls not yet applied to Google Search."
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

  private void addOperations(
      ProviderCampaignCompileContext context,
      Long effectiveBudget,
      AudienceMappingStatus audienceMappingStatus,
      List<ProviderPlanOperation> operations) {
    int sequence = 1;
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
      case GOOGLE_PERFORMANCE_MAX -> {
        operations.add(
            operation(
                sequence++,
                "create-asset-group",
                ProviderPlanAction.CREATE,
                ProviderPlanResource.ASSET_GROUP,
                true,
                Set.of(),
                ProviderCampaignPlanSupport.config("status", "PAUSED")));
        operations.add(
            operation(
                sequence++,
                "attach-asset-group-assets",
                ProviderPlanAction.ATTACH,
                ProviderPlanResource.ASSET_GROUP_ASSETS,
                false,
                Set.of(AdFeatureCapability.CREATIVE_CREATE),
                ProviderCampaignPlanSupport.config(
                    "creativePackId",
                    context.creativePack() == null
                        ? null
                        : context.creativePack().getId().toString())));
        if (context.audienceDefinition() != null) {
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
                      context.audienceDefinition().getId().toString(),
                      "mappingStatus",
                      AudienceMappingStatus.APPROXIMATE)));
        }
      }
      case GOOGLE_DISPLAY, GOOGLE_DEMAND_GEN -> {
        operations.add(
            operation(
                sequence++,
                "create-ad-group",
                ProviderPlanAction.CREATE,
                ProviderPlanResource.AD_GROUP,
                true,
                Set.of(),
                ProviderCampaignPlanSupport.config("status", "PAUSED")));
        operations.add(creativeOperation(sequence++, context, ProviderPlanResource.CREATIVE));
      }
      case GOOGLE_SHOPPING -> {
        operations.add(
            operation(
                sequence++,
                "create-shopping-ad-group",
                ProviderPlanAction.CREATE,
                ProviderPlanResource.AD_GROUP,
                true,
                Set.of(AdFeatureCapability.GOOGLE_SHOPPING),
                ProviderCampaignPlanSupport.config("status", "PAUSED")));
        operations.add(
            operation(
                sequence++,
                "configure-listing-group",
                ProviderPlanAction.CONFIGURE,
                ProviderPlanResource.LISTING_GROUP,
                false,
                Set.of(AdFeatureCapability.GOOGLE_MERCHANT),
                ProviderCampaignPlanSupport.config("source", "MERCHANT_CENTER")));
        operations.add(
            operation(
                sequence++,
                "create-product-ad",
                ProviderPlanAction.CREATE,
                ProviderPlanResource.PRODUCT_AD,
                true,
                Set.of(AdFeatureCapability.GOOGLE_SHOPPING),
                ProviderCampaignPlanSupport.config("status", "PAUSED")));
      }
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
        audience.getAgeMin() != null
            || audience.getAgeMax() != null
            || !isBlank(audience.getGendersJson())
            || !isBlank(audience.getCustomerSegmentsJson())
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

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
