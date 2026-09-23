package com.messaging.ads.meta;

import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.enums.AdConnectionStatus;
import com.messaging.ads.enums.AdInsightGranularity;
import com.messaging.ads.enums.AdInsightLevel;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.meta.dto.MetaInsightsSyncResponse;
import com.messaging.ads.repository.AdAccountRepository;
import com.messaging.ads.repository.AdProviderConnectionRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MetaAdsInsightSyncService {

  private static final long MAX_SYNC_DAYS = 90;

  private final AdAccountRepository accountRepository;
  private final AdProviderConnectionRepository connectionRepository;
  private final MetaAdsTokenService tokenService;
  private final MetaAdsApiClient apiClient;
  private final MetaAdsInsightPersistenceService persistenceService;

  public MetaInsightsSyncResponse sync(
      Long companyId, Long accountId, LocalDate from, LocalDate to) {
    validateRange(from, to);
    AdAccount account =
        accountRepository
            .findByIdAndCompanyId(accountId, companyId)
            .orElseThrow(() -> new NotFoundException("Ad account not found"));
    if (account.getProvider() != AdProvider.META_ADS
        || !account.isManaged()
        || !account.isAccessible()) {
      throw new BadRequestException("Meta ad account is not enabled for analytics");
    }

    var connection =
        connectionRepository
            .findByIdAndCompanyId(account.getConnectionId(), companyId)
            .orElseThrow(() -> new NotFoundException("Meta Ads connection not found"));
    if (connection.getStatus() != AdConnectionStatus.ACTIVE) {
      throw new BadRequestException("Meta Ads connection is not active");
    }

    String token = tokenService.read(connection.getId()).token().accessToken();

    int accountRows = syncLevel(companyId, account, token, AdInsightLevel.ACCOUNT, from, to);
    int campaignRows = syncLevel(companyId, account, token, AdInsightLevel.CAMPAIGN, from, to);
    int adSetRows = syncLevel(companyId, account, token, AdInsightLevel.AD_SET, from, to);
    int adRows = syncLevel(companyId, account, token, AdInsightLevel.AD, from, to);

    return new MetaInsightsSyncResponse(accountRows, campaignRows, adSetRows, adRows);
  }

  private int syncLevel(
      Long companyId,
      AdAccount account,
      String token,
      AdInsightLevel level,
      LocalDate from,
      LocalDate to) {
    String providerLevel = providerLevel(level);

    List<MetaAdsApiClient.InsightSummary> daily =
        apiClient.insights(
            token,
            account.getExternalAccountId(),
            providerLevel,
            from,
            to,
            "1");
    persistenceService.persist(
        companyId,
        account.getId(),
        level,
        AdInsightGranularity.DAILY,
        from,
        to,
        daily);

    List<MetaAdsApiClient.InsightSummary> range =
        apiClient.insights(
            token,
            account.getExternalAccountId(),
            providerLevel,
            from,
            to,
            "all_days");
    return persistenceService.persist(
        companyId,
        account.getId(),
        level,
        AdInsightGranularity.RANGE,
        from,
        to,
        range);
  }

  private String providerLevel(AdInsightLevel level) {
    return switch (level) {
      case ACCOUNT -> "account";
      case CAMPAIGN -> "campaign";
      case AD_SET -> "adset";
      case AD -> "ad";
    };
  }

  private void validateRange(LocalDate from, LocalDate to) {
    if (from == null || to == null) {
      throw new BadRequestException("Analytics start and end dates are required");
    }
    if (to.isBefore(from)) {
      throw new BadRequestException("Analytics end date must be on or after the start date");
    }
    if (to.isAfter(LocalDate.now())) {
      throw new BadRequestException("Analytics end date cannot be in the future");
    }
    long days = ChronoUnit.DAYS.between(from, to) + 1;
    if (days > MAX_SYNC_DAYS) {
      throw new BadRequestException("Meta analytics sync is limited to 90 days per request");
    }
  }
}
