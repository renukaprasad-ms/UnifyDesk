package com.messaging.ads.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.messaging.ads.entity.AdCampaign;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.dto.GooglePerformanceMaxListingGroupReplaceRequest;
import com.messaging.ads.google.dto.GooglePerformanceMaxListingGroupResponse;
import com.messaging.ads.google.dto.GooglePerformanceMaxListingGroupValueRequest;
import com.messaging.ads.google.dto.GooglePerformanceMaxRetailSettingsResponse;
import com.messaging.ads.repository.AdCampaignRepository;
import com.messaging.ads.service.AdActivityLogService;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
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
public class GoogleAdsPerformanceMaxListingGroupService {

  private static final Set<String> DIMENSIONS =
      Set.of(
          "BRAND", "CONDITION", "PRODUCT_TYPE", "CATEGORY", "CUSTOM_LABEL", "CHANNEL", "ITEM_ID");
  private static final Set<String> CONDITIONS = Set.of("NEW", "USED", "REFURBISHED");
  private static final Set<String> CHANNELS = Set.of("ONLINE", "LOCAL");
  private static final Set<String> LEVELS =
      Set.of("LEVEL1", "LEVEL2", "LEVEL3", "LEVEL4", "LEVEL5");
  private static final Set<String> CUSTOM_INDEXES =
      Set.of("INDEX0", "INDEX1", "INDEX2", "INDEX3", "INDEX4");

  private final GoogleAdsAccountContextService contextService;
  private final GoogleAdsApiClient apiClient;
  private final AdCampaignRepository campaignRepository;
  private final AdActivityLogService activityLogService;

  public GooglePerformanceMaxRetailSettingsResponse retailSettings(
      Long companyId, Long campaignId) {
    Context context = context(companyId, campaignId, null);
    List<JsonNode> rows =
        apiClient.searchRows(
            context.google().accessToken(),
            context.google().account().getExternalAccountId(),
            context.google().loginCustomerId(),
            "SELECT campaign.id, campaign.shopping_setting.merchant_id, "
                + "campaign.shopping_setting.feed_label "
                + "FROM campaign WHERE campaign.id = "
                + numeric(context.campaign().getExternalCampaignId(), "Google campaign ID")
                + " LIMIT 1");
    if (rows.isEmpty()) {
      throw new NotFoundException("Google Performance Max campaign could not be reloaded");
    }
    JsonNode shopping = rows.getFirst().path("campaign").path("shoppingSetting");
    String merchantId = text(shopping, "merchantId");
    return new GooglePerformanceMaxRetailSettingsResponse(
        merchantId != null, merchantId, text(shopping, "feedLabel"));
  }

  public List<GooglePerformanceMaxListingGroupResponse> list(
      Long companyId, Long campaignId, String assetGroupId, Integer requestedDays) {
    Context context = context(companyId, campaignId, assetGroupId);
    requireRetail(companyId, campaignId);
    String normalizedAssetGroupId = context.assetGroupId();

    String query =
        "SELECT asset_group_listing_group_filter.id, "
            + "asset_group_listing_group_filter.resource_name, "
            + "asset_group_listing_group_filter.parent_listing_group_filter, "
            + "asset_group_listing_group_filter.type, "
            + "asset_group_listing_group_filter.listing_source, "
            + "asset_group_listing_group_filter.case_value.product_brand.value, "
            + "asset_group_listing_group_filter.case_value.product_condition.condition, "
            + "asset_group_listing_group_filter.case_value.product_type.level, "
            + "asset_group_listing_group_filter.case_value.product_type.value, "
            + "asset_group_listing_group_filter.case_value.product_category.level, "
            + "asset_group_listing_group_filter.case_value.product_category.category_id, "
            + "asset_group_listing_group_filter.case_value.product_custom_attribute.index, "
            + "asset_group_listing_group_filter.case_value.product_custom_attribute.value, "
            + "asset_group_listing_group_filter.case_value.product_channel.channel, "
            + "asset_group_listing_group_filter.case_value.product_item_id.value "
            + "FROM asset_group_listing_group_filter WHERE asset_group.id = "
            + normalizedAssetGroupId;

    Map<String, Metrics> metrics = performance(context, requestedDays);
    List<GooglePerformanceMaxListingGroupResponse> result = new ArrayList<>();
    for (JsonNode row :
        apiClient.searchRows(
            context.google().accessToken(),
            context.google().account().getExternalAccountId(),
            context.google().loginCustomerId(),
            query)) {
      JsonNode filter = row.path("assetGroupListingGroupFilter");
      String id = text(filter, "id");
      Dimension dimension = dimension(filter.path("caseValue"));
      Metrics rowMetrics = metrics.getOrDefault(id, Metrics.EMPTY);
      result.add(
          new GooglePerformanceMaxListingGroupResponse(
              id,
              text(filter, "resourceName"),
              text(filter, "parentListingGroupFilter"),
              text(filter, "type"),
              text(filter, "listingSource"),
              dimension.type(),
              dimension.value(),
              dimension.level(),
              dimension.customIndex(),
              rowMetrics.impressions(),
              rowMetrics.clicks(),
              rowMetrics.conversions(),
              rowMetrics.costMicros()));
    }
    return List.copyOf(result);
  }

  public List<GooglePerformanceMaxListingGroupResponse> replace(
      Long companyId,
      Long campaignId,
      String assetGroupId,
      GooglePerformanceMaxListingGroupReplaceRequest request,
      Long actorUserId) {
    Context context = context(companyId, campaignId, assetGroupId);
    requireRetail(companyId, campaignId);

    String dimensionType = normalizeDimension(request.dimensionType());
    List<ValueSpec> values = validateValues(dimensionType, request.values());
    if (dimensionType == null && !values.isEmpty()) {
      throw new BadRequestException(
          "Performance Max listing-group values require a subdivision dimension");
    }

    List<GooglePerformanceMaxListingGroupResponse> before =
        list(companyId, campaignId, assetGroupId, 30);
    List<Map<String, Object>> operations = new ArrayList<>();

    Map<String, String> parentByResource = new HashMap<>();
    for (GooglePerformanceMaxListingGroupResponse node : before) {
      parentByResource.put(node.resourceName(), node.parentResourceName());
    }
    before.stream()
        .filter(node -> node.resourceName() != null)
        .sorted(
            Comparator.comparingInt(
                    (GooglePerformanceMaxListingGroupResponse node) ->
                        depth(node.resourceName(), parentByResource, new HashMap<>()))
                .reversed())
        .forEach(
            node ->
                operations.add(
                    Map.of(
                        "assetGroupListingGroupFilterOperation",
                        Map.of("remove", node.resourceName()))));

    String customerId = context.google().account().getExternalAccountId();
    String assetGroupResource =
        apiClient.customerResourceName(customerId, "assetGroups", context.assetGroupId());
    long nextId = -1L;
    String rootResource = listingResource(customerId, context.assetGroupId(), nextId);

    Map<String, Object> root = new LinkedHashMap<>();
    root.put("resourceName", rootResource);
    root.put("assetGroup", assetGroupResource);
    root.put("listingSource", "SHOPPING");
    root.put("type", dimensionType == null ? "UNIT_INCLUDED" : "SUBDIVISION");
    operations.add(Map.of("assetGroupListingGroupFilterOperation", Map.of("create", root)));

    if (dimensionType != null) {
      for (ValueSpec value : values) {
        nextId--;
        Map<String, Object> child = new LinkedHashMap<>();
        child.put("resourceName", listingResource(customerId, context.assetGroupId(), nextId));
        child.put("assetGroup", assetGroupResource);
        child.put("parentListingGroupFilter", rootResource);
        child.put("listingSource", "SHOPPING");
        child.put("type", value.included() ? "UNIT_INCLUDED" : "UNIT_EXCLUDED");
        child.put("caseValue", caseValue(dimensionType, value));
        operations.add(Map.of("assetGroupListingGroupFilterOperation", Map.of("create", child)));
      }

      nextId--;
      Map<String, Object> other = new LinkedHashMap<>();
      other.put("resourceName", listingResource(customerId, context.assetGroupId(), nextId));
      other.put("assetGroup", assetGroupResource);
      other.put("parentListingGroupFilter", rootResource);
      other.put("listingSource", "SHOPPING");
      other.put("type", request.includeOtherProducts() ? "UNIT_INCLUDED" : "UNIT_EXCLUDED");
      other.put("caseValue", Map.of());
      operations.add(Map.of("assetGroupListingGroupFilterOperation", Map.of("create", other)));
    }

    apiClient.mutateAll(
        context.google().accessToken(), customerId, context.google().loginCustomerId(), operations);

    List<GooglePerformanceMaxListingGroupResponse> after =
        list(companyId, campaignId, assetGroupId, 30);
    activityLogService.record(
        companyId,
        context.campaign().getAdAccountId(),
        AdProvider.GOOGLE_ADS,
        actorUserId,
        "GOOGLE_PMAX_LISTING_GROUP_TREE_REPLACED",
        "ASSET_GROUP",
        context.assetGroupId(),
        before,
        after,
        Map.of(
            "dimensionType", dimensionType == null ? "ALL_PRODUCTS" : dimensionType,
            "valueCount", values.size(),
            "includeOtherProducts", request.includeOtherProducts()));

    return after;
  }

  private Map<String, Metrics> performance(Context context, Integer requestedDays) {
    int days = requestedDays == null ? 30 : Math.max(1, Math.min(requestedDays, 90));
    java.time.LocalDate endDate = java.time.LocalDate.now();
    java.time.LocalDate startDate = endDate.minusDays(days - 1L);
    String query =
        "SELECT asset_group_listing_group_filter.id, metrics.impressions, metrics.clicks, "
            + "metrics.conversions, metrics.cost_micros "
            + "FROM asset_group_product_group_view WHERE asset_group.id = "
            + context.assetGroupId()
            + " AND segments.date BETWEEN '"
            + startDate
            + "' AND '"
            + endDate
            + "'";

    Map<String, Metrics> result = new HashMap<>();
    for (JsonNode row :
        apiClient.searchRows(
            context.google().accessToken(),
            context.google().account().getExternalAccountId(),
            context.google().loginCustomerId(),
            query)) {
      String id = text(row.path("assetGroupListingGroupFilter"), "id");
      if (id == null) continue;
      JsonNode metrics = row.path("metrics");
      result.put(
          id,
          new Metrics(
              longOrNull(metrics, "impressions"),
              longOrNull(metrics, "clicks"),
              doubleOrNull(metrics, "conversions"),
              longOrNull(metrics, "costMicros")));
    }
    return result;
  }

  private List<ValueSpec> validateValues(
      String dimensionType, List<GooglePerformanceMaxListingGroupValueRequest> requested) {
    if (requested == null || requested.isEmpty()) return List.of();
    if (dimensionType == null) {
      throw new BadRequestException(
          "Choose a Performance Max product subdivision before adding values");
    }
    if (requested.size() > 100) {
      throw new BadRequestException(
          "Performance Max product grouping supports at most 100 explicit values per edit");
    }

    List<ValueSpec> result = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (GooglePerformanceMaxListingGroupValueRequest value : requested) {
      String raw = value.value().trim();
      String level = normalizeLevel(value.level(), dimensionType);
      String customIndex = normalizeCustomIndex(value.customAttributeIndex(), dimensionType);
      String canonical = canonicalValue(dimensionType, raw);
      String key = dimensionType + "|" + level + "|" + customIndex + "|" + canonical;
      if (!seen.add(key)) {
        throw new BadRequestException("Duplicate Performance Max product group value: " + raw);
      }
      result.add(new ValueSpec(canonical, level, customIndex, value.included()));
    }
    return List.copyOf(result);
  }

  private String canonicalValue(String dimensionType, String value) {
    if (value.isBlank()) {
      throw new BadRequestException("Performance Max product-group value is required");
    }
    return switch (dimensionType) {
      case "CONDITION" -> enumValue(value, CONDITIONS, "product condition");
      case "CHANNEL" -> enumValue(value, CHANNELS, "product channel");
      case "CATEGORY" -> {
        if (!value.matches("\\d+")) {
          throw new BadRequestException("Google product category must use its numeric taxonomy ID");
        }
        yield value;
      }
      default -> value;
    };
  }

  private String normalizeLevel(String value, String dimensionType) {
    if (!"PRODUCT_TYPE".equals(dimensionType) && !"CATEGORY".equals(dimensionType)) {
      return null;
    }
    String normalized =
        value == null || value.isBlank() ? "LEVEL1" : value.trim().toUpperCase(Locale.ROOT);
    if (!LEVELS.contains(normalized)) {
      throw new BadRequestException("Performance Max product level must be LEVEL1 through LEVEL5");
    }
    return normalized;
  }

  private String normalizeCustomIndex(String value, String dimensionType) {
    if (!"CUSTOM_LABEL".equals(dimensionType)) return null;
    String normalized =
        value == null || value.isBlank() ? "INDEX0" : value.trim().toUpperCase(Locale.ROOT);
    if (!CUSTOM_INDEXES.contains(normalized)) {
      throw new BadRequestException(
          "Performance Max custom-label index must be INDEX0 through INDEX4");
    }
    return normalized;
  }

  private Map<String, Object> caseValue(String dimensionType, ValueSpec value) {
    return switch (dimensionType) {
      case "BRAND" -> Map.of("productBrand", Map.of("value", value.value()));
      case "CONDITION" -> Map.of("productCondition", Map.of("condition", value.value()));
      case "PRODUCT_TYPE" ->
          Map.of(
              "productType",
              Map.of(
                  "level", value.level(),
                  "value", value.value()));
      case "CATEGORY" ->
          Map.of(
              "productCategory",
              Map.of(
                  "level", value.level(),
                  "categoryId", Long.valueOf(value.value())));
      case "CUSTOM_LABEL" ->
          Map.of(
              "productCustomAttribute",
              Map.of(
                  "index", value.customIndex(),
                  "value", value.value()));
      case "CHANNEL" -> Map.of("productChannel", Map.of("channel", value.value()));
      case "ITEM_ID" -> Map.of("productItemId", Map.of("value", value.value()));
      default ->
          throw new BadRequestException(
              "Unsupported Performance Max product grouping: " + dimensionType);
    };
  }

  private Dimension dimension(JsonNode caseValue) {
    if (caseValue == null || caseValue.isMissingNode() || caseValue.isNull()) {
      return Dimension.EMPTY;
    }
    String value;
    JsonNode node;
    node = caseValue.path("productBrand");
    value = text(node, "value");
    if (value != null) return new Dimension("BRAND", value, null, null);

    node = caseValue.path("productCondition");
    value = text(node, "condition");
    if (value != null) return new Dimension("CONDITION", value, null, null);

    node = caseValue.path("productType");
    value = text(node, "value");
    if (value != null) {
      return new Dimension("PRODUCT_TYPE", value, text(node, "level"), null);
    }

    node = caseValue.path("productCategory");
    value = text(node, "categoryId");
    if (value != null) {
      return new Dimension("CATEGORY", value, text(node, "level"), null);
    }

    node = caseValue.path("productCustomAttribute");
    value = text(node, "value");
    if (value != null) {
      return new Dimension("CUSTOM_LABEL", value, null, text(node, "index"));
    }

    node = caseValue.path("productChannel");
    value = text(node, "channel");
    if (value != null) return new Dimension("CHANNEL", value, null, null);

    node = caseValue.path("productItemId");
    value = text(node, "value");
    if (value != null) return new Dimension("ITEM_ID", value, null, null);

    return Dimension.EMPTY;
  }

  private int depth(
      String resourceName, Map<String, String> parentByResource, Map<String, Integer> memo) {
    if (resourceName == null) return 0;
    Integer cached = memo.get(resourceName);
    if (cached != null) return cached;
    String parent = parentByResource.get(resourceName);
    int depth = parent == null ? 0 : 1 + depth(parent, parentByResource, memo);
    memo.put(resourceName, depth);
    return depth;
  }

  private void requireRetail(Long companyId, Long campaignId) {
    if (!retailSettings(companyId, campaignId).retail()) {
      throw new BadRequestException(
          "Product listing groups are available only for retail Performance Max campaigns linked to Merchant Center");
    }
  }

  private Context context(Long companyId, Long campaignId, String assetGroupId) {
    AdCampaign campaign =
        campaignRepository
            .findByIdAndCompanyId(campaignId, companyId)
            .orElseThrow(() -> new NotFoundException("Advertising campaign not found"));
    if (campaign.getProvider() != AdProvider.GOOGLE_ADS
        || !"PERFORMANCE_MAX".equalsIgnoreCase(campaign.getObjective())
        || !campaign.isAccessible()) {
      throw new BadRequestException(
          "Google product listing groups require an accessible Performance Max campaign");
    }
    var google = contextService.requireManaged(companyId, campaign.getAdAccountId());

    String normalizedAssetGroupId = null;
    if (assetGroupId != null) {
      normalizedAssetGroupId = numeric(assetGroupId, "Google asset group ID");
      List<JsonNode> groups =
          apiClient.searchRows(
              google.accessToken(),
              google.account().getExternalAccountId(),
              google.loginCustomerId(),
              "SELECT asset_group.id FROM asset_group WHERE campaign.id = "
                  + numeric(campaign.getExternalCampaignId(), "Google campaign ID")
                  + " AND asset_group.id = "
                  + normalizedAssetGroupId
                  + " LIMIT 1");
      if (groups.isEmpty()) {
        throw new NotFoundException(
            "Google Performance Max asset group was not found in this campaign");
      }
    }
    return new Context(campaign, google, normalizedAssetGroupId);
  }

  private String listingResource(String customerId, String assetGroupId, long id) {
    return apiClient.customerResourceName(
        customerId, "assetGroupListingGroupFilters", assetGroupId + "~" + id);
  }

  private String normalizeDimension(String value) {
    if (value == null || value.isBlank() || "ALL_PRODUCTS".equalsIgnoreCase(value.trim())) {
      return null;
    }
    String normalized = value.trim().toUpperCase(Locale.ROOT);
    if (!DIMENSIONS.contains(normalized)) {
      throw new BadRequestException(
          "Performance Max product grouping must be BRAND, CONDITION, PRODUCT_TYPE, CATEGORY, CUSTOM_LABEL, CHANNEL, ITEM_ID, or ALL_PRODUCTS");
    }
    return normalized;
  }

  private String enumValue(String value, Set<String> allowed, String label) {
    String normalized = value.trim().toUpperCase(Locale.ROOT);
    if (!allowed.contains(normalized)) {
      throw new BadRequestException("Unsupported Google " + label + ": " + value);
    }
    return normalized;
  }

  private String numeric(String value, String label) {
    if (value == null || !value.trim().matches("\\d+")) {
      throw new BadRequestException("Invalid " + label);
    }
    return value.trim();
  }

  private String text(JsonNode node, String field) {
    if (node == null || node.isNull() || node.isMissingNode()) return null;
    JsonNode value = node.get(field);
    if (value == null || value.isNull()) return null;
    String text = value.asText(null);
    return text == null || text.isBlank() ? null : text.trim();
  }

  private Long longOrNull(JsonNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || value.isNull()) return null;
    if (value.isIntegralNumber()) return value.longValue();
    try {
      return new BigDecimal(value.asText()).longValue();
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  private Double doubleOrNull(JsonNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || value.isNull()) return null;
    if (value.isNumber()) return value.doubleValue();
    try {
      return new BigDecimal(value.asText()).doubleValue();
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  private record Context(
      AdCampaign campaign, GoogleAdsAccountContextService.Context google, String assetGroupId) {}

  private record ValueSpec(String value, String level, String customIndex, boolean included) {}

  private record Dimension(String type, String value, String level, String customIndex) {
    private static final Dimension EMPTY = new Dimension(null, null, null, null);
  }

  private record Metrics(Long impressions, Long clicks, Double conversions, Long costMicros) {
    private static final Metrics EMPTY = new Metrics(null, null, null, null);
  }
}
