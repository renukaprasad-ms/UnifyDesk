package com.messaging.ads.campaign.service;

import com.messaging.ads.campaign.dto.CanonicalLocationSuggestion;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.GoogleAdsCampaignTargetingService;
import com.messaging.ads.google.dto.GoogleGeoLocationSuggestion;
import com.messaging.ads.meta.MetaAdsGeoTargetingService;
import com.messaging.ads.meta.dto.MetaGeoLocationSuggestion;
import com.messaging.common.exception.BadRequestException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CanonicalLocationSuggestionService {

  private final GoogleAdsCampaignTargetingService googleTargetingService;
  private final MetaAdsGeoTargetingService metaGeoTargetingService;

  public List<CanonicalLocationSuggestion> search(
      Long companyId,
      String query,
      String countryCode,
      Long googleAccountId,
      Long metaAccountId,
      Integer requestedLimit) {
    if (query == null || query.isBlank()) {
      throw new BadRequestException("Location search query is required");
    }
    String country = normalizeCountryCode(countryCode);
    if (googleAccountId == null && metaAccountId == null) {
      throw new BadRequestException(
          "Choose at least one Google or Meta advertising account before searching locations");
    }
    int limit = requestedLimit == null ? 12 : requestedLimit;
    if (limit < 1 || limit > 30) {
      throw new BadRequestException("Location suggestion limit must be between 1 and 30");
    }

    Map<String, MutableSuggestion> merged = new LinkedHashMap<>();

    if (googleAccountId != null) {
      List<GoogleGeoLocationSuggestion> google =
          googleTargetingService.searchLocations(
              companyId, googleAccountId, query, country, Math.min(50, limit * 3));
      for (GoogleGeoLocationSuggestion suggestion : google) {
        CanonicalParts parts = googleParts(suggestion);
        if (parts == null) continue;
        merge(merged, parts, AdProvider.GOOGLE_ADS);
      }
    }

    if (metaAccountId != null) {
      List<MetaGeoLocationSuggestion> meta =
          metaGeoTargetingService.search(
              companyId,
              metaAccountId,
              query,
              List.of("COUNTRY", "REGION", "CITY", "ZIP"),
              country,
              Math.min(50, limit * 3));
      for (MetaGeoLocationSuggestion suggestion : meta) {
        CanonicalParts parts = metaParts(suggestion);
        if (parts == null) continue;
        merge(merged, parts, AdProvider.META_ADS);
      }
    }

    int requestedProviderCount =
        (googleAccountId == null ? 0 : 1) + (metaAccountId == null ? 0 : 1);

    return merged.values().stream()
        .sorted(
            Comparator
                .comparingInt(
                    (MutableSuggestion value) ->
                        value.supportedProviders.size() == requestedProviderCount ? 0 : 1)
                .thenComparing(value -> value.displayName.toLowerCase(Locale.ROOT)))
        .limit(limit)
        .map(MutableSuggestion::toResponse)
        .toList();
  }

  private void merge(
      Map<String, MutableSuggestion> merged,
      CanonicalParts parts,
      AdProvider provider) {
    String key = key(parts);
    MutableSuggestion existing = merged.get(key);
    if (existing == null) {
      existing =
          new MutableSuggestion(
              key,
              parts.displayName(),
              parts.type(),
              parts.countryCode(),
              parts.region(),
              parts.city(),
              parts.postalCode());
      merged.put(key, existing);
    } else {
      existing.fillMissing(parts);
    }
    existing.supportedProviders.add(provider);
  }

  private CanonicalParts googleParts(GoogleGeoLocationSuggestion suggestion) {
    if (suggestion == null) return null;
    String type = canonicalGoogleType(suggestion.targetType());
    if (type == null) return null;

    String name = trimToNull(suggestion.name());
    String country = trimToNull(suggestion.countryCode());
    if (name == null || country == null) return null;

    List<String> canonicalParts = commaParts(suggestion.canonicalName());
    String region = null;
    String city = null;
    String postalCode = null;

    switch (type) {
      case "REGION" -> region = name;
      case "CITY" -> {
        city = name;
        if (canonicalParts.size() >= 3) {
          region = canonicalParts.get(canonicalParts.size() - 2);
        }
      }
      case "ZIP" -> {
        postalCode = name;
        if (canonicalParts.size() >= 3) {
          region = canonicalParts.get(canonicalParts.size() - 2);
        }
      }
      default -> {
        // Country suggestions do not require more canonical fields.
      }
    }

    return new CanonicalParts(
        type,
        country.toUpperCase(Locale.ROOT),
        region,
        city,
        postalCode,
        displayName(name, region, country));
  }

  private CanonicalParts metaParts(MetaGeoLocationSuggestion suggestion) {
    if (suggestion == null) return null;
    String type = canonicalMetaType(suggestion.type());
    String name = trimToNull(suggestion.name());
    String country = trimToNull(suggestion.countryCode());
    if (type == null || name == null || country == null) return null;

    String region = trimToNull(suggestion.region());
    String city = null;
    String postalCode = null;
    if ("REGION".equals(type)) region = name;
    if ("CITY".equals(type)) city = name;
    if ("ZIP".equals(type)) postalCode = name;

    return new CanonicalParts(
        type,
        country.toUpperCase(Locale.ROOT),
        region,
        city,
        postalCode,
        displayName(name, region, firstNonBlank(suggestion.countryName(), country)));
  }

  private String canonicalGoogleType(String raw) {
    String normalized = normalize(raw);
    if (normalized.equals("country")) return "COUNTRY";
    if (normalized.equals("city") || normalized.contains("municip")) return "CITY";
    if (normalized.contains("postal")) return "ZIP";
    if (normalized.contains("state")
        || normalized.contains("province")
        || normalized.equals("region")
        || normalized.contains("territory")) {
      return "REGION";
    }
    return null;
  }

  private String canonicalMetaType(String raw) {
    String normalized = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
    return Set.of("COUNTRY", "REGION", "CITY", "ZIP").contains(normalized)
        ? normalized
        : null;
  }

  private String key(CanonicalParts value) {
    String primary =
        switch (value.type()) {
          case "COUNTRY" -> value.countryCode();
          case "REGION" -> value.region();
          case "CITY" -> value.city();
          case "ZIP" -> value.postalCode();
          default -> value.displayName();
        };
    return value.type()
        + "|"
        + normalize(value.countryCode())
        + "|"
        + normalize(primary)
        + "|"
        + normalize(value.region());
  }

  private List<String> commaParts(String value) {
    if (value == null || value.isBlank()) return List.of();
    List<String> result = new ArrayList<>();
    for (String part : value.split(",")) {
      String normalized = trimToNull(part);
      if (normalized != null) result.add(normalized);
    }
    return List.copyOf(result);
  }

  private String displayName(String name, String region, String country) {
    List<String> pieces = new ArrayList<>();
    addDistinct(pieces, name);
    addDistinct(pieces, region);
    addDistinct(pieces, country);
    return String.join(", ", pieces);
  }

  private void addDistinct(List<String> values, String value) {
    String normalized = trimToNull(value);
    if (normalized == null) return;
    boolean exists =
        values.stream().anyMatch(item -> item.equalsIgnoreCase(normalized));
    if (!exists) values.add(normalized);
  }

  private String firstNonBlank(String first, String second) {
    String normalized = trimToNull(first);
    return normalized != null ? normalized : second;
  }

  private String normalizeCountryCode(String value) {
    String normalized = trimToNull(value);
    if (normalized == null || !normalized.matches("(?i)[A-Z]{2}")) {
      throw new BadRequestException(
          "Location search countryCode must be a two-letter ISO country code");
    }
    return normalized.toUpperCase(Locale.ROOT);
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
  }

  private String trimToNull(String value) {
    if (value == null || value.isBlank()) return null;
    return value.trim();
  }

  private record CanonicalParts(
      String type,
      String countryCode,
      String region,
      String city,
      String postalCode,
      String displayName) {}

  private static final class MutableSuggestion {
    private final String key;
    private String displayName;
    private final String type;
    private final String countryCode;
    private String region;
    private String city;
    private String postalCode;
    private final EnumSet<AdProvider> supportedProviders =
        EnumSet.noneOf(AdProvider.class);

    private MutableSuggestion(
        String key,
        String displayName,
        String type,
        String countryCode,
        String region,
        String city,
        String postalCode) {
      this.key = key;
      this.displayName = displayName;
      this.type = type;
      this.countryCode = countryCode;
      this.region = region;
      this.city = city;
      this.postalCode = postalCode;
    }

    private void fillMissing(CanonicalParts value) {
      if (region == null) region = value.region();
      if (city == null) city = value.city();
      if (postalCode == null) postalCode = value.postalCode();
      if (value.displayName().length() > displayName.length()) {
        displayName = value.displayName();
      }
    }

    private CanonicalLocationSuggestion toResponse() {
      return new CanonicalLocationSuggestion(
          key,
          displayName,
          type,
          countryCode,
          region,
          city,
          postalCode,
          Set.copyOf(supportedProviders));
    }
  }
}
