package com.messaging.ads.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.entity.AdAudience;
import com.messaging.ads.entity.AdSet;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.repository.AdAudienceRepository;
import com.messaging.ads.repository.AdSetRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import java.util.ArrayList;
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
public class GoogleAdsVisualAudienceService {

  private final AdAudienceRepository audienceRepository;
  private final AdSetRepository adSetRepository;
  private final GoogleAdsAccountContextService contextService;
  private final GoogleAdsApiClient apiClient;
  private final ObjectMapper objectMapper;

  public ValidationResult validateUserLists(
      Long companyId,
      Long accountId,
      List<Long> includedIds,
      List<Long> excludedIds) {
    List<AdAudience> included = requireLists(companyId, accountId, includedIds);
    List<AdAudience> excluded = requireLists(companyId, accountId, excludedIds);
    return new ValidationResult(included.size(), excluded.size());
  }

  public DisplayApplyResult applyDisplayUserLists(
      Long companyId,
      Long adGroupId,
      List<Long> includedIds,
      List<Long> excludedIds) {
    AdSet adGroup =
        adSetRepository
            .findByIdAndCompanyId(adGroupId, companyId)
            .orElseThrow(() -> new NotFoundException("Advertising ad group not found"));
    if (adGroup.getProvider() != AdProvider.GOOGLE_ADS || !adGroup.isAccessible()) {
      throw new BadRequestException("Display audience targeting requires an accessible Google ad group");
    }

    List<AdAudience> included =
        requireLists(companyId, adGroup.getAdAccountId(), includedIds);
    List<AdAudience> excluded =
        requireLists(companyId, adGroup.getAdAccountId(), excludedIds);

    if (included.isEmpty() && excluded.isEmpty()) {
      return new DisplayApplyResult(0, 0, 0);
    }

    var context = contextService.requireManaged(companyId, adGroup.getAdAccountId());
    String customerId = context.account().getExternalAccountId();
    String externalAdGroupId = numeric(adGroup.getExternalAdSetId(), "Google ad group ID");
    String adGroupResource =
        apiClient.customerResourceName(customerId, "adGroups", externalAdGroupId);

    Set<String> existing =
        existingDisplayFingerprints(context, externalAdGroupId);

    List<Map<String, Object>> operations = new ArrayList<>();
    int reused = 0;
    for (AdAudience audience : included) {
      String resource =
          apiClient.customerResourceName(
              customerId, "userLists", numeric(audience.getExternalAudienceId(), "Google audience ID"));
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
              customerId, "userLists", numeric(audience.getExternalAudienceId(), "Google audience ID"));
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

    return new DisplayApplyResult(
        operations.size(), reused, included.size() + excluded.size());
  }

  public String ensureCustomerAudience(
      Long companyId,
      Long accountId,
      String name,
      List<Long> includedIds,
      List<Long> excludedIds) {
    List<AdAudience> included = requireLists(companyId, accountId, includedIds);
    List<AdAudience> excluded = requireLists(companyId, accountId, excludedIds);
    if (included.isEmpty()) {
      throw new BadRequestException(
          "Google reusable audience requires at least one included audience list");
    }

    var context = contextService.requireManaged(companyId, accountId);
    String customerId = context.account().getExternalAccountId();

    List<JsonNode> existing =
        apiClient.searchRows(
            context.accessToken(),
            customerId,
            context.loginCustomerId(),
            "SELECT audience.resource_name, audience.name, audience.status "
                + "FROM audience WHERE audience.status != 'REMOVED'");
    for (JsonNode row : existing) {
      JsonNode audience = row.path("audience");
      if (name.equals(audience.path("name").asText(null))) {
        String resourceName = audience.path("resourceName").asText(null);
        if (resourceName != null && !resourceName.isBlank()) return resourceName;
      }
    }

    List<Map<String, Object>> segments = new ArrayList<>();
    for (AdAudience audience : included) {
      segments.add(
          Map.of(
              "userList",
              Map.of(
                  "userList",
                  apiClient.customerResourceName(
                      customerId,
                      "userLists",
                      numeric(audience.getExternalAudienceId(), "Google audience ID")))));
    }

    Map<String, Object> create = new LinkedHashMap<>();
    create.put("name", name);
    create.put("scope", "CUSTOMER");
    create.put(
        "dimensions",
        List.of(
            Map.of(
                "audienceSegments",
                Map.of("segments", segments))));

    if (!excluded.isEmpty()) {
      List<Map<String, Object>> exclusions = new ArrayList<>();
      for (AdAudience audience : excluded) {
        exclusions.add(
            Map.of(
                "userList",
                Map.of(
                    "userList",
                    apiClient.customerResourceName(
                        customerId,
                        "userLists",
                        numeric(audience.getExternalAudienceId(), "Google audience ID")))));
      }
      create.put("exclusionDimension", Map.of("exclusions", exclusions));
    }

    JsonNode response =
        apiClient.mutate(
            context.accessToken(),
            customerId,
            context.loginCustomerId(),
            "audiences",
            List.of(Map.of("create", create)));
    String resourceName = apiClient.firstMutatedResourceName(response);
    if (resourceName == null || resourceName.isBlank()) {
      throw new BadRequestException(
          "Google Ads did not return the reusable audience resource");
    }
    return resourceName;
  }

  private List<AdAudience> requireLists(
      Long companyId, Long accountId, List<Long> ids) {
    if (ids == null || ids.isEmpty()) return List.of();
    List<AdAudience> result = new ArrayList<>();
    Set<Long> distinct = new LinkedHashSet<>(ids);
    for (Long id : distinct) {
      if (id == null) continue;
      AdAudience audience =
          audienceRepository
              .findByIdAndCompanyId(id, companyId)
              .orElseThrow(() -> new NotFoundException("Advertising audience not found"));
      if (audience.getProvider() != AdProvider.GOOGLE_ADS
          || !audience.isAccessible()
          || !accountId.equals(audience.getAdAccountId())) {
        throw new BadRequestException(
            "All Google visual-channel audiences must belong to the selected Google Ads account");
      }
      if (!eligibleForDisplay(audience)) {
        throw new BadRequestException(
            "Google audience '" + audience.getName() + "' is not eligible for Display targeting");
      }
      result.add(audience);
    }
    return List.copyOf(result);
  }

  private boolean eligibleForDisplay(AdAudience audience) {
    if (audience.getDeliveryStatusJson() == null
        || audience.getDeliveryStatusJson().isBlank()) {
      return false;
    }
    try {
      JsonNode status = objectMapper.readTree(audience.getDeliveryStatusJson());
      return status.path("eligibleForDisplay").asBoolean(false)
          && !"DISABLED".equalsIgnoreCase(status.path("accountStatus").asText());
    } catch (Exception exception) {
      return false;
    }
  }

  private Set<String> existingDisplayFingerprints(
      GoogleAdsAccountContextService.Context context,
      String adGroupId) {
    String query =
        "SELECT ad_group_criterion.negative, "
            + "ad_group_criterion.user_list.user_list "
            + "FROM ad_group_criterion WHERE ad_group.id = "
            + adGroupId
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
      String userList = criterion.path("userList").path("userList").asText(null);
      if (userList == null || userList.isBlank()) continue;
      result.add(
          (criterion.path("negative").asBoolean(false) ? "EXCLUDE|" : "INCLUDE|")
              + userList);
    }
    return Set.copyOf(result);
  }

  private String numeric(String value, String label) {
    if (value == null || !value.matches("\\d+")) {
      throw new BadRequestException("Invalid " + label);
    }
    return value;
  }

  public record ValidationResult(int includedCount, int excludedCount) {}

  public record DisplayApplyResult(
      int createdCount, int reusedCount, int requestedCount) {}
}
