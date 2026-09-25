package com.messaging.ads.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.messaging.ads.entity.AdCampaign;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.dto.GoogleBrandSuggestionResponse;
import com.messaging.ads.repository.AdCampaignRepository;
import com.messaging.ads.service.AdActivityLogService;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GoogleAdsAiMaxBrandService {

  private final AdCampaignRepository campaignRepository;
  private final GoogleAdsAccountContextService contextService;
  private final GoogleAdsApiClient apiClient;
  private final AdActivityLogService activityLogService;

  public List<GoogleBrandSuggestionResponse> suggest(
      Long companyId,
      Long accountId,
      String prefix,
      List<String> selectedBrandIds) {
    var context = contextService.requireManaged(companyId, accountId);
    JsonNode response =
        apiClient.suggestBrands(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            prefix,
            normalizeBrandIds(selectedBrandIds));

    List<GoogleBrandSuggestionResponse> result = new ArrayList<>();
    JsonNode brands = response.path("brands");
    if (brands.isArray()) {
      for (JsonNode brand : brands) {
        String id = brand.path("id").asText(null);
        String name = brand.path("name").asText(null);
        if (id == null || id.isBlank() || name == null || name.isBlank()) continue;
        List<String> urls = new ArrayList<>();
        if (brand.path("urls").isArray()) {
          for (JsonNode url : brand.path("urls")) {
            if (url.isTextual() && !url.asText().isBlank()) urls.add(url.asText().trim());
          }
        }
        result.add(
            new GoogleBrandSuggestionResponse(
                id.trim(),
                name.trim(),
                List.copyOf(urls),
                brand.path("state").asText(null)));
      }
    }
    return List.copyOf(result);
  }

  public ValidationResult validate(
      List<String> inclusionBrandIds, List<String> exclusionBrandIds) {
    List<String> inclusions = normalizeBrandIds(inclusionBrandIds);
    List<String> exclusions = normalizeBrandIds(exclusionBrandIds);
    Set<String> overlap = new LinkedHashSet<>(inclusions);
    overlap.retainAll(exclusions);
    if (!overlap.isEmpty()) {
      throw new BadRequestException(
          "The same Google brand cannot be both included and excluded");
    }
    return new ValidationResult(inclusions.size(), exclusions.size());
  }

  public ApplyResult apply(
      Long companyId,
      Long campaignId,
      List<String> inclusionBrandIds,
      List<String> exclusionBrandIds,
      Long actorUserId) {
    ValidationResult validated = validate(inclusionBrandIds, exclusionBrandIds);
    AdCampaign campaign =
        campaignRepository
            .findByIdAndCompanyId(campaignId, companyId)
            .orElseThrow(() -> new NotFoundException("Advertising campaign not found"));
    if (campaign.getProvider() != AdProvider.GOOGLE_ADS
        || !"SEARCH".equalsIgnoreCase(campaign.getObjective())
        || !campaign.isAccessible()) {
      throw new BadRequestException(
          "AI Max brand controls are available only for accessible Google Search campaigns");
    }

    var context = contextService.requireManaged(companyId, campaign.getAdAccountId());
    String campaignResource =
        apiClient.customerResourceName(
            context.account().getExternalAccountId(),
            "campaigns",
            campaign.getExternalCampaignId());

    int createdLists = 0;
    int createdCriteria = 0;
    if (validated.inclusionCount() > 0) {
      BrandApplyResult result =
          ensureListAndCriterion(
              context,
              campaignResource,
              campaign.getId(),
              "inclusions",
              normalizeBrandIds(inclusionBrandIds),
              false);
      createdLists += result.createdList() ? 1 : 0;
      createdCriteria += result.createdCriterion() ? 1 : 0;
    }
    if (validated.exclusionCount() > 0) {
      BrandApplyResult result =
          ensureListAndCriterion(
              context,
              campaignResource,
              campaign.getId(),
              "exclusions",
              normalizeBrandIds(exclusionBrandIds),
              true);
      createdLists += result.createdList() ? 1 : 0;
      createdCriteria += result.createdCriterion() ? 1 : 0;
    }

    activityLogService.record(
        companyId,
        campaign.getAdAccountId(),
        AdProvider.GOOGLE_ADS,
        actorUserId,
        "GOOGLE_AI_MAX_BRANDS_APPLIED",
        "CAMPAIGN",
        campaign.getExternalCampaignId(),
        null,
        null,
        Map.of(
            "inclusionCount", validated.inclusionCount(),
            "exclusionCount", validated.exclusionCount(),
            "createdLists", createdLists,
            "createdCriteria", createdCriteria));

    return new ApplyResult(createdLists, createdCriteria);
  }

  private BrandApplyResult ensureListAndCriterion(
      GoogleAdsAccountContextService.Context context,
      String campaignResource,
      Long localCampaignId,
      String suffix,
      List<String> brandIds,
      boolean negative) {
    String listName = "UnifyDesk AI Max " + suffix + " " + localCampaignId;
    SharedSet sharedSet = sharedSet(context, listName);
    boolean createdList = false;
    if (sharedSet == null) {
      JsonNode response =
          apiClient.mutate(
              context.accessToken(),
              context.account().getExternalAccountId(),
              context.loginCustomerId(),
              "sharedSets",
              List.of(
                  Map.of(
                      "create",
                      Map.of("name", listName, "type", "BRANDS"))));
      String resourceName = apiClient.firstMutatedResourceName(response);
      if (resourceName == null || resourceName.isBlank()) {
        throw new BadRequestException("Google did not return the AI Max brand list");
      }
      sharedSet = new SharedSet(resourceName, Set.of());
      createdList = true;
    }

    Set<String> existing = new LinkedHashSet<>(sharedSet.brandIds());
    List<Map<String, Object>> memberOps = new ArrayList<>();
    for (String brandId : brandIds) {
      if (existing.contains(brandId)) continue;
      memberOps.add(
          Map.of(
              "create",
              Map.of(
                  "sharedSet",
                  sharedSet.resourceName(),
                  "brand",
                  Map.of("entityId", brandId))));
    }
    if (!memberOps.isEmpty()) {
      apiClient.mutate(
          context.accessToken(),
          context.account().getExternalAccountId(),
          context.loginCustomerId(),
          "sharedCriteria",
          memberOps);
    }

    boolean createdCriterion = false;
    if (!hasBrandCriterion(
        context, campaignResource, sharedSet.resourceName(), negative)) {
      apiClient.mutate(
          context.accessToken(),
          context.account().getExternalAccountId(),
          context.loginCustomerId(),
          "campaignCriteria",
          List.of(
              Map.of(
                  "create",
                  Map.of(
                      "campaign",
                      campaignResource,
                      "negative",
                      negative,
                      "brandList",
                      Map.of("sharedSet", sharedSet.resourceName())))));
      createdCriterion = true;
    }
    return new BrandApplyResult(createdList, createdCriterion);
  }

  private SharedSet sharedSet(
      GoogleAdsAccountContextService.Context context, String name) {
    String escaped = name.replace("'", "\\'");
    String query =
        "SELECT shared_set.resource_name, shared_criterion.brand.entity_id "
            + "FROM shared_criterion WHERE shared_set.type = 'BRANDS' "
            + "AND shared_set.name = '"
            + escaped
            + "'";
    List<JsonNode> rows =
        apiClient.searchRows(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            query);
    if (rows.isEmpty()) {
      String setQuery =
          "SELECT shared_set.resource_name FROM shared_set "
              + "WHERE shared_set.type = 'BRANDS' AND shared_set.name = '"
              + escaped
              + "' AND shared_set.status != 'REMOVED' LIMIT 1";
      List<JsonNode> sets =
          apiClient.searchRows(
              context.accessToken(),
              context.account().getExternalAccountId(),
              context.loginCustomerId(),
              setQuery);
      if (sets.isEmpty()) return null;
      return new SharedSet(
          sets.getFirst().path("sharedSet").path("resourceName").asText(),
          Set.of());
    }

    String resourceName =
        rows.getFirst().path("sharedSet").path("resourceName").asText(null);
    Set<String> ids = new LinkedHashSet<>();
    for (JsonNode row : rows) {
      String id =
          row.path("sharedCriterion").path("brand").path("entityId").asText(null);
      if (id != null && !id.isBlank()) ids.add(id);
    }
    return new SharedSet(resourceName, Set.copyOf(ids));
  }

  private boolean hasBrandCriterion(
      GoogleAdsAccountContextService.Context context,
      String campaignResource,
      String sharedSetResource,
      boolean negative) {
    String campaignId = campaignResource.substring(campaignResource.lastIndexOf('/') + 1);
    String sharedSetId =
        sharedSetResource.substring(sharedSetResource.lastIndexOf('/') + 1);
    String query =
        "SELECT campaign_criterion.resource_name, campaign_criterion.negative "
            + "FROM campaign_criterion WHERE campaign.id = "
            + numeric(campaignId)
            + " AND campaign_criterion.type = 'BRAND_LIST' "
            + "AND campaign_criterion.brand_list.shared_set = '"
            + sharedSetResource
            + "' AND campaign_criterion.negative = "
            + (negative ? "TRUE" : "FALSE")
            + " AND campaign_criterion.status != 'REMOVED' "
            + "LIMIT 1";
    // Keep sharedSetId validation explicit even though GAQL compares by resource name.
    numeric(sharedSetId);
    return !apiClient
        .searchRows(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            query)
        .isEmpty();
  }

  private List<String> normalizeBrandIds(List<String> values) {
    if (values == null || values.isEmpty()) return List.of();
    Set<String> result = new LinkedHashSet<>();
    for (String value : values) {
      if (value == null || value.isBlank()) {
        throw new BadRequestException("Google brand IDs cannot be blank");
      }
      String normalized = value.trim();
      if (normalized.length() > 256) {
        throw new BadRequestException("Google brand ID is invalid");
      }
      result.add(normalized);
    }
    return List.copyOf(result);
  }

  private String numeric(String value) {
    if (value == null || !value.trim().matches("\\d+")) {
      throw new BadRequestException("Invalid Google Ads resource ID");
    }
    return value.trim();
  }

  public record ValidationResult(int inclusionCount, int exclusionCount) {}

  public record ApplyResult(int createdLists, int createdCriteria) {}

  private record BrandApplyResult(boolean createdList, boolean createdCriterion) {}

  private record SharedSet(String resourceName, Set<String> brandIds) {}
}
