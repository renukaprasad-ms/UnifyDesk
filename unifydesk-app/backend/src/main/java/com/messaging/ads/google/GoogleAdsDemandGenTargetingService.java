package com.messaging.ads.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.messaging.ads.campaign.dto.AudienceLocationInput;
import com.messaging.ads.entity.AdCampaign;
import com.messaging.ads.entity.AdSet;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.dto.GoogleDemandGenAudienceOption;
import com.messaging.ads.google.dto.GoogleDemandGenChannelPerformance;
import com.messaging.ads.google.dto.GoogleDemandGenSelectedChannels;
import com.messaging.ads.google.dto.GoogleDemandGenTargetingRequest;
import com.messaging.ads.google.dto.GoogleDemandGenTargetingResponse;
import com.messaging.ads.repository.AdCampaignRepository;
import com.messaging.ads.repository.AdSetRepository;
import com.messaging.ads.service.AdActivityLogService;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import java.time.LocalDate;
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
public class GoogleAdsDemandGenTargetingService {

  private static final Set<String> CHANNEL_STRATEGIES =
      Set.of("ALL_CHANNELS", "ALL_OWNED_AND_OPERATED_CHANNELS");

  private final AdSetRepository adSetRepository;
  private final AdCampaignRepository campaignRepository;
  private final GoogleAdsAccountContextService contextService;
  private final GoogleAdsApiClient apiClient;
  private final AdActivityLogService activityLogService;

  public GoogleDemandGenTargetingResponse targeting(Long companyId, Long adGroupId) {
    Context context = requireDemandGen(companyId, adGroupId);
    String externalId = numeric(context.adGroup().getExternalAdSetId(), "Google ad group ID");

    List<JsonNode> groupRows =
        apiClient.searchRows(
            context.google().accessToken(),
            context.google().account().getExternalAccountId(),
            context.google().loginCustomerId(),
            "SELECT ad_group.id, "
                + "ad_group.demand_gen_ad_group_settings.channel_controls.channel_config, "
                + "ad_group.demand_gen_ad_group_settings.channel_controls.channel_strategy, "
                + "ad_group.demand_gen_ad_group_settings.channel_controls.selected_channels.youtube_in_feed, "
                + "ad_group.demand_gen_ad_group_settings.channel_controls.selected_channels.youtube_in_stream, "
                + "ad_group.demand_gen_ad_group_settings.channel_controls.selected_channels.youtube_shorts, "
                + "ad_group.demand_gen_ad_group_settings.channel_controls.selected_channels.discover, "
                + "ad_group.demand_gen_ad_group_settings.channel_controls.selected_channels.gmail, "
                + "ad_group.demand_gen_ad_group_settings.channel_controls.selected_channels.display, "
                + "ad_group.demand_gen_ad_group_settings.channel_controls.selected_channels.maps, "
                + "ad_group.audience_setting.use_audience_grouped "
                + "FROM ad_group WHERE ad_group.id = "
                + externalId
                + " LIMIT 1");

    if (groupRows.isEmpty()) {
      throw new NotFoundException("Google Demand Gen ad group was not found");
    }

    JsonNode group = groupRows.getFirst().path("adGroup");
    JsonNode controls = group.path("demandGenAdGroupSettings").path("channelControls");
    JsonNode selected = controls.path("selectedChannels");

    List<String> locations = new ArrayList<>();
    List<String> excludedLocations = new ArrayList<>();
    List<String> languages = new ArrayList<>();
    List<String> audiences = new ArrayList<>();

    String criteriaQuery =
        "SELECT ad_group_criterion.type, ad_group_criterion.negative, "
            + "ad_group_criterion.location.geo_target_constant, "
            + "ad_group_criterion.language.language_constant, "
            + "ad_group_criterion.audience.audience "
            + "FROM ad_group_criterion WHERE ad_group.id = "
            + externalId
            + " AND ad_group_criterion.status != 'REMOVED'";
    for (JsonNode row :
        apiClient.searchRows(
            context.google().accessToken(),
            context.google().account().getExternalAccountId(),
            context.google().loginCustomerId(),
            criteriaQuery)) {
      JsonNode criterion = row.path("adGroupCriterion");
      String type = text(criterion, "type");
      boolean negative = criterion.path("negative").asBoolean(false);
      if ("LOCATION".equals(type)) {
        String value = text(criterion.path("location"), "geoTargetConstant");
        if (value != null) {
          (negative ? excludedLocations : locations).add(value);
        }
      } else if ("LANGUAGE".equals(type)) {
        String value = text(criterion.path("language"), "languageConstant");
        if (value != null) languages.add(value);
      } else if ("AUDIENCE".equals(type)) {
        String value = text(criterion.path("audience"), "audience");
        if (value != null) audiences.add(value);
      }
    }

    return new GoogleDemandGenTargetingResponse(
        text(controls, "channelConfig"),
        text(controls, "channelStrategy"),
        new GoogleDemandGenSelectedChannels(
            bool(selected, "youtubeInFeed"),
            bool(selected, "youtubeInStream"),
            bool(selected, "youtubeShorts"),
            bool(selected, "discover"),
            bool(selected, "gmail"),
            bool(selected, "display"),
            bool(selected, "maps")),
        group.path("audienceSetting").path("useAudienceGrouped").asBoolean(false),
        locations,
        excludedLocations,
        languages,
        audiences);
  }

  public GoogleDemandGenTargetingResponse update(
      Long companyId,
      Long adGroupId,
      GoogleDemandGenTargetingRequest request,
      Long actorUserId) {
    Context context = requireDemandGen(companyId, adGroupId);
    GoogleDemandGenTargetingResponse before = targeting(companyId, adGroupId);
    String customerId = context.google().account().getExternalAccountId();
    String externalId = numeric(context.adGroup().getExternalAdSetId(), "Google ad group ID");
    String adGroupResource = apiClient.customerResourceName(customerId, "adGroups", externalId);

    Map<String, Object> update = new LinkedHashMap<>();
    update.put("resourceName", adGroupResource);
    List<String> masks = new ArrayList<>();

    Map<String, Object> channelControls = channelControls(request);
    if (channelControls != null) {
      update.put(
          "demandGenAdGroupSettings",
          Map.of("channelControls", channelControls));
      masks.add("demand_gen_ad_group_settings.channel_controls");
    }

    if (request.audienceResourceNames() != null
        && !before.useAudienceGrouped()
        && !request.audienceResourceNames().isEmpty()) {
      throw new BadRequestException(
          "This Demand Gen ad group was created without grouped Audience targeting. Google marks that setting immutable; create a new Demand Gen ad group to use reusable Audience resources.");
    }

    if (!masks.isEmpty()) {
      apiClient.mutate(
          context.google().accessToken(),
          customerId,
          context.google().loginCustomerId(),
          "adGroups",
          List.of(
              Map.of(
                  "update", update,
                  "updateMask", String.join(",", masks))));
    }

    if (request.locations() != null
        || request.locationResourceNames() != null
        || request.excludedLocations() != null
        || request.excludedLocationResourceNames() != null
        || request.languages() != null
        || request.languageResourceNames() != null
        || request.audienceResourceNames() != null) {
      replaceCriteria(context, request);
    }

    GoogleDemandGenTargetingResponse after = targeting(companyId, adGroupId);
    activityLogService.record(
        companyId,
        context.adGroup().getAdAccountId(),
        AdProvider.GOOGLE_ADS,
        actorUserId,
        "GOOGLE_DEMAND_GEN_TARGETING_UPDATED",
        "AD_GROUP",
        context.adGroup().getExternalAdSetId(),
        before,
        after,
        Map.of("campaignId", context.campaign().getExternalCampaignId()));
    return after;
  }

  public List<GoogleDemandGenAudienceOption> audiences(Long companyId, Long adGroupId) {
    Context context = requireDemandGen(companyId, adGroupId);
    String query =
        "SELECT audience.id, audience.resource_name, audience.name, "
            + "audience.status, audience.description FROM audience "
            + "WHERE audience.status != 'REMOVED'";
    return apiClient
        .searchRows(
            context.google().accessToken(),
            context.google().account().getExternalAccountId(),
            context.google().loginCustomerId(),
            query)
        .stream()
        .map(row -> row.path("audience"))
        .map(
            audience ->
                new GoogleDemandGenAudienceOption(
                    text(audience, "id"),
                    text(audience, "resourceName"),
                    text(audience, "name"),
                    text(audience, "status"),
                    text(audience, "description")))
        .filter(option -> option.resourceName() != null)
        .sorted(Comparator.comparing(option -> option.name() == null ? "" : option.name()))
        .toList();
  }

  public List<GoogleDemandGenChannelPerformance> channelPerformance(
      Long companyId, Long adGroupId, LocalDate from, LocalDate to) {
    if (from == null || to == null || to.isBefore(from)) {
      throw new BadRequestException("Demand Gen report requires a valid date range");
    }
    Context context = requireDemandGen(companyId, adGroupId);
    String externalId = numeric(context.adGroup().getExternalAdSetId(), "Google ad group ID");
    String query =
        "SELECT segments.ad_network_type, metrics.impressions, metrics.clicks, "
            + "metrics.cost_micros, metrics.conversions, metrics.conversions_value "
            + "FROM ad_group WHERE ad_group.id = "
            + externalId
            + " AND segments.date BETWEEN '"
            + from
            + "' AND '"
            + to
            + "'";

    Map<String, long[]> whole = new LinkedHashMap<>();
    Map<String, double[]> decimal = new LinkedHashMap<>();
    for (JsonNode row :
        apiClient.searchRows(
            context.google().accessToken(),
            context.google().account().getExternalAccountId(),
            context.google().loginCustomerId(),
            query)) {
      String channel = text(row.path("segments"), "adNetworkType");
      if (channel == null) channel = "UNKNOWN";
      JsonNode metrics = row.path("metrics");
      long[] longs = whole.computeIfAbsent(channel, ignored -> new long[3]);
      longs[0] += metrics.path("impressions").asLong(0);
      longs[1] += metrics.path("clicks").asLong(0);
      longs[2] += metrics.path("costMicros").asLong(0);
      double[] doubles = decimal.computeIfAbsent(channel, ignored -> new double[2]);
      doubles[0] += metrics.path("conversions").asDouble(0d);
      doubles[1] += metrics.path("conversionsValue").asDouble(0d);
    }

    List<GoogleDemandGenChannelPerformance> result = new ArrayList<>();
    for (Map.Entry<String, long[]> entry : whole.entrySet()) {
      double[] doubles = decimal.getOrDefault(entry.getKey(), new double[2]);
      result.add(
          new GoogleDemandGenChannelPerformance(
              entry.getKey(),
              entry.getValue()[0],
              entry.getValue()[1],
              entry.getValue()[2],
              doubles[0],
              doubles[1]));
    }
    return List.copyOf(result);
  }

  private void replaceCriteria(Context context, GoogleDemandGenTargetingRequest request) {
    String customerId = context.google().account().getExternalAccountId();
    String adGroupId = numeric(context.adGroup().getExternalAdSetId(), "Google ad group ID");
    String adGroupResource = apiClient.customerResourceName(customerId, "adGroups", adGroupId);

    List<JsonNode> current =
        apiClient.searchRows(
            context.google().accessToken(),
            customerId,
            context.google().loginCustomerId(),
            "SELECT ad_group_criterion.resource_name, ad_group_criterion.type, "
                + "ad_group_criterion.negative, "
                + "ad_group_criterion.location.geo_target_constant, "
                + "ad_group_criterion.language.language_constant, "
                + "ad_group_criterion.audience.audience "
                + "FROM ad_group_criterion WHERE ad_group.id = "
                + adGroupId
                + " AND ad_group_criterion.status != 'REMOVED'");

    Set<String> managedTypes = new LinkedHashSet<>();
    if (request.locations() != null
        || request.locationResourceNames() != null
        || request.excludedLocations() != null
        || request.excludedLocationResourceNames() != null) {
      managedTypes.add("LOCATION");
    }
    if (request.languages() != null || request.languageResourceNames() != null) {
      managedTypes.add("LANGUAGE");
    }
    if (request.audienceResourceNames() != null) managedTypes.add("AUDIENCE");

    List<Map<String, Object>> operations = new ArrayList<>();
    for (JsonNode row : current) {
      JsonNode criterion = row.path("adGroupCriterion");
      String type = text(criterion, "type");
      String resourceName = text(criterion, "resourceName");
      if (resourceName != null && managedTypes.contains(type)) {
        operations.add(Map.of("remove", resourceName));
      }
    }

    if (request.locationResourceNames() != null) {
      for (String resource : request.locationResourceNames()) {
        operations.add(
            Map.of(
                "create",
                Map.of(
                    "adGroup", adGroupResource,
                    "location",
                    Map.of("geoTargetConstant", normalizeGeoResource(resource)),
                    "negative", false)));
      }
    }
    if (request.locations() != null) {
      for (AudienceLocationInput input : request.locations()) {
        operations.add(
            Map.of(
                "create",
                Map.of(
                    "adGroup", adGroupResource,
                    "location",
                    Map.of("geoTargetConstant", resolveLocation(context.google(), input)),
                    "negative", false)));
      }
    }
    if (request.excludedLocationResourceNames() != null) {
      for (String resource : request.excludedLocationResourceNames()) {
        operations.add(
            Map.of(
                "create",
                Map.of(
                    "adGroup", adGroupResource,
                    "location",
                    Map.of("geoTargetConstant", normalizeGeoResource(resource)),
                    "negative", true)));
      }
    }
    if (request.excludedLocations() != null) {
      for (AudienceLocationInput input : request.excludedLocations()) {
        operations.add(
            Map.of(
                "create",
                Map.of(
                    "adGroup", adGroupResource,
                    "location",
                    Map.of("geoTargetConstant", resolveLocation(context.google(), input)),
                    "negative", true)));
      }
    }
    if (request.languageResourceNames() != null) {
      for (String resource : request.languageResourceNames()) {
        operations.add(
            Map.of(
                "create",
                Map.of(
                    "adGroup", adGroupResource,
                    "language", Map.of("languageConstant", normalizeLanguageResource(resource)),
                    "negative", false)));
      }
    }
    if (request.languages() != null) {
      Map<String, String> resources = languageResources(context.google());
      for (String language : request.languages()) {
        if (language == null || language.isBlank()) continue;
        String resource = resources.get(normalize(language));
        if (resource == null) {
          throw new BadRequestException(
              "Google Ads language is not targetable or unknown: " + language);
        }
        operations.add(
            Map.of(
                "create",
                Map.of(
                    "adGroup", adGroupResource,
                    "language", Map.of("languageConstant", resource),
                    "negative", false)));
      }
    }
    if (request.audienceResourceNames() != null) {
      for (String audience : request.audienceResourceNames()) {
        String resource = normalizeAudienceResource(customerId, audience);
        operations.add(
            Map.of(
                "create",
                Map.of(
                    "adGroup", adGroupResource,
                    "audience", Map.of("audience", resource),
                    "negative", false)));
      }
    }

    if (!operations.isEmpty()) {
      apiClient.mutate(
          context.google().accessToken(),
          customerId,
          context.google().loginCustomerId(),
          "adGroupCriteria",
          operations);
    }
  }

  private Map<String, Object> channelControls(GoogleDemandGenTargetingRequest request) {
    String strategy =
        request.channelStrategy() == null
            ? null
            : request.channelStrategy().trim().toUpperCase(Locale.ROOT);
    if (strategy != null && !strategy.isBlank()) {
      if (!CHANNEL_STRATEGIES.contains(strategy)) {
        throw new BadRequestException(
            "Demand Gen channel strategy must be ALL_CHANNELS or ALL_OWNED_AND_OPERATED_CHANNELS");
      }
      if (request.selectedChannels() != null) {
        throw new BadRequestException(
            "Demand Gen channel strategy and individual selected channels are mutually exclusive");
      }
      return Map.of("channelStrategy", strategy);
    }

    GoogleDemandGenSelectedChannels selected = request.selectedChannels();
    if (selected == null) return null;
    Map<String, Object> values = new LinkedHashMap<>();
    putBoolean(values, "youtubeInFeed", selected.youtubeInFeed());
    putBoolean(values, "youtubeInStream", selected.youtubeInStream());
    putBoolean(values, "youtubeShorts", selected.youtubeShorts());
    putBoolean(values, "discover", selected.discover());
    putBoolean(values, "gmail", selected.gmail());
    putBoolean(values, "display", selected.display());
    putBoolean(values, "maps", selected.maps());
    if (values.values().stream().noneMatch(Boolean.TRUE::equals)) {
      throw new BadRequestException(
          "Demand Gen selected channels require at least one enabled channel");
    }
    return Map.of("selectedChannels", values);
  }

  private String resolveLocation(
      GoogleAdsAccountContextService.Context context, AudienceLocationInput location) {
    if (location == null || location.countryCode() == null || location.countryCode().isBlank()) {
      throw new BadRequestException("Demand Gen location countryCode is required");
    }
    if (location.radiusKm() != null
        || location.latitude() != null
        || location.longitude() != null) {
      throw new BadRequestException(
          "Demand Gen C7 location targeting currently supports country/region/city/postal targets, not proximity radius");
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
      return candidates.stream()
          .max(Comparator.comparingInt(geo -> geoScore(geo, location)))
          .map(geo -> text(geo, "resourceName"))
          .filter(this::hasText)
          .orElseThrow(
              () ->
                  new BadRequestException(
                      "Google Ads could not resolve Demand Gen location: " + search));
    }

    String query =
        "SELECT geo_target_constant.resource_name, geo_target_constant.country_code, "
            + "geo_target_constant.target_type, geo_target_constant.status "
            + "FROM geo_target_constant WHERE geo_target_constant.country_code = '"
            + country
            + "' AND geo_target_constant.target_type = 'Country'";
    return apiClient
        .searchRows(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            query)
        .stream()
        .map(row -> row.path("geoTargetConstant"))
        .filter(geo -> "ENABLED".equalsIgnoreCase(text(geo, "status")))
        .map(geo -> text(geo, "resourceName"))
        .filter(this::hasText)
        .findFirst()
        .orElseThrow(
            () ->
                new BadRequestException(
                    "Google Ads country target is unavailable: " + country));
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

  private Map<String, String> languageResources(
      GoogleAdsAccountContextService.Context context) {
    Map<String, String> result = new LinkedHashMap<>();
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
      if (hasText(text(language, "code"))) result.put(normalize(text(language, "code")), resource);
      if (hasText(text(language, "name"))) result.put(normalize(text(language, "name")), resource);
    }
    return result;
  }

  private String normalizeGeoResource(String value) {
    if (value == null || !value.trim().matches("geoTargetConstants/\\d+")) {
      throw new BadRequestException("Invalid Google geo target resource name");
    }
    return value.trim();
  }

  private String normalizeLanguageResource(String value) {
    if (value == null || !value.trim().matches("languageConstants/\\d+")) {
      throw new BadRequestException("Invalid Google language resource name");
    }
    return value.trim();
  }

  private String normalizeAudienceResource(String customerId, String value) {
    if (value == null || value.isBlank()) {
      throw new BadRequestException("Demand Gen audience resource is required");
    }
    String normalized = value.trim();
    if (normalized.matches("\\d+")) {
      return apiClient.customerResourceName(customerId, "audiences", normalized);
    }
    if (!normalized.matches("customers/\\d+/audiences/\\d+")) {
      throw new BadRequestException("Invalid Google Audience resource name");
    }
    return normalized;
  }

  private Context requireDemandGen(Long companyId, Long adGroupId) {
    AdSet adGroup =
        adSetRepository
            .findByIdAndCompanyId(adGroupId, companyId)
            .orElseThrow(() -> new NotFoundException("Advertising ad group not found"));
    AdCampaign campaign =
        campaignRepository
            .findByIdAndCompanyId(adGroup.getCampaignId(), companyId)
            .orElseThrow(() -> new NotFoundException("Advertising campaign not found"));
    if (adGroup.getProvider() != AdProvider.GOOGLE_ADS
        || campaign.getProvider() != AdProvider.GOOGLE_ADS
        || !"DEMAND_GEN".equalsIgnoreCase(campaign.getObjective())
        || !adGroup.isAccessible()
        || !campaign.isAccessible()) {
      throw new BadRequestException("Google Demand Gen ad group is not available");
    }
    return new Context(
        adGroup,
        campaign,
        contextService.requireManaged(companyId, adGroup.getAdAccountId()));
  }

  private String numeric(String value, String label) {
    if (value == null || !value.trim().matches("\\d+")) {
      throw new BadRequestException(label + " is invalid");
    }
    return value.trim();
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

  private Boolean bool(JsonNode node, String field) {
    if (node == null || node.isNull() || !node.has(field)) return null;
    return node.path(field).asBoolean();
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private void putBoolean(Map<String, Object> target, String key, Boolean value) {
    if (value != null) target.put(key, value);
  }

  private record Context(
      AdSet adGroup,
      AdCampaign campaign,
      GoogleAdsAccountContextService.Context google) {}
}
