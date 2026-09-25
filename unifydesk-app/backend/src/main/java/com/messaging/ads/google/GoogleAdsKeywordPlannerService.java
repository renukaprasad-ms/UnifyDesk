package com.messaging.ads.google;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.campaign.dto.AudienceLocationInput;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.dto.GoogleKeywordPlannerHistoricalRequest;
import com.messaging.ads.google.dto.GoogleKeywordPlannerIdeasRequest;
import com.messaging.ads.google.dto.GoogleKeywordPlannerResponse;
import com.messaging.ads.service.AdActivityLogService;
import com.messaging.common.exception.BadRequestException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleAdsKeywordPlannerService {

  private static final Duration IDEA_CACHE_TTL = Duration.ofHours(6);
  private static final Duration HISTORICAL_CACHE_TTL = Duration.ofHours(24);
  private static final Set<String> NETWORKS =
      Set.of("GOOGLE_SEARCH", "GOOGLE_SEARCH_AND_PARTNERS");

  private final GoogleAdsAccountContextService contextService;
  private final GoogleAdsApiClient apiClient;
  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;
  private final AdActivityLogService activityLogService;

  public GoogleKeywordPlannerResponse ideas(
      Long companyId,
      Long accountId,
      GoogleKeywordPlannerIdeasRequest request,
      Long actorUserId) {
    var context = contextService.requireManaged(companyId, accountId);
    requirePlannerAccount(context);

    List<String> seeds = normalizeSeeds(request.seedKeywords(), 20);
    String url = normalizeUrl(request.url());
    boolean siteSeed = Boolean.TRUE.equals(request.useEntireSite());

    if (seeds.isEmpty() && url == null) {
      throw new BadRequestException(
          "Keyword Planner requires at least one seed keyword or a website URL");
    }
    if (siteSeed && url == null) {
      throw new BadRequestException("Entire-site planning requires a website URL");
    }
    if (siteSeed && !seeds.isEmpty()) {
      throw new BadRequestException(
          "Entire-site planning cannot be combined with seed keywords");
    }

    PlannerTargets targets =
        resolveTargets(context, request.locations(), request.language());
    String network = normalizeNetwork(request.network());
    Map<String, Object> body = commonBody(targets, network, request.includeAdultKeywords());
    body.put("pageSize", request.pageSize() == null ? 250 : request.pageSize());
    if (hasText(request.pageToken())) body.put("pageToken", request.pageToken().trim());
    body.put("keywordAnnotation", List.of("KEYWORD_CONCEPT"));
    body.put("historicalMetricsOptions", Map.of("includeAverageCpc", true));

    if (siteSeed) {
      body.put("siteSeed", Map.of("site", url));
    } else if (!seeds.isEmpty() && url != null) {
      body.put("keywordAndUrlSeed", Map.of("keywords", seeds, "url", url));
    } else if (!seeds.isEmpty()) {
      body.put("keywordSeed", Map.of("keywords", seeds));
    } else {
      body.put("urlSeed", Map.of("url", url));
    }

    String cacheKey = cacheKey("ideas", accountId, body);
    GoogleKeywordPlannerResponse cached = readCache(cacheKey);
    if (cached != null) {
      recordActivity(
          companyId,
          context,
          actorUserId,
          "GOOGLE_KEYWORD_PLANNER_IDEAS_GENERATED",
          true,
          seeds.size(),
          cached.rows().size(),
          network);
      return cached.asCached();
    }

    JsonNode provider =
        apiClient.customerColonAction(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            "generateKeywordIdeas",
            body);

    GoogleKeywordPlannerResponse response =
        map(
            context.account().getCurrency(),
            network,
            targets,
            provider,
            "keywordIdeaMetrics");
    writeCache(cacheKey, response, IDEA_CACHE_TTL);
    recordActivity(
        companyId,
        context,
        actorUserId,
        "GOOGLE_KEYWORD_PLANNER_IDEAS_GENERATED",
        false,
        seeds.size(),
        response.rows().size(),
        network);
    return response;
  }

  public GoogleKeywordPlannerResponse historical(
      Long companyId,
      Long accountId,
      GoogleKeywordPlannerHistoricalRequest request,
      Long actorUserId) {
    var context = contextService.requireManaged(companyId, accountId);
    requirePlannerAccount(context);

    List<String> keywords = normalizeSeeds(request.keywords(), 10_000);
    if (keywords.isEmpty()) {
      throw new BadRequestException("Historical metrics require at least one keyword");
    }

    PlannerTargets targets =
        resolveTargets(context, request.locations(), request.language());
    String network = normalizeNetwork(request.network());
    Map<String, Object> body = commonBody(targets, network, request.includeAdultKeywords());
    body.put("keywords", keywords);
    body.put("historicalMetricsOptions", Map.of("includeAverageCpc", true));

    String cacheKey = cacheKey("historical", accountId, body);
    GoogleKeywordPlannerResponse cached = readCache(cacheKey);
    if (cached != null) {
      recordActivity(
          companyId,
          context,
          actorUserId,
          "GOOGLE_KEYWORD_PLANNER_HISTORICAL_GENERATED",
          true,
          keywords.size(),
          cached.rows().size(),
          network);
      return cached.asCached();
    }

    JsonNode provider =
        apiClient.customerColonAction(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            "generateKeywordHistoricalMetrics",
            body);

    GoogleKeywordPlannerResponse response =
        map(
            context.account().getCurrency(),
            network,
            targets,
            provider,
            "keywordMetrics");
    writeCache(cacheKey, response, HISTORICAL_CACHE_TTL);
    recordActivity(
        companyId,
        context,
        actorUserId,
        "GOOGLE_KEYWORD_PLANNER_HISTORICAL_GENERATED",
        false,
        keywords.size(),
        response.rows().size(),
        network);
    return response;
  }

  private Map<String, Object> commonBody(
      PlannerTargets targets, String network, Boolean includeAdultKeywords) {
    Map<String, Object> body = new LinkedHashMap<>();
    if (targets.languageResource() != null) {
      body.put("language", targets.languageResource());
    }
    if (!targets.geoTargetResources().isEmpty()) {
      body.put("geoTargetConstants", targets.geoTargetResources());
    }
    body.put("includeAdultKeywords", Boolean.TRUE.equals(includeAdultKeywords));
    body.put("keywordPlanNetwork", network);
    return body;
  }

  private PlannerTargets resolveTargets(
      GoogleAdsAccountContextService.Context context,
      List<AudienceLocationInput> requestedLocations,
      String requestedLanguage) {
    List<AudienceLocationInput> locations =
        requestedLocations == null ? List.of() : requestedLocations;
    if (locations.size() > 10) {
      throw new BadRequestException("Google Keyword Planner supports at most 10 geo targets");
    }

    LinkedHashSet<String> geoResources = new LinkedHashSet<>();
    for (AudienceLocationInput location : locations) {
      if (location == null) continue;
      if (location.radiusKm() != null
          || location.latitude() != null
          || location.longitude() != null) {
        throw new BadRequestException(
            "Google Keyword Planner supports discrete country, region, city, or postal targets; proximity/radius targeting is not supported by the planning request");
      }
      geoResources.add(resolvePlanningLocation(context, location));
    }

    String languageResource = null;
    if (hasText(requestedLanguage)) {
      Map<String, String> resources = languageResources(context);
      languageResource = resources.get(normalize(requestedLanguage));
      if (languageResource == null) {
        throw new BadRequestException(
            "Google Keyword Planner language is unavailable: " + requestedLanguage.trim());
      }
    }

    return new PlannerTargets(List.copyOf(geoResources), languageResource);
  }

  private String resolvePlanningLocation(
      GoogleAdsAccountContextService.Context context, AudienceLocationInput location) {
    String countryCode = normalizeCountryCode(location.countryCode());
    if (hasText(location.postalCode())
        || hasText(location.city())
        || hasText(location.region())) {
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
          .max(Comparator.comparingInt(candidate -> locationScore(candidate, location)))
          .map(candidate -> candidate.path("resourceName").asText(null))
          .filter(this::hasText)
          .orElseThrow(
              () ->
                  new BadRequestException(
                      "Google Keyword Planner could not resolve location '"
                          + searchName
                          + "' in "
                          + countryCode));
    }

    String query =
        "SELECT geo_target_constant.resource_name, geo_target_constant.country_code, "
            + "geo_target_constant.target_type, geo_target_constant.status "
            + "FROM geo_target_constant WHERE geo_target_constant.country_code = '"
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
                    "Google Keyword Planner country target is unavailable: " + countryCode));
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

  private GoogleKeywordPlannerResponse map(
      String currency,
      String network,
      PlannerTargets targets,
      JsonNode provider,
      String metricsField) {
    List<GoogleKeywordPlannerResponse.Row> rows = new ArrayList<>();
    JsonNode results = provider == null ? null : provider.path("results");
    if (results != null && results.isArray()) {
      for (JsonNode result : results) {
        String text = text(result, "text");
        if (!hasText(text)) continue;
        JsonNode metrics = result.path(metricsField);
        rows.add(
            new GoogleKeywordPlannerResponse.Row(
                text,
                longOrNull(metrics, "avgMonthlySearches"),
                text(metrics, "competition"),
                longOrNull(metrics, "competitionIndex"),
                money(metrics, "lowTopOfPageBidMicros"),
                money(metrics, "highTopOfPageBidMicros"),
                money(metrics, "averageCpcMicros"),
                monthlyVolumes(metrics.path("monthlySearchVolumes")),
                strings(result.path("closeVariants")),
                concepts(result.path("keywordAnnotations").path("concepts"))));
      }
    }

    return new GoogleKeywordPlannerResponse(
        false,
        currency,
        network,
        targets.languageResource(),
        targets.geoTargetResources(),
        List.copyOf(rows),
        text(provider, "nextPageToken"),
        longValue(provider, "totalSize", rows.size()));
  }

  private List<GoogleKeywordPlannerResponse.MonthlyVolume> monthlyVolumes(JsonNode node) {
    if (node == null || !node.isArray()) return List.of();
    List<GoogleKeywordPlannerResponse.MonthlyVolume> result = new ArrayList<>();
    for (JsonNode item : node) {
      result.add(
          new GoogleKeywordPlannerResponse.MonthlyVolume(
              longValue(item, "year", 0L),
              text(item, "month"),
              longOrNull(item, "monthlySearches")));
    }
    return List.copyOf(result);
  }

  private List<GoogleKeywordPlannerResponse.Concept> concepts(JsonNode node) {
    if (node == null || !node.isArray()) return List.of();
    List<GoogleKeywordPlannerResponse.Concept> result = new ArrayList<>();
    for (JsonNode item : node) {
      JsonNode group = item.path("conceptGroup");
      result.add(
          new GoogleKeywordPlannerResponse.Concept(
              text(item, "name"),
              text(group, "name"),
              text(group, "type")));
    }
    return List.copyOf(result);
  }

  private List<String> strings(JsonNode node) {
    if (node == null || !node.isArray()) return List.of();
    List<String> result = new ArrayList<>();
    for (JsonNode item : node) {
      if (item.isTextual() && !item.asText().isBlank()) {
        result.add(item.asText().trim());
      }
    }
    return List.copyOf(result);
  }

  private java.math.BigDecimal money(JsonNode node, String field) {
    Long micros = longOrNull(node, field);
    return micros == null ? null : GoogleAdsMoney.microsToMajor(micros);
  }

  private void requirePlannerAccount(GoogleAdsAccountContextService.Context context) {
    if (context.managerAccount()) {
      throw new BadRequestException(
          "Google Keyword Planner requires a managed advertising customer account, not a manager account");
    }
  }

  private List<String> normalizeSeeds(List<String> values, int max) {
    if (values == null || values.isEmpty()) return List.of();
    if (values.size() > max) {
      throw new BadRequestException("Google Keyword Planner supports at most " + max + " keywords");
    }
    LinkedHashSet<String> result = new LinkedHashSet<>();
    for (String value : values) {
      if (!hasText(value)) continue;
      String normalized = value.trim();
      if (normalized.length() > 80) {
        throw new BadRequestException(
            "Google Keyword Planner seed keywords may contain at most 80 characters");
      }
      result.add(normalized);
    }
    return List.copyOf(result);
  }

  private String normalizeUrl(String value) {
    if (!hasText(value)) return null;
    String normalized = value.trim();
    try {
      java.net.URI uri = java.net.URI.create(normalized);
      if (uri.getScheme() == null
          || (!"http".equalsIgnoreCase(uri.getScheme())
              && !"https".equalsIgnoreCase(uri.getScheme()))
          || uri.getHost() == null) {
        throw new BadRequestException(
            "Keyword Planner website URL must be an absolute HTTP(S) URL");
      }
    } catch (IllegalArgumentException exception) {
      throw new BadRequestException(
          "Keyword Planner website URL must be a valid absolute HTTP(S) URL");
    }
    return normalized;
  }

  private String normalizeNetwork(String value) {
    String normalized =
        hasText(value)
            ? value.trim().toUpperCase(Locale.ROOT)
            : "GOOGLE_SEARCH";
    if (!NETWORKS.contains(normalized)) {
      throw new BadRequestException(
          "Keyword Planner network must be GOOGLE_SEARCH or GOOGLE_SEARCH_AND_PARTNERS");
    }
    return normalized;
  }

  private String normalizeCountryCode(String value) {
    String normalized =
        value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    if (!normalized.matches("[A-Z]{2}")) {
      throw new BadRequestException(
          "Keyword Planner locations require a two-letter ISO country code");
    }
    return normalized;
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

  private String firstText(String... values) {
    for (String value : values) if (hasText(value)) return value.trim();
    return null;
  }

  private String text(JsonNode node, String field) {
    if (node == null || node.isNull()) return null;
    JsonNode value = node.get(field);
    if (value == null || value.isNull()) return null;
    String text = value.asText(null);
    return hasText(text) ? text.trim() : null;
  }

  private Long longOrNull(JsonNode node, String field) {
    String value = text(node, field);
    if (value == null) return null;
    try {
      return new java.math.BigDecimal(value).longValue();
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  private long longValue(JsonNode node, String field, long fallback) {
    Long value = longOrNull(node, field);
    return value == null ? fallback : value;
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private String cacheKey(String mode, Long accountId, Map<String, Object> body) {
    try {
      String canonical = objectMapper.writeValueAsString(body);
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      String hash =
          HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
      return "ads:google:keyword-planner:" + mode + ":" + accountId + ":" + hash;
    } catch (Exception exception) {
      throw new BadRequestException("Unable to prepare Keyword Planner cache key");
    }
  }

  private GoogleKeywordPlannerResponse readCache(String key) {
    try {
      String value = redisTemplate.opsForValue().get(key);
      if (!hasText(value)) return null;
      return objectMapper.readValue(value, GoogleKeywordPlannerResponse.class);
    } catch (RuntimeException | JsonProcessingException exception) {
      log.debug("keyword_planner_cache_read_failed key={} reason={}", key, exception.getMessage());
      return null;
    }
  }

  private void writeCache(
      String key, GoogleKeywordPlannerResponse response, Duration ttl) {
    try {
      redisTemplate
          .opsForValue()
          .set(key, objectMapper.writeValueAsString(response), ttl);
    } catch (RuntimeException | JsonProcessingException exception) {
      log.debug("keyword_planner_cache_write_failed key={} reason={}", key, exception.getMessage());
    }
  }

  private void recordActivity(
      Long companyId,
      GoogleAdsAccountContextService.Context context,
      Long actorUserId,
      String action,
      boolean cached,
      int inputCount,
      int resultCount,
      String network) {
    activityLogService.record(
        companyId,
        context.account().getId(),
        AdProvider.GOOGLE_ADS,
        actorUserId,
        action,
        "AD_ACCOUNT",
        context.account().getExternalAccountId(),
        null,
        null,
        Map.of(
            "cached", cached,
            "inputCount", inputCount,
            "resultCount", resultCount,
            "network", network));
  }

  private record PlannerTargets(
      List<String> geoTargetResources, String languageResource) {}
}
