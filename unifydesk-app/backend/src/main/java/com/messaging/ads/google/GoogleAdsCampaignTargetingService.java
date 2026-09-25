package com.messaging.ads.google;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.campaign.dto.AudienceLocationInput;
import com.messaging.ads.campaign.entity.AudienceDefinition;
import com.messaging.ads.entity.AdCampaign;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.dto.GoogleSearchCampaignSettings;
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
    return new GoogleSearchCampaignSettings(
        searchPartners, contentNetwork, positiveGeoType);
  }

  public ValidationResult resolve(
      Long companyId,
      Long accountId,
      AudienceDefinition audience,
      JsonNode override) {
    var context = contextService.requireManaged(companyId, accountId);
    TargetingPlan plan = resolvePlan(context, audience, override);
    return new ValidationResult(
        plan.locationCount(), plan.languageCount(), plan.scheduleCount());
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
      return new ApplyResult(0, 0, 0, 0, 0, null);
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
        distinct, locationCount, languageCount, scheduleCount);
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
            + "campaign_criterion.ad_schedule.end_minute "
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

  private List<String> strings(String json) {
    if (!hasText(json)) return List.of();
    try {
      return objectMapper.readValue(json, new TypeReference<List<String>>() {});
    } catch (Exception exception) {
      throw new BadRequestException("Canonical audience language list is invalid JSON");
    }
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

  private record EndTime(int hour, int minute, int minutesSinceMidnight) {}

  private record TargetingPlan(
      List<Criterion> criteria,
      int locationCount,
      int languageCount,
      int scheduleCount) {}

  public record ValidationResult(
      int locationCount,
      int languageCount,
      int scheduleCount) {}

  public record ApplyResult(
      int createdCount,
      int locationCount,
      int languageCount,
      int scheduleCount,
      int reusedCount,
      String firstExternalResource) {}
}
