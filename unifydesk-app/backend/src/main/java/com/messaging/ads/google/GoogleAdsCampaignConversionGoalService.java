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
    if (mapping == null
        || mapping.getProvider() != AdProvider.GOOGLE_ADS
        || mapping.getTargetType() != ConversionTargetType.PROVIDER_CONVERSION
        || mapping.getAdConversionId() == null
        || mapping.getExternalConversionId() == null
        || mapping.getExternalConversionId().isBlank()) {
      throw new BadRequestException(
          "Google campaign conversion goal requires a validated provider conversion mapping");
    }

    GoogleAdsAccountContextService.Context context =
        contextService.requireManaged(companyId, accountId);
    AdAccount account = context.account();
    String campaignId = numeric(externalCampaignId, "Google campaign ID");
    String conversionActionId =
        numeric(mapping.getExternalConversionId(), "Google conversion action ID");

    String conversionCustomerId = conversionCustomerId(context);
    String conversionActionResource =
        apiClient.customerResourceName(
            conversionCustomerId, "conversionActions", conversionActionId);
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

    return new ApplyResult(customGoalResource, created, updated);
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
    if (value == null || !value.trim().matches("\\d+")) {
      throw new BadRequestException(label + " is invalid");
    }
    return value.trim();
  }

  private record ExistingGoal(
      String resourceName, String status, List<String> conversionActions) {}

  public record ApplyResult(
      String customConversionGoalResource, boolean created, boolean updated) {}
}
