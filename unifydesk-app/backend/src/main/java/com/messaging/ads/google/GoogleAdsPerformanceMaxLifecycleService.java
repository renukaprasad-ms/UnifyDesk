package com.messaging.ads.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.messaging.ads.entity.AdAudience;
import com.messaging.ads.entity.AdCampaign;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.dto.GoogleLifecycleGoalResponse;
import com.messaging.ads.google.dto.GoogleLifecycleGoalUpsertRequest;
import com.messaging.ads.google.dto.GooglePerformanceMaxLifecycleRequest;
import com.messaging.ads.google.dto.GooglePerformanceMaxLifecycleResponse;
import com.messaging.ads.google.dto.GoogleUserListCustomerTypeRequest;
import com.messaging.ads.google.dto.GoogleUserListCustomerTypeResponse;
import com.messaging.ads.repository.AdAudienceRepository;
import com.messaging.ads.repository.AdCampaignRepository;
import com.messaging.ads.service.AdActivityLogService;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GoogleAdsPerformanceMaxLifecycleService {

  private static final Set<String> GOAL_TYPES =
      Set.of("NEW_CUSTOMER_ACQUISITION", "CUSTOMER_RETENTION", "LOYALTY_RETENTION");
  private static final Set<String> TARGET_OPTIONS = Set.of("TARGET_ALL", "TARGET_SPECIFIC");
  private static final Set<String> CUSTOMER_TYPES =
      Set.of(
          "ALL_CUSTOMERS",
          "CART_ABANDONERS",
          "CONVERTED_LEADS",
          "DISENGAGED_CUSTOMERS",
          "HIGH_VALUE_CUSTOMERS",
          "LOYALTY_TIER_1_MEMBERS",
          "LOYALTY_TIER_2_MEMBERS",
          "LOYALTY_TIER_3_MEMBERS",
          "LOYALTY_TIER_4_MEMBERS",
          "LOYALTY_TIER_5_MEMBERS",
          "LOYALTY_TIER_6_MEMBERS",
          "LOYALTY_TIER_7_MEMBERS",
          "PAID_SUBSCRIBERS",
          "PURCHASERS",
          "QUALIFIED_LEADS");

  private final GoogleAdsAccountContextService contextService;
  private final GoogleAdsApiClient apiClient;
  private final AdAudienceRepository audienceRepository;
  private final AdCampaignRepository campaignRepository;
  private final AdActivityLogService activityLogService;

  public List<GoogleLifecycleGoalResponse> goals(Long companyId, Long accountId) {
    var context = contextService.requireManaged(companyId, accountId);
    return goalRows(context).stream().map(this::goalResponse).toList();
  }

  public GoogleLifecycleGoalResponse upsertGoal(
      Long companyId, Long accountId, GoogleLifecycleGoalUpsertRequest request, Long actorUserId) {
    var context = contextService.requireManaged(companyId, accountId);
    String goalType = goalType(request.goalType());
    validateGoalValues(
        goalType,
        request.additionalValue(),
        request.additionalHighLifetimeValue(),
        request.valueMultiplier());

    GoogleLifecycleGoalResponse before =
        goals(companyId, accountId).stream()
            .filter(goal -> goalType.equals(goal.goalType()))
            .findFirst()
            .orElse(null);

    Map<String, Object> settings =
        accountSettings(
            goalType,
            request.additionalValue(),
            request.additionalHighLifetimeValue(),
            request.valueMultiplier());

    if (before == null) {
      Map<String, Object> create = new LinkedHashMap<>();
      create.put("goalType", goalType);
      create.putAll(settings);
      apiClient.mutate(
          context.accessToken(),
          context.account().getExternalAccountId(),
          context.loginCustomerId(),
          "goals",
          List.of(Map.of("create", create)));
    } else {
      Map<String, Object> update = new LinkedHashMap<>();
      update.put("resourceName", before.resourceName());
      update.putAll(settings);
      apiClient.mutate(
          context.accessToken(),
          context.account().getExternalAccountId(),
          context.loginCustomerId(),
          "goals",
          List.of(Map.of("update", update, "updateMask", accountUpdateMask(goalType))));
    }

    GoogleLifecycleGoalResponse after =
        goals(companyId, accountId).stream()
            .filter(goal -> goalType.equals(goal.goalType()))
            .findFirst()
            .orElseThrow(
                () -> new IllegalStateException("Google lifecycle goal could not be reloaded"));

    activityLogService.record(
        companyId,
        accountId,
        AdProvider.GOOGLE_ADS,
        actorUserId,
        "GOOGLE_LIFECYCLE_GOAL_UPSERTED",
        "GOAL",
        after.goalId(),
        before,
        after,
        Map.of("goalType", goalType));

    return after;
  }

  public List<GoogleUserListCustomerTypeResponse> customerTypes(Long companyId, Long accountId) {
    var context = contextService.requireManaged(companyId, accountId);
    Map<String, AdAudience> localByExternal = new HashMap<>();
    for (AdAudience audience :
        audienceRepository.findByCompanyIdAndAdAccountIdOrderByProviderUpdatedAtDesc(
            companyId, accountId)) {
      if (audience.getProvider() == AdProvider.GOOGLE_ADS
          && audience.getExternalAudienceId() != null) {
        localByExternal.put(audience.getExternalAudienceId(), audience);
      }
    }

    String query =
        "SELECT user_list_customer_type.resource_name, "
            + "user_list_customer_type.user_list, "
            + "user_list_customer_type.customer_type_category "
            + "FROM user_list_customer_type";

    List<GoogleUserListCustomerTypeResponse> result = new ArrayList<>();
    for (JsonNode row :
        apiClient.searchRows(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            query)) {
      JsonNode type = row.path("userListCustomerType");
      String userListResource = text(type, "userList");
      String externalId = idFromResource(userListResource);
      AdAudience local = localByExternal.get(externalId);
      result.add(
          new GoogleUserListCustomerTypeResponse(
              text(type, "resourceName"),
              userListResource,
              externalId,
              local == null ? null : local.getId(),
              local == null ? null : local.getName(),
              text(type, "customerTypeCategory")));
    }
    return List.copyOf(result);
  }

  public List<GoogleUserListCustomerTypeResponse> replaceCustomerTypes(
      Long companyId, Long accountId, GoogleUserListCustomerTypeRequest request, Long actorUserId) {
    var context = contextService.requireManaged(companyId, accountId);
    AdAudience audience =
        audienceRepository
            .findByIdAndCompanyId(request.audienceId(), companyId)
            .orElseThrow(() -> new NotFoundException("Advertising audience not found"));
    if (audience.getProvider() != AdProvider.GOOGLE_ADS
        || !accountId.equals(audience.getAdAccountId())
        || !audience.isAccessible()) {
      throw new BadRequestException(
          "Lifecycle customer type requires an accessible Google audience from this account");
    }

    String externalId = numeric(audience.getExternalAudienceId(), "Google audience ID");
    List<String> categories = normalizeCustomerTypes(request.customerTypeCategories());
    validateCustomerTypeConflicts(categories);

    List<GoogleUserListCustomerTypeResponse> before =
        customerTypes(companyId, accountId).stream()
            .filter(value -> externalId.equals(value.externalUserListId()))
            .toList();

    List<Map<String, Object>> operations = new ArrayList<>();
    for (GoogleUserListCustomerTypeResponse existing : before) {
      if (existing.resourceName() != null) {
        operations.add(Map.of("remove", existing.resourceName()));
      }
    }

    String userListResource =
        apiClient.customerResourceName(
            context.account().getExternalAccountId(), "userLists", externalId);
    for (String category : categories) {
      operations.add(
          Map.of(
              "create",
              Map.of(
                  "userList", userListResource,
                  "customerTypeCategory", category)));
    }

    if (!operations.isEmpty()) {
      apiClient.mutate(
          context.accessToken(),
          context.account().getExternalAccountId(),
          context.loginCustomerId(),
          "userListCustomerTypes",
          operations);
    }

    List<GoogleUserListCustomerTypeResponse> after =
        customerTypes(companyId, accountId).stream()
            .filter(value -> externalId.equals(value.externalUserListId()))
            .toList();

    activityLogService.record(
        companyId,
        accountId,
        AdProvider.GOOGLE_ADS,
        actorUserId,
        "GOOGLE_USER_LIST_CUSTOMER_TYPES_REPLACED",
        "AUDIENCE",
        externalId,
        before,
        after,
        Map.of("audienceId", request.audienceId()));

    return after;
  }

  public void validateCampaignConfiguration(
      Long companyId, Long accountId, GooglePerformanceMaxLifecycleRequest request) {
    String type = goalType(request.goalType());
    String target = targetOption(request.targetOption(), type);
    validateCampaignValues(
        type,
        target,
        request.additionalValue(),
        request.additionalHighLifetimeValue(),
        request.valueMultiplier());

    boolean goalExists =
        goals(companyId, accountId).stream().anyMatch(goal -> type.equals(goal.goalType()));
    if (!goalExists) {
      throw new BadRequestException(
          "Configure the Google "
              + type
              + " account lifecycle goal before launching this Performance Max campaign");
    }

    List<GoogleUserListCustomerTypeResponse> classifications = customerTypes(companyId, accountId);
    if (classifications.isEmpty()) {
      throw new BadRequestException(
          "Classify at least one Google customer list before using lifecycle optimization");
    }
    if ("LOYALTY_RETENTION".equals(type)
        && classifications.stream()
            .noneMatch(
                value ->
                    value.customerTypeCategory() != null
                        && value.customerTypeCategory().startsWith("LOYALTY_TIER_"))) {
      throw new BadRequestException(
          "Loyalty Retention requires at least one Google customer list classified into a loyalty tier");
    }
  }

  public List<GooglePerformanceMaxLifecycleResponse> campaignLifecycle(
      Long companyId, Long campaignId) {
    AdCampaign campaign = requirePerformanceMaxCampaign(companyId, campaignId);
    var context = contextService.requireManaged(companyId, campaign.getAdAccountId());
    return campaignGoalRows(context, campaign.getExternalCampaignId()).stream()
        .map(this::campaignGoalResponse)
        .toList();
  }

  public GooglePerformanceMaxLifecycleResponse upsertCampaignLifecycle(
      Long companyId,
      Long campaignId,
      GooglePerformanceMaxLifecycleRequest request,
      Long actorUserId) {
    AdCampaign campaign = requirePerformanceMaxCampaign(companyId, campaignId);
    var context = contextService.requireManaged(companyId, campaign.getAdAccountId());
    String type = goalType(request.goalType());
    String targetOption = targetOption(request.targetOption(), type);

    validateCampaignValues(
        type,
        targetOption,
        request.additionalValue(),
        request.additionalHighLifetimeValue(),
        request.valueMultiplier());

    GoogleLifecycleGoalResponse accountGoal =
        goals(companyId, campaign.getAdAccountId()).stream()
            .filter(goal -> type.equals(goal.goalType()))
            .findFirst()
            .orElseThrow(
                () ->
                    new BadRequestException(
                        "Create the Google "
                            + type
                            + " account lifecycle goal before applying it to this Performance Max campaign"));

    GooglePerformanceMaxLifecycleResponse before =
        campaignLifecycle(companyId, campaignId).stream()
            .filter(config -> type.equals(config.goalType()))
            .findFirst()
            .orElse(null);

    Map<String, Object> settings = campaignSettings(type, targetOption, request);
    String campaignResource =
        apiClient.customerResourceName(
            context.account().getExternalAccountId(),
            "campaigns",
            numeric(campaign.getExternalCampaignId(), "Google campaign ID"));

    if (before == null) {
      Map<String, Object> create = new LinkedHashMap<>();
      create.put("campaign", campaignResource);
      create.put("goal", accountGoal.resourceName());
      create.putAll(settings);
      apiClient.mutate(
          context.accessToken(),
          context.account().getExternalAccountId(),
          context.loginCustomerId(),
          "campaignGoalConfigs",
          List.of(Map.of("create", create)));
    } else {
      Map<String, Object> update = new LinkedHashMap<>();
      update.put("resourceName", before.resourceName());
      update.putAll(settings);
      apiClient.mutate(
          context.accessToken(),
          context.account().getExternalAccountId(),
          context.loginCustomerId(),
          "campaignGoalConfigs",
          List.of(Map.of("update", update, "updateMask", campaignUpdateMask(type))));
    }

    GooglePerformanceMaxLifecycleResponse after =
        campaignLifecycle(companyId, campaignId).stream()
            .filter(config -> type.equals(config.goalType()))
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Google campaign lifecycle config could not be reloaded"));

    activityLogService.record(
        companyId,
        campaign.getAdAccountId(),
        AdProvider.GOOGLE_ADS,
        actorUserId,
        "GOOGLE_PMAX_LIFECYCLE_CONFIG_UPSERTED",
        "CAMPAIGN",
        campaign.getExternalCampaignId(),
        before,
        after,
        Map.of("goalType", type));

    return after;
  }

  public List<GooglePerformanceMaxLifecycleResponse> removeCampaignLifecycle(
      Long companyId, Long campaignId, String requestedGoalType, Long actorUserId) {
    AdCampaign campaign = requirePerformanceMaxCampaign(companyId, campaignId);
    var context = contextService.requireManaged(companyId, campaign.getAdAccountId());
    String type = goalType(requestedGoalType);

    GooglePerformanceMaxLifecycleResponse before =
        campaignLifecycle(companyId, campaignId).stream()
            .filter(config -> type.equals(config.goalType()))
            .findFirst()
            .orElse(null);
    if (before == null) return campaignLifecycle(companyId, campaignId);

    apiClient.mutate(
        context.accessToken(),
        context.account().getExternalAccountId(),
        context.loginCustomerId(),
        "campaignGoalConfigs",
        List.of(Map.of("remove", before.resourceName())));

    List<GooglePerformanceMaxLifecycleResponse> after = campaignLifecycle(companyId, campaignId);

    activityLogService.record(
        companyId,
        campaign.getAdAccountId(),
        AdProvider.GOOGLE_ADS,
        actorUserId,
        "GOOGLE_PMAX_LIFECYCLE_CONFIG_REMOVED",
        "CAMPAIGN",
        campaign.getExternalCampaignId(),
        before,
        null,
        Map.of("goalType", type));

    return after;
  }

  private List<JsonNode> goalRows(GoogleAdsAccountContextService.Context context) {
    return apiClient.searchRows(
        context.accessToken(),
        context.account().getExternalAccountId(),
        context.loginCustomerId(),
        "SELECT goal.resource_name, goal.goal_id, goal.goal_type, "
            + "goal.new_customer_acquisition_goal_settings.value_settings.additional_value, "
            + "goal.new_customer_acquisition_goal_settings.value_settings.additional_high_lifetime_value, "
            + "goal.new_customer_acquisition_goal_settings.value_settings.value_multiplier, "
            + "goal.new_customer_acquisition_goal_settings.value_settings.high_lifetime_value_multiplier, "
            + "goal.retention_goal_settings.value_settings.additional_value, "
            + "goal.retention_goal_settings.value_settings.additional_high_lifetime_value, "
            + "goal.retention_goal_settings.value_settings.value_multiplier, "
            + "goal.retention_goal_settings.value_settings.high_lifetime_value_multiplier, "
            + "goal.loyalty_retention_goal_settings.value_settings.value_multiplier "
            + "FROM goal");
  }

  private List<JsonNode> campaignGoalRows(
      GoogleAdsAccountContextService.Context context, String externalCampaignId) {
    String campaignId = numeric(externalCampaignId, "Google campaign ID");
    return apiClient.searchRows(
        context.accessToken(),
        context.account().getExternalAccountId(),
        context.loginCustomerId(),
        "SELECT campaign_goal_config.resource_name, campaign_goal_config.campaign, "
            + "campaign_goal_config.goal, campaign_goal_config.goal_type, "
            + "campaign_goal_config.campaign_new_customer_acquisition_settings.target_option, "
            + "campaign_goal_config.campaign_new_customer_acquisition_settings.value_settings_override.additional_value, "
            + "campaign_goal_config.campaign_new_customer_acquisition_settings.value_settings_override.additional_high_lifetime_value, "
            + "campaign_goal_config.campaign_new_customer_acquisition_settings.value_settings_override.value_multiplier, "
            + "campaign_goal_config.campaign_new_customer_acquisition_settings.value_settings_override.high_lifetime_value_multiplier, "
            + "campaign_goal_config.campaign_retention_settings.target_option, "
            + "campaign_goal_config.campaign_retention_settings.value_settings_override.additional_value, "
            + "campaign_goal_config.campaign_retention_settings.value_settings_override.additional_high_lifetime_value, "
            + "campaign_goal_config.campaign_retention_settings.value_settings_override.value_multiplier, "
            + "campaign_goal_config.campaign_retention_settings.value_settings_override.high_lifetime_value_multiplier, "
            + "campaign_goal_config.campaign_loyalty_retention_settings.value_settings_override.value_multiplier, "
            + "campaign_goal_config.campaign_loyalty_retention_settings.enable_bid_adjustments_for_loyalty_members, "
            + "campaign_goal_config.campaign_loyalty_retention_settings.show_targeted_loyalty_member_benefits_in_pla "
            + "FROM campaign_goal_config WHERE campaign.id = "
            + campaignId);
  }

  private GoogleLifecycleGoalResponse goalResponse(JsonNode row) {
    JsonNode goal = row.path("goal");
    String type = text(goal, "goalType");
    JsonNode settings =
        switch (type == null ? "" : type) {
          case "NEW_CUSTOMER_ACQUISITION" ->
              goal.path("newCustomerAcquisitionGoalSettings").path("valueSettings");
          case "CUSTOMER_RETENTION" -> goal.path("retentionGoalSettings").path("valueSettings");
          case "LOYALTY_RETENTION" ->
              goal.path("loyaltyRetentionGoalSettings").path("valueSettings");
          default -> goal.path("missing");
        };
    return new GoogleLifecycleGoalResponse(
        text(goal, "resourceName"),
        text(goal, "goalId"),
        type,
        number(settings, "additionalValue"),
        number(settings, "additionalHighLifetimeValue"),
        number(settings, "valueMultiplier"),
        number(settings, "highLifetimeValueMultiplier"));
  }

  private GooglePerformanceMaxLifecycleResponse campaignGoalResponse(JsonNode row) {
    JsonNode config = row.path("campaignGoalConfig");
    String type = text(config, "goalType");
    JsonNode settings;
    String target = null;
    Boolean bidAdjustments = null;
    Boolean plaBenefits = null;
    if ("NEW_CUSTOMER_ACQUISITION".equals(type)) {
      JsonNode value = config.path("campaignNewCustomerAcquisitionSettings");
      target = text(value, "targetOption");
      settings = value.path("valueSettingsOverride");
    } else if ("CUSTOMER_RETENTION".equals(type)) {
      JsonNode value = config.path("campaignRetentionSettings");
      target = text(value, "targetOption");
      settings = value.path("valueSettingsOverride");
    } else if ("LOYALTY_RETENTION".equals(type)) {
      JsonNode value = config.path("campaignLoyaltyRetentionSettings");
      settings = value.path("valueSettingsOverride");
      bidAdjustments = nullableBoolean(value, "enableBidAdjustmentsForLoyaltyMembers");
      plaBenefits = nullableBoolean(value, "showTargetedLoyaltyMemberBenefitsInPla");
    } else {
      settings = config.path("missing");
    }

    return new GooglePerformanceMaxLifecycleResponse(
        text(config, "resourceName"),
        text(config, "goal"),
        idFromResource(text(config, "goal")),
        type,
        target,
        number(settings, "additionalValue"),
        number(settings, "additionalHighLifetimeValue"),
        number(settings, "valueMultiplier"),
        number(settings, "highLifetimeValueMultiplier"),
        bidAdjustments,
        plaBenefits);
  }

  private Map<String, Object> accountSettings(
      String type, Double value, Double highValue, Double multiplier) {
    Map<String, Object> valueSettings = new LinkedHashMap<>();
    if ("LOYALTY_RETENTION".equals(type)) {
      if (multiplier != null) valueSettings.put("valueMultiplier", multiplier);
      return Map.of("loyaltyRetentionGoalSettings", Map.of("valueSettings", valueSettings));
    }
    if (value != null) valueSettings.put("additionalValue", value);
    if (highValue != null) valueSettings.put("additionalHighLifetimeValue", highValue);
    String key =
        "NEW_CUSTOMER_ACQUISITION".equals(type)
            ? "newCustomerAcquisitionGoalSettings"
            : "retentionGoalSettings";
    return Map.of(key, Map.of("valueSettings", valueSettings));
  }

  private Map<String, Object> campaignSettings(
      String type, String targetOption, GooglePerformanceMaxLifecycleRequest request) {
    Map<String, Object> settings = new LinkedHashMap<>();
    Map<String, Object> values = new LinkedHashMap<>();

    if ("NEW_CUSTOMER_ACQUISITION".equals(type) || "CUSTOMER_RETENTION".equals(type)) {
      settings.put("targetOption", targetOption);
      if (request.additionalValue() != null) {
        values.put("additionalValue", request.additionalValue());
      }
      if (request.additionalHighLifetimeValue() != null) {
        values.put("additionalHighLifetimeValue", request.additionalHighLifetimeValue());
      }
      if (request.valueMultiplier() != null) {
        values.put("valueMultiplier", request.valueMultiplier());
      }
      if (!values.isEmpty()) settings.put("valueSettingsOverride", values);
      return Map.of(
          "NEW_CUSTOMER_ACQUISITION".equals(type)
              ? "campaignNewCustomerAcquisitionSettings"
              : "campaignRetentionSettings",
          settings);
    }

    if (request.valueMultiplier() != null) {
      values.put("valueMultiplier", request.valueMultiplier());
      settings.put("valueSettingsOverride", values);
    }
    if (request.enableBidAdjustmentsForLoyaltyMembers() != null) {
      settings.put(
          "enableBidAdjustmentsForLoyaltyMembers", request.enableBidAdjustmentsForLoyaltyMembers());
    }
    if (request.showTargetedLoyaltyMemberBenefitsInPla() != null) {
      settings.put(
          "showTargetedLoyaltyMemberBenefitsInPla",
          request.showTargetedLoyaltyMemberBenefitsInPla());
    }
    return Map.of("campaignLoyaltyRetentionSettings", settings);
  }

  private String accountUpdateMask(String type) {
    return switch (type) {
      case "NEW_CUSTOMER_ACQUISITION" -> "new_customer_acquisition_goal_settings.value_settings";
      case "CUSTOMER_RETENTION" -> "retention_goal_settings.value_settings";
      case "LOYALTY_RETENTION" -> "loyalty_retention_goal_settings.value_settings";
      default -> throw new IllegalStateException("Unsupported lifecycle goal type");
    };
  }

  private String campaignUpdateMask(String type) {
    return switch (type) {
      case "NEW_CUSTOMER_ACQUISITION" -> "campaign_new_customer_acquisition_settings";
      case "CUSTOMER_RETENTION" -> "campaign_retention_settings";
      case "LOYALTY_RETENTION" -> "campaign_loyalty_retention_settings";
      default -> throw new IllegalStateException("Unsupported lifecycle goal type");
    };
  }

  private void validateGoalValues(String type, Double value, Double highValue, Double multiplier) {
    if ("LOYALTY_RETENTION".equals(type)) {
      if (multiplier == null || multiplier <= 0) {
        throw new BadRequestException(
            "Google Loyalty Retention requires a positive value multiplier");
      }
      if (value != null || highValue != null) {
        throw new BadRequestException(
            "Google Loyalty Retention uses valueMultiplier rather than additional values");
      }
      return;
    }
    if (multiplier != null) {
      throw new BadRequestException(
          "Google currently supports additional-value lifecycle settings for this goal type; value multiplier is not enabled here");
    }
    validateAdditionalValues(value, highValue);
  }

  private void validateCampaignValues(
      String type, String targetOption, Double value, Double highValue, Double multiplier) {
    if ("LOYALTY_RETENTION".equals(type)) {
      if (value != null || highValue != null) {
        throw new BadRequestException(
            "Google Loyalty Retention campaign overrides use valueMultiplier");
      }
      if (multiplier != null && multiplier <= 0) {
        throw new BadRequestException("Google lifecycle value multiplier must be positive");
      }
      return;
    }
    if ("TARGET_SPECIFIC".equals(targetOption)
        && (value != null || highValue != null || multiplier != null)) {
      throw new BadRequestException(
          "Google TARGET_SPECIFIC lifecycle mode cannot use campaign value overrides");
    }
    if (multiplier != null && multiplier <= 0) {
      throw new BadRequestException("Google lifecycle value multiplier must be positive");
    }
    validateAdditionalValues(value, highValue);
  }

  private void validateAdditionalValues(Double value, Double highValue) {
    if (value != null && value < 0) {
      throw new BadRequestException("Google lifecycle additional value cannot be negative");
    }
    if (highValue != null) {
      if (value == null) {
        throw new BadRequestException(
            "Google lifecycle high lifetime value requires an additional value");
      }
      if (highValue <= value) {
        throw new BadRequestException(
            "Google lifecycle high lifetime value must be greater than the additional value");
      }
    }
  }

  private List<String> normalizeCustomerTypes(List<String> values) {
    List<String> result = new ArrayList<>();
    for (String value : values == null ? List.<String>of() : values) {
      String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
      if (!CUSTOMER_TYPES.contains(normalized)) {
        throw new BadRequestException("Unsupported Google lifecycle customer type: " + value);
      }
      if (!result.contains(normalized)) result.add(normalized);
    }
    return List.copyOf(result);
  }

  private void validateCustomerTypeConflicts(List<String> values) {
    Set<String> types = new HashSet<>(values);
    conflict(types, "PURCHASERS", "CONVERTED_LEADS");
    conflict(types, "PURCHASERS", "QUALIFIED_LEADS");
    conflict(types, "PURCHASERS", "CART_ABANDONERS");
    conflict(types, "CONVERTED_LEADS", "QUALIFIED_LEADS");
    conflict(types, "DISENGAGED_CUSTOMERS", "CONVERTED_LEADS");
    conflict(types, "DISENGAGED_CUSTOMERS", "QUALIFIED_LEADS");
    conflict(types, "DISENGAGED_CUSTOMERS", "CART_ABANDONERS");

    long loyaltyTiers = types.stream().filter(value -> value.startsWith("LOYALTY_TIER_")).count();
    if (loyaltyTiers > 1) {
      throw new BadRequestException("A Google user list cannot belong to multiple loyalty tiers");
    }
  }

  private void conflict(Set<String> values, String left, String right) {
    if (values.contains(left) && values.contains(right)) {
      throw new BadRequestException(
          "Google lifecycle customer types conflict: " + left + " and " + right);
    }
  }

  private String goalType(String value) {
    String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    if (!GOAL_TYPES.contains(normalized)) {
      throw new BadRequestException(
          "Google lifecycle goal must be NEW_CUSTOMER_ACQUISITION, CUSTOMER_RETENTION, or LOYALTY_RETENTION");
    }
    return normalized;
  }

  private String targetOption(String value, String goalType) {
    if ("LOYALTY_RETENTION".equals(goalType)) return null;
    String normalized =
        value == null || value.isBlank() ? "TARGET_ALL" : value.trim().toUpperCase(Locale.ROOT);
    if (!TARGET_OPTIONS.contains(normalized)) {
      throw new BadRequestException(
          "Google lifecycle target option must be TARGET_ALL or TARGET_SPECIFIC");
    }
    return normalized;
  }

  private AdCampaign requirePerformanceMaxCampaign(Long companyId, Long campaignId) {
    AdCampaign campaign =
        campaignRepository
            .findByIdAndCompanyId(campaignId, companyId)
            .orElseThrow(() -> new NotFoundException("Advertising campaign not found"));
    if (campaign.getProvider() != AdProvider.GOOGLE_ADS
        || !"PERFORMANCE_MAX".equalsIgnoreCase(campaign.getObjective())
        || !campaign.isAccessible()) {
      throw new BadRequestException(
          "Google lifecycle configuration is available only for accessible Performance Max campaigns");
    }
    return campaign;
  }

  private String text(JsonNode node, String field) {
    if (node == null || node.isNull()) return null;
    JsonNode value = node.get(field);
    if (value == null || value.isNull()) return null;
    String result = value.asText(null);
    return result == null || result.isBlank() ? null : result;
  }

  private Double number(JsonNode node, String field) {
    if (node == null || node.isNull()) return null;
    JsonNode value = node.get(field);
    return value == null || value.isNull() || !value.isNumber() ? null : value.doubleValue();
  }

  private Boolean nullableBoolean(JsonNode node, String field) {
    if (node == null || node.isNull()) return null;
    JsonNode value = node.get(field);
    return value == null || value.isNull() || !value.isBoolean() ? null : value.booleanValue();
  }

  private String idFromResource(String value) {
    if (value == null || value.isBlank()) return null;
    int slash = value.lastIndexOf('/');
    return slash < 0 || slash == value.length() - 1 ? value : value.substring(slash + 1);
  }

  private String numeric(String value, String label) {
    if (value == null || !value.matches("\\d+")) {
      throw new BadRequestException("Invalid " + label);
    }
    return value;
  }
}
