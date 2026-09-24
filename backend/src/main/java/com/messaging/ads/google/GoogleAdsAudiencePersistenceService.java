package com.messaging.ads.google;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.entity.AdAudience;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.dto.GoogleAudienceSyncResponse;
import com.messaging.ads.repository.AdAudienceRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GoogleAdsAudiencePersistenceService {

  private final AdAudienceRepository repository;
  private final ObjectMapper objectMapper;

  @Transactional
  public GoogleAudienceSyncResponse persist(
      Long companyId, Long accountId, List<JsonNode> rows) {
    Map<String, AdAudience> existing = new HashMap<>();
    repository
        .findByCompanyIdAndAdAccountIdOrderByProviderUpdatedAtDesc(companyId, accountId)
        .stream()
        .filter(value -> value.getProvider() == AdProvider.GOOGLE_ADS)
        .forEach(value -> existing.put(value.getExternalAudienceId(), value));

    List<AdAudience> changed = new ArrayList<>();
    Instant syncedAt = Instant.now();
    int count = 0;

    for (JsonNode row : rows) {
      JsonNode snapshot = row.path("userList");
      String externalId = text(snapshot, "id");
      if (externalId == null) continue;
      AdAudience audience = existing.remove(externalId);
      changed.add(apply(companyId, accountId, audience, row, syncedAt));
      count++;
    }

    existing.values().forEach(
        audience -> {
          audience.setAccessible(false);
          audience.setLastSyncAt(syncedAt);
          changed.add(audience);
        });
    repository.saveAll(changed);
    return new GoogleAudienceSyncResponse(count);
  }

  @Transactional
  public AdAudience persistOne(Long companyId, Long accountId, JsonNode row) {
    JsonNode snapshot = row.path("userList");
    String externalId = text(snapshot, "id");
    if (externalId == null) {
      throw new com.messaging.common.exception.BadRequestException(
          "Google audience snapshot is missing an ID");
    }

    AdAudience existing =
        repository
            .findByCompanyIdAndAdAccountIdAndProviderAndExternalAudienceId(
                companyId, accountId, AdProvider.GOOGLE_ADS, externalId)
            .orElse(null);

    return repository.save(apply(companyId, accountId, existing, row, Instant.now()));
  }

  private AdAudience apply(
      Long companyId,
      Long accountId,
      AdAudience audience,
      JsonNode row,
      Instant syncedAt) {
    JsonNode snapshot = row.path("userList");
    String externalId = text(snapshot, "id");

    if (audience == null) {
      audience = new AdAudience();
      audience.setCompanyId(companyId);
      audience.setAdAccountId(accountId);
      audience.setProvider(AdProvider.GOOGLE_ADS);
      audience.setExternalAudienceId(externalId);
    }

    audience.setName(first(text(snapshot, "name"), "Google Audience " + externalId));
    audience.setDescription(text(snapshot, "description"));
    audience.setSubtype(text(snapshot, "type"));
    audience.setRetentionDays(integer(snapshot, "membershipLifeSpan"));
    audience.setApproximateCountLowerBound(longValue(snapshot, "sizeForSearch"));
    audience.setApproximateCountUpperBound(longValue(snapshot, "sizeForDisplay"));
    audience.setDeliveryStatusJson(
        json(
            objectMapper.valueToTree(
                Map.of(
                    "membershipStatus", first(text(snapshot, "membershipStatus"), ""),
                    "eligibleForSearch", snapshot.path("eligibleForSearch").asBoolean(false),
                    "eligibleForDisplay", snapshot.path("eligibleForDisplay").asBoolean(false),
                    "accountStatus", first(text(snapshot, "accountUserListStatus"), "")))));
    audience.setOperationStatusJson(
        json(
            objectMapper.valueToTree(
                Map.of(
                    "readOnly", snapshot.path("readOnly").asBoolean(false),
                    "accessReason", first(text(snapshot, "accessReason"), ""),
                    "matchRatePercentage", snapshot.path("matchRatePercentage").asInt(0)))));
    audience.setRuleJson(json(snapshot.path("ruleBasedUserList")));
    audience.setLookalikeSpecJson(json(snapshot.path("lookalikeUserList")));
    audience.setAccessible(
        !"DISABLED".equalsIgnoreCase(text(snapshot, "accountUserListStatus")));
    audience.setProviderPayloadJson(json(row));
    audience.setLastSyncAt(syncedAt);
    return audience;
  }

  private String text(JsonNode node, String field) {
    if (node == null || node.path(field).isMissingNode() || node.path(field).isNull()) return null;
    String value = node.path(field).asText(null);
    return value == null || value.isBlank() ? null : value.trim();
  }

  private Integer integer(JsonNode node, String field) {
    String value = text(node, field);
    if (value == null) return null;
    try {
      return Integer.valueOf(value);
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  private Long longValue(JsonNode node, String field) {
    String value = text(node, field);
    if (value == null) return null;
    try {
      return Long.valueOf(value);
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  private String first(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  private String json(JsonNode value) {
    if (value == null || value.isMissingNode() || value.isNull()) return null;
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to serialize Google audience snapshot", exception);
    }
  }
}
