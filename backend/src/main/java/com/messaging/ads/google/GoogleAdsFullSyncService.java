package com.messaging.ads.google;

import com.messaging.ads.entity.AdCampaign;
import com.messaging.ads.entity.AdSet;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.dto.GoogleFullSyncResponse;
import com.messaging.ads.repository.AdCampaignRepository;
import com.messaging.ads.repository.AdSetRepository;
import com.messaging.common.exception.BadRequestException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleAdsFullSyncService {

  private final GoogleAdsAccountContextService contextService;
  private final GoogleAdsCampaignSyncService campaignSyncService;
  private final GoogleAdsAdGroupSyncService adGroupSyncService;
  private final GoogleAdsKeywordSyncService keywordSyncService;
  private final GoogleAdsAdSyncService adSyncService;
  private final GoogleAdsAudienceSyncService audienceSyncService;
  private final GoogleAdsConversionSyncService conversionSyncService;
  private final GoogleAdsInsightSyncService insightSyncService;
  private final GoogleAdsInsightBreakdownSyncService insightBreakdownSyncService;
  private final GoogleAdsHealthService healthService;
  private final GoogleAdsBillingService billingService;
  private final AdCampaignRepository campaignRepository;
  private final AdSetRepository adSetRepository;

  public GoogleFullSyncResponse sync(Long companyId, Long accountId) {
    var context = contextService.requireManaged(companyId, accountId);
    if (context.managerAccount()) {
      throw new BadRequestException(
          "Select a Google Ads advertiser account for full synchronization; manager accounts only provide hierarchy access");
    }

    List<String> failed = new ArrayList<>();

    int campaigns =
        attempt(
            "campaigns",
            failed,
            () -> campaignSyncService.sync(companyId, accountId).synchronizedCount());

    int adGroups = 0;
    int keywords = 0;
    int ads = 0;

    List<AdCampaign> storedCampaigns =
        campaignRepository.findByCompanyIdAndAdAccountId(companyId, accountId).stream()
            .filter(AdCampaign::isAccessible)
            .filter(campaign -> campaign.getProvider() == AdProvider.GOOGLE_ADS)
            .toList();

    for (AdCampaign campaign : storedCampaigns) {
      adGroups +=
          attempt(
              "ad-groups:" + campaign.getId(),
              failed,
              () -> adGroupSyncService.sync(companyId, campaign.getId()).synchronizedCount());

      List<AdSet> groups =
          adSetRepository
              .findByCompanyIdAndCampaignIdOrderByProviderUpdatedAtDesc(
                  companyId, campaign.getId())
              .stream()
              .filter(AdSet::isAccessible)
              .filter(group -> group.getProvider() == AdProvider.GOOGLE_ADS)
              .toList();

      for (AdSet group : groups) {
        keywords +=
            attempt(
                "keywords:" + group.getId(),
                failed,
                () -> keywordSyncService.sync(companyId, group.getId()).synchronizedCount());
        ads +=
            attempt(
                "ads:" + group.getId(),
                failed,
                () -> adSyncService.sync(companyId, group.getId()).synchronizedCount());
      }
    }

    int audiences =
        attempt(
            "audiences",
            failed,
            () -> audienceSyncService.sync(companyId, accountId).synchronizedCount());
    int conversions =
        attempt(
            "conversions",
            failed,
            () -> conversionSyncService.sync(companyId, accountId).synchronizedCount());

    attemptVoid("health", failed, () -> healthService.sync(companyId, accountId));
    attemptVoid("billing", failed, () -> billingService.sync(companyId, accountId));

    LocalDate to = LocalDate.now(ZoneOffset.UTC);
    LocalDate from = to.minusDays(6);
    int analyticsRows =
        attempt(
            "analytics",
            failed,
            () -> {
              var result = insightSyncService.sync(companyId, accountId, from, to);
              return result.accountRows()
                  + result.campaignRows()
                  + result.adGroupRows()
                  + result.adRows();
            });

    attemptVoid(
        "analytics-breakdowns",
        failed,
        () -> insightBreakdownSyncService.sync(companyId, accountId, from, to));

    return new GoogleFullSyncResponse(
        campaigns,
        adGroups,
        keywords,
        ads,
        audiences,
        conversions,
        analyticsRows,
        List.copyOf(failed));
  }

  private int attempt(String operation, List<String> failed, IntSupplier action) {
    try {
      return action.getAsInt();
    } catch (RuntimeException exception) {
      failed.add(operation);
      log.warn(
          "google_ads_full_sync_step_failed operation={} error={}",
          operation,
          exception.getMessage());
      return 0;
    }
  }

  private void attemptVoid(String operation, List<String> failed, Runnable action) {
    try {
      action.run();
    } catch (RuntimeException exception) {
      failed.add(operation);
      log.warn(
          "google_ads_full_sync_step_failed operation={} error={}",
          operation,
          exception.getMessage());
    }
  }
}
