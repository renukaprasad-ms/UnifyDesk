package com.messaging.ads.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.entity.AdAudience;
import com.messaging.ads.entity.AdCampaign;
import com.messaging.ads.entity.AdSet;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.repository.AdAudienceRepository;
import com.messaging.ads.repository.AdCampaignRepository;
import com.messaging.ads.repository.AdSetRepository;
import com.messaging.ads.service.AdActivityLogService;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GoogleAdsSearchAudienceService {

  private static final Set<String> MODES = Set.of("OBSERVATION", "TARGETING");

  private final AdAudienceRepository audienceRepository;
  private final AdCampaignRepository campaignRepository;
  private final AdSetRepository adSetRepository;
  private final GoogleAdsAccountContextService contextService;
  private final GoogleAdsApiClient apiClient;
  private final ObjectMapper objectMapper;
  private final AdActivityLogService activityLogService;

  public ValidationResult validateSelection(
      Long companyId,
      Long accountId,
      List<Long> includedIds,
      List<Long> excludedIds,
      String requestedMode) {
    String mode = normalizeMode(requestedMode);
    List<AdAudience> included = requireSearchEligibleLists(companyId, accountId, includedIds);
    List<AdAudience> excluded = requireSearchEligibleLists(companyId, accountId, excludedIds);
    requireDisjoint(included, excluded);
    return new ValidationResult(mode, included.size(), excluded.size());
  }

  public ApplyResult apply(
      Long companyId,
      Long adGroupId,
      List<Long> includedIds,
      List<Long> excludedIds,
      String requestedMode,
      Long actorUserId) {
    AdSet adGroup =
        adSetRepository
            .findByIdAndCompanyId(adGroupId, companyId)
            .orElseThrow(() -> new NotFoundException("Advertising ad group not found"));
    if (adGroup.getProvider() != AdProvider.GOOGLE_ADS || !adGroup.isAccessible()) {
      throw new BadRequestException(
          "Google Search audience targeting requires an accessible Google ad group");
    }

    AdCampaign campaign =
        campaignRepository
            .findByIdAndCompanyId(adGroup.getCampaignId(), companyId)
            .orElseThrow(() -> new NotFoundException("Advertising campaign not found"));
    if (!"SEARCH".equalsIgnoreCase(campaign.getObjective())) {
      throw new BadRequestException(
          "Google Search audience targeting applies only to Search campaigns");
    }

    String mode = normalizeMode(requestedMode);
    List<AdAudience> included =
        requireSearchEligibleLists(companyId, adGroup.getAdAccountId(), includedIds);
    List<AdAudience> excluded =
        requireSearchEligibleLists(companyId, adGroup.getAdAccountId(), excludedIds);
    requireDisjoint(included, excluded);

    var context = contextService.requireManaged(companyId, adGroup.getAdAccountId());
    String customerId = context.account().getExternalAccountId();
    String externalAdGroupId = numeric(adGroup.getExternalAdSetId(), "Google ad group ID");
    String adGroupResource =
        apiClient.customerResourceName(customerId, "adGroups", externalAdGroupId);

    boolean targetingModeChanged = false;
    if (!included.isEmpty()) {
      targetingModeChanged =
          applyAudienceMode(context, adGroupResource, externalAdGroupId, mode);
    }

    Set<String> existing = existingFingerprints(context, externalAdGroupId);
    List<Map<String, Object>> operations = new ArrayList<>();
    int reused = 0;

    for (AdAudience audience : included) {
      String resource =
          apiClient.customerResourceName(
              customerId,
              "userLists",
              numeric(audience.getExternalAudienceId(), "Google audience ID"));
      String fingerprint = "INCLUDE|" + resource;
      if (existing.contains(fingerprint)) {
        reused++;
        continue;
      }
      operations.add(
          Map.of(
              "create",
              Map.of(
                  "adGroup", adGroupResource,
                  "userList", Map.of("userList", resource),
                  "negative", false)));
    }

    for (AdAudience audience : excluded) {
      String resource =
          apiClient.customerResourceName(
              customerId,
              "userLists",
              numeric(audience.getExternalAudienceId(), "Google audience ID"));
      String fingerprint = "EXCLUDE|" + resource;
      if (existing.contains(fingerprint)) {
        reused++;
        continue;
      }
      operations.add(
          Map.of(
              "create",
              Map.of(
                  "adGroup", adGroupResource,
                  "userList", Map.of("userList", resource),
                  "negative", true)));
    }

    if (!operations.isEmpty()) {
      apiClient.mutate(
          context.accessToken(),
          customerId,
          context.loginCustomerId(),
          "adGroupCriteria",
          operations);
    }

    activityLogService.record(
        companyId,
        adGroup.getAdAccountId(),
        AdProvider.GOOGLE_ADS,
        actorUserId,
        "GOOGLE_SEARCH_AUDIENCE_TARGETING_UPDATED",
        "AD_GROUP",
        externalAdGroupId,
        null,
        null,
        Map.of(
            "mode", mode,
            "includedCount", included.size(),
            "excludedCount", excluded.size(),
            "createdCriteria", operations.size(),
            "reusedCriteria", reused,
            "targetingModeChanged", targetingModeChanged));

    return new ApplyResult(
        mode,
        included.size(),
        excluded.size(),
        operations.size(),
        reused,
        targetingModeChanged);
  }

  private boolean applyAudienceMode(
      GoogleAdsAccountContextService.Context context,
      String adGroupResource,
      String externalAdGroupId,
      String mode) {
    List<TargetRestriction> current =
        currentTargetRestrictions(context, externalAdGroupId);
    boolean requestedBidOnly = "OBSERVATION".equals(mode);
    TargetRestriction currentAudience =
        current.stream()
            .filter(value -> "AUDIENCE".equals(value.dimension()))
            .findFirst()
            .orElse(null);
    if (currentAudience != null && currentAudience.bidOnly() == requestedBidOnly) {
      return false;
    }

    List<Map<String, Object>> targetRestrictions = new ArrayList<>();
    for (TargetRestriction restriction : current) {
      if ("AUDIENCE".equals(restriction.dimension())) continue;
      targetRestrictions.add(
          Map.of(
              "targetingDimension", restriction.dimension(),
              "bidOnly", restriction.bidOnly()));
    }
    targetRestrictions.add(
        Map.of(
            "targetingDimension", "AUDIENCE",
            "bidOnly", requestedBidOnly));

    Map<String, Object> update =
        Map.of(
            "resourceName",
            adGroupResource,
            "targetingSetting",
            Map.of("targetRestrictions", targetRestrictions));

    apiClient.mutate(
        context.accessToken(),
        context.account().getExternalAccountId(),
        context.loginCustomerId(),
        "adGroups",
        List.of(
            Map.of(
                "update",
                update,
                "updateMask",
                "targeting_setting.target_restrictions")));
    return true;
  }

  private List<TargetRestriction> currentTargetRestrictions(
      GoogleAdsAccountContextService.Context context,
      String externalAdGroupId) {
    String query =
        "SELECT ad_group.targeting_setting.target_restrictions "
            + "FROM ad_group WHERE ad_group.id = "
            + externalAdGroupId
            + " LIMIT 1";
    List<JsonNode> rows =
        apiClient.searchRows(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            query);
    if (rows.isEmpty()) return List.of();

    JsonNode restrictions =
        rows.getFirst().path("adGroup").path("targetingSetting").path("targetRestrictions");
    if (!restrictions.isArray()) return List.of();

    List<TargetRestriction> result = new ArrayList<>();
    for (JsonNode restriction : restrictions) {
      String dimension =
          restriction.path("targetingDimension").asText("").trim().toUpperCase(Locale.ROOT);
      if (dimension.isBlank()) continue;
      result.add(
          new TargetRestriction(
              dimension,
              restriction.path("bidOnly").asBoolean(false)));
    }
    return List.copyOf(result);
  }

  private Set<String> existingFingerprints(
      GoogleAdsAccountContextService.Context context,
      String externalAdGroupId) {
    String query =
        "SELECT ad_group_criterion.resource_name, ad_group_criterion.negative, "
            + "ad_group_criterion.user_list.user_list "
            + "FROM ad_group_criterion WHERE ad_group.id = "
            + externalAdGroupId
            + " AND ad_group_criterion.type = 'USER_LIST' "
            + "AND ad_group_criterion.status != 'REMOVED'";

    Set<String> result = new LinkedHashSet<>();
    for (JsonNode row :
        apiClient.searchRows(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            query)) {
      JsonNode criterion = row.path("adGroupCriterion");
      String resource = criterion.path("userList").path("userList").asText(null);
      if (resource == null || resource.isBlank()) continue;
      result.add(
          (criterion.path("negative").asBoolean(false) ? "EXCLUDE|" : "INCLUDE|")
              + resource);
    }
    return Set.copyOf(result);
  }

  private List<AdAudience> requireSearchEligibleLists(
      Long companyId,
      Long accountId,
      List<Long> ids) {
    if (ids == null || ids.isEmpty()) return List.of();
    List<AdAudience> result = new ArrayList<>();
    for (Long id : new LinkedHashSet<>(ids)) {
      if (id == null) continue;
      AdAudience audience =
          audienceRepository
              .findByIdAndCompanyId(id, companyId)
              .orElseThrow(() -> new NotFoundException("Advertising audience not found"));
      if (audience.getProvider() != AdProvider.GOOGLE_ADS
          || !audience.isAccessible()
          || !accountId.equals(audience.getAdAccountId())) {
        throw new BadRequestException(
            "All Google Search audiences must belong to the selected Google Ads account");
      }
      if (!eligibleForSearch(audience)) {
        throw new BadRequestException(
            "Google audience '" + audience.getName() + "' is not eligible for Search targeting");
      }
      result.add(audience);
    }
    return List.copyOf(result);
  }

  private void requireDisjoint(
      List<AdAudience> included,
      List<AdAudience> excluded) {
    Set<Long> includedIds =
        included.stream().map(AdAudience::getId).collect(java.util.stream.Collectors.toSet());
    if (excluded.stream().map(AdAudience::getId).anyMatch(includedIds::contains)) {
      throw new BadRequestException(
          "A Google Search audience cannot be both included and excluded");
    }
  }

  private boolean eligibleForSearch(AdAudience audience) {
    if (audience.getDeliveryStatusJson() == null
        || audience.getDeliveryStatusJson().isBlank()) {
      return false;
    }
    try {
      JsonNode status = objectMapper.readTree(audience.getDeliveryStatusJson());
      return status.path("eligibleForSearch").asBoolean(false)
          && !"DISABLED".equalsIgnoreCase(status.path("accountStatus").asText());
    } catch (Exception exception) {
      return false;
    }
  }

  private String normalizeMode(String value) {
    String normalized =
        value == null || value.isBlank()
            ? "OBSERVATION"
            : value.trim().toUpperCase(Locale.ROOT);
    if (!MODES.contains(normalized)) {
      throw new BadRequestException(
          "Google Search audience mode must be OBSERVATION or TARGETING");
    }
    return normalized;
  }

  private String numeric(String value, String label) {
    if (value == null || !value.matches("\\d+")) {
      throw new BadRequestException("Invalid " + label);
    }
    return value;
  }

  private record TargetRestriction(String dimension, boolean bidOnly) {}

  public record ValidationResult(
      String mode,
      int includedCount,
      int excludedCount) {}

  public record ApplyResult(
      String mode,
      int includedCount,
      int excludedCount,
      int createdCriteria,
      int reusedCriteria,
      boolean targetingModeChanged) {}
}
