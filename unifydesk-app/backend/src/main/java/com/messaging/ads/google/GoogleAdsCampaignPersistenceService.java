package com.messaging.ads.google;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdCampaign;
import com.messaging.ads.enums.AdEntityStatus;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.dto.GoogleCampaignSyncResponse;
import com.messaging.ads.repository.AdAccountRepository;
import com.messaging.ads.repository.AdCampaignRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GoogleAdsCampaignPersistenceService {

  private static final DateTimeFormatter GOOGLE_DATE_TIME =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private final AdAccountRepository accountRepository;
  private final AdCampaignRepository campaignRepository;
  private final ObjectMapper objectMapper;

  @Transactional
  public GoogleCampaignSyncResponse persist(
      Long companyId, Long adAccountId, List<JsonNode> rows) {
    AdAccount account = requireAccount(companyId, adAccountId);

    Map<String, AdCampaign> existing = new HashMap<>();
    campaignRepository
        .findByCompanyIdAndAdAccountId(companyId, adAccountId)
        .stream()
        .filter(campaign -> campaign.getProvider() == AdProvider.GOOGLE_ADS)
        .forEach(campaign -> existing.put(campaign.getExternalCampaignId(), campaign));

    List<AdCampaign> changed = new ArrayList<>();
    Instant syncedAt = Instant.now();
    int count = 0;

    for (JsonNode row : rows) {
      JsonNode snapshot = row.path("campaign");
      String externalId = text(snapshot, "id");
      if (externalId == null) continue;

      AdCampaign campaign = existing.remove(externalId);
      changed.add(apply(account, campaign, row, syncedAt));
      count++;
    }

    existing.values().forEach(
        campaign -> {
          campaign.setAccessible(false);
          campaign.setLastSyncAt(syncedAt);
          changed.add(campaign);
        });

    campaignRepository.saveAll(changed);
    return new GoogleCampaignSyncResponse(count);
  }

  @Transactional
  public AdCampaign persistOne(Long companyId, Long adAccountId, JsonNode row) {
    AdAccount account = requireAccount(companyId, adAccountId);
    JsonNode snapshot = row.path("campaign");
    String externalId = text(snapshot, "id");
    if (externalId == null) {
      throw new BadRequestException("Google campaign snapshot is missing an ID");
    }

    AdCampaign existing =
        campaignRepository
            .findByCompanyIdAndAdAccountIdAndProviderAndExternalCampaignId(
                companyId, adAccountId, AdProvider.GOOGLE_ADS, externalId)
            .orElse(null);

    return campaignRepository.save(apply(account, existing, row, Instant.now()));
  }

  private AdCampaign apply(
      AdAccount account, AdCampaign campaign, JsonNode row, Instant syncedAt) {
    JsonNode snapshot = row.path("campaign");
    JsonNode budget = row.path("campaignBudget");
    String externalId = text(snapshot, "id");

    if (campaign == null) {
      campaign = new AdCampaign();
      campaign.setCompanyId(account.getCompanyId());
      campaign.setAdAccountId(account.getId());
      campaign.setProvider(AdProvider.GOOGLE_ADS);
      campaign.setExternalCampaignId(externalId);
    }

    campaign.setName(first(text(snapshot, "name"), "Google Campaign " + externalId));
    String channelType = text(snapshot, "advertisingChannelType");
    String channelSubType = text(snapshot, "advertisingChannelSubType");
    campaign.setObjective(
        "MULTI_CHANNEL".equalsIgnoreCase(channelType)
                && channelSubType != null
                && channelSubType.startsWith("APP_CAMPAIGN")
            ? "APP"
            : channelType);
    campaign.setBuyingType(channelSubType);
    campaign.setBidStrategy(productBidStrategy(text(snapshot, "biddingStrategyType")));
    campaign.setProviderStatus(text(snapshot, "status"));
    campaign.setEffectiveStatus(text(snapshot, "primaryStatus"));
    campaign.setStatus(mapStatus(text(snapshot, "status")));

    Long amountMicros = longValue(budget, "amountMicros");
    campaign.setDailyBudgetMinor(
        GoogleAdsMoney.microsToMinor(amountMicros, account.getCurrency()));
    campaign.setLifetimeBudgetMinor(null);
    campaign.setSpendCapMinor(null);
    campaign.setStartAt(
        dateTime(text(snapshot, "startDateTime"), false, account.getTimezone()));
    campaign.setEndAt(
        dateTime(text(snapshot, "endDateTime"), true, account.getTimezone()));
    campaign.setAccessible(true);
    campaign.setProviderPayloadJson(json(row));
    campaign.setLastSyncAt(syncedAt);
    return campaign;
  }

  private AdAccount requireAccount(Long companyId, Long accountId) {
    AdAccount account =
        accountRepository
            .findByIdAndCompanyIdForUpdate(accountId, companyId)
            .orElseThrow(() -> new NotFoundException("Ad account not found"));
    if (account.getProvider() != AdProvider.GOOGLE_ADS
        || !account.isManaged()
        || !account.isAccessible()) {
      throw new BadRequestException(
          "Google Ads account is not available for campaign synchronization");
    }
    return account;
  }

  private String productBidStrategy(String providerStrategy) {
    if (providerStrategy == null) return null;
    return "TARGET_SPEND".equalsIgnoreCase(providerStrategy)
        ? "MAXIMIZE_CLICKS"
        : providerStrategy;
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

  private Instant dateTime(String value, boolean endOfDay, String timezone) {
    if (value == null) return null;
    ZoneId zone = zone(timezone);
    try {
      return LocalDateTime.parse(value, GOOGLE_DATE_TIME).atZone(zone).toInstant();
    } catch (RuntimeException ignored) {
      try {
        LocalDate date = LocalDate.parse(value);
        return endOfDay
            ? date.plusDays(1).atStartOfDay(zone).minusNanos(1).toInstant()
            : date.atStartOfDay(zone).toInstant();
      } catch (RuntimeException ignoredAgain) {
        return null;
      }
    }
  }

  private ZoneId zone(String timezone) {
    if (timezone == null || timezone.isBlank()) return ZoneOffset.UTC;
    try {
      return ZoneId.of(timezone);
    } catch (RuntimeException ignored) {
      return ZoneOffset.UTC;
    }
  }

  private Long longValue(JsonNode node, String field) {
    if (node == null || node.path(field).isMissingNode() || node.path(field).isNull()) return null;
    String value = node.path(field).asText(null);
    if (value == null || value.isBlank()) return null;
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
      throw new IllegalStateException("Unable to serialize Google campaign snapshot", exception);
    }
  }
}
