package com.messaging.ads.meta;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.entity.Ad;
import com.messaging.ads.entity.AdCreative;
import com.messaging.ads.entity.AdSet;
import com.messaging.ads.enums.AdEntityStatus;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.meta.MetaAdsApiClient.AdSummary;
import com.messaging.ads.meta.dto.MetaAdSyncResponse;
import com.messaging.ads.repository.AdCreativeRepository;
import com.messaging.ads.repository.AdRepository;
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
public class MetaAdsAdPersistenceService {

  private static final DateTimeFormatter META_OFFSET_TIME =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");

  private final AdSetRepository adSetRepository;
  private final AdCreativeRepository creativeRepository;
  private final AdRepository adRepository;
  private final ObjectMapper objectMapper;

  @Transactional
  public MetaAdSyncResponse persist(Long companyId, Long adSetId, List<AdSummary> snapshots) {
    AdSet adSet = requireAdSet(companyId, adSetId);
    Map<String, Ad> existing = new HashMap<>();
    adRepository.findByCompanyIdAndAdSetIdOrderByProviderUpdatedAtDesc(companyId, adSetId)
        .forEach(ad -> existing.put(ad.getExternalAdId(), ad));

    List<Ad> changed = new ArrayList<>();
    Instant syncedAt = Instant.now();
    int count = 0;
    for (AdSummary snapshot : snapshots) {
      if (snapshot.id() == null || snapshot.id().isBlank()) continue;
      String externalId = snapshot.id().trim();
      changed.add(applySnapshot(adSet, existing.remove(externalId), snapshot, syncedAt));
      count++;
    }
    existing.values().forEach(
        ad -> {
          ad.setAccessible(false);
          ad.setLastSyncAt(syncedAt);
          changed.add(ad);
        });
    adRepository.saveAll(changed);
    return new MetaAdSyncResponse(count);
  }

  @Transactional
  public Ad persistOne(Long companyId, Long adSetId, AdSummary snapshot) {
    AdSet adSet = requireAdSet(companyId, adSetId);
    if (snapshot.id() == null || snapshot.id().isBlank()) {
      throw new BadRequestException("Meta ad snapshot is missing an ID");
    }
    Ad existing = adRepository
        .findByCompanyIdAndAdAccountIdAndProviderAndExternalAdId(
            companyId, adSet.getAdAccountId(), AdProvider.META_ADS, snapshot.id().trim())
        .orElse(null);
    return adRepository.save(applySnapshot(adSet, existing, snapshot, Instant.now()));
  }

  private AdSet requireAdSet(Long companyId, Long adSetId) {
    AdSet adSet = adSetRepository.findByIdAndCompanyId(adSetId, companyId)
        .orElseThrow(() -> new NotFoundException("Advertising ad set not found"));
    if (adSet.getProvider() != AdProvider.META_ADS || !adSet.isAccessible()) {
      throw new BadRequestException("Meta ad set is not available for ad synchronization");
    }
    return adSet;
  }

  private Ad applySnapshot(AdSet adSet, Ad ad, AdSummary snapshot, Instant syncedAt) {
    if (ad == null) {
      ad = new Ad();
      ad.setCompanyId(adSet.getCompanyId());
      ad.setAdAccountId(adSet.getAdAccountId());
      ad.setCampaignId(adSet.getCampaignId());
      ad.setAdSetId(adSet.getId());
      ad.setProvider(AdProvider.META_ADS);
      ad.setExternalAdId(snapshot.id().trim());
    }

    String externalCreativeId =
        snapshot.creative() == null ? null : blankToNull(snapshot.creative().id());
    ad.setExternalCreativeId(externalCreativeId);
    ad.setCreativeId(resolveCreativeId(adSet, externalCreativeId));
    ad.setName(
        snapshot.name() == null || snapshot.name().isBlank()
            ? "Meta Ad " + snapshot.id()
            : snapshot.name().trim());
    ad.setProviderStatus(blankToNull(snapshot.status()));
    ad.setStatus(mapStatus(snapshot.status()));
    ad.setEffectiveStatus(blankToNull(snapshot.effectiveStatus()));
    ad.setTrackingSpecsJson(jsonNode(snapshot.trackingSpecs()));
    ad.setConversionSpecsJson(jsonNode(snapshot.conversionSpecs()));
    ad.setProviderCreatedAt(parseInstant(snapshot.createdTime()));
    ad.setProviderUpdatedAt(parseInstant(snapshot.updatedTime()));
    ad.setAccessible(true);
    ad.setProviderPayloadJson(json(snapshot));
    ad.setLastSyncAt(syncedAt);
    return ad;
  }

  private Long resolveCreativeId(AdSet adSet, String externalCreativeId) {
    if (externalCreativeId == null) return null;
    return creativeRepository
        .findByCompanyIdAndAdAccountIdAndProviderAndExternalCreativeId(
            adSet.getCompanyId(), adSet.getAdAccountId(), AdProvider.META_ADS, externalCreativeId)
        .map(AdCreative::getId)
        .orElse(null);
  }

  private AdEntityStatus mapStatus(String status) {
    if (status == null) return AdEntityStatus.UNKNOWN;
    return switch (status.trim().toUpperCase()) {
      case "ACTIVE" -> AdEntityStatus.ACTIVE;
      case "PAUSED" -> AdEntityStatus.PAUSED;
      case "ARCHIVED" -> AdEntityStatus.ARCHIVED;
      case "DELETED" -> AdEntityStatus.REMOVED;
      case "DISAPPROVED", "WITH_ISSUES" -> AdEntityStatus.DISABLED;
      default -> AdEntityStatus.UNKNOWN;
    };
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
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

  private String jsonNode(JsonNode value) {
    return value == null || value.isNull() ? null : json(value);
  }

  private String json(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to serialize Meta ad snapshot", exception);
    }
  }
}
