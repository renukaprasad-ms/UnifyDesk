package com.messaging.ads.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.messaging.ads.campaign.entity.ConversionGoalMapping;
import com.messaging.ads.campaign.enums.ConversionTargetType;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.enums.AdProvider;
import com.messaging.common.exception.BadRequestException;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GoogleAdsCampaignConversionGoalService {

  private final GoogleAdsAccountContextService contextService;
  private final GoogleAdsApiClient apiClient;

  public ApplyResult apply(
      Long companyId,
      Long accountId,
      String externalCampaignId,
      Long reachlyoCampaignId,
      ConversionGoalMapping mapping) {
    ValidatedMapping validated = validatedMapping(companyId, accountId, mapping);
    GoogleAdsAccountContextService.Context context = validated.context();
    AdAccount account = context.account();
    String campaignId = numeric(externalCampaignId, "Google campaign ID");
    String conversionCustomerId = validated.conversionCustomerId();
    String conversionActionResource = validated.conversionActionResource();
    String goalName =
        "UnifyDesk campaign "
            + reachlyoCampaignId
            + " conversion mapping "
            + mapping.getId();

    ExistingGoal existing =
        existingGoal(context, conversionCustomerId, goalName);
    String customGoalResource;
    boolean created;
    boolean updated;

    if (existing == null) {
      JsonNode response =
          apiClient.mutate(
              context.accessToken(),
              conversionCustomerId,
              context.loginCustomerId(),
              "customConversionGoals",
              List.of(
                  Map.of(
                      "create",
                      Map.of(
                          "name",
                          goalName,
                          "conversionActions",
                          List.of(conversionActionResource),
                          "status",
                          "ENABLED"))));
      customGoalResource = apiClient.firstMutatedResourceName(response);
      if (customGoalResource == null || customGoalResource.isBlank()) {
        throw new BadRequestException(
            "Google Ads created the custom conversion goal without returning a resource name");
      }
      created = true;
      updated = false;
    } else {
      customGoalResource = existing.resourceName();
      created = false;
      updated = !existing.conversionActions().equals(List.of(conversionActionResource))
          || !"ENABLED".equalsIgnoreCase(existing.status());
      if (updated) {
        apiClient.mutate(
            context.accessToken(),
            conversionCustomerId,
            context.loginCustomerId(),
            "customConversionGoals",
            List.of(
                Map.of(
                    "update",
                    Map.of(
                        "resourceName",
                        customGoalResource,
                        "conversionActions",
                        List.of(conversionActionResource),
                        "status",
                        "ENABLED"),
                    "updateMask",
                    "conversion_actions,status")));
      }
    }

    String configResource =
        apiClient.customerResourceName(
            account.getExternalAccountId(),
            "conversionGoalCampaignConfigs",
            campaignId);
    apiClient.mutate(
        context.accessToken(),
        account.getExternalAccountId(),
        context.loginCustomerId(),
        "conversionGoalCampaignConfigs",
        List.of(
            Map.of(
                "update",
                Map.of(
                    "resourceName", configResource,
                    "customConversionGoal", customGoalResource),
                "updateMask",
                "custom_conversion_goal")));

    int disabledCampaignGoals =
        disableOtherCampaignGoals(context, account.getExternalAccountId(), campaignId);

    return new ApplyResult(
        customGoalResource, created, updated, disabledCampaignGoals);
  }

  public void validate(
      Long companyId, Long accountId, ConversionGoalMapping mapping) {
    validatedMapping(companyId, accountId, mapping);
  }

  private ValidatedMapping validatedMapping(
      Long companyId, Long accountId, ConversionGoalMapping mapping) {
    if (mapping == null
        || mapping.getProvider() != AdProvider.GOOGLE_ADS
        || mapping.getTargetType() != ConversionTargetType.PROVIDER_CONVERSION
        || mapping.getAdConversionId() == null
        || mapping.getExternalConversionId() == null
        || mapping.getExternalConversionId().isBlank()
        || !mapping.isValidated()) {
      throw new BadRequestException(
          "Google campaign conversion goal requires a validated provider conversion mapping");
    }

    GoogleAdsAccountContextService.Context context =
        contextService.requireManaged(companyId, accountId);
    String conversionCustomerId = conversionCustomerId(context);
    String conversionActionId =
        numeric(mapping.getExternalConversionId(), "Google conversion action ID");
    List<JsonNode> rows =
        apiClient.searchRows(
            context.accessToken(),
            conversionCustomerId,
            context.loginCustomerId(),
            "SELECT conversion_action.id, conversion_action.status, "
                + "conversion_action.category, conversion_action.origin "
                + "FROM conversion_action WHERE conversion_action.id = "
                + conversionActionId
                + " LIMIT 1");
    if (rows.isEmpty()) {
      throw new BadRequestException(
          "Selected Google conversion action is no longer available");
    }
    JsonNode action = rows.getFirst().path("conversionAction");
    String status = action.path("status").asText("");
    if ("REMOVED".equalsIgnoreCase(status) || "HIDDEN".equalsIgnoreCase(status)) {
      throw new BadRequestException(
          "Selected Google conversion action is no longer active");
    }
    String resource =
        apiClient.customerResourceName(
            conversionCustomerId, "conversionActions", conversionActionId);
    return new ValidatedMapping(context, conversionCustomerId, resource);
  }

  private int disableOtherCampaignGoals(
      GoogleAdsAccountContextService.Context context,
      String customerId,
      String campaignId) {
    List<JsonNode> rows =
        apiClient.searchRows(
            context.accessToken(),
            customerId,
            context.loginCustomerId(),
            "SELECT campaign_conversion_goal.resource_name, "
                + "campaign_conversion_goal.biddable "
                + "FROM campaign_conversion_goal "
                + "WHERE campaign.id = "
                + campaignId);
    List<Map<String, Object>> operations = new java.util.ArrayList<>();
    for (JsonNode row : rows) {
      JsonNode goal = row.path("campaignConversionGoal");
      if (!goal.path("biddable").asBoolean(false)) continue;
      String resourceName = goal.path("resourceName").asText(null);
      if (resourceName == null || resourceName.isBlank()) continue;
      operations.add(
          Map.of(
              "update",
              Map.of("resourceName", resourceName, "biddable", false),
              "updateMask",
              "biddable"));
    }
    if (!operations.isEmpty()) {
      apiClient.mutate(
          context.accessToken(),
          customerId,
          context.loginCustomerId(),
          "campaignConversionGoals",
          operations);
    }
    return operations.size();
  }

  private String conversionCustomerId(
      GoogleAdsAccountContextService.Context context) {
    List<JsonNode> rows =
        apiClient.searchRows(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            "SELECT customer.conversion_tracking_setting.google_ads_conversion_customer "
                + "FROM customer LIMIT 1");
    if (rows.isEmpty()) {
      throw new BadRequestException(
          "Google Ads conversion tracking settings are unavailable for this account");
    }
    String resource =
        rows.getFirst()
            .path("customer")
            .path("conversionTrackingSetting")
            .path("googleAdsConversionCustomer")
            .asText(null);
    if (resource == null || resource.isBlank()) {
      throw new BadRequestException(
          "Google Ads account does not expose a conversion customer");
    }
    return numericCustomerId(resource);
  }

  private ExistingGoal existingGoal(
      GoogleAdsAccountContextService.Context context,
      String conversionCustomerId,
      String goalName) {
    List<JsonNode> rows =
        apiClient.searchRows(
            context.accessToken(),
            conversionCustomerId,
            context.loginCustomerId(),
            "SELECT custom_conversion_goal.resource_name, custom_conversion_goal.name, "
                + "custom_conversion_goal.status, custom_conversion_goal.conversion_actions "
                + "FROM custom_conversion_goal WHERE custom_conversion_goal.status != 'REMOVED'");
    for (JsonNode row : rows) {
      JsonNode goal = row.path("customConversionGoal");
      if (!goalName.equals(goal.path("name").asText(null))) continue;
      String resourceName = goal.path("resourceName").asText(null);
      if (resourceName == null || resourceName.isBlank()) continue;
      List<String> actions = new java.util.ArrayList<>();
      JsonNode actionArray = goal.path("conversionActions");
      if (actionArray.isArray()) {
        actionArray.forEach(
            action -> {
              if (action.isTextual() && !action.asText().isBlank()) {
                actions.add(action.asText());
              }
            });
      }
      return new ExistingGoal(
          resourceName, goal.path("status").asText(null), List.copyOf(actions));
    }
    return null;
  }

  private String numericCustomerId(String value) {
    if (value == null) return "";
    String normalized = value.trim();
    if (normalized.startsWith("customers/")) {
      normalized = normalized.substring("customers/".length());
    }
    return numeric(normalized.replace("-", ""), "Google conversion customer ID");
  }

  private String numeric(String value, String label) {
    if (value == null) {
      throw new BadRequestException(label + " is invalid");
    }
    String normalized = value.trim();
    int slash = normalized.lastIndexOf('/');
    if (slash >= 0 && slash < normalized.length() - 1) {
      normalized = normalized.substring(slash + 1);
    }
    normalized = normalized.replace("-", "");
    if (!normalized.matches("\\d+")) {
      throw new BadRequestException(label + " is invalid");
    }
    return normalized;
  }

  private record ExistingGoal(
      String resourceName, String status, List<String> conversionActions) {}

  private record ValidatedMapping(
      GoogleAdsAccountContextService.Context context,
      String conversionCustomerId,
      String conversionActionResource) {}

  public record ApplyResult(
      String customConversionGoalResource,
      boolean created,
      boolean updated,
      int disabledCampaignGoals) {}
}
