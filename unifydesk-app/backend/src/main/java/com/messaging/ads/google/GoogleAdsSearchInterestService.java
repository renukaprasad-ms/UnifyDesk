package com.messaging.ads.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.messaging.ads.entity.AdCampaign;
import com.messaging.ads.entity.AdSet;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.dto.GoogleSearchInterestCatalogResponse;
import com.messaging.ads.google.dto.GoogleSearchInterestOption;
import com.messaging.ads.repository.AdCampaignRepository;
import com.messaging.ads.repository.AdSetRepository;
import com.messaging.ads.service.AdActivityLogService;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
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
public class GoogleAdsSearchInterestService {

  private static final Set<String> SUPPORTED_TAXONOMIES = Set.of("AFFINITY", "IN_MARKET");
  private static final int MAX_SELECTIONS_PER_SIDE = 100;

  private final AdSetRepository adSetRepository;
  private final AdCampaignRepository campaignRepository;
  private final GoogleAdsAccountContextService contextService;
  private final GoogleAdsApiClient apiClient;
  private final AdActivityLogService activityLogService;

  public GoogleSearchInterestCatalogResponse catalog(
      Long companyId,
      Long accountId,
      String query,
      Integer requestedLimit) {
    var context = contextService.requireManaged(companyId, accountId);
    int limit = Math.max(1, Math.min(requestedLimit == null ? 40 : requestedLimit, 100));
    int providerLimit = Math.min(500, Math.max(limit * 5, 100));

    StringBuilder gaql =
        new StringBuilder(
            "SELECT user_interest.user_interest_id, user_interest.resource_name, "
                + "user_interest.name, user_interest.taxonomy_type, "
                + "user_interest.user_interest_parent, user_interest.launched_to_all, "
                + "user_interest.availabilities "
                + "FROM user_interest "
                + "WHERE user_interest.taxonomy_type IN ('AFFINITY','IN_MARKET')");
    if (hasText(query)) {
      gaql.append(" AND user_interest.name LIKE '%")
          .append(gaqlLiteral(query.trim()))
          .append("%'");
    }
    gaql.append(" ORDER BY user_interest.name LIMIT ").append(providerLimit);

    List<GoogleSearchInterestOption> interests =
        apiClient
            .searchRows(
                context.accessToken(),
                context.account().getExternalAccountId(),
                context.loginCustomerId(),
                gaql.toString())
            .stream()
            .map(row -> option(row.path("userInterest")))
            .filter(option -> option.resourceName() != null)
            .filter(GoogleSearchInterestOption::searchAvailable)
            .sorted(
                Comparator.comparing(
                        (GoogleSearchInterestOption option) ->
                            "IN_MARKET".equals(option.taxonomyType()) ? 0 : 1)
                    .thenComparing(
                        option -> option.name() == null ? "" : option.name(),
                        String.CASE_INSENSITIVE_ORDER))
            .limit(limit)
            .toList();

    return new GoogleSearchInterestCatalogResponse(
        interests,
        false,
        "Google Search supports Affinity and In-market segments, but not Custom segments.");
  }

  public void validateSelection(
      Long companyId,
      Long accountId,
      List<String> includedResources,
      List<String> excludedResources) {
    var context = contextService.requireManaged(companyId, accountId);
    Selection selection =
        validateResources(context, includedResources, excludedResources);
    requireDisjoint(selection.included(), selection.excluded());
  }

  public TargetingSnapshot targeting(Long companyId, Long adGroupId) {
    Context context = requireSearch(companyId, adGroupId);
    List<ExistingCriterion> current =
        currentCriteria(context.google(), context.adGroup().getExternalAdSetId());

    List<String> included = new ArrayList<>();
    List<String> excluded = new ArrayList<>();
    List<String> preserved = new ArrayList<>();

    for (ExistingCriterion criterion : current) {
      if (!SUPPORTED_TAXONOMIES.contains(criterion.taxonomyType())) {
        preserved.add(criterion.userInterestResource());
      } else if (criterion.negative()) {
        excluded.add(criterion.userInterestResource());
      } else {
        included.add(criterion.userInterestResource());
      }
    }

    return new TargetingSnapshot(
        List.copyOf(included),
        List.copyOf(excluded),
        List.copyOf(preserved));
  }

  public TargetingSnapshot replace(
      Long companyId,
      Long adGroupId,
      List<String> includedResources,
      List<String> excludedResources,
      Long actorUserId) {
    Context context = requireSearch(companyId, adGroupId);
    Selection selection =
        validateResources(context.google(), includedResources, excludedResources);
    requireDisjoint(selection.included(), selection.excluded());

    String customerId = context.google().account().getExternalAccountId();
    String externalAdGroupId =
        numeric(context.adGroup().getExternalAdSetId(), "Google ad group ID");
    String adGroupResource =
        apiClient.customerResourceName(customerId, "adGroups", externalAdGroupId);

    List<ExistingCriterion> current =
        currentCriteria(context.google(), externalAdGroupId);
    Map<String, ExistingCriterion> currentSupported = new LinkedHashMap<>();
    for (ExistingCriterion criterion : current) {
      if (SUPPORTED_TAXONOMIES.contains(criterion.taxonomyType())) {
        currentSupported.put(
            (criterion.negative() ? "EXCLUDE|" : "INCLUDE|")
                + criterion.userInterestResource(),
            criterion);
      }
    }

    Set<String> desiredIncluded =
        selection.included().stream()
            .map(GoogleSearchInterestOption::resourceName)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    Set<String> desiredExcluded =
        selection.excluded().stream()
            .map(GoogleSearchInterestOption::resourceName)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

    List<Map<String, Object>> operations = new ArrayList<>();
    int removed = 0;
    int created = 0;

    for (ExistingCriterion criterion : currentSupported.values()) {
      boolean keep =
          criterion.negative()
              ? desiredExcluded.contains(criterion.userInterestResource())
              : desiredIncluded.contains(criterion.userInterestResource());
      if (!keep) {
        operations.add(Map.of("remove", criterion.resourceName()));
        removed++;
      }
    }

    for (GoogleSearchInterestOption option : selection.included()) {
      String key = "INCLUDE|" + option.resourceName();
      if (currentSupported.containsKey(key)) continue;
      operations.add(
          Map.of(
              "create",
              Map.of(
                  "adGroup", adGroupResource,
                  "userInterest",
                  Map.of("userInterestCategory", option.resourceName()),
                  "negative", false)));
      created++;
    }

    for (GoogleSearchInterestOption option : selection.excluded()) {
      String key = "EXCLUDE|" + option.resourceName();
      if (currentSupported.containsKey(key)) continue;
      operations.add(
          Map.of(
              "create",
              Map.of(
                  "adGroup", adGroupResource,
                  "userInterest",
                  Map.of("userInterestCategory", option.resourceName()),
                  "negative", true)));
      created++;
    }

    if (!operations.isEmpty()) {
      apiClient.mutate(
          context.google().accessToken(),
          customerId,
          context.google().loginCustomerId(),
          "adGroupCriteria",
          operations);
    }

    activityLogService.record(
        companyId,
        context.adGroup().getAdAccountId(),
        AdProvider.GOOGLE_ADS,
        actorUserId,
        "GOOGLE_SEARCH_INTEREST_TARGETING_REPLACED",
        "AD_GROUP",
        externalAdGroupId,
        null,
        null,
        Map.of(
            "includedCount", selection.included().size(),
            "excludedCount", selection.excluded().size(),
            "createdCriteria", created,
            "removedCriteria", removed));

    return targeting(companyId, adGroupId);
  }

  private Selection validateResources(
      GoogleAdsAccountContextService.Context context,
      List<String> includedResources,
      List<String> excludedResources) {
    List<String> included =
        normalizedResources(
            context.account().getExternalAccountId(),
            includedResources,
            "included");
    List<String> excluded =
        normalizedResources(
            context.account().getExternalAccountId(),
            excludedResources,
            "excluded");

    Set<String> all = new LinkedHashSet<>();
    all.addAll(included);
    all.addAll(excluded);
    if (all.isEmpty()) return new Selection(List.of(), List.of());

    List<String> ids =
        all.stream()
            .map(this::idFromResource)
            .distinct()
            .toList();
    String query =
        "SELECT user_interest.user_interest_id, user_interest.resource_name, "
            + "user_interest.name, user_interest.taxonomy_type, "
            + "user_interest.user_interest_parent, user_interest.launched_to_all, "
            + "user_interest.availabilities "
            + "FROM user_interest WHERE user_interest.user_interest_id IN ("
            + String.join(",", ids)
            + ")";

    Map<String, GoogleSearchInterestOption> byResource = new LinkedHashMap<>();
    for (JsonNode row :
        apiClient.searchRows(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            query)) {
      GoogleSearchInterestOption option = option(row.path("userInterest"));
      if (option.resourceName() != null) byResource.put(option.resourceName(), option);
    }

    for (String resource : all) {
      GoogleSearchInterestOption option = byResource.get(resource);
      if (option == null) {
        throw new BadRequestException(
            "Google Search interest segment is unavailable: " + resource);
      }
      if (!SUPPORTED_TAXONOMIES.contains(option.taxonomyType())) {
        throw new BadRequestException(
            "Google Search segment must be an Affinity or In-market segment");
      }
      if (!option.searchAvailable()) {
        throw new BadRequestException(
            "Google interest segment is not available for Search: " + option.name());
      }
    }

    return new Selection(
        included.stream().map(byResource::get).toList(),
        excluded.stream().map(byResource::get).toList());
  }

  private List<String> normalizedResources(
      String customerId,
      List<String> resources,
      String side) {
    if (resources == null || resources.isEmpty()) return List.of();
    if (resources.size() > MAX_SELECTIONS_PER_SIDE) {
      throw new BadRequestException(
          "Google Search supports at most "
              + MAX_SELECTIONS_PER_SIDE
              + " "
              + side
              + " interest segments per UnifyDesk edit");
    }

    List<String> result = new ArrayList<>();
    for (String value : resources) {
      if (!hasText(value)) {
        throw new BadRequestException("Google Search interest resource is required");
      }
      String normalized = value.trim();
      if (normalized.matches("\\d+")) {
        normalized =
            apiClient.customerResourceName(
                customerId,
                "userInterests",
                normalized);
      }
      if (!normalized.matches("customers/" + java.util.regex.Pattern.quote(customerId) + "/userInterests/\\d+")) {
        throw new BadRequestException(
            "Google Search interest must belong to the selected Google Ads account");
      }
      if (!result.contains(normalized)) result.add(normalized);
    }
    return List.copyOf(result);
  }

  private void requireDisjoint(
      List<GoogleSearchInterestOption> included,
      List<GoogleSearchInterestOption> excluded) {
    Set<String> includedResources =
        included.stream()
            .map(GoogleSearchInterestOption::resourceName)
            .collect(java.util.stream.Collectors.toSet());
    if (excluded.stream()
        .map(GoogleSearchInterestOption::resourceName)
        .anyMatch(includedResources::contains)) {
      throw new BadRequestException(
          "A Google Search interest segment cannot be both included and excluded");
    }
  }

  private List<ExistingCriterion> currentCriteria(
      GoogleAdsAccountContextService.Context context,
      String externalAdGroupId) {
    String id = numeric(externalAdGroupId, "Google ad group ID");
    String query =
        "SELECT ad_group_criterion.resource_name, ad_group_criterion.negative, "
            + "ad_group_criterion.user_interest.user_interest_category, "
            + "user_interest.resource_name, user_interest.taxonomy_type "
            + "FROM ad_group_criterion WHERE ad_group.id = "
            + id
            + " AND ad_group_criterion.type = 'USER_INTEREST' "
            + "AND ad_group_criterion.status != 'REMOVED'";

    List<ExistingCriterion> result = new ArrayList<>();
    for (JsonNode row :
        apiClient.searchRows(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            query)) {
      JsonNode criterion = row.path("adGroupCriterion");
      String criterionResource = text(criterion, "resourceName");
      String interestResource =
          text(criterion.path("userInterest"), "userInterestCategory");
      String taxonomy = text(row.path("userInterest"), "taxonomyType");
      if (!hasText(criterionResource) || !hasText(interestResource)) continue;
      result.add(
          new ExistingCriterion(
              criterionResource,
              interestResource,
              taxonomy == null ? "UNKNOWN" : taxonomy.toUpperCase(Locale.ROOT),
              criterion.path("negative").asBoolean(false)));
    }
    return List.copyOf(result);
  }

  private GoogleSearchInterestOption option(JsonNode interest) {
    String taxonomy = text(interest, "taxonomyType");
    boolean launchedToAll = interest.path("launchedToAll").asBoolean(false);
    return new GoogleSearchInterestOption(
        text(interest, "userInterestId"),
        text(interest, "resourceName"),
        text(interest, "name"),
        taxonomy == null ? null : taxonomy.toUpperCase(Locale.ROOT),
        text(interest, "userInterestParent"),
        launchedToAll,
        launchedToAll || availableForSearch(interest.path("availabilities")));
  }

  private boolean availableForSearch(JsonNode availabilities) {
    if (!availabilities.isArray()) return false;
    for (JsonNode availability : availabilities) {
      JsonNode channel = availability.path("channel");
      String mode = text(channel, "availabilityMode");
      if ("ALL_CHANNELS".equalsIgnoreCase(mode)) return true;
      String channelType = text(channel, "advertisingChannelType");
      if ("SEARCH".equalsIgnoreCase(channelType)) return true;
    }
    return false;
  }

  private Context requireSearch(Long companyId, Long adGroupId) {
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
        || !"SEARCH".equalsIgnoreCase(campaign.getObjective())
        || !adGroup.isAccessible()
        || !campaign.isAccessible()) {
      throw new BadRequestException("Google Search ad group is not available");
    }
    return new Context(
        adGroup,
        campaign,
        contextService.requireManaged(companyId, adGroup.getAdAccountId()));
  }

  private String idFromResource(String value) {
    int slash = value == null ? -1 : value.lastIndexOf('/');
    if (slash < 0 || slash == value.length() - 1) {
      throw new BadRequestException("Invalid Google Search interest resource");
    }
    return value.substring(slash + 1);
  }

  private String numeric(String value, String label) {
    if (value == null || !value.trim().matches("\\d+")) {
      throw new BadRequestException(label + " is invalid");
    }
    return value.trim();
  }

  private String gaqlLiteral(String value) {
    String normalized = value.replace("\\", "\\\\").replace("'", "\\'");
    if (normalized.length() > 100) {
      throw new BadRequestException(
          "Google Search interest query may contain at most 100 characters");
    }
    return normalized;
  }

  private String text(JsonNode node, String field) {
    if (node == null || node.isNull()) return null;
    JsonNode value = node.get(field);
    if (value == null || value.isNull()) return null;
    String result = value.asText(null);
    return hasText(result) ? result.trim() : null;
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private record Context(
      AdSet adGroup,
      AdCampaign campaign,
      GoogleAdsAccountContextService.Context google) {}

  private record Selection(
      List<GoogleSearchInterestOption> included,
      List<GoogleSearchInterestOption> excluded) {}

  private record ExistingCriterion(
      String resourceName,
      String userInterestResource,
      String taxonomyType,
      boolean negative) {}

  public record TargetingSnapshot(
      List<String> includedResourceNames,
      List<String> excludedResourceNames,
      List<String> preservedUnsupportedResourceNames) {}
}
