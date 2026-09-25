package com.messaging.ads.google;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.entity.AdKeyword;
import com.messaging.ads.entity.AdSet;
import com.messaging.ads.enums.AdEntityStatus;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.dto.GoogleKeywordSyncResponse;
import com.messaging.ads.repository.AdKeywordRepository;
import com.messaging.ads.repository.AdSetRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
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
public class GoogleAdsKeywordPersistenceService {

  private final AdSetRepository adSetRepository;
  private final AdKeywordRepository keywordRepository;
  private final ObjectMapper objectMapper;

  @Transactional
  public GoogleKeywordSyncResponse persist(
      Long companyId, Long adSetId, List<JsonNode> rows) {
    AdSet adGroup = requireAdGroup(companyId, adSetId);

    Map<String, AdKeyword> existing = new HashMap<>();
    keywordRepository
        .findByCompanyIdAndAdSetIdOrderByUpdatedAtDesc(companyId, adSetId)
        .stream()
        .filter(keyword -> keyword.getProvider() == AdProvider.GOOGLE_ADS)
        .forEach(keyword -> existing.put(keyword.getExternalKeywordId(), keyword));

    List<AdKeyword> changed = new ArrayList<>();
    Instant syncedAt = Instant.now();
    int count = 0;

    for (JsonNode row : rows) {
      JsonNode snapshot = row.path("adGroupCriterion");
      String externalId = externalId(snapshot);
      if (externalId == null) continue;

      AdKeyword keyword = existing.remove(externalId);
      changed.add(apply(adGroup, keyword, row, syncedAt));
      count++;
    }

    existing.values().forEach(
        keyword -> {
          keyword.setAccessible(false);
          keyword.setLastSyncAt(syncedAt);
          changed.add(keyword);
        });

    keywordRepository.saveAll(changed);
    return new GoogleKeywordSyncResponse(count);
  }

  @Transactional
  public AdKeyword persistOne(Long companyId, Long adSetId, JsonNode row) {
    AdSet adGroup = requireAdGroup(companyId, adSetId);
    JsonNode snapshot = row.path("adGroupCriterion");
    String externalId = externalId(snapshot);
    if (externalId == null) {
      throw new BadRequestException("Google keyword snapshot is missing an ID");
    }

    AdKeyword existing =
        keywordRepository
            .findByCompanyIdAndAdAccountIdAndProviderAndExternalKeywordId(
                companyId,
                adGroup.getAdAccountId(),
                AdProvider.GOOGLE_ADS,
                externalId)
            .orElse(null);

    return keywordRepository.save(apply(adGroup, existing, row, Instant.now()));
  }

  private AdKeyword apply(
      AdSet adGroup, AdKeyword keyword, JsonNode row, Instant syncedAt) {
    JsonNode snapshot = row.path("adGroupCriterion");
    JsonNode keywordNode = snapshot.path("keyword");
    String externalId = externalId(snapshot);

    if (keyword == null) {
      keyword = new AdKeyword();
      keyword.setCompanyId(adGroup.getCompanyId());
      keyword.setAdAccountId(adGroup.getAdAccountId());
      keyword.setCampaignId(adGroup.getCampaignId());
      keyword.setAdSetId(adGroup.getId());
      keyword.setProvider(AdProvider.GOOGLE_ADS);
      keyword.setExternalKeywordId(externalId);
    }

    keyword.setKeywordText(first(text(keywordNode, "text"), "Keyword " + externalId));
    keyword.setMatchType(text(keywordNode, "matchType"));
    keyword.setProviderStatus(text(snapshot, "status"));
    keyword.setStatus(mapStatus(text(snapshot, "status")));
    keyword.setBidAmountMicros(longValue(snapshot, "cpcBidMicros"));
    keyword.setQualityScore(integer(snapshot.path("qualityInfo"), "qualityScore"));
    keyword.setFinalUrlsJson(json(snapshot.path("finalUrls")));
    keyword.setNegative(snapshot.path("negative").asBoolean(false));
    keyword.setAccessible(true);
    keyword.setProviderPayloadJson(json(row));
    keyword.setLastSyncAt(syncedAt);
    return keyword;
  }

  private AdSet requireAdGroup(Long companyId, Long adSetId) {
    AdSet adGroup =
        adSetRepository
            .findByIdAndCompanyId(adSetId, companyId)
            .orElseThrow(() -> new NotFoundException("Advertising ad group not found"));
    if (adGroup.getProvider() != AdProvider.GOOGLE_ADS || !adGroup.isAccessible()) {
      throw new BadRequestException(
          "Google ad group is not available for keyword synchronization");
    }
    return adGroup;
  }

  private String externalId(JsonNode snapshot) {
    String resourceName = text(snapshot, "resourceName");
    if (resourceName != null) {
      int slash = resourceName.lastIndexOf('/');
      if (slash >= 0 && slash < resourceName.length() - 1) {
        return resourceName.substring(slash + 1);
      }
    }
    String criterionId = text(snapshot, "criterionId");
    return criterionId;
  }

  private AdEntityStatus mapStatus(String status) {
    if (status == null) return AdEntityStatus.UNKNOWN;
    return switch (status.toUpperCase()) {
      case "ENABLED" -> AdEntityStatus.ACTIVE;
      case "PAUSED" -> AdEntityStatus.PAUSED;
      case "REMOVED" -> AdEntityStatus.REMOVED;
      default -> AdEntityStatus.UNKNOWN;
    };
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

  private Integer integer(JsonNode node, String field) {
    String value = text(node, field);
    if (value == null) return null;
    try {
      return Integer.valueOf(value);
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  private String text(JsonNode node, String field) {
    if (node == null || node.path(field).isMissingNode() || node.path(field).isNull()) return null;
    String value = node.path(field).asText(null);
    return value == null || value.isBlank() ? null : value.trim();
  }

  private String first(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  private String json(JsonNode value) {
    if (value == null || value.isMissingNode() || value.isNull()) return null;
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to serialize Google keyword snapshot", exception);
    }
  }
}
