package com.messaging.ads.meta;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.entity.AdCampaign;
import com.messaging.ads.entity.AdSet;
import com.messaging.ads.enums.AdEntityStatus;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.meta.MetaAdsApiClient.AdSetSummary;
import com.messaging.ads.meta.dto.MetaAdSetSyncResponse;
import com.messaging.ads.repository.AdCampaignRepository;
import com.messaging.ads.repository.AdSetRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MetaAdsAdSetPersistenceService {

  private static final DateTimeFormatter META_OFFSET_TIME =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");

  private final AdCampaignRepository campaignRepository;
  private final AdSetRepository adSetRepository;
  private final ObjectMapper objectMapper;

  @Transactional
  public MetaAdSetSyncResponse persist(
      Long companyId, Long campaignId, List<AdSetSummary> snapshots) {
    AdCampaign campaign = requireCampaign(companyId, campaignId);
    Map<String, AdSet> existing = new HashMap<>();
    adSetRepository
        .findByCompanyIdAndCampaignIdOrderByProviderUpdatedAtDesc(companyId, campaignId)
        .forEach(adSet -> existing.put(adSet.getExternalAdSetId(), adSet));

    List<AdSet> changed = new ArrayList<>();
    Instant syncedAt = Instant.now();
    int synchronizedCount = 0;

    for (AdSetSummary snapshot : snapshots) {
      if (snapshot.id() == null || snapshot.id().isBlank()) continue;
      String externalId = snapshot.id().trim();
      AdSet adSet = existing.remove(externalId);
      changed.add(applySnapshot(campaign, adSet, snapshot, syncedAt));
      synchronizedCount++;
    }

    existing.values().forEach(
        adSet -> {
          adSet.setAccessible(false);
          adSet.setLastSyncAt(syncedAt);
          changed.add(adSet);
        });
    adSetRepository.saveAll(changed);
    return new MetaAdSetSyncResponse(synchronizedCount);
  }

  @Transactional
  public AdSet persistOne(Long companyId, Long campaignId, AdSetSummary snapshot) {
    AdCampaign campaign = requireCampaign(companyId, campaignId);
    if (snapshot.id() == null || snapshot.id().isBlank()) {
      throw new BadRequestException("Meta ad set snapshot is missing an ID");
    }
    AdSet existing =
        adSetRepository
            .findByCompanyIdAndAdAccountIdAndProviderAndExternalAdSetId(
                companyId,
                campaign.getAdAccountId(),
                AdProvider.META_ADS,
                snapshot.id().trim())
            .orElse(null);
    return adSetRepository.save(
        applySnapshot(campaign, existing, snapshot, Instant.now()));
  }

  private AdCampaign requireCampaign(Long companyId, Long campaignId) {
    AdCampaign campaign =
        campaignRepository
            .findByIdAndCompanyId(campaignId, companyId)
            .orElseThrow(() -> new NotFoundException("Advertising campaign not found"));
    if (campaign.getProvider() != AdProvider.META_ADS || !campaign.isAccessible()) {
      throw new BadRequestException("Meta campaign is not available for ad-set synchronization");
    }
    return campaign;
  }

  private AdSet applySnapshot(
      AdCampaign campaign, AdSet adSet, AdSetSummary snapshot, Instant syncedAt) {
    String externalId = snapshot.id().trim();
    if (adSet == null) {
      adSet = new AdSet();
      adSet.setCompanyId(campaign.getCompanyId());
      adSet.setAdAccountId(campaign.getAdAccountId());
      adSet.setCampaignId(campaign.getId());
      adSet.setProvider(AdProvider.META_ADS);
      adSet.setExternalAdSetId(externalId);
    }
    adSet.setName(
        snapshot.name() == null || snapshot.name().isBlank()
            ? "Meta Ad Set " + externalId
            : snapshot.name().trim());
    adSet.setOptimizationGoal(trimToNull(snapshot.optimizationGoal()));
    adSet.setBillingEvent(trimToNull(snapshot.billingEvent()));
    adSet.setBidStrategy(trimToNull(snapshot.bidStrategy()));
    adSet.setDestinationType(trimToNull(snapshot.destinationType()));
    adSet.setProviderStatus(trimToNull(snapshot.status()));
    adSet.setStatus(mapStatus(snapshot.status()));
    adSet.setEffectiveStatus(trimToNull(snapshot.effectiveStatus()));
    adSet.setDailyBudgetMinor(parseLong(snapshot.dailyBudget()));
    adSet.setLifetimeBudgetMinor(parseLong(snapshot.lifetimeBudget()));
    adSet.setBidAmountMinor(parseLong(snapshot.bidAmount()));
    adSet.setStartAt(parseInstant(snapshot.startTime()));
    adSet.setEndAt(parseInstant(snapshot.endTime()));
    adSet.setTargetingJson(jsonNode(snapshot.targeting()));
    adSet.setPromotedObjectJson(jsonNode(snapshot.promotedObject()));
    adSet.setAttributionSpecJson(jsonNode(snapshot.attributionSpec()));
    adSet.setProviderCreatedAt(parseInstant(snapshot.createdTime()));
    adSet.setProviderUpdatedAt(parseInstant(snapshot.updatedTime()));
    adSet.setAccessible(true);
    adSet.setProviderPayloadJson(json(snapshot));
    adSet.setLastSyncAt(syncedAt);
    return adSet;
  }

  private AdEntityStatus mapStatus(String status) {
    if (status == null) return AdEntityStatus.UNKNOWN;
    return switch (status.trim().toUpperCase()) {
      case "ACTIVE" -> AdEntityStatus.ACTIVE;
      case "PAUSED" -> AdEntityStatus.PAUSED;
      case "ARCHIVED" -> AdEntityStatus.ARCHIVED;
      case "DELETED" -> AdEntityStatus.REMOVED;
      default -> AdEntityStatus.UNKNOWN;
    };
  }

  private Long parseLong(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return Long.valueOf(value.trim());
    } catch (NumberFormatException exception) {
      return null;
    }
  }

  private Instant parseInstant(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return Instant.parse(value.trim());
    } catch (DateTimeParseException ignored) {
      try {
        return OffsetDateTime.parse(value.trim(), META_OFFSET_TIME).toInstant();
      } catch (DateTimeParseException ignoredAgain) {
        return null;
      }
    }
  }

  private String trimToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private String jsonNode(JsonNode value) {
    return value == null || value.isNull() ? null : json(value);
  }

  private String json(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to serialize Meta Ads ad set snapshot", exception);
    }
  }
}
