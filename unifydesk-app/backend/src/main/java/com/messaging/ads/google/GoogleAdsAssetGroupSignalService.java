package com.messaging.ads.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.messaging.ads.entity.AdAudience;
import com.messaging.ads.entity.AdCampaign;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.dto.GoogleAssetGroupSignalResponse;
import com.messaging.ads.google.dto.GoogleAssetGroupSignalsRequest;
import com.messaging.ads.repository.AdAudienceRepository;
import com.messaging.ads.repository.AdCampaignRepository;
import com.messaging.ads.service.AdActivityLogService;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GoogleAdsAssetGroupSignalService {

  private static final int MAX_SEARCH_THEMES = 25;

  private final GoogleAdsAccountContextService contextService;
  private final GoogleAdsApiClient apiClient;
  private final AdCampaignRepository campaignRepository;
  private final AdAudienceRepository audienceRepository;
  private final AdActivityLogService activityLogService;

  public List<GoogleAssetGroupSignalResponse> list(
      Long companyId, Long campaignId, String assetGroupId) {
    AdCampaign campaign = requirePerformanceMaxCampaign(companyId, campaignId);
    var context = contextService.requireManaged(companyId, campaign.getAdAccountId());
    String normalizedGroupId = numeric(assetGroupId);

    String query =
        "SELECT asset_group_signal.resource_name, "
            + "asset_group_signal.search_theme.text, "
            + "asset_group_signal.audience.audience, "
            + "asset_group_signal.approval_status, "
            + "asset_group_signal.disapproval_reasons "
            + "FROM asset_group_signal "
            + "WHERE asset_group.id = "
            + normalizedGroupId;

    return apiClient
        .searchRows(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            query)
        .stream()
        .map(this::response)
        .toList();
  }

  public List<GoogleAssetGroupSignalResponse> add(
      Long companyId,
      Long campaignId,
      String assetGroupId,
      GoogleAssetGroupSignalsRequest request,
      Long actorUserId) {
    AdCampaign campaign = requirePerformanceMaxCampaign(companyId, campaignId);
    var context = contextService.requireManaged(companyId, campaign.getAdAccountId());
    String normalizedGroupId = numeric(assetGroupId);
    String assetGroupResource =
        resource(context.account().getExternalAccountId(), "assetGroups", normalizedGroupId);

    List<GoogleAssetGroupSignalResponse> existing =
        list(companyId, campaignId, normalizedGroupId);

    Set<String> existingThemes = new LinkedHashSet<>();
    existing.stream()
        .filter(signal -> "SEARCH_THEME".equals(signal.type()))
        .map(GoogleAssetGroupSignalResponse::value)
        .filter(value -> value != null && !value.isBlank())
        .map(value -> value.trim().toLowerCase())
        .forEach(existingThemes::add);
    boolean hasAudienceSignal =
        existing.stream().anyMatch(signal -> "AUDIENCE".equals(signal.type()));

    List<String> requestedThemes =
        request.searchThemes() == null
            ? List.of()
            : request.searchThemes().stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();

    if (existingThemes.size() + requestedThemes.stream()
            .filter(value -> !existingThemes.contains(value.toLowerCase()))
            .count()
        > MAX_SEARCH_THEMES) {
      throw new BadRequestException(
          "Performance Max supports at most 25 search themes per asset group");
    }

    List<Map<String, Object>> signalOperations = new ArrayList<>();
    for (String theme : requestedThemes) {
      if (theme.length() > 80) {
        throw new BadRequestException(
            "Performance Max search themes must be at most 80 characters");
      }
      if (existingThemes.contains(theme.toLowerCase())) continue;
      signalOperations.add(
          Map.of(
              "create",
              Map.of(
                  "assetGroup", assetGroupResource,
                  "searchTheme", Map.of("text", theme))));
    }

    if (request.audienceIds() != null
        && !request.audienceIds().isEmpty()
        && !hasAudienceSignal) {
      String audienceResource =
          createScopedAudience(
              companyId,
              campaign.getAdAccountId(),
              assetGroupResource,
              request.audienceIds(),
              context);

      signalOperations.add(
          Map.of(
              "create",
              Map.of(
                  "assetGroup", assetGroupResource,
                  "audience", Map.of("audience", audienceResource))));
    }

    if (signalOperations.isEmpty()) {
      return existing;
    }

    apiClient.mutate(
        context.accessToken(),
        context.account().getExternalAccountId(),
        context.loginCustomerId(),
        "assetGroupSignals",
        signalOperations);

    List<GoogleAssetGroupSignalResponse> after =
        list(companyId, campaignId, normalizedGroupId);

    activityLogService.record(
        companyId,
        campaign.getAdAccountId(),
        AdProvider.GOOGLE_ADS,
        actorUserId,
        "GOOGLE_ASSET_GROUP_SIGNALS_ADDED",
        "ASSET_GROUP",
        normalizedGroupId,
        existing,
        after,
        Map.of(
            "searchThemeCount", requestedThemes.size(),
            "audienceCount",
                request.audienceIds() == null ? 0 : request.audienceIds().size(),
            "campaignId", campaign.getExternalCampaignId()));

    return after;
  }

  private String createScopedAudience(
      Long companyId,
      Long adAccountId,
      String assetGroupResource,
      List<Long> audienceIds,
      GoogleAdsAccountContextService.Context context) {
    List<AdAudience> audiences = new ArrayList<>();
    for (Long id : audienceIds) {
      AdAudience audience =
          audienceRepository
              .findByIdAndCompanyId(id, companyId)
              .orElseThrow(() -> new NotFoundException("Advertising audience not found"));

      if (audience.getProvider() != AdProvider.GOOGLE_ADS
          || !audience.isAccessible()
          || !adAccountId.equals(audience.getAdAccountId())) {
        throw new BadRequestException(
            "All Performance Max audience signals must belong to the selected Google Ads account");
      }
      audiences.add(audience);
    }

    if (audiences.isEmpty()) {
      throw new BadRequestException(
          "Performance Max audience signal requires at least one Google audience list");
    }

    String customerId = context.account().getExternalAccountId();
    List<Map<String, Object>> segments =
        audiences.stream()
            .map(
                audience ->
                    Map.<String, Object>of(
                        "userList",
                        Map.of(
                            "userList",
                            resource(
                                customerId,
                                "userLists",
                                numeric(audience.getExternalAudienceId())))))
            .toList();

    Map<String, Object> audience = new LinkedHashMap<>();
    audience.put("scope", "ASSET_GROUP");
    audience.put("assetGroup", assetGroupResource);
    audience.put(
        "dimensions",
        List.of(
            Map.of(
                "audienceSegments",
                Map.of("segments", segments))));

    JsonNode result =
        apiClient.mutate(
            context.accessToken(),
            customerId,
            context.loginCustomerId(),
            "audiences",
            List.of(Map.of("create", audience)));

    String resourceName = apiClient.firstMutatedResourceName(result);
    if (resourceName == null || resourceName.isBlank()) {
      throw new BadRequestException(
          "Google Ads did not return the Performance Max audience resource");
    }
    return resourceName;
  }

  private GoogleAssetGroupSignalResponse response(JsonNode row) {
    JsonNode signal = row.path("assetGroupSignal");
    String searchTheme = text(signal.path("searchTheme"), "text");
    String audience = text(signal.path("audience"), "audience");
    List<String> reasons = new ArrayList<>();
    JsonNode values = signal.path("disapprovalReasons");
    if (values.isArray()) values.forEach(value -> reasons.add(value.asText()));

    return new GoogleAssetGroupSignalResponse(
        text(signal, "resourceName"),
        searchTheme != null ? "SEARCH_THEME" : "AUDIENCE",
        searchTheme != null ? searchTheme : audience,
        text(signal, "approvalStatus"),
        List.copyOf(reasons));
  }

  private AdCampaign requirePerformanceMaxCampaign(Long companyId, Long campaignId) {
    AdCampaign campaign =
        campaignRepository
            .findByIdAndCompanyId(campaignId, companyId)
            .orElseThrow(() -> new NotFoundException("Advertising campaign not found"));

    if (campaign.getProvider() != AdProvider.GOOGLE_ADS
        || !campaign.isAccessible()
        || !"PERFORMANCE_MAX".equalsIgnoreCase(campaign.getObjective())) {
      throw new BadRequestException(
          "Performance Max asset group signals require an accessible Google Performance Max campaign");
    }
    return campaign;
  }

  private String numeric(String value) {
    if (value == null || !value.matches("\\d+")) {
      throw new BadRequestException("Invalid Google Ads resource ID");
    }
    return value;
  }

  private String resource(String customerId, String collection, String id) {
    return "customers/" + customerId.replace("-", "") + "/" + collection + "/" + id;
  }

  private String text(JsonNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || value.isNull() || value.asText().isBlank()) return null;
    return value.asText().trim();
  }
}
