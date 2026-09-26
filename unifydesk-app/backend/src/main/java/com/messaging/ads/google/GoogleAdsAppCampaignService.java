package com.messaging.ads.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.messaging.ads.campaign.dto.AudienceLocationInput;
import com.messaging.ads.dto.AdCampaignResponse;
import com.messaging.ads.entity.AdCampaign;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.dto.CreateGoogleAppCampaignRequest;
import com.messaging.ads.google.dto.GoogleAppAssetPerformance;
import com.messaging.ads.google.dto.GoogleAppCampaignPerformanceResponse;
import com.messaging.ads.google.dto.GoogleAppCampaignSettingsResponse;
import com.messaging.ads.repository.AdCampaignRepository;
import com.messaging.ads.service.AdActivityLogService;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GoogleAdsAppCampaignService {

  private static final DateTimeFormatter GOOGLE_DATE_TIME =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private static final Set<String> SUBTYPES =
      Set.of("APP_CAMPAIGN", "APP_CAMPAIGN_FOR_ENGAGEMENT", "APP_CAMPAIGN_FOR_PRE_REGISTRATION");
  private static final Set<String> STORES = Set.of("GOOGLE_APP_STORE", "APPLE_APP_STORE");
  private static final Set<String> GOALS =
      Set.of(
          "OPTIMIZE_INSTALLS_TARGET_INSTALL_COST",
          "OPTIMIZE_INSTALLS_WITHOUT_TARGET_INSTALL_COST",
          "OPTIMIZE_IN_APP_CONVERSIONS_TARGET_CONVERSION_COST",
          "OPTIMIZE_IN_APP_CONVERSIONS_TARGET_INSTALL_COST",
          "OPTIMIZE_IN_APP_CONVERSIONS_WITHOUT_TARGET_CPA",
          "OPTIMIZE_PRE_REGISTRATION_CONVERSION_VOLUME",
          "OPTIMIZE_RETURN_ON_ADVERTISING_SPEND",
          "OPTIMIZE_TOTAL_VALUE_WITHOUT_TARGET_ROAS");

  private final GoogleAdsAccountContextService contextService;
  private final GoogleAdsApiClient apiClient;
  private final GoogleAdsCampaignSyncService campaignSyncService;
  private final GoogleAdsCampaignPersistenceService campaignPersistenceService;
  private final AdCampaignRepository campaignRepository;
  private final AdActivityLogService activityLogService;

  public AdCampaignResponse create(
      Long companyId,
      Long accountId,
      CreateGoogleAppCampaignRequest request,
      Long actorUserId) {
    var context = contextService.requireManaged(companyId, accountId);
    if (context.managerAccount()) {
      throw new BadRequestException(
          "Google Ads manager accounts cannot directly own App campaigns");
    }

    validateSchedule(request.startDate(), request.endDate());
    String subtype = enumValue(request.advertisingChannelSubType(), SUBTYPES, "App campaign subtype");
    String store = enumValue(request.appStore(), STORES, "App store");
    String goal = enumValue(request.biddingStrategyGoalType(), GOALS, "App bidding goal");
    validateCombination(subtype, store, goal, request);
    validateAppId(store, request.appId());

    String customerId = context.account().getExternalAccountId();
    String budgetResource = apiClient.customerResourceName(customerId, "campaignBudgets", "-1");
    String campaignResource = apiClient.customerResourceName(customerId, "campaigns", "-2");

    List<String> conversionResources =
        conversionResources(context, request.conversionActionIds(), goal, subtype);

    List<Map<String, Object>> operations = new ArrayList<>();

    Map<String, Object> budget = new LinkedHashMap<>();
    budget.put("resourceName", budgetResource);
    budget.put("name", request.name().trim() + " budget " + System.currentTimeMillis());
    budget.put(
        "amountMicros",
        GoogleAdsMoney.minorToMicros(
            request.dailyBudgetMinor(), context.account().getCurrency()));
    budget.put("deliveryMethod", "STANDARD");
    budget.put("explicitlyShared", false);
    operations.add(Map.of("campaignBudgetOperation", Map.of("create", budget)));

    Map<String, Object> campaign = new LinkedHashMap<>();
    campaign.put("resourceName", campaignResource);
    campaign.put("name", request.name().trim());
    campaign.put("status", "PAUSED");
    campaign.put("advertisingChannelType", "MULTI_CHANNEL");
    campaign.put("advertisingChannelSubType", subtype);
    campaign.put("campaignBudget", budgetResource);
    campaign.put(
        "appCampaignSetting",
        Map.of(
            "appId", request.appId().trim(),
            "appStore", store,
            "biddingStrategyGoalType", goal));
    campaign.put(
        "containsEuPoliticalAdvertising",
        Boolean.TRUE.equals(request.containsEuPoliticalAdvertising())
            ? "CONTAINS_EU_POLITICAL_ADVERTISING"
            : "DOES_NOT_CONTAIN_EU_POLITICAL_ADVERTISING");

    applyBidding(campaign, request, goal, context.account().getCurrency());
    if (!conversionResources.isEmpty() && !"APP_CAMPAIGN_FOR_PRE_REGISTRATION".equals(subtype)) {
      campaign.put(
          "selectiveOptimization",
          Map.of("conversionActions", conversionResources));
    }
    if ("APP_CAMPAIGN_FOR_PRE_REGISTRATION".equals(subtype)) {
      campaign.put(
          "optimizationGoalSetting",
          Map.of("optimizationGoalTypes", List.of("APP_PRE_REGISTRATION")));
    }
    if (request.startDate() != null) {
      campaign.put("startDateTime", startDateTime(request.startDate()));
    }
    if (request.endDate() != null) {
      campaign.put("endDateTime", endDateTime(request.endDate()));
    }

    operations.add(Map.of("campaignOperation", Map.of("create", campaign)));

    for (String location : resolveLocations(context, request.locations())) {
      operations.add(
          Map.of(
              "campaignCriterionOperation",
              Map.of(
                  "create",
                  Map.of(
                      "campaign", campaignResource,
                      "location", Map.of("geoTargetConstant", location),
                      "negative", false))));
    }
    for (String location : resolveLocations(context, request.excludedLocations())) {
      operations.add(
          Map.of(
              "campaignCriterionOperation",
              Map.of(
                  "create",
                  Map.of(
                      "campaign", campaignResource,
                      "location", Map.of("geoTargetConstant", location),
                      "negative", true))));
    }
    for (String language : resolveLanguages(context, request.languages())) {
      operations.add(
          Map.of(
              "campaignCriterionOperation",
              Map.of(
                  "create",
                  Map.of(
                      "campaign", campaignResource,
                      "language", Map.of("languageConstant", language),
                      "negative", false))));
    }

    JsonNode response =
        apiClient.mutateAll(
            context.accessToken(),
            customerId,
            context.loginCustomerId(),
            operations);
    String resourceName = apiClient.firstBulkResourceName(response, "campaignResult");
    String externalCampaignId = suffix(resourceName, "campaign");

    JsonNode row = campaignSyncService.campaign(context, externalCampaignId);
    AdCampaignResponse created =
        AdCampaignResponse.from(
            campaignPersistenceService.persistOne(companyId, accountId, row));

    activityLogService.record(
        companyId,
        accountId,
        AdProvider.GOOGLE_ADS,
        actorUserId,
        "GOOGLE_APP_CAMPAIGN_CREATED",
        "CAMPAIGN",
        externalCampaignId,
        null,
        created,
        Map.of(
            "subtype", subtype,
            "appStore", store,
            "biddingGoal", goal,
            "initialStatus", "PAUSED"));
    return created;
  }

  public GoogleAppCampaignSettingsResponse settings(Long companyId, Long campaignId) {
    Context context = requireAppCampaign(companyId, campaignId);
    String externalId = numeric(context.campaign().getExternalCampaignId(), "Google App campaign ID");
    List<JsonNode> rows =
        apiClient.searchRows(
            context.google().accessToken(),
            context.google().account().getExternalAccountId(),
            context.google().loginCustomerId(),
            "SELECT campaign.advertising_channel_sub_type, "
                + "campaign.app_campaign_setting.app_id, "
                + "campaign.app_campaign_setting.app_store, "
                + "campaign.app_campaign_setting.bidding_strategy_goal_type, "
                + "campaign.selective_optimization.conversion_actions "
                + "FROM campaign WHERE campaign.id = "
                + externalId
                + " LIMIT 1");
    if (rows.isEmpty()) {
      throw new NotFoundException("Google App campaign was not found");
    }
    JsonNode campaign = rows.getFirst().path("campaign");
    JsonNode app = campaign.path("appCampaignSetting");

    List<String> conversions = strings(campaign.path("selectiveOptimization").path("conversionActions"));
    List<String> locations = new ArrayList<>();
    List<String> excludedLocations = new ArrayList<>();
    List<String> languages = new ArrayList<>();
    for (JsonNode row :
        apiClient.searchRows(
            context.google().accessToken(),
            context.google().account().getExternalAccountId(),
            context.google().loginCustomerId(),
            "SELECT campaign_criterion.type, campaign_criterion.negative, "
                + "campaign_criterion.location.geo_target_constant, "
                + "campaign_criterion.language.language_constant "
                + "FROM campaign_criterion WHERE campaign.id = "
                + externalId)) {
      JsonNode criterion = row.path("campaignCriterion");
      String type = text(criterion, "type");
      if ("LOCATION".equals(type)) {
        String resource = text(criterion.path("location"), "geoTargetConstant");
        if (resource != null) {
          (criterion.path("negative").asBoolean(false) ? excludedLocations : locations)
              .add(resource);
        }
      } else if ("LANGUAGE".equals(type)) {
        String resource = text(criterion.path("language"), "languageConstant");
        if (resource != null) languages.add(resource);
      }
    }

    return new GoogleAppCampaignSettingsResponse(
        text(campaign, "advertisingChannelSubType"),
        text(app, "appId"),
        text(app, "appStore"),
        text(app, "biddingStrategyGoalType"),
        conversions,
        locations,
        excludedLocations,
        languages);
  }

  public GoogleAppCampaignPerformanceResponse performance(
      Long companyId, Long campaignId, LocalDate from, LocalDate to) {
    if (from == null || to == null || to.isBefore(from)) {
      throw new BadRequestException("App campaign report requires a valid date range");
    }
    Context context = requireAppCampaign(companyId, campaignId);
    String externalId = numeric(context.campaign().getExternalCampaignId(), "Google App campaign ID");

    long impressions = 0;
    long clicks = 0;
    long costMicros = 0;
    double conversions = 0d;
    double allConversions = 0d;
    long skInstalls = 0;
    long skConversions = 0;

    for (JsonNode row :
        apiClient.searchRows(
            context.google().accessToken(),
            context.google().account().getExternalAccountId(),
            context.google().loginCustomerId(),
            "SELECT metrics.impressions, metrics.clicks, metrics.cost_micros, "
                + "metrics.conversions, metrics.all_conversions, "
                + "metrics.sk_ad_network_installs, metrics.sk_ad_network_total_conversions "
                + "FROM campaign WHERE campaign.id = "
                + externalId
                + " AND segments.date BETWEEN '"
                + from
                + "' AND '"
                + to
                + "'")) {
      JsonNode metrics = row.path("metrics");
      impressions += metrics.path("impressions").asLong(0);
      clicks += metrics.path("clicks").asLong(0);
      costMicros += metrics.path("costMicros").asLong(0);
      conversions += metrics.path("conversions").asDouble(0d);
      allConversions += metrics.path("allConversions").asDouble(0d);
      skInstalls += metrics.path("skAdNetworkInstalls").asLong(0);
      skConversions += metrics.path("skAdNetworkTotalConversions").asLong(0);
    }

    Map<String, long[]> assets = new LinkedHashMap<>();
    Map<String, String> assetNames = new LinkedHashMap<>();
    for (JsonNode row :
        apiClient.searchRows(
            context.google().accessToken(),
            context.google().account().getExternalAccountId(),
            context.google().loginCustomerId(),
            "SELECT ad_group_ad_asset_view.asset, asset.name, "
                + "metrics.impressions, metrics.clicks "
                + "FROM ad_group_ad_asset_view WHERE campaign.id = "
                + externalId
                + " AND segments.date BETWEEN '"
                + from
                + "' AND '"
                + to
                + "'")) {
      String resource = text(row.path("adGroupAdAssetView"), "asset");
      if (resource == null) continue;
      long[] totals = assets.computeIfAbsent(resource, ignored -> new long[2]);
      totals[0] += row.path("metrics").path("impressions").asLong(0);
      totals[1] += row.path("metrics").path("clicks").asLong(0);
      String name = text(row.path("asset"), "name");
      if (name != null) assetNames.put(resource, name);
    }

    List<GoogleAppAssetPerformance> assetPerformance =
        assets.entrySet().stream()
            .map(
                entry ->
                    new GoogleAppAssetPerformance(
                        entry.getKey(),
                        assetNames.get(entry.getKey()),
                        entry.getValue()[0],
                        entry.getValue()[1]))
            .sorted(
                Comparator.comparingLong(GoogleAppAssetPerformance::impressions)
                    .reversed())
            .toList();

    return new GoogleAppCampaignPerformanceResponse(
        impressions,
        clicks,
        costMicros,
        conversions,
        allConversions,
        skInstalls,
        skConversions,
        assetPerformance);
  }

  private void validateCombination(
      String subtype,
      String store,
      String goal,
      CreateGoogleAppCampaignRequest request) {
    if ("APP_CAMPAIGN_FOR_PRE_REGISTRATION".equals(subtype)) {
      if (!"GOOGLE_APP_STORE".equals(store)) {
        throw new BadRequestException(
            "Google App pre-registration campaigns are supported for Google Play apps");
      }
      if (!"OPTIMIZE_PRE_REGISTRATION_CONVERSION_VOLUME".equals(goal)) {
        throw new BadRequestException(
            "App pre-registration requires OPTIMIZE_PRE_REGISTRATION_CONVERSION_VOLUME");
      }
      if (request.conversionActionIds() != null && !request.conversionActionIds().isEmpty()) {
        throw new BadRequestException(
            "Google manages the pre-registration conversion action automatically");
      }
      requireTargetCpa(request);
      return;
    }

    if ("APP_CAMPAIGN_FOR_ENGAGEMENT".equals(subtype)
        && !"OPTIMIZE_IN_APP_CONVERSIONS_TARGET_CONVERSION_COST".equals(goal)) {
      throw new BadRequestException(
          "App engagement campaigns require OPTIMIZE_IN_APP_CONVERSIONS_TARGET_CONVERSION_COST");
    }

    switch (goal) {
      case "OPTIMIZE_INSTALLS_TARGET_INSTALL_COST",
          "OPTIMIZE_IN_APP_CONVERSIONS_TARGET_INSTALL_COST",
          "OPTIMIZE_IN_APP_CONVERSIONS_TARGET_CONVERSION_COST" -> requireTargetCpa(request);
      case "OPTIMIZE_RETURN_ON_ADVERTISING_SPEND" -> requireTargetRoas(request);
      case "OPTIMIZE_INSTALLS_WITHOUT_TARGET_INSTALL_COST",
          "OPTIMIZE_IN_APP_CONVERSIONS_WITHOUT_TARGET_CPA",
          "OPTIMIZE_TOTAL_VALUE_WITHOUT_TARGET_ROAS" -> {
        if (!"APP_CAMPAIGN".equals(subtype)) {
          throw new BadRequestException(
              "Selected App optimization goal is supported only for APP_CAMPAIGN subtype");
        }
      }
      default -> throw new BadRequestException("Unsupported Google App bidding goal");
    }
  }

  private void applyBidding(
      Map<String, Object> campaign,
      CreateGoogleAppCampaignRequest request,
      String goal,
      String currency) {
    switch (goal) {
      case "OPTIMIZE_INSTALLS_TARGET_INSTALL_COST",
          "OPTIMIZE_IN_APP_CONVERSIONS_TARGET_INSTALL_COST",
          "OPTIMIZE_IN_APP_CONVERSIONS_TARGET_CONVERSION_COST",
          "OPTIMIZE_PRE_REGISTRATION_CONVERSION_VOLUME" ->
          campaign.put(
              "targetCpa",
              Map.of(
                  "targetCpaMicros",
                  GoogleAdsMoney.minorToMicros(request.targetCpaMinor(), currency)));
      case "OPTIMIZE_INSTALLS_WITHOUT_TARGET_INSTALL_COST",
          "OPTIMIZE_IN_APP_CONVERSIONS_WITHOUT_TARGET_CPA" ->
          campaign.put("maximizeConversions", Map.of());
      case "OPTIMIZE_RETURN_ON_ADVERTISING_SPEND" ->
          campaign.put("targetRoas", Map.of("targetRoas", request.targetRoas().doubleValue()));
      case "OPTIMIZE_TOTAL_VALUE_WITHOUT_TARGET_ROAS" ->
          campaign.put("maximizeConversionValue", Map.of());
      default -> throw new BadRequestException("Unsupported Google App bidding goal");
    }
  }

  private List<String> conversionResources(
      GoogleAdsAccountContextService.Context context,
      List<String> ids,
      String goal,
      String subtype) {
    boolean required =
        "APP_CAMPAIGN_FOR_ENGAGEMENT".equals(subtype)
            || Set.of(
                    "OPTIMIZE_IN_APP_CONVERSIONS_TARGET_INSTALL_COST",
                    "OPTIMIZE_IN_APP_CONVERSIONS_TARGET_CONVERSION_COST",
                    "OPTIMIZE_IN_APP_CONVERSIONS_WITHOUT_TARGET_CPA",
                    "OPTIMIZE_RETURN_ON_ADVERTISING_SPEND",
                    "OPTIMIZE_TOTAL_VALUE_WITHOUT_TARGET_ROAS")
                .contains(goal);
    if ((ids == null || ids.isEmpty()) && required) {
      throw new BadRequestException(
          "Selected Google App optimization goal requires at least one app conversion action");
    }
    if (ids == null || ids.isEmpty()) return List.of();

    LinkedHashSet<String> result = new LinkedHashSet<>();
    for (String raw : ids) {
      String id = numeric(raw, "Google conversion action ID");
      List<JsonNode> rows =
          apiClient.searchRows(
              context.accessToken(),
              context.account().getExternalAccountId(),
              context.loginCustomerId(),
              "SELECT conversion_action.id, conversion_action.status, "
                  + "conversion_action.origin, conversion_action.type "
                  + "FROM conversion_action WHERE conversion_action.id = "
                  + id
                  + " LIMIT 1");
      if (rows.isEmpty()) {
        throw new BadRequestException("Selected Google app conversion action was not found: " + id);
      }
      JsonNode action = rows.getFirst().path("conversionAction");
      String status = text(action, "status");
      if ("REMOVED".equalsIgnoreCase(status) || "HIDDEN".equalsIgnoreCase(status)) {
        throw new BadRequestException("Selected Google app conversion action is not active: " + id);
      }
      if (!"APP".equalsIgnoreCase(text(action, "origin"))) {
        throw new BadRequestException(
            "Google App campaigns require conversion actions whose origin is APP: " + id);
      }
      String type = text(action, "type");
      if (Set.of(
              "OPTIMIZE_RETURN_ON_ADVERTISING_SPEND",
              "OPTIMIZE_TOTAL_VALUE_WITHOUT_TARGET_ROAS")
          .contains(goal)
          && (type == null || !type.toUpperCase(Locale.ROOT).startsWith("FIREBASE_"))) {
        throw new BadRequestException(
            "Google App value optimization requires Firebase SDK conversion actions: " + id);
      }
      result.add(
          apiClient.customerResourceName(
              context.account().getExternalAccountId(), "conversionActions", id));
    }
    return List.copyOf(result);
  }

  private List<String> resolveLocations(
      GoogleAdsAccountContextService.Context context, List<AudienceLocationInput> values) {
    if (values == null || values.isEmpty()) return List.of();
    LinkedHashSet<String> result = new LinkedHashSet<>();
    for (AudienceLocationInput location : values) {
      if (location == null || location.countryCode() == null || location.countryCode().isBlank()) {
        throw new BadRequestException("App campaign location countryCode is required");
      }
      if (location.radiusKm() != null
          || location.latitude() != null
          || location.longitude() != null) {
        throw new BadRequestException(
            "Google App campaign targeting supports location geo targets, not proximity radius");
      }
      String country = location.countryCode().trim().toUpperCase(Locale.ROOT);
      if (hasText(location.postalCode()) || hasText(location.city()) || hasText(location.region())) {
        String search =
            hasText(location.postalCode())
                ? location.postalCode().trim()
                : hasText(location.city()) ? location.city().trim() : location.region().trim();
        JsonNode response =
            apiClient.suggestGeoTargets(
                context.accessToken(),
                context.loginCustomerId(),
                country,
                List.of(search));
        List<JsonNode> candidates = new ArrayList<>();
        JsonNode suggestions = response.path("geoTargetConstantSuggestions");
        if (suggestions.isArray()) {
          for (JsonNode suggestion : suggestions) {
            JsonNode geo = suggestion.path("geoTargetConstant");
            if (country.equalsIgnoreCase(text(geo, "countryCode"))
                && "ENABLED".equalsIgnoreCase(text(geo, "status"))) {
              candidates.add(geo);
            }
          }
        }
        result.add(
            candidates.stream()
                .max(Comparator.comparingInt(geo -> geoScore(geo, location)))
                .map(geo -> text(geo, "resourceName"))
                .filter(this::hasText)
                .orElseThrow(
                    () ->
                        new BadRequestException(
                            "Google Ads could not resolve App campaign location: " + search)));
      } else {
        List<JsonNode> rows =
            apiClient.searchRows(
                context.accessToken(),
                context.account().getExternalAccountId(),
                context.loginCustomerId(),
                "SELECT geo_target_constant.resource_name, geo_target_constant.status "
                    + "FROM geo_target_constant WHERE geo_target_constant.country_code = '"
                    + country
                    + "' AND geo_target_constant.target_type = 'Country'");
        String resource =
            rows.stream()
                .map(row -> row.path("geoTargetConstant"))
                .filter(geo -> "ENABLED".equalsIgnoreCase(text(geo, "status")))
                .map(geo -> text(geo, "resourceName"))
                .filter(this::hasText)
                .findFirst()
                .orElseThrow(
                    () ->
                        new BadRequestException(
                            "Google Ads country target is unavailable: " + country));
        result.add(resource);
      }
    }
    return List.copyOf(result);
  }

  private List<String> resolveLanguages(
      GoogleAdsAccountContextService.Context context, List<String> values) {
    if (values == null || values.isEmpty()) return List.of();
    Map<String, String> available = new LinkedHashMap<>();
    for (JsonNode row :
        apiClient.searchRows(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            "SELECT language_constant.resource_name, language_constant.code, "
                + "language_constant.name, language_constant.targetable "
                + "FROM language_constant WHERE language_constant.targetable = TRUE")) {
      JsonNode language = row.path("languageConstant");
      String resource = text(language, "resourceName");
      if (resource == null) continue;
      if (hasText(text(language, "code"))) {
        available.put(normalize(text(language, "code")), resource);
      }
      if (hasText(text(language, "name"))) {
        available.put(normalize(text(language, "name")), resource);
      }
    }

    LinkedHashSet<String> result = new LinkedHashSet<>();
    for (String value : values) {
      if (!hasText(value)) continue;
      String resource = available.get(normalize(value));
      if (resource == null) {
        throw new BadRequestException("Google Ads language is not targetable or unknown: " + value);
      }
      result.add(resource);
    }
    return List.copyOf(result);
  }

  private Context requireAppCampaign(Long companyId, Long campaignId) {
    AdCampaign campaign =
        campaignRepository
            .findByIdAndCompanyId(campaignId, companyId)
            .orElseThrow(() -> new NotFoundException("Advertising campaign not found"));
    if (campaign.getProvider() != AdProvider.GOOGLE_ADS
        || !"APP".equalsIgnoreCase(campaign.getObjective())
        || !campaign.isAccessible()) {
      throw new BadRequestException("Google App campaign is not available");
    }
    return new Context(
        campaign, contextService.requireManaged(companyId, campaign.getAdAccountId()));
  }

  private void validateAppId(String store, String value) {
    if (!hasText(value)) {
      throw new BadRequestException("Google App ID is required");
    }
    String appId = value.trim();
    if ("APPLE_APP_STORE".equals(store) && !appId.matches("\\d+")) {
      throw new BadRequestException(
          "Apple App Store ID must contain digits only");
    }
    if ("GOOGLE_APP_STORE".equals(store)
        && (!appId.contains(".") || appId.length() > 255)) {
      throw new BadRequestException(
          "Google Play App ID must be a valid package-style identifier");
    }
  }

  private void requireTargetCpa(CreateGoogleAppCampaignRequest request) {
    if (request.targetCpaMinor() == null || request.targetCpaMinor() <= 0) {
      throw new BadRequestException("Selected Google App optimization goal requires targetCpaMinor");
    }
  }

  private void requireTargetRoas(CreateGoogleAppCampaignRequest request) {
    BigDecimal value = request.targetRoas();
    if (value == null || value.signum() <= 0) {
      throw new BadRequestException("Selected Google App optimization goal requires targetRoas");
    }
  }

  private int geoScore(JsonNode geo, AudienceLocationInput location) {
    String type = normalize(text(geo, "targetType"));
    String name = normalize(text(geo, "name"));
    String canonical = normalize(text(geo, "canonicalName"));
    if (hasText(location.postalCode())) {
      return (type.contains("postal") ? 20 : 0)
          + (name.equals(normalize(location.postalCode())) ? 10 : 0);
    }
    if (hasText(location.city())) {
      return ((type.equals("city") || type.contains("municip")) ? 20 : 0)
          + (name.equals(normalize(location.city())) ? 10 : 0)
          + (hasText(location.region()) && canonical.contains(normalize(location.region())) ? 8 : 0);
    }
    return (type.contains("state") || type.contains("province") || type.contains("region") ? 20 : 0)
        + (name.equals(normalize(location.region())) ? 10 : 0);
  }

  private String enumValue(String value, Set<String> allowed, String label) {
    if (!hasText(value)) throw new BadRequestException(label + " is required");
    String normalized = value.trim().toUpperCase(Locale.ROOT);
    if (!allowed.contains(normalized)) {
      throw new BadRequestException("Unsupported " + label + ": " + normalized);
    }
    return normalized;
  }

  private void validateSchedule(LocalDate start, LocalDate end) {
    if (start != null && end != null && end.isBefore(start)) {
      throw new BadRequestException("Google App campaign end date must be on or after start date");
    }
  }

  private String startDateTime(LocalDate date) {
    return LocalDateTime.of(date, LocalTime.MIDNIGHT).format(GOOGLE_DATE_TIME);
  }

  private String endDateTime(LocalDate date) {
    return LocalDateTime.of(date, LocalTime.of(23, 59, 59)).format(GOOGLE_DATE_TIME);
  }

  private String suffix(String resourceName, String label) {
    if (!hasText(resourceName)) {
      throw new BadRequestException("Google Ads did not return the created " + label);
    }
    int slash = resourceName.lastIndexOf('/');
    if (slash < 0 || slash == resourceName.length() - 1) {
      throw new BadRequestException("Invalid Google Ads " + label + " resource name");
    }
    return resourceName.substring(slash + 1);
  }

  private String numeric(String value, String label) {
    if (value == null) throw new BadRequestException(label + " is invalid");
    String normalized = value.trim();
    int slash = normalized.lastIndexOf('/');
    if (slash >= 0 && slash < normalized.length() - 1) {
      normalized = normalized.substring(slash + 1);
    }
    normalized = normalized.replace("-", "");
    if (!normalized.matches("\d+")) {
      throw new BadRequestException(label + " is invalid");
    }
    return normalized;
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
  }

  private String text(JsonNode node, String field) {
    if (node == null || node.isNull()) return null;
    JsonNode value = node.get(field);
    if (value == null || value.isNull()) return null;
    String result = value.asText(null);
    return hasText(result) ? result.trim() : null;
  }

  private List<String> strings(JsonNode node) {
    if (node == null || !node.isArray()) return List.of();
    List<String> result = new ArrayList<>();
    for (JsonNode item : node) {
      if (item.isTextual() && hasText(item.asText())) result.add(item.asText().trim());
    }
    return List.copyOf(result);
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private record Context(
      AdCampaign campaign,
      GoogleAdsAccountContextService.Context google) {}
}
