package com.messaging.ads.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.messaging.ads.entity.AdCampaign;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.repository.AdCampaignRepository;
import com.messaging.ads.service.AdActivityLogService;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GoogleAdsAiMaxControlService {

  private static final int MAX_URL_EXCLUSIONS = 100;

  private final AdCampaignRepository campaignRepository;
  private final GoogleAdsAccountContextService contextService;
  private final GoogleAdsApiClient apiClient;
  private final AdActivityLogService activityLogService;

  public ValidationResult validateUrlExclusions(List<String> values) {
    List<String> normalized = normalizeUrlExclusions(values);
    return new ValidationResult(normalized.size());
  }

  public ValidationResult validateUrlInclusions(List<String> values) {
    List<String> normalized = normalizeAbsoluteUrls(values, "URL inclusions");
    return new ValidationResult(normalized.size());
  }

  public ApplyResult applyUrlExclusions(
      Long companyId,
      Long campaignId,
      List<String> values,
      Long actorUserId) {
    List<String> requested = normalizeUrlExclusions(values);
    if (requested.isEmpty()) return new ApplyResult(0, 0);

    AdCampaign campaign =
        campaignRepository
            .findByIdAndCompanyId(campaignId, companyId)
            .orElseThrow(() -> new NotFoundException("Advertising campaign not found"));
    if (campaign.getProvider() != AdProvider.GOOGLE_ADS
        || !"SEARCH".equalsIgnoreCase(campaign.getObjective())
        || !campaign.isAccessible()) {
      throw new BadRequestException(
          "AI Max URL exclusions are available only for accessible Google Search campaigns");
    }

    var context = contextService.requireManaged(companyId, campaign.getAdAccountId());
    String customerId = context.account().getExternalAccountId();
    Set<String> existing = existingUrlExclusions(context, campaign.getExternalCampaignId());

    List<Map<String, Object>> operations = new ArrayList<>();
    for (String value : requested) {
      if (existing.contains(value.toLowerCase())) continue;
      Map<String, Object> webpage =
          Map.of(
              "conditions",
              List.of(
                  Map.of(
                      "operand", "URL",
                      "argument", value)));
      operations.add(
          Map.of(
              "create",
              Map.of(
                  "campaign",
                  apiClient.customerResourceName(
                      customerId, "campaigns", campaign.getExternalCampaignId()),
                  "negative",
                  true,
                  "webpage",
                  webpage)));
    }

    if (!operations.isEmpty()) {
      apiClient.mutate(
          context.accessToken(),
          customerId,
          context.loginCustomerId(),
          "campaignCriteria",
          operations);
    }

    activityLogService.record(
        companyId,
        campaign.getAdAccountId(),
        AdProvider.GOOGLE_ADS,
        actorUserId,
        "GOOGLE_AI_MAX_URL_EXCLUSIONS_APPLIED",
        "CAMPAIGN",
        campaign.getExternalCampaignId(),
        null,
        null,
        Map.of(
            "requestedCount", requested.size(),
            "createdCount", operations.size(),
            "reusedCount", requested.size() - operations.size()));

    return new ApplyResult(
        operations.size(), requested.size() - operations.size());
  }

  public UrlInclusionApplyResult applyUrlInclusions(
      Long companyId,
      Long campaignId,
      Long adGroupId,
      List<String> values,
      Long actorUserId) {
    List<String> requested = normalizeAbsoluteUrls(values, "URL inclusions");
    if (requested.isEmpty()) return new UrlInclusionApplyResult(0, 0, 0);

    AdCampaign campaign =
        campaignRepository
            .findByIdAndCompanyId(campaignId, companyId)
            .orElseThrow(() -> new NotFoundException("Advertising campaign not found"));
    if (campaign.getProvider() != AdProvider.GOOGLE_ADS
        || !"SEARCH".equalsIgnoreCase(campaign.getObjective())
        || !campaign.isAccessible()) {
      throw new BadRequestException(
          "AI Max URL inclusions are available only for accessible Google Search campaigns");
    }
    var context = contextService.requireManaged(companyId, campaign.getAdAccountId());
    String customerId = context.account().getExternalAccountId();
    String adGroupResource =
        apiClient.customerResourceName(customerId, "adGroups", adGroupId.toString());
    String campaignResource =
        apiClient.customerResourceName(
            customerId, "campaigns", campaign.getExternalCampaignId());

    String assetSetName = "UnifyDesk AI Max page feed " + campaign.getId();
    String assetSetResource = pageFeedAssetSet(context, assetSetName);
    if (assetSetResource == null) {
      JsonNode created =
          apiClient.mutate(
              context.accessToken(),
              customerId,
              context.loginCustomerId(),
              "assetSets",
              List.of(
                  Map.of(
                      "create",
                      Map.of("name", assetSetName, "type", "PAGE_FEED"))));
      assetSetResource = apiClient.firstMutatedResourceName(created);
    }
    if (assetSetResource == null || assetSetResource.isBlank()) {
      throw new BadRequestException("Google did not return an AI Max page-feed asset set");
    }

    Set<String> existingFeedUrls = pageFeedUrls(context, assetSetResource);
    int createdAssets = 0;
    for (String url : requested) {
      if (existingFeedUrls.contains(url.toLowerCase())) continue;
      JsonNode assetResponse =
          apiClient.mutate(
              context.accessToken(),
              customerId,
              context.loginCustomerId(),
              "assets",
              List.of(
                  Map.of(
                      "create",
                      Map.of(
                          "name", "UnifyDesk AI Max URL " + Integer.toUnsignedString(url.hashCode()),
                          "pageFeedAsset",
                          Map.of("pageUrl", url, "labels", List.of("unifydesk-ai-max"))))));
      String assetResource = apiClient.firstMutatedResourceName(assetResponse);
      if (assetResource == null || assetResource.isBlank()) {
        throw new BadRequestException("Google did not return a page-feed asset");
      }
      apiClient.mutate(
          context.accessToken(),
          customerId,
          context.loginCustomerId(),
          "assetSetAssets",
          List.of(
              Map.of(
                  "create",
                  Map.of("assetSet", assetSetResource, "asset", assetResource))));
      createdAssets++;
    }

    if (!campaignHasAssetSet(context, campaign.getExternalCampaignId(), assetSetResource)) {
      apiClient.mutate(
          context.accessToken(),
          customerId,
          context.loginCustomerId(),
          "campaignAssetSets",
          List.of(
              Map.of(
                  "create",
                  Map.of("campaign", campaignResource, "assetSet", assetSetResource))));
    }

    Set<String> existingCriteria =
        existingAdGroupWebpageInclusions(context, adGroupId.toString());
    List<Map<String, Object>> operations = new ArrayList<>();
    for (String url : requested) {
      if (existingCriteria.contains(url.toLowerCase())) continue;
      operations.add(
          Map.of(
              "create",
              Map.of(
                  "adGroup",
                  adGroupResource,
                  "negative",
                  false,
                  "webpage",
                  Map.of(
                      "criterionName",
                      "UnifyDesk AI Max inclusion " + Integer.toUnsignedString(url.hashCode()),
                      "conditions",
                      List.of(Map.of("operand", "URL", "argument", url))))));
    }
    if (!operations.isEmpty()) {
      apiClient.mutate(
          context.accessToken(),
          customerId,
          context.loginCustomerId(),
          "adGroupCriteria",
          operations);
    }

    activityLogService.record(
        companyId,
        campaign.getAdAccountId(),
        AdProvider.GOOGLE_ADS,
        actorUserId,
        "GOOGLE_AI_MAX_URL_INCLUSIONS_APPLIED",
        "CAMPAIGN",
        campaign.getExternalCampaignId(),
        null,
        null,
        Map.of(
            "requestedCount", requested.size(),
            "createdAssets", createdAssets,
            "createdCriteria", operations.size()));

    return new UrlInclusionApplyResult(
        createdAssets,
        operations.size(),
        requested.size() - operations.size());
  }

  private String pageFeedAssetSet(
      GoogleAdsAccountContextService.Context context, String name) {
    String escaped = name.replace("'", "\\'");
    String query =
        "SELECT asset_set.resource_name, asset_set.name, asset_set.type, asset_set.status "
            + "FROM asset_set WHERE asset_set.type = 'PAGE_FEED' "
            + "AND asset_set.name = '"
            + escaped
            + "' AND asset_set.status != 'REMOVED' LIMIT 1";
    List<JsonNode> rows =
        apiClient.searchRows(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            query);
    return rows.isEmpty()
        ? null
        : rows.getFirst().path("assetSet").path("resourceName").asText(null);
  }

  private Set<String> pageFeedUrls(
      GoogleAdsAccountContextService.Context context, String assetSetResource) {
    String assetSetId = assetSetResource.substring(assetSetResource.lastIndexOf('/') + 1);
    String query =
        "SELECT asset.page_feed_asset.page_url "
            + "FROM asset_set_asset WHERE asset_set.id = "
            + numeric(assetSetId);
    Set<String> result = new LinkedHashSet<>();
    for (JsonNode row :
        apiClient.searchRows(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            query)) {
      String url = row.path("asset").path("pageFeedAsset").path("pageUrl").asText(null);
      if (url != null && !url.isBlank()) result.add(url.trim().toLowerCase());
    }
    return result;
  }

  private boolean campaignHasAssetSet(
      GoogleAdsAccountContextService.Context context,
      String externalCampaignId,
      String assetSetResource) {
    String assetSetId = assetSetResource.substring(assetSetResource.lastIndexOf('/') + 1);
    String query =
        "SELECT campaign_asset_set.resource_name FROM campaign_asset_set "
            + "WHERE campaign.id = "
            + numeric(externalCampaignId)
            + " AND asset_set.id = "
            + numeric(assetSetId)
            + " LIMIT 1";
    return !apiClient
        .searchRows(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            query)
        .isEmpty();
  }

  private Set<String> existingAdGroupWebpageInclusions(
      GoogleAdsAccountContextService.Context context, String adGroupId) {
    String query =
        "SELECT ad_group_criterion.webpage.conditions, ad_group_criterion.negative "
            + "FROM ad_group_criterion WHERE ad_group.id = "
            + numeric(adGroupId)
            + " AND ad_group_criterion.type = 'WEBPAGE' "
            + "AND ad_group_criterion.negative = FALSE "
            + "AND ad_group_criterion.status != 'REMOVED'";
    Set<String> result = new LinkedHashSet<>();
    for (JsonNode row :
        apiClient.searchRows(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            query)) {
      JsonNode conditions = row.path("adGroupCriterion").path("webpage").path("conditions");
      if (!conditions.isArray()) continue;
      for (JsonNode condition : conditions) {
        if (!"URL".equalsIgnoreCase(condition.path("operand").asText())) continue;
        String argument = condition.path("argument").asText(null);
        if (argument != null && !argument.isBlank()) result.add(argument.trim().toLowerCase());
      }
    }
    return result;
  }

  private Set<String> existingUrlExclusions(
      GoogleAdsAccountContextService.Context context,
      String externalCampaignId) {
    String query =
        "SELECT campaign_criterion.webpage.conditions, campaign_criterion.negative "
            + "FROM campaign_criterion WHERE campaign.id = "
            + numeric(externalCampaignId)
            + " AND campaign_criterion.type = 'WEBPAGE' "
            + "AND campaign_criterion.negative = TRUE "
            + "AND campaign_criterion.status != 'REMOVED'";

    Set<String> result = new LinkedHashSet<>();
    for (JsonNode row :
        apiClient.searchRows(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            query)) {
      JsonNode conditions = row.path("campaignCriterion").path("webpage").path("conditions");
      if (!conditions.isArray()) continue;
      for (JsonNode condition : conditions) {
        if (!"URL".equalsIgnoreCase(condition.path("operand").asText())) continue;
        String argument = condition.path("argument").asText(null);
        if (argument != null && !argument.isBlank()) {
          result.add(argument.trim().toLowerCase());
        }
      }
    }
    return result;
  }

  private List<String> normalizeAbsoluteUrls(List<String> values, String label) {
    if (values == null || values.isEmpty()) return List.of();
    if (values.size() > MAX_URL_EXCLUSIONS) {
      throw new BadRequestException("Google AI Max " + label + " are limited to 100 values");
    }
    Set<String> seen = new LinkedHashSet<>();
    List<String> result = new ArrayList<>();
    for (String value : values) {
      if (value == null || value.isBlank()) {
        throw new BadRequestException("Google AI Max " + label + " cannot be blank");
      }
      String normalized = value.trim();
      try {
        URI uri = URI.create(normalized);
        if (uri.getScheme() == null
            || (!"http".equalsIgnoreCase(uri.getScheme())
                && !"https".equalsIgnoreCase(uri.getScheme()))
            || uri.getHost() == null) {
          throw new BadRequestException(
              "Google AI Max " + label + " must be absolute HTTP(S) URLs");
        }
      } catch (IllegalArgumentException exception) {
        throw new BadRequestException(
            "Google AI Max " + label + " must be valid absolute URLs");
      }
      if (seen.add(normalized.toLowerCase())) result.add(normalized);
    }
    return List.copyOf(result);
  }

  private List<String> normalizeUrlExclusions(List<String> values) {
    if (values == null || values.isEmpty()) return List.of();
    if (values.size() > MAX_URL_EXCLUSIONS) {
      throw new BadRequestException(
          "Google AI Max URL exclusions are limited to 100 values per campaign configuration");
    }

    Set<String> seen = new LinkedHashSet<>();
    List<String> result = new ArrayList<>();
    for (String value : values) {
      if (value == null || value.isBlank()) {
        throw new BadRequestException("Google AI Max URL exclusions cannot be blank");
      }
      String normalized = value.trim();
      validateUrlPattern(normalized);
      if (seen.add(normalized.toLowerCase())) result.add(normalized);
    }
    return List.copyOf(result);
  }

  private void validateUrlPattern(String value) {
    if (value.length() > 2048) {
      throw new BadRequestException("Google AI Max URL exclusion is too long");
    }
    if (value.startsWith("/")) return;
    try {
      URI uri = URI.create(value);
      if (uri.getScheme() == null
          || (!"http".equalsIgnoreCase(uri.getScheme())
              && !"https".equalsIgnoreCase(uri.getScheme()))
          || uri.getHost() == null) {
        throw new BadRequestException(
            "Google AI Max URL exclusions must be an absolute HTTP(S) URL or a path beginning with /");
      }
    } catch (IllegalArgumentException exception) {
      throw new BadRequestException(
          "Google AI Max URL exclusions must be valid URLs or URL paths");
    }
  }

  private String numeric(String value) {
    if (value == null || !value.trim().matches("\\d+")) {
      throw new BadRequestException("Invalid Google Ads campaign ID");
    }
    return value.trim();
  }

  public record ValidationResult(int requestedCount) {}

  public record ApplyResult(int createdCount, int reusedCount) {}

  public record UrlInclusionApplyResult(
      int createdAssets, int createdCriteria, int reusedCriteria) {}
}

