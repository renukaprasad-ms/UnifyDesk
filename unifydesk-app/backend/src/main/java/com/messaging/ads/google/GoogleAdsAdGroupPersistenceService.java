package com.messaging.ads.google;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.entity.AdCampaign;
import com.messaging.ads.entity.AdSet;
import com.messaging.ads.enums.AdEntityStatus;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.dto.GoogleAdGroupSyncResponse;
import com.messaging.ads.repository.AdAccountRepository;
import com.messaging.ads.repository.AdCampaignRepository;
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
public class GoogleAdsAdGroupPersistenceService {

  private final AdCampaignRepository campaignRepository;
  private final AdAccountRepository accountRepository;
  private final AdSetRepository adSetRepository;
  private final ObjectMapper objectMapper;

  @Transactional
  public GoogleAdGroupSyncResponse persist(
      Long companyId, Long campaignId, List<JsonNode> rows) {
    AdCampaign campaign = requireCampaign(companyId, campaignId);

    Map<String, AdSet> existing = new HashMap<>();
    adSetRepository
        .findByCompanyIdAndCampaignIdOrderByProviderUpdatedAtDesc(companyId, campaignId)
        .stream()
        .filter(adSet -> adSet.getProvider() == AdProvider.GOOGLE_ADS)
        .forEach(adSet -> existing.put(adSet.getExternalAdSetId(), adSet));

    List<AdSet> changed = new ArrayList<>();
    Instant syncedAt = Instant.now();
    int count = 0;

    for (JsonNode row : rows) {
      JsonNode snapshot = row.path("adGroup");
      String externalId = text(snapshot, "id");
      if (externalId == null) continue;

      AdSet adGroup = existing.remove(externalId);
      changed.add(apply(campaign, currency(campaign), adGroup, row, syncedAt));
      count++;
    }

    existing.values().forEach(
        adGroup -> {
          adGroup.setAccessible(false);
          adGroup.setLastSyncAt(syncedAt);
          changed.add(adGroup);
        });

    adSetRepository.saveAll(changed);
    return new GoogleAdGroupSyncResponse(count);
  }

  @Transactional
  public AdSet persistOne(Long companyId, Long campaignId, JsonNode row) {
    AdCampaign campaign = requireCampaign(companyId, campaignId);
    JsonNode snapshot = row.path("adGroup");
    String externalId = text(snapshot, "id");
    if (externalId == null) {
      throw new BadRequestException("Google ad group snapshot is missing an ID");
    }

    AdSet existing =
        adSetRepository
            .findByCompanyIdAndAdAccountIdAndProviderAndExternalAdSetId(
                companyId,
                campaign.getAdAccountId(),
                AdProvider.GOOGLE_ADS,
                externalId)
            .orElse(null);

    return adSetRepository.save(apply(campaign, currency(campaign), existing, row, Instant.now()));
  }

  private AdSet apply(
      AdCampaign campaign, String currency, AdSet adGroup, JsonNode row, Instant syncedAt) {
    JsonNode snapshot = row.path("adGroup");
    String externalId = text(snapshot, "id");

    if (adGroup == null) {
      adGroup = new AdSet();
      adGroup.setCompanyId(campaign.getCompanyId());
      adGroup.setAdAccountId(campaign.getAdAccountId());
      adGroup.setCampaignId(campaign.getId());
      adGroup.setProvider(AdProvider.GOOGLE_ADS);
      adGroup.setExternalAdSetId(externalId);
    }

    adGroup.setName(first(text(snapshot, "name"), "Google Ad Group " + externalId));
    adGroup.setOptimizationGoal(text(snapshot, "type"));
    adGroup.setBillingEvent("CLICKS");
    adGroup.setProviderStatus(text(snapshot, "status"));
    adGroup.setStatus(mapStatus(text(snapshot, "status")));
    adGroup.setEffectiveStatus(text(snapshot, "primaryStatus"));
    Long cpcMicros = longValue(snapshot, "cpcBidMicros");
    Long targetCpaMicros = longValue(snapshot, "targetCpaMicros");
    Long storedBidMicros = targetCpaMicros != null ? targetCpaMicros : cpcMicros;
    adGroup.setBidAmountMinor(
        GoogleAdsMoney.microsToMinor(storedBidMicros, currency));
    if (targetCpaMicros != null) {
      adGroup.setBidStrategy("TARGET_CPA");
    }
    adGroup.setAccessible(true);
    adGroup.setProviderPayloadJson(json(row));
    adGroup.setLastSyncAt(syncedAt);
    return adGroup;
  }

  private AdCampaign requireCampaign(Long companyId, Long campaignId) {
    AdCampaign campaign =
        campaignRepository
            .findByIdAndCompanyId(campaignId, companyId)
            .orElseThrow(() -> new NotFoundException("Advertising campaign not found"));

    if (campaign.getProvider() != AdProvider.GOOGLE_ADS || !campaign.isAccessible()) {
      throw new BadRequestException(
          "Google campaign is not available for ad-group synchronization");
    }
    return campaign;
  }


  private String currency(AdCampaign campaign) {
    return accountRepository
        .findById(campaign.getAdAccountId())
        .map(com.messaging.ads.entity.AdAccount::getCurrency)
        .orElse(null);
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

  private String text(JsonNode node, String field) {
    if (node == null || node.path(field).isMissingNode() || node.path(field).isNull()) return null;
    String value = node.path(field).asText(null);
    return value == null || value.isBlank() ? null : value.trim();
  }

  private String first(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  private String json(JsonNode value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to serialize Google ad-group snapshot", exception);
    }
  }
}
