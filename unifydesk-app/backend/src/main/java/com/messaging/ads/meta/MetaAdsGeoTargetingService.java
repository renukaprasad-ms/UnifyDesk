package com.messaging.ads.meta;

import com.fasterxml.jackson.databind.JsonNode;
import com.messaging.ads.campaign.dto.AudienceLocationInput;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.enums.AdConnectionStatus;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.meta.dto.CreateMetaAdSetRequest;
import com.messaging.ads.meta.dto.MetaGeoLocationSuggestion;
import com.messaging.ads.repository.AdAccountRepository;
import com.messaging.ads.repository.AdProviderConnectionRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MetaAdsGeoTargetingService {

  private static final Set<String> SEARCH_TYPES = Set.of("COUNTRY", "REGION", "CITY", "ZIP");
  private static final List<String> DEFAULT_LOCATION_TYPES = List.of("home", "recent");

  private final AdAccountRepository accountRepository;
  private final AdProviderConnectionRepository connectionRepository;
  private final MetaAdsTokenService tokenService;
  private final MetaAdsApiClient apiClient;

  public List<MetaGeoLocationSuggestion> search(
      Long companyId,
      Long accountId,
      String query,
      List<String> types,
      String countryCode,
      Integer limit) {
    Context context = context(companyId, accountId);
    List<String> normalizedTypes = normalizeSearchTypes(types);
    if ((query == null || query.isBlank())
        && normalizedTypes.stream().anyMatch(type -> !"COUNTRY".equals(type))) {
      throw new BadRequestException("Meta geo search query is required");
    }
    return search(
        context,
        query,
        normalizedTypes,
        normalizeCountryCodeNullable(countryCode),
        limit == null ? 20 : limit);
  }

  public List<CreateMetaAdSetRequest.TargetingLocation> resolveCanonical(
      Long companyId,
      Long accountId,
      List<AudienceLocationInput> locations) {
    if (locations == null || locations.isEmpty()) {
      throw new BadRequestException("Meta targeting requires at least one location");
    }
    Context context = context(companyId, accountId);
    List<CreateMetaAdSetRequest.TargetingLocation> result = new ArrayList<>();
    for (AudienceLocationInput location : locations) {
      if (location == null) continue;
      String countryCode = normalizeCountryCode(location.countryCode());

      if (location.radiusKm() != null) {
        if (location.latitude() == null || location.longitude() == null) {
          throw new BadRequestException(
              "Meta radius targeting requires latitude and longitude");
        }
        result.add(
            new CreateMetaAdSetRequest.TargetingLocation(
                countryCode,
                "CUSTOM",
                null,
                firstText(location.city(), location.region(), location.postalCode()),
                location.latitude(),
                location.longitude(),
                location.radiusKm(),
                DEFAULT_LOCATION_TYPES));
        continue;
      }

      if (location.latitude() != null || location.longitude() != null) {
        throw new BadRequestException(
            "Meta coordinate targeting requires radiusKm");
      }

      if (hasText(location.postalCode())) {
        MetaGeoLocationSuggestion resolved =
            resolveOne(
                context,
                location.postalCode(),
                "ZIP",
                countryCode,
                location.region());
        result.add(fromSuggestion(resolved, DEFAULT_LOCATION_TYPES));
      } else if (hasText(location.city())) {
        MetaGeoLocationSuggestion resolved =
            resolveOne(
                context,
                location.city(),
                "CITY",
                countryCode,
                location.region());
        result.add(fromSuggestion(resolved, DEFAULT_LOCATION_TYPES));
      } else if (hasText(location.region())) {
        MetaGeoLocationSuggestion resolved =
            resolveOne(
                context,
                location.region(),
                "REGION",
                countryCode,
                null);
        result.add(fromSuggestion(resolved, DEFAULT_LOCATION_TYPES));
      } else {
        result.add(
            new CreateMetaAdSetRequest.TargetingLocation(
                countryCode,
                "COUNTRY",
                countryCode,
                countryCode,
                null,
                null,
                null,
                DEFAULT_LOCATION_TYPES));
      }
    }
    if (result.isEmpty()) {
      throw new BadRequestException("Meta targeting requires at least one valid location");
    }
    return List.copyOf(result);
  }

  private MetaGeoLocationSuggestion resolveOne(
      Context context,
      String query,
      String type,
      String countryCode,
      String expectedRegion) {
    List<MetaGeoLocationSuggestion> suggestions =
        search(context, query, List.of(type), countryCode, 25);
    return suggestions.stream()
        .max(
            Comparator.comparingInt(
                suggestion ->
                    score(suggestion, query, type, countryCode, expectedRegion)))
        .filter(
            suggestion ->
                score(suggestion, query, type, countryCode, expectedRegion) > 0)
        .orElseThrow(
            () ->
                new BadRequestException(
                    "Meta could not resolve "
                        + type.toLowerCase(Locale.ROOT)
                        + " target '"
                        + query
                        + "' in "
                        + countryCode));
  }

  private int score(
      MetaGeoLocationSuggestion suggestion,
      String query,
      String type,
      String countryCode,
      String expectedRegion) {
    if (suggestion == null
        || !type.equalsIgnoreCase(suggestion.type())
        || !countryCode.equalsIgnoreCase(suggestion.countryCode())) {
      return -1000;
    }
    int score = 10;
    if (normalize(suggestion.name()).equals(normalize(query))) score += 100;
    else if (normalize(suggestion.name()).contains(normalize(query))) score += 50;
    if (hasText(expectedRegion)
        && normalize(suggestion.region()).equals(normalize(expectedRegion))) {
      score += 75;
    }
    return score;
  }

  private List<MetaGeoLocationSuggestion> search(
      Context context,
      String query,
      List<String> normalizedTypes,
      String countryCode,
      int limit) {
    JsonNode response =
        apiClient.searchAdGeoLocations(
            context.accessToken(),
            query,
            normalizedTypes.stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .toList(),
            countryCode,
            limit);
    JsonNode data = response.path("data");
    if (!data.isArray()) return List.of();

    List<MetaGeoLocationSuggestion> result = new ArrayList<>();
    for (JsonNode item : data) {
      String key = text(item, "key");
      String name = text(item, "name");
      String type = text(item, "type");
      if (!hasText(key) || !hasText(name) || !hasText(type)) continue;
      result.add(
          new MetaGeoLocationSuggestion(
              key,
              name,
              type.toUpperCase(Locale.ROOT),
              text(item, "country_code"),
              text(item, "country_name"),
              text(item, "region"),
              text(item, "region_id"),
              booleanValue(item, "supports_region"),
              booleanValue(item, "supports_city")));
    }
    return List.copyOf(result);
  }

  private CreateMetaAdSetRequest.TargetingLocation fromSuggestion(
      MetaGeoLocationSuggestion suggestion, List<String> locationTypes) {
    return new CreateMetaAdSetRequest.TargetingLocation(
        suggestion.countryCode(),
        suggestion.type(),
        suggestion.key(),
        suggestion.name(),
        null,
        null,
        null,
        locationTypes);
  }

  private List<String> normalizeSearchTypes(List<String> values) {
    List<String> result =
        values == null || values.isEmpty()
            ? List.of("REGION", "CITY", "ZIP")
            : values.stream()
                .filter(this::hasText)
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
    if (result.isEmpty() || result.stream().anyMatch(value -> !SEARCH_TYPES.contains(value))) {
      throw new BadRequestException(
          "Meta geo search types must be COUNTRY, REGION, CITY, or ZIP");
    }
    return result;
  }

  private Context context(Long companyId, Long accountId) {
    AdAccount account =
        accountRepository
            .findByIdAndCompanyId(accountId, companyId)
            .orElseThrow(() -> new NotFoundException("Ad account not found"));
    if (account.getProvider() != AdProvider.META_ADS
        || !account.isManaged()
        || !account.isAccessible()) {
      throw new BadRequestException("Meta ad account is not enabled for management");
    }
    var connection =
        connectionRepository
            .findByIdAndCompanyId(account.getConnectionId(), companyId)
            .orElseThrow(() -> new NotFoundException("Meta Ads connection not found"));
    if (connection.getStatus() != AdConnectionStatus.ACTIVE) {
      throw new BadRequestException("Meta Ads connection is not active");
    }
    return new Context(
        account,
        tokenService.read(connection.getId()).token().accessToken());
  }

  private String normalizeCountryCode(String value) {
    String normalized = normalizeCountryCodeNullable(value);
    if (normalized == null) {
      throw new BadRequestException("Meta location country code is required");
    }
    return normalized;
  }

  private String normalizeCountryCodeNullable(String value) {
    if (!hasText(value)) return null;
    String normalized = value.trim().toUpperCase(Locale.ROOT);
    if (!normalized.matches("[A-Z]{2}")) {
      throw new BadRequestException(
          "Meta location country code must be a two-letter ISO code");
    }
    return normalized;
  }

  private String text(JsonNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || value.isNull()) return null;
    String result = value.asText(null);
    return hasText(result) ? result.trim() : null;
  }

  private Boolean booleanValue(JsonNode node, String field) {
    JsonNode value = node.get(field);
    return value == null || value.isNull() ? null : value.asBoolean();
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
  }

  private String firstText(String... values) {
    for (String value : values) if (hasText(value)) return value.trim();
    return null;
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private record Context(AdAccount account, String accessToken) {}
}
