package com.messaging.ads.google;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.campaign.dto.AudienceLocationInput;
import com.messaging.ads.campaign.entity.AudienceDefinition;
import com.messaging.ads.entity.AdCampaign;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.dto.GoogleDetailedDemographicCatalogResponse;
import com.messaging.ads.google.dto.GoogleDetailedDemographicOption;
import com.messaging.ads.google.dto.GoogleGeoLocationSuggestion;
import com.messaging.ads.google.dto.GoogleSearchCampaignSettings;
import com.messaging.ads.google.dto.GoogleSearchDemographicTargetingRequest;
import com.messaging.ads.repository.AdCampaignRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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
public class GoogleAdsCampaignTargetingService {

  private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
  private static final Set<String> DAYS =
      Set.of(
          "MONDAY",
          "TUESDAY",
          "WEDNESDAY",
          "THURSDAY",
          "FRIDAY",
          "SATURDAY",
          "SUNDAY");
  private static final Set<Integer> ALLOWED_MINUTES = Set.of(0, 15, 30, 45);
  private static final Set<String> POSITIVE_GEO_TYPES =
      Set.of("PRESENCE", "PRESENCE_OR_INTEREST");
  private static final Set<String> SEARCH_DEVICE_TYPES =
      Set.of("DESKTOP", "MOBILE", "TABLET");
  private static final Set<String> PARENTAL_STATUS_TYPES =
      Set.of("PARENT", "NOT_A_PARENT", "UNDETERMINED");
  private static final Set<String> INCOME_RANGE_TYPES =
      Set.of(
          "INCOME_RANGE_0_50",
          "INCOME_RANGE_50_60",
          "INCOME_RANGE_60_70",
          "INCOME_RANGE_70_80",
          "INCOME_RANGE_80_90",
          "INCOME_RANGE_90_UP",
          "INCOME_RANGE_UNDETERMINED");

  private final GoogleAdsAccountContextService contextService;
  private final GoogleAdsApiClient apiClient;
  private final AdCampaignRepository campaignRepository;
  private final ObjectMapper objectMapper;

  public GoogleSearchCampaignSettings searchCampaignSettings(JsonNode override) {
    boolean searchPartners = booleanValue(override, "targetSearchNetwork", true);
    boolean contentNetwork = booleanValue(override, "targetContentNetwork", false);
    String positiveGeoType =
        text(override, "positiveGeoTargetType", "PRESENCE_OR_INTEREST")
            .toUpperCase(Locale.ROOT);
    if (!POSITIVE_GEO_TYPES.contains(positiveGeoType)) {
      throw new BadRequestException(
          "Google positive geo target type must be PRESENCE or PRESENCE_OR_INTEREST");
    }
    boolean aiMaxEnabled = booleanValue(override, "aiMaxEnabled", false);
    boolean textCustomizationEnabled =
        booleanValue(override, "aiMaxTextCustomizationEnabled", false);
    boolean finalUrlExpansionEnabled =
        booleanValue(override, "aiMaxFinalUrlExpansionEnabled", false);
    List<String> termExclusions =
        stringArray(override, "aiMaxTermExclusions", 25, 30);
    List<String> messagingRestrictions =
        stringArray(override, "aiMaxMessagingRestrictions", 40, 300);
    if (!aiMaxEnabled
        && (textCustomizationEnabled
            || finalUrlExpansionEnabled
            || !termExclusions.isEmpty()
            || !messagingRestrictions.isEmpty())) {
      throw new BadRequestException(
          "Google AI Max text customization, final URL expansion, and text guidelines require AI Max to be enabled");
    }
    return new GoogleSearchCampaignSettings(
        searchPartners,
        contentNetwork,
        positiveGeoType,
        aiMaxEnabled,
        textCustomizationEnabled,
        finalUrlExpansionEnabled,
        termExclusions,
        messagingRestrictions);
  }

  public List<GoogleGeoLocationSuggestion> searchLocations(
      Long companyId,
      Long accountId,
      String query,
      String countryCode,
      int limit) {
    if (query == null || query.isBlank()) {
      throw new BadRequestException("Google geo search query is required");
    }
    if (limit < 1 || limit > 50) {
      throw new BadRequestException("Google geo search limit must be between 1 and 50");
    }

    String normalizedCountry = normalizeCountryCode(countryCode);
    var context = contextService.requireManaged(companyId, accountId);
    JsonNode response =
        apiClient.suggestGeoTargets(
            context.accessToken(),
            context.loginCustomerId(),
            normalizedCountry,
            List.of(query.trim()));

    JsonNode suggestions = response.path("geoTargetConstantSuggestions");
    if (!suggestions.isArray()) return List.of();

    List<GoogleGeoLocationSuggestion> result = new ArrayList<>();
    for (JsonNode suggestion : suggestions) {
      JsonNode geo = suggestion.path("geoTargetConstant");
      if (!normalizedCountry.equalsIgnoreCase(geo.path("countryCode").asText())) continue;
      if (!"ENABLED".equalsIgnoreCase(geo.path("status").asText())) continue;
      String resourceName = geo.path("resourceName").asText(null);
      String name = geo.path("name").asText(null);
      String targetType = geo.path("targetType").asText(null);
      if (!hasText(resourceName) || !hasText(name) || !hasText(targetType)) continue;
      result.add(
          new GoogleGeoLocationSuggestion(
              resourceName,
              name,
              geo.path("canonicalName").asText(name),
              targetType,
              normalizedCountry));
      if (result.size() >= limit) break;
    }
    return List.copyOf(result);
  }

  public GoogleDetailedDemographicCatalogResponse detailedDemographics(
      Long companyId,
      Long accountId,
      String query,
      int requestedLimit) {
    int limit = Math.max(1, Math.min(requestedLimit, 100));
    int providerLimit = Math.min(500, Math.max(limit * 5, 100));
    var context = contextService.requireManaged(companyId, accountId);

    StringBuilder gaql =
        new StringBuilder(
            "SELECT detailed_demographic.id, detailed_demographic.resource_name, "
                + "detailed_demographic.name, detailed_demographic.parent, "
                + "detailed_demographic.launched_to_all, detailed_demographic.availabilities "
                + "FROM detailed_demographic");
    if (hasText(query)) {
      gaql.append(" WHERE detailed_demographic.name LIKE '%")
          .append(gaqlLiteral(query.trim()))
          .append("%'");
    }
    gaql.append(" ORDER BY detailed_demographic.name LIMIT ").append(providerLimit);

    List<GoogleDetailedDemographicOption> options =
        apiClient
            .searchRows(
                context.accessToken(),
                context.account().getExternalAccountId(),
                context.loginCustomerId(),
                gaql.toString())
            .stream()
            .map(row -> row.path("detailedDemographic"))
            .map(
                demographic ->
                    new GoogleDetailedDemographicOption(
                        demographic.path("id").asText(null),
                        demographic.path("resourceName").asText(null),
                        demographic.path("name").asText(null),
                        demographic.path("parent").asText(null),
                        demographic.path("launchedToAll").asBoolean(false),
                        searchAvailable(demographic)))
            .filter(option -> hasText(option.id()) && option.searchAvailable())
            .limit(limit)
            .toList();

    return new GoogleDetailedDemographicCatalogResponse(options);
  }

  public SearchDemographicSnapshot searchDemographics(
      Long companyId,
      Long localCampaignId) {
    AdCampaign campaign = requireSearchCampaign(companyId, localCampaignId);
    var context = contextService.requireManaged(companyId, campaign.getAdAccountId());

    List<String> parentalIncludes = new ArrayList<>();
    List<String> parentalExcludes = new ArrayList<>();
    List<String> incomeIncludes = new ArrayList<>();
    List<String> incomeExcludes = new ArrayList<>();
    List<Long> detailedIncludes = new ArrayList<>();
    List<Long> detailedExcludes = new ArrayList<>();

    for (JsonNode row : demographicCriteria(context, campaign.getExternalCampaignId())) {
      JsonNode criterion = row.path("campaignCriterion");
      String type = criterion.path("type").asText("");
      boolean negative = criterion.path("negative").asBoolean(false);
      if ("PARENTAL_STATUS".equals(type)) {
        String value = criterion.path("parentalStatus").path("type").asText(null);
        if (hasText(value)) {
          (negative ? parentalExcludes : parentalIncludes).add(value);
        }
      } else if ("INCOME_RANGE".equals(type)) {
        String value = criterion.path("incomeRange").path("type").asText(null);
        if (hasText(value)) {
          (negative ? incomeExcludes : incomeIncludes).add(value);
        }
      } else if ("EXTENDED_DEMOGRAPHIC".equals(type)) {
        long id = criterion.path("extendedDemographic").path("extendedDemographicId").asLong(-1);
        if (id > 0) {
          (negative ? detailedExcludes : detailedIncludes).add(id);
        }
      }
    }

    return new SearchDemographicSnapshot(
        List.copyOf(parentalIncludes),
        List.copyOf(parentalExcludes),
        List.copyOf(incomeIncludes),
        List.copyOf(incomeExcludes),
        List.copyOf(detailedIncludes),
        List.copyOf(detailedExcludes));
  }

  public SearchDemographicSnapshot replaceSearchDemographics(
      Long companyId,
      Long localCampaignId,
      GoogleSearchDemographicTargetingRequest request,
      Long actorUserId) {
    AdCampaign campaign = requireSearchCampaign(companyId, localCampaignId);
    var context = contextService.requireManaged(companyId, campaign.getAdAccountId());

    List<String> parentalIncludes =
        enumValues(request.parentalStatusIncludes(), "parentalStatusIncludes", PARENTAL_STATUS_TYPES);
    List<String> parentalExcludes =
        enumValues(request.parentalStatusExcludes(), "parentalStatusExcludes", PARENTAL_STATUS_TYPES);
    List<String> incomeIncludes =
        enumValues(request.incomeRangeIncludes(), "incomeRangeIncludes", INCOME_RANGE_TYPES);
    List<String> incomeExcludes =
        enumValues(request.incomeRangeExcludes(), "incomeRangeExcludes", INCOME_RANGE_TYPES);
    requireDisjoint(parentalIncludes, parentalExcludes, "parental status");
    requireDisjoint(incomeIncludes, incomeExcludes, "income range");

    List<Long> detailedIncludes =
        validateDetailedDemographicIds(context, request.detailedDemographicIds());
    List<Long> detailedExcludes =
        validateDetailedDemographicIds(context, request.excludedDetailedDemographicIds());
    requireDisjoint(detailedIncludes, detailedExcludes, "detailed demographic");

    String customerId = context.account().getExternalAccountId();
    String campaignResource =
        apiClient.customerResourceName(
            customerId, "campaigns", campaign.getExternalCampaignId());

    List<Map<String, Object>> operations = new ArrayList<>();
    for (JsonNode row : demographicCriteria(context, campaign.getExternalCampaignId())) {
      String resourceName = row.path("campaignCriterion").path("resourceName").asText(null);
      if (hasText(resourceName)) operations.add(Map.of("remove", resourceName));
    }

    addEnumCriteria(
        operations, campaignResource, "parentalStatus", "type", parentalIncludes, false);
    addEnumCriteria(
        operations, campaignResource, "parentalStatus", "type", parentalExcludes, true);
    addEnumCriteria(
        operations, campaignResource, "incomeRange", "type", incomeIncludes, false);
    addEnumCriteria(
        operations, campaignResource, "incomeRange", "type", incomeExcludes, true);
    addDetailedCriteria(operations, campaignResource, detailedIncludes, false);
    addDetailedCriteria(operations, campaignResource, detailedExcludes, true);

    if (!operations.isEmpty()) {
      apiClient.mutate(
          context.accessToken(),
          customerId,
          context.loginCustomerId(),
          "campaignCriteria",
          operations);
    }

    return searchDemographics(companyId, localCampaignId);
  }

  public ValidationResult resolve(
      Long companyId,
      Long accountId,
      AudienceDefinition audience,
      JsonNode override) {
    var context = contextService.requireManaged(companyId, accountId);
    TargetingPlan plan = resolvePlan(context, audience, override);
    return new ValidationResult(
        plan.locationCount(),
        plan.languageCount(),
        plan.scheduleCount(),
        plan.deviceCount());
  }

  public ApplyResult apply(
      Long companyId,
      Long localCampaignId,
      AudienceDefinition audience,
      JsonNode override) {
    AdCampaign campaign =
        campaignRepository
            .findByIdAndCompanyId(localCampaignId, companyId)
            .orElseThrow(() -> new NotFoundException("Advertising campaign not found"));
    if (campaign.getProvider() != AdProvider.GOOGLE_ADS || !campaign.isAccessible()) {
      throw new BadRequestException("Google campaign is not available for targeting");
    }

    var context = contextService.requireManaged(companyId, campaign.getAdAccountId());
    TargetingPlan targeting = resolvePlan(context, audience, override);
    if (targeting.criteria().isEmpty()) {
      return new ApplyResult(0, 0, 0, 0, 0, 0, null);
    }

    String customerId = context.account().getExternalAccountId();
    String campaignResource =
        apiClient.customerResourceName(
            customerId, "campaigns", campaign.getExternalCampaignId());
    Set<String> existing = existingFingerprints(context, campaign.getExternalCampaignId());

    List<Map<String, Object>> operations = new ArrayList<>();
    for (Criterion criterion : targeting.criteria()) {
      if (existing.contains(criterion.fingerprint())) continue;
      Map<String, Object> create = new LinkedHashMap<>();
      create.put("campaign", campaignResource);
      create.putAll(criterion.payload());
      operations.add(Map.of("create", create));
    }

    String firstResourceName = null;
    if (!operations.isEmpty()) {
      JsonNode response =
          apiClient.mutate(
              context.accessToken(),
              customerId,
              context.loginCustomerId(),
              "campaignCriteria",
              operations);
      firstResourceName = firstResultResourceName(response);
    }

    return new ApplyResult(
        operations.size(),
        targeting.locationCount(),
        targeting.languageCount(),
        targeting.scheduleCount(),
        targeting.deviceCount(),
        targeting.criteria().size() - operations.size(),
        firstResourceName);
  }

  private TargetingPlan resolvePlan(
      GoogleAdsAccountContextService.Context context,
      AudienceDefinition audience,
      JsonNode override) {
    searchCampaignSettings(override);

    List<Criterion> criteria = new ArrayList<>();
    int locationCount = 0;
    int languageCount = 0;
    int scheduleCount = 0;
    int deviceCount = 0;

    if (audience != null) {
      for (AudienceLocationInput location : locations(audience.getLocationsJson())) {
        criteria.add(resolveLocation(context, location));
        locationCount++;
      }

      List<String> requestedLanguages = strings(audience.getLanguagesJson());
      if (!requestedLanguages.isEmpty()) {
        Map<String, String> languageResources = languageResources(context);
        for (String requested : requestedLanguages) {
          String normalized = normalize(requested);
          String resource = languageResources.get(normalized);
          if (resource == null) {
            throw new BadRequestException(
                "Google Ads language is not targetable or unknown: " + requested);
          }
          criteria.add(
              new Criterion(
                  "LANGUAGE|" + resource,
                  Map.of("language", Map.of("languageConstant", resource))));
          languageCount++;
        }
      }

      for (String ageType : ageRangeTypes(audience.getAgeMin(), audience.getAgeMax())) {
        criteria.add(
            new Criterion(
                "AGE_RANGE|" + ageType,
                Map.of("ageRange", Map.of("type", ageType))));
      }

      for (String genderType : genderTypes(audience.getGendersJson())) {
        criteria.add(
            new Criterion(
                "GENDER|" + genderType,
                Map.of("gender", Map.of("type", genderType))));
      }
    }

    for (String type :
        enumArray(override, "googleParentalStatusIncludes", PARENTAL_STATUS_TYPES)) {
      criteria.add(
          new Criterion(
              "PARENTAL_STATUS|INCLUDE|" + type,
              Map.of(
                  "parentalStatus", Map.of("type", type),
                  "negative", false)));
    }
    for (String type :
        enumArray(override, "googleParentalStatusExcludes", PARENTAL_STATUS_TYPES)) {
      criteria.add(
          new Criterion(
              "PARENTAL_STATUS|EXCLUDE|" + type,
              Map.of(
                  "parentalStatus", Map.of("type", type),
                  "negative", true)));
    }

    for (String type :
        enumArray(override, "googleIncomeRangeIncludes", INCOME_RANGE_TYPES)) {
      criteria.add(
          new Criterion(
              "INCOME_RANGE|INCLUDE|" + type,
              Map.of(
                  "incomeRange", Map.of("type", type),
                  "negative", false)));
    }
    for (String type :
        enumArray(override, "googleIncomeRangeExcludes", INCOME_RANGE_TYPES)) {
      criteria.add(
          new Criterion(
              "INCOME_RANGE|EXCLUDE|" + type,
              Map.of(
                  "incomeRange", Map.of("type", type),
                  "negative", true)));
    }

    for (Long id :
        detailedDemographicIds(
            context, override, "googleDetailedDemographicIds")) {
      criteria.add(
          new Criterion(
              "EXTENDED_DEMOGRAPHIC|INCLUDE|" + id,
              Map.of(
                  "extendedDemographic",
                  Map.of("extendedDemographicId", id),
                  "negative",
                  false)));
    }
    for (Long id :
        detailedDemographicIds(
            context, override, "googleExcludedDetailedDemographicIds")) {
      criteria.add(
          new Criterion(
              "EXTENDED_DEMOGRAPHIC|EXCLUDE|" + id,
              Map.of(
                  "extendedDemographic",
                  Map.of("extendedDemographicId", id),
                  "negative",
                  true)));
    }

    for (DeviceBidModifier device : deviceBidModifiers(override)) {
      criteria.add(
          new Criterion(
              device.fingerprint(),
              Map.of(
                  "device", Map.of("type", device.type()),
                  "bidModifier", device.bidModifier())));
      deviceCount++;
    }

    for (AdSchedule schedule : schedules(override)) {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put(
          "adSchedule",
          Map.of(
              "dayOfWeek",
              schedule.dayOfWeek(),
              "startHour",
              schedule.startHour(),
              "startMinute",
              minuteName(schedule.startMinute()),
              "endHour",
              schedule.endHour(),
              "endMinute",
              minuteName(schedule.endMinute())));
      criteria.add(new Criterion(schedule.fingerprint(), payload));
      scheduleCount++;
    }

    List<Criterion> distinct =
        criteria.stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    Criterion::fingerprint,
                    value -> value,
                    (left, right) -> left,
                    LinkedHashMap::new))
            .values()
            .stream()
            .toList();

    return new TargetingPlan(
        distinct, locationCount, languageCount, scheduleCount, deviceCount);
  }

  private Criterion resolveLocation(
      GoogleAdsAccountContextService.Context context,
      AudienceLocationInput location) {
    String countryCode = normalizeCountryCode(location.countryCode());

    if (location.radiusKm() != null) {
      if (location.radiusKm() <= 0) {
        throw new BadRequestException("Google proximity radius must be positive");
      }
      if ((location.latitude() == null) != (location.longitude() == null)) {
        throw new BadRequestException(
            "Google proximity targeting requires both latitude and longitude when coordinates are used");
      }

      Map<String, Object> proximity = new LinkedHashMap<>();
      proximity.put("radius", location.radiusKm());
      proximity.put("radiusUnits", "KILOMETERS");

      if (location.latitude() != null) {
        proximity.put(
            "geoPoint",
            Map.of(
                "latitudeInMicroDegrees",
                Math.round(location.latitude() * 1_000_000d),
                "longitudeInMicroDegrees",
                Math.round(location.longitude() * 1_000_000d)));
      } else {
        Map<String, Object> address = new LinkedHashMap<>();
        address.put("countryCode", countryCode);
        putIfText(address, "provinceName", location.region());
        putIfText(address, "cityName", location.city());
        putIfText(address, "postalCode", location.postalCode());
        if (address.size() == 1) {
          throw new BadRequestException(
              "Google radius targeting requires a city, region, postal code, or coordinates");
        }
        proximity.put("address", address);
      }

      String fingerprint = proximityFingerprint(proximity);
      return new Criterion(fingerprint, Map.of("proximity", proximity));
    }

    if (location.latitude() != null || location.longitude() != null) {
      throw new BadRequestException(
          "Google coordinate targeting requires radiusKm for proximity targeting");
    }

    String resource =
        hasText(location.postalCode())
            || hasText(location.city())
            || hasText(location.region())
            ? suggestLocation(context, countryCode, location)
            : countryResource(context, countryCode);

    return new Criterion(
        "LOCATION|" + resource,
        Map.of("location", Map.of("geoTargetConstant", resource)));
  }

  private String countryResource(
      GoogleAdsAccountContextService.Context context, String countryCode) {
    String query =
        "SELECT geo_target_constant.resource_name, geo_target_constant.country_code, "
            + "geo_target_constant.target_type, geo_target_constant.status "
            + "FROM geo_target_constant "
            + "WHERE geo_target_constant.country_code = '"
            + countryCode
            + "' AND geo_target_constant.target_type = 'Country'";
    return apiClient
        .searchRows(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            query)
        .stream()
        .map(row -> row.path("geoTargetConstant"))
        .filter(node -> "ENABLED".equalsIgnoreCase(node.path("status").asText()))
        .map(node -> node.path("resourceName").asText(null))
        .filter(this::hasText)
        .findFirst()
        .orElseThrow(
            () ->
                new BadRequestException(
                    "Google Ads country target is unavailable: " + countryCode));
  }

  private String suggestLocation(
      GoogleAdsAccountContextService.Context context,
      String countryCode,
      AudienceLocationInput location) {
    String searchName =
        firstText(location.postalCode(), location.city(), location.region());
    JsonNode response =
        apiClient.suggestGeoTargets(
            context.accessToken(),
            context.loginCustomerId(),
            countryCode,
            List.of(searchName));

    List<JsonNode> candidates = new ArrayList<>();
    JsonNode suggestions = response.path("geoTargetConstantSuggestions");
    if (suggestions.isArray()) {
      for (JsonNode suggestion : suggestions) {
        JsonNode geo = suggestion.path("geoTargetConstant");
        if (!countryCode.equalsIgnoreCase(geo.path("countryCode").asText())) continue;
        if (!"ENABLED".equalsIgnoreCase(geo.path("status").asText())) continue;
        candidates.add(geo);
      }
    }

    return candidates.stream()
        .max(
            Comparator.comparingInt(
                candidate -> locationScore(candidate, location)))
        .map(candidate -> candidate.path("resourceName").asText(null))
        .filter(this::hasText)
        .orElseThrow(
            () ->
                new BadRequestException(
                    "Google Ads could not resolve location '"
                        + searchName
                        + "' in "
                        + countryCode));
  }

  private int locationScore(JsonNode candidate, AudienceLocationInput location) {
    int score = 0;
    String type = normalize(candidate.path("targetType").asText());
    String name = normalize(candidate.path("name").asText());
    String canonical = normalize(candidate.path("canonicalName").asText());

    if (hasText(location.postalCode())) {
      if (type.contains("postal")) score += 20;
      if (name.equals(normalize(location.postalCode()))) score += 10;
    } else if (hasText(location.city())) {
      if (type.equals("city") || type.contains("municip")) score += 20;
      if (name.equals(normalize(location.city()))) score += 10;
      if (hasText(location.region())
          && canonical.contains(normalize(location.region()))) score += 8;
    } else if (hasText(location.region())) {
      if (type.contains("state")
          || type.contains("province")
          || type.contains("region")
          || type.contains("territory")) score += 20;
      if (name.equals(normalize(location.region()))) score += 10;
    }
    return score;
  }

  private Map<String, String> languageResources(
      GoogleAdsAccountContextService.Context context) {
    String query =
        "SELECT language_constant.resource_name, language_constant.code, "
            + "language_constant.name, language_constant.targetable "
            + "FROM language_constant WHERE language_constant.targetable = TRUE";
    Map<String, String> result = new HashMap<>();
    for (JsonNode row :
        apiClient.searchRows(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            query)) {
      JsonNode language = row.path("languageConstant");
      String resource = language.path("resourceName").asText(null);
      if (!hasText(resource)) continue;
      String code = language.path("code").asText(null);
      String name = language.path("name").asText(null);
      if (hasText(code)) result.put(normalize(code), resource);
      if (hasText(name)) result.put(normalize(name), resource);
    }
    return result;
  }

  private Set<String> existingFingerprints(
      GoogleAdsAccountContextService.Context context,
      String externalCampaignId) {
    String query =
        "SELECT campaign_criterion.resource_name, campaign_criterion.type, "
            + "campaign_criterion.location.geo_target_constant, "
            + "campaign_criterion.language.language_constant, "
            + "campaign_criterion.proximity.geo_point.latitude_in_micro_degrees, "
            + "campaign_criterion.proximity.geo_point.longitude_in_micro_degrees, "
            + "campaign_criterion.proximity.radius, "
            + "campaign_criterion.proximity.radius_units, "
            + "campaign_criterion.proximity.address.country_code, "
            + "campaign_criterion.proximity.address.province_name, "
            + "campaign_criterion.proximity.address.city_name, "
            + "campaign_criterion.proximity.address.postal_code, "
            + "campaign_criterion.ad_schedule.day_of_week, "
            + "campaign_criterion.ad_schedule.start_hour, "
            + "campaign_criterion.ad_schedule.start_minute, "
            + "campaign_criterion.ad_schedule.end_hour, "
            + "campaign_criterion.ad_schedule.end_minute, "
            + "campaign_criterion.age_range.type, "
            + "campaign_criterion.gender.type, "
            + "campaign_criterion.parental_status.type, "
            + "campaign_criterion.income_range.type, "
            + "campaign_criterion.extended_demographic.extended_demographic_id, "
            + "campaign_criterion.negative, "
            + "campaign_criterion.device.type, "
            + "campaign_criterion.bid_modifier "
            + "FROM campaign_criterion WHERE campaign.id = "
            + externalCampaignId;

    Set<String> result = new HashSet<>();
    for (JsonNode row :
        apiClient.searchRows(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            query)) {
      JsonNode criterion = row.path("campaignCriterion");
      String type = criterion.path("type").asText();
      if ("LOCATION".equals(type)) {
        String resource = criterion.path("location").path("geoTargetConstant").asText(null);
        if (hasText(resource)) result.add("LOCATION|" + resource);
      } else if ("LANGUAGE".equals(type)) {
        String resource = criterion.path("language").path("languageConstant").asText(null);
        if (hasText(resource)) result.add("LANGUAGE|" + resource);
      } else if ("PROXIMITY".equals(type)) {
        result.add(proximityFingerprint(criterion.path("proximity")));
      } else if ("AD_SCHEDULE".equals(type)) {
        JsonNode schedule = criterion.path("adSchedule");
        result.add(
            "AD_SCHEDULE|"
                + schedule.path("dayOfWeek").asText()
                + "|"
                + schedule.path("startHour").asInt()
                + "|"
                + minuteValue(schedule.path("startMinute").asText())
                + "|"
                + schedule.path("endHour").asInt()
                + "|"
                + minuteValue(schedule.path("endMinute").asText()));
      } else if ("AGE_RANGE".equals(type)) {
        String ageType = criterion.path("ageRange").path("type").asText(null);
        if (hasText(ageType)) result.add("AGE_RANGE|" + ageType);
      } else if ("GENDER".equals(type)) {
        String genderType = criterion.path("gender").path("type").asText(null);
        if (hasText(genderType)) result.add("GENDER|" + genderType);
      } else if ("PARENTAL_STATUS".equals(type)) {
        String value = criterion.path("parentalStatus").path("type").asText(null);
        if (hasText(value)) {
          result.add(
              "PARENTAL_STATUS|"
                  + (criterion.path("negative").asBoolean(false) ? "EXCLUDE|" : "INCLUDE|")
                  + value);
        }
      } else if ("INCOME_RANGE".equals(type)) {
        String value = criterion.path("incomeRange").path("type").asText(null);
        if (hasText(value)) {
          result.add(
              "INCOME_RANGE|"
                  + (criterion.path("negative").asBoolean(false) ? "EXCLUDE|" : "INCLUDE|")
                  + value);
        }
      } else if ("EXTENDED_DEMOGRAPHIC".equals(type)) {
        String value =
            criterion.path("extendedDemographic").path("extendedDemographicId").asText(null);
        if (hasText(value)) {
          result.add(
              "EXTENDED_DEMOGRAPHIC|"
                  + (criterion.path("negative").asBoolean(false) ? "EXCLUDE|" : "INCLUDE|")
                  + value);
        }
      } else if ("DEVICE".equals(type)) {
        String deviceType = criterion.path("device").path("type").asText(null);
        if (hasText(deviceType)) {
          result.add(
              "DEVICE|"
                  + deviceType.toUpperCase(Locale.ROOT)
                  + "|"
                  + normalizedBidModifier(criterion.path("bidModifier").asDouble(1.0d)));
        }
      }
    }
    return result;
  }

  private List<AudienceLocationInput> locations(String json) {
    if (!hasText(json)) return List.of();
    try {
      return objectMapper.readValue(
          json, new TypeReference<List<AudienceLocationInput>>() {});
    } catch (Exception exception) {
      throw new BadRequestException("Canonical audience locations are invalid JSON");
    }
  }

  private List<String> ageRangeTypes(Integer minAge, Integer maxAge) {
    if (minAge == null && maxAge == null) return List.of();

    int effectiveMin = minAge == null ? 18 : minAge;
    int effectiveMax = maxAge == null ? Integer.MAX_VALUE : maxAge;
    if (effectiveMin < 18 || effectiveMax < effectiveMin) {
      throw new BadRequestException(
          "Google demographic age targeting requires a valid range starting at age 18 or above");
    }

    List<AgeBucket> buckets =
        List.of(
            new AgeBucket(18, 24, "AGE_RANGE_18_24"),
            new AgeBucket(25, 34, "AGE_RANGE_25_34"),
            new AgeBucket(35, 44, "AGE_RANGE_35_44"),
            new AgeBucket(45, 54, "AGE_RANGE_45_54"),
            new AgeBucket(55, 64, "AGE_RANGE_55_64"),
            new AgeBucket(65, Integer.MAX_VALUE, "AGE_RANGE_65_UP"));

    boolean minAligned = buckets.stream().anyMatch(bucket -> bucket.min() == effectiveMin);
    boolean maxAligned =
        effectiveMax == Integer.MAX_VALUE
            || buckets.stream().anyMatch(bucket -> bucket.max() == effectiveMax);
    if (!minAligned || !maxAligned) {
      throw new BadRequestException(
          "Google age targeting uses fixed buckets: 18-24, 25-34, 35-44, 45-54, 55-64, and 65+. Choose boundaries that match those ranges.");
    }

    return buckets.stream()
        .filter(bucket -> bucket.min() >= effectiveMin && bucket.max() <= effectiveMax)
        .map(AgeBucket::type)
        .toList();
  }

  private List<String> genderTypes(String json) {
    List<String> requested = strings(json);
    if (requested.isEmpty()) return List.of();

    List<String> result = new ArrayList<>();
    for (String value : requested) {
      String normalized = value.trim().toUpperCase(Locale.ROOT);
      if (!Set.of("MALE", "FEMALE").contains(normalized)) {
        throw new BadRequestException(
            "Google gender targeting supports MALE and FEMALE in the canonical audience");
      }
      if (!result.contains(normalized)) result.add(normalized);
    }
    return List.copyOf(result);
  }

  private List<String> strings(String json) {
    if (!hasText(json)) return List.of();
    try {
      return objectMapper.readValue(json, new TypeReference<List<String>>() {});
    } catch (Exception exception) {
      throw new BadRequestException("Canonical audience language list is invalid JSON");
    }
  }

  private AdCampaign requireSearchCampaign(Long companyId, Long localCampaignId) {
    AdCampaign campaign =
        campaignRepository
            .findByIdAndCompanyId(localCampaignId, companyId)
            .orElseThrow(() -> new NotFoundException("Advertising campaign not found"));
    if (campaign.getProvider() != AdProvider.GOOGLE_ADS
        || !"SEARCH".equalsIgnoreCase(campaign.getObjective())
        || !campaign.isAccessible()) {
      throw new BadRequestException("Google Search campaign is not available for targeting");
    }
    return campaign;
  }

  private List<JsonNode> demographicCriteria(
      GoogleAdsAccountContextService.Context context,
      String externalCampaignId) {
    return apiClient.searchRows(
        context.accessToken(),
        context.account().getExternalAccountId(),
        context.loginCustomerId(),
        "SELECT campaign_criterion.resource_name, campaign_criterion.type, "
            + "campaign_criterion.negative, campaign_criterion.parental_status.type, "
            + "campaign_criterion.income_range.type, "
            + "campaign_criterion.extended_demographic.extended_demographic_id "
            + "FROM campaign_criterion WHERE campaign.id = "
            + externalCampaignId
            + " AND campaign_criterion.status != 'REMOVED' "
            + "AND campaign_criterion.type IN "
            + "('PARENTAL_STATUS','INCOME_RANGE','EXTENDED_DEMOGRAPHIC')");
  }

  private List<String> enumValues(
      List<String> values,
      String field,
      Set<String> allowed) {
    if (values == null || values.isEmpty()) return List.of();
    List<String> result = new ArrayList<>();
    for (String value : values) {
      String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
      if (!allowed.contains(normalized)) {
        throw new BadRequestException(
            "Google " + field + " contains unsupported value: " + value);
      }
      if (!result.contains(normalized)) result.add(normalized);
    }
    return List.copyOf(result);
  }

  private <T> void requireDisjoint(
      List<T> included,
      List<T> excluded,
      String label) {
    Set<T> selected = new HashSet<>(included);
    if (excluded.stream().anyMatch(selected::contains)) {
      throw new BadRequestException(
          "A Google Search " + label + " cannot be both included and excluded");
    }
  }

  private void addEnumCriteria(
      List<Map<String, Object>> operations,
      String campaignResource,
      String criterionField,
      String valueField,
      List<String> values,
      boolean negative) {
    for (String value : values) {
      Map<String, Object> create = new LinkedHashMap<>();
      create.put("campaign", campaignResource);
      create.put(criterionField, Map.of(valueField, value));
      create.put("negative", negative);
      operations.add(Map.of("create", create));
    }
  }

  private void addDetailedCriteria(
      List<Map<String, Object>> operations,
      String campaignResource,
      List<Long> ids,
      boolean negative) {
    for (Long id : ids) {
      Map<String, Object> create = new LinkedHashMap<>();
      create.put("campaign", campaignResource);
      create.put("extendedDemographic", Map.of("extendedDemographicId", id));
      create.put("negative", negative);
      operations.add(Map.of("create", create));
    }
  }

  private String gaqlLiteral(String value) {
    String normalized = value.replace("\\", "\\\\").replace("'", "\\'");
    if (normalized.length() > 100) {
      throw new BadRequestException(
          "Google demographic search query may contain at most 100 characters");
    }
    return normalized;
  }

  private List<String> enumArray(
      JsonNode node, String field, Set<String> allowed) {
    List<String> values = stringArray(node, field, 20, 64);
    if (values.isEmpty()) return List.of();

    List<String> result = new ArrayList<>();
    for (String value : values) {
      String normalized = value.trim().toUpperCase(Locale.ROOT);
      if (!allowed.contains(normalized)) {
        throw new BadRequestException(
            "Google " + field + " contains unsupported value: " + value);
      }
      if (!result.contains(normalized)) result.add(normalized);
    }
    return List.copyOf(result);
  }

  private List<Long> detailedDemographicIds(
      GoogleAdsAccountContextService.Context context,
      JsonNode override,
      String field) {
    if (override == null || override.isNull() || !override.has(field) || override.get(field).isNull()) {
      return List.of();
    }
    JsonNode value = override.get(field);
    if (!value.isArray()) {
      throw new BadRequestException("Google " + field + " must be an array");
    }

    List<Long> requested = new ArrayList<>();
    for (JsonNode item : value) {
      String raw = item.isNumber() || item.isTextual() ? item.asText() : null;
      if (raw == null || !raw.matches("\\d+")) {
        throw new BadRequestException(
            "Google detailed demographic IDs must be numeric");
      }
      Long id = Long.valueOf(raw);
      if (!requested.contains(id)) requested.add(id);
    }
    return validateDetailedDemographicIds(context, requested);
  }

  private List<Long> validateDetailedDemographicIds(
      GoogleAdsAccountContextService.Context context,
      List<Long> requested) {
    if (requested == null || requested.isEmpty()) return List.of();
    if (requested.size() > 100) {
      throw new BadRequestException(
          "Google detailed demographics support at most 100 values per selection");
    }

    String query =
        "SELECT detailed_demographic.id, detailed_demographic.resource_name, "
            + "detailed_demographic.name, detailed_demographic.launched_to_all, "
            + "detailed_demographic.availabilities "
            + "FROM detailed_demographic WHERE detailed_demographic.id IN ("
            + requested.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","))
            + ")";

    Set<Long> available = new HashSet<>();
    for (JsonNode row :
        apiClient.searchRows(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            query)) {
      JsonNode demographic = row.path("detailedDemographic");
      long id = demographic.path("id").asLong(-1);
      if (id > 0 && searchAvailable(demographic)) available.add(id);
    }

    for (Long id : requested) {
      if (id == null || !available.contains(id)) {
        throw new BadRequestException(
            "Google detailed demographic is unavailable for Search: " + id);
      }
    }
    return List.copyOf(requested);
  }

  private boolean searchAvailable(JsonNode category) {
    if (category.path("launchedToAll").asBoolean(false)) return true;
    JsonNode availabilities = category.path("availabilities");
    if (!availabilities.isArray()) return false;
    for (JsonNode availability : availabilities) {
      JsonNode channel = availability.path("channel");
      String mode = channel.path("availabilityMode").asText("");
      if ("ALL_CHANNELS".equalsIgnoreCase(mode)) return true;
      String channelType = channel.path("advertisingChannelType").asText("");
      if ("SEARCH".equalsIgnoreCase(channelType)) return true;
    }
    return false;
  }

  private List<DeviceBidModifier> deviceBidModifiers(JsonNode override) {
    JsonNode value = override == null ? null : override.get("deviceBidModifiers");
    if (value == null || value.isNull()) return List.of();
    if (!value.isObject()) {
      throw new BadRequestException("Google deviceBidModifiers must be an object");
    }

    List<DeviceBidModifier> result = new ArrayList<>();
    var fields = value.fields();
    while (fields.hasNext()) {
      var entry = fields.next();
      String type = entry.getKey().trim().toUpperCase(Locale.ROOT);
      if (!SEARCH_DEVICE_TYPES.contains(type)) {
        throw new BadRequestException(
            "Google Search device targeting supports DESKTOP, MOBILE, and TABLET");
      }
      JsonNode modifierNode = entry.getValue();
      if (!modifierNode.isNumber()) {
        throw new BadRequestException(
            "Google device bid modifier for " + type + " must be numeric");
      }
      double modifier = modifierNode.doubleValue();
      if (modifier != 0.0d && (modifier < 0.1d || modifier > 10.0d)) {
        throw new BadRequestException(
            "Google device bid modifier for "
                + type
                + " must be 0 (exclude) or between 0.1 and 10.0");
      }
      result.add(new DeviceBidModifier(type, normalizedBidModifier(modifier)));
    }
    return List.copyOf(result);
  }

  private double normalizedBidModifier(double value) {
    return Math.round(value * 10000d) / 10000d;
  }

  private List<AdSchedule> schedules(JsonNode override) {
    JsonNode value = override == null ? null : override.get("adSchedules");
    if (value == null || value.isNull()) return List.of();
    if (!value.isArray()) {
      throw new BadRequestException("Google adSchedules must be an array");
    }

    List<AdSchedule> result = new ArrayList<>();
    Map<String, Integer> countByDay = new HashMap<>();
    for (JsonNode item : value) {
      String day = text(item, "dayOfWeek", "").toUpperCase(Locale.ROOT);
      if (!DAYS.contains(day)) {
        throw new BadRequestException("Google ad schedule has an invalid dayOfWeek");
      }
      LocalTime start = time(text(item, "startTime", null), false);
      EndTime end = endTime(text(item, "endTime", null));
      if (end.minutesSinceMidnight() <= start.toSecondOfDay() / 60) {
        throw new BadRequestException(
            "Google ad schedule endTime must be after startTime");
      }

      int count = countByDay.merge(day, 1, Integer::sum);
      if (count > 6) {
        throw new BadRequestException(
            "Google Ads supports at most six ad schedule windows per day");
      }

      result.add(
          new AdSchedule(
              day,
              start.getHour(),
              start.getMinute(),
              end.hour(),
              end.minute()));
    }
    return result;
  }

  private LocalTime time(String value, boolean allowMidnight24) {
    if (!hasText(value)) {
      throw new BadRequestException("Google ad schedule time is required");
    }
    if (allowMidnight24 && "24:00".equals(value)) return LocalTime.MIDNIGHT;
    try {
      LocalTime parsed = LocalTime.parse(value, TIME);
      if (!ALLOWED_MINUTES.contains(parsed.getMinute())) {
        throw new BadRequestException(
            "Google ad schedule minutes must be 00, 15, 30, or 45");
      }
      return parsed;
    } catch (DateTimeParseException exception) {
      throw new BadRequestException(
          "Google ad schedule time must use HH:mm format");
    }
  }

  private EndTime endTime(String value) {
    if ("24:00".equals(value)) return new EndTime(24, 0, 1440);
    LocalTime parsed = time(value, false);
    return new EndTime(
        parsed.getHour(),
        parsed.getMinute(),
        parsed.toSecondOfDay() / 60);
  }

  private List<String> stringArray(
      JsonNode node, String field, int maxItems, int maxLength) {
    if (node == null || node.isNull() || !node.has(field) || node.get(field).isNull()) {
      return List.of();
    }
    JsonNode value = node.get(field);
    if (!value.isArray()) {
      throw new BadRequestException("Google " + field + " must be an array");
    }
    if (value.size() > maxItems) {
      throw new BadRequestException(
          "Google " + field + " supports at most " + maxItems + " values");
    }
    List<String> result = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (JsonNode item : value) {
      if (!item.isTextual() || item.asText().isBlank()) {
        throw new BadRequestException("Google " + field + " values must be non-empty text");
      }
      String normalized = item.asText().trim();
      if (normalized.length() > maxLength) {
        throw new BadRequestException(
            "Google " + field + " values may contain at most " + maxLength + " characters");
      }
      if (seen.add(normalized.toLowerCase(Locale.ROOT))) result.add(normalized);
    }
    return List.copyOf(result);
  }

  private boolean booleanValue(JsonNode node, String field, boolean defaultValue) {
    if (node == null || node.isNull() || !node.has(field) || node.get(field).isNull()) {
      return defaultValue;
    }
    if (!node.get(field).isBoolean()) {
      throw new BadRequestException("Google " + field + " must be true or false");
    }
    return node.get(field).booleanValue();
  }

  private String text(JsonNode node, String field, String defaultValue) {
    if (node == null || node.isNull() || !node.has(field) || node.get(field).isNull()) {
      return defaultValue;
    }
    if (!node.get(field).isTextual()) {
      throw new BadRequestException("Google " + field + " must be text");
    }
    return node.get(field).asText(defaultValue);
  }

  private String normalizeCountryCode(String value) {
    String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    if (!normalized.matches("[A-Z]{2}")) {
      throw new BadRequestException(
          "Canonical audience countryCode must be a 2-letter ISO code");
    }
    return normalized;
  }

  private String proximityFingerprint(Map<String, Object> proximity) {
    Object geoPoint = proximity.get("geoPoint");
    if (geoPoint instanceof Map<?, ?> point) {
      return "PROXIMITY|GEO|"
          + point.get("latitudeInMicroDegrees")
          + "|"
          + point.get("longitudeInMicroDegrees")
          + "|"
          + proximity.get("radius")
          + "|KILOMETERS";
    }
    Object address = proximity.get("address");
    if (address instanceof Map<?, ?> map) {
      return "PROXIMITY|ADDRESS|"
          + normalize(mapText(map, "countryCode"))
          + "|"
          + normalize(mapText(map, "provinceName"))
          + "|"
          + normalize(mapText(map, "cityName"))
          + "|"
          + normalize(mapText(map, "postalCode"))
          + "|"
          + proximity.get("radius")
          + "|KILOMETERS";
    }
    return "PROXIMITY|" + proximity;
  }

  private String proximityFingerprint(JsonNode proximity) {
    JsonNode point = proximity.path("geoPoint");
    if (!point.isMissingNode()
        && (point.has("latitudeInMicroDegrees")
            || point.has("longitudeInMicroDegrees"))) {
      return "PROXIMITY|GEO|"
          + point.path("latitudeInMicroDegrees").asLong()
          + "|"
          + point.path("longitudeInMicroDegrees").asLong()
          + "|"
          + proximity.path("radius").asDouble()
          + "|KILOMETERS";
    }
    JsonNode address = proximity.path("address");
    return "PROXIMITY|ADDRESS|"
        + normalize(address.path("countryCode").asText())
        + "|"
        + normalize(address.path("provinceName").asText())
        + "|"
        + normalize(address.path("cityName").asText())
        + "|"
        + normalize(address.path("postalCode").asText())
        + "|"
        + proximity.path("radius").asDouble()
        + "|KILOMETERS";
  }

  private String minuteName(int minute) {
    return switch (minute) {
      case 0 -> "ZERO";
      case 15 -> "FIFTEEN";
      case 30 -> "THIRTY";
      case 45 -> "FORTY_FIVE";
      default -> throw new BadRequestException("Unsupported Google ad schedule minute");
    };
  }

  private int minuteValue(String value) {
    return switch (value) {
      case "ZERO" -> 0;
      case "FIFTEEN" -> 15;
      case "THIRTY" -> 30;
      case "FORTY_FIVE" -> 45;
      default -> -1;
    };
  }

  private String firstResultResourceName(JsonNode response) {
    if (response == null || !response.path("results").isArray()) return null;
    for (JsonNode result : response.path("results")) {
      String value = result.path("resourceName").asText(null);
      if (hasText(value)) return value;
    }
    return null;
  }

  private void putIfText(Map<String, Object> target, String key, String value) {
    if (hasText(value)) target.put(key, value.trim());
  }

  private String mapText(Map<?, ?> map, String key) {
    Object value = map.get(key);
    return value == null ? "" : String.valueOf(value);
  }

  private String firstText(String... values) {
    for (String value : values) {
      if (hasText(value)) return value.trim();
    }
    return null;
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private record Criterion(String fingerprint, Map<String, Object> payload) {}

  private record AdSchedule(
      String dayOfWeek,
      int startHour,
      int startMinute,
      int endHour,
      int endMinute) {
    String fingerprint() {
      return "AD_SCHEDULE|"
          + dayOfWeek
          + "|"
          + startHour
          + "|"
          + startMinute
          + "|"
          + endHour
          + "|"
          + endMinute;
    }
  }

  private record AgeBucket(int min, int max, String type) {}

  private record DeviceBidModifier(String type, double bidModifier) {
    String fingerprint() {
      return "DEVICE|" + type + "|" + bidModifier;
    }
  }

  private record EndTime(int hour, int minute, int minutesSinceMidnight) {}

  private record TargetingPlan(
      List<Criterion> criteria,
      int locationCount,
      int languageCount,
      int scheduleCount,
      int deviceCount) {}

  public record SearchDemographicSnapshot(
      List<String> parentalStatusIncludes,
      List<String> parentalStatusExcludes,
      List<String> incomeRangeIncludes,
      List<String> incomeRangeExcludes,
      List<Long> detailedDemographicIds,
      List<Long> excludedDetailedDemographicIds) {}

  public record ValidationResult(
      int locationCount,
      int languageCount,
      int scheduleCount,
      int deviceCount) {}

  public record ApplyResult(
      int createdCount,
      int locationCount,
      int languageCount,
      int scheduleCount,
      int deviceCount,
      int reusedCount,
      String firstExternalResource) {}
}
