package com.messaging.ads.meta;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdCampaign;
import com.messaging.ads.enums.AdEntityStatus;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.meta.MetaAdsApiClient.CampaignSummary;
import com.messaging.ads.meta.dto.MetaAdsCampaignSyncResponse;
import com.messaging.ads.repository.AdAccountRepository;
import com.messaging.ads.repository.AdCampaignRepository;
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
public class MetaAdsCampaignPersistenceService {

  private static final DateTimeFormatter META_OFFSET_TIME =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");

  private final AdAccountRepository accountRepository;
  private final AdCampaignRepository campaignRepository;
  private final ObjectMapper objectMapper;

  @Transactional
  public MetaAdsCampaignSyncResponse persist(
      Long companyId, Long adAccountId, List<CampaignSummary> snapshots) {
    AdAccount account =
        accountRepository
            .findByIdAndCompanyIdForUpdate(adAccountId, companyId)
            .orElseThrow(() -> new NotFoundException("Ad account not found"));
    if (account.getProvider() != AdProvider.META_ADS
        || !account.isAccessible()
        || !account.isManaged()) {
      throw new BadRequestException(
          "Meta ad account is no longer available for campaign synchronization");
    }

    Map<String, AdCampaign> existing =
        new HashMap<>();
    campaignRepository
        .findByCompanyIdAndAdAccountId(companyId, adAccountId)
        .forEach(campaign -> existing.put(campaign.getExternalCampaignId(), campaign));

    List<AdCampaign> changed = new ArrayList<>();
    int synchronizedCount = 0;
    Instant syncedAt = Instant.now();

    for (CampaignSummary snapshot : snapshots) {
      if (snapshot.id() == null || snapshot.id().isBlank()) continue;
      String externalId = snapshot.id().trim();
      AdCampaign campaign = existing.remove(externalId);
      changed.add(applySnapshot(companyId, adAccountId, campaign, snapshot, syncedAt));
      synchronizedCount++;
    }

    existing.values().forEach(
        campaign -> {
          campaign.setAccessible(false);
          campaign.setLastSyncAt(syncedAt);
          changed.add(campaign);
        });

    campaignRepository.saveAll(changed);
    return new MetaAdsCampaignSyncResponse(synchronizedCount);
  }

  @Transactional
  public AdCampaign persistOne(
      Long companyId, Long adAccountId, CampaignSummary snapshot) {
    AdAccount account =
        accountRepository
            .findByIdAndCompanyIdForUpdate(adAccountId, companyId)
            .orElseThrow(() -> new NotFoundException("Ad account not found"));
    if (account.getProvider() != AdProvider.META_ADS
        || !account.isAccessible()
        || !account.isManaged()) {
      throw new BadRequestException("Meta ad account is not available for campaign updates");
    }
    if (snapshot.id() == null || snapshot.id().isBlank()) {
      throw new BadRequestException("Meta campaign snapshot is missing an ID");
    }
    AdCampaign existing =
        campaignRepository
            .findByCompanyIdAndAdAccountIdAndProviderAndExternalCampaignId(
                companyId, adAccountId, AdProvider.META_ADS, snapshot.id().trim())
            .orElse(null);
    return campaignRepository.save(
        applySnapshot(companyId, adAccountId, existing, snapshot, Instant.now()));
  }

  private AdCampaign applySnapshot(
      Long companyId,
      Long adAccountId,
      AdCampaign campaign,
      CampaignSummary snapshot,
      Instant syncedAt) {
    String externalId = snapshot.id().trim();
    if (campaign == null) {
      campaign = new AdCampaign();
      campaign.setCompanyId(companyId);
      campaign.setAdAccountId(adAccountId);
      campaign.setProvider(AdProvider.META_ADS);
      campaign.setExternalCampaignId(externalId);
    }
    campaign.setName(
        snapshot.name() == null || snapshot.name().isBlank()
            ? "Meta Campaign " + externalId
            : snapshot.name().trim());
    campaign.setObjective(trimToNull(snapshot.objective()));
    campaign.setBuyingType(trimToNull(snapshot.buyingType()));
    campaign.setBidStrategy(trimToNull(snapshot.bidStrategy()));
    campaign.setProviderStatus(trimToNull(snapshot.status()));
    campaign.setStatus(mapStatus(snapshot.status()));
    campaign.setEffectiveStatus(trimToNull(snapshot.effectiveStatus()));
    campaign.setDailyBudgetMinor(parseLong(snapshot.dailyBudget()));
    campaign.setLifetimeBudgetMinor(parseLong(snapshot.lifetimeBudget()));
    campaign.setSpendCapMinor(parseLong(snapshot.spendCap()));
    campaign.setStartAt(parseInstant(snapshot.startTime()));
    campaign.setEndAt(parseInstant(snapshot.stopTime()));
    campaign.setProviderCreatedAt(parseInstant(snapshot.createdTime()));
    campaign.setProviderUpdatedAt(parseInstant(snapshot.updatedTime()));
    campaign.setAccessible(true);
    campaign.setProviderPayloadJson(json(snapshot));
    campaign.setLastSyncAt(syncedAt);
    return campaign;
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
    String normalized = value.trim();
    try {
      return Instant.parse(normalized);
    } catch (DateTimeParseException ignored) {
      try {
        return OffsetDateTime.parse(normalized, META_OFFSET_TIME).toInstant();
      } catch (DateTimeParseException ignoredAgain) {
        return null;
      }
    }
  }

  private String trimToNull(String value) {
    if (value == null || value.isBlank()) return null;
    return value.trim();
  }

  private String json(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to serialize Meta Ads campaign snapshot", exception);
    }
  }
}
