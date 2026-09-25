package com.messaging.ads.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.dto.GoogleNativeRecommendation;
import com.messaging.ads.google.dto.GoogleRecommendationActionResponse;
import com.messaging.ads.service.AdActivityLogService;
import com.messaging.common.exception.BadRequestException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GoogleAdsNativeRecommendationService {

  private static final int MAX_ACTIONS = 100;

  private final GoogleAdsAccountContextService contextService;
  private final GoogleAdsApiClient apiClient;
  private final AdActivityLogService activityLogService;

  public List<GoogleNativeRecommendation> list(Long companyId, Long accountId) {
    var context = contextService.requireManaged(companyId, accountId);

    String query =
        "SELECT recommendation.resource_name, recommendation.type, "
            + "recommendation.dismissed, recommendation.campaign, "
            + "recommendation.ad_group, recommendation.impact "
            + "FROM recommendation "
            + "WHERE recommendation.dismissed = FALSE";

    return apiClient
        .searchRows(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            query)
        .stream()
        .map(this::map)
        .toList();
  }

  public GoogleRecommendationActionResponse apply(
      Long companyId,
      Long accountId,
      List<String> resourceNames,
      Long actorUserId) {
    return act(companyId, accountId, resourceNames, actorUserId, true);
  }

  public GoogleRecommendationActionResponse dismiss(
      Long companyId,
      Long accountId,
      List<String> resourceNames,
      Long actorUserId) {
    return act(companyId, accountId, resourceNames, actorUserId, false);
  }

  private GoogleRecommendationActionResponse act(
      Long companyId,
      Long accountId,
      List<String> values,
      Long actorUserId,
      boolean apply) {
    var context = contextService.requireManaged(companyId, accountId);
    List<String> resourceNames = validate(context, values);

    List<Map<String, Object>> operations =
        resourceNames.stream()
            .map(name -> Map.<String, Object>of("resourceName", name))
            .toList();

    String actionPath = apply ? "recommendations:apply" : "recommendations:dismiss";
    JsonNode response =
        apiClient.customerAction(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            actionPath,
            Map.of("operations", operations, "partialFailure", false));

    List<String> processed = new ArrayList<>();
    JsonNode results = response.path("results");
    if (results.isArray()) {
      for (JsonNode result : results) {
        String resourceName = result.path("resourceName").asText(null);
        if (resourceName != null && !resourceName.isBlank()) processed.add(resourceName);
      }
    }
    if (processed.isEmpty()) processed.addAll(resourceNames);

    activityLogService.record(
        companyId,
        accountId,
        AdProvider.GOOGLE_ADS,
        actorUserId,
        apply
            ? "GOOGLE_RECOMMENDATIONS_APPLIED"
            : "GOOGLE_RECOMMENDATIONS_DISMISSED",
        "RECOMMENDATION",
        null,
        null,
        Map.of(
            "action", apply ? "APPLY" : "DISMISS",
            "processedCount", processed.size()),
        Map.of("resourceNames", processed));

    return new GoogleRecommendationActionResponse(
        apply ? "APPLY" : "DISMISS",
        processed.size(),
        List.copyOf(processed));
  }

  private List<String> validate(
      GoogleAdsAccountContextService.Context context, List<String> values) {
    if (values == null || values.isEmpty()) {
      throw new BadRequestException("Select at least one Google recommendation");
    }

    Set<String> unique = new LinkedHashSet<>();
    String prefix =
        "customers/"
            + context.account().getExternalAccountId().replace("-", "")
            + "/recommendations/";

    for (String value : values) {
      if (value == null || value.isBlank() || !value.trim().startsWith(prefix)) {
        throw new BadRequestException(
            "Google recommendation does not belong to the selected ad account");
      }
      unique.add(value.trim());
    }

    if (unique.size() > MAX_ACTIONS) {
      throw new BadRequestException(
          "Google recommendation actions are limited to 100 recommendations per request");
    }
    return List.copyOf(unique);
  }

  private GoogleNativeRecommendation map(JsonNode row) {
    JsonNode recommendation = row.path("recommendation");
    return new GoogleNativeRecommendation(
        text(recommendation, "resourceName"),
        text(recommendation, "type"),
        recommendation.path("dismissed").asBoolean(false),
        text(recommendation, "campaign"),
        text(recommendation, "adGroup"),
        recommendation.has("impact") ? recommendation.path("impact").toString() : null);
  }

  private String text(JsonNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || value.isNull() || value.asText().isBlank()) return null;
    return value.asText().trim();
  }
}
