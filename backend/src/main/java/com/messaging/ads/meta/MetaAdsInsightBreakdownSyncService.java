package com.messaging.ads.meta;

import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.enums.AdConnectionStatus;
import com.messaging.ads.enums.AdInsightBreakdownType;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.meta.dto.MetaInsightBreakdownsSyncResponse;
import com.messaging.ads.repository.AdAccountRepository;
import com.messaging.ads.repository.AdProviderConnectionRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetaAdsInsightBreakdownSyncService {

  private static final long MAX_SYNC_DAYS = 90;

  private final AdAccountRepository accountRepository;
  private final AdProviderConnectionRepository connectionRepository;
  private final MetaAdsTokenService tokenService;
  private final MetaAdsApiClient apiClient;
  private final MetaAdsInsightBreakdownPersistenceService persistenceService;

  public MetaInsightBreakdownsSyncResponse sync(
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
    List<String> failed = new ArrayList<>();

    int age = syncOne(companyId, account, token, from, to, AdInsightBreakdownType.AGE, failed);
    int gender =
        syncOne(companyId, account, token, from, to, AdInsightBreakdownType.GENDER, failed);
    int country =
        syncOne(companyId, account, token, from, to, AdInsightBreakdownType.COUNTRY, failed);
    int region =
        syncOne(companyId, account, token, from, to, AdInsightBreakdownType.REGION, failed);
    int publisherPlatform =
        syncOne(
            companyId,
            account,
            token,
            from,
            to,
            AdInsightBreakdownType.PUBLISHER_PLATFORM,
            failed);
    int platformPosition =
        syncOne(
            companyId,
            account,
            token,
            from,
            to,
            AdInsightBreakdownType.PLATFORM_POSITION,
            failed);
    int impressionDevice =
        syncOne(
            companyId,
            account,
            token,
            from,
            to,
            AdInsightBreakdownType.IMPRESSION_DEVICE,
            failed);
    int devicePlatform =
        syncOne(
            companyId,
            account,
            token,
            from,
            to,
            AdInsightBreakdownType.DEVICE_PLATFORM,
            failed);
    int conversionDestination =
        syncOne(
            companyId,
            account,
            token,
            from,
            to,
            AdInsightBreakdownType.CONVERSION_DESTINATION,
            failed);
    int actionDevice =
        syncOne(
            companyId,
            account,
            token,
            from,
            to,
            AdInsightBreakdownType.ACTION_DEVICE,
            failed);

    return new MetaInsightBreakdownsSyncResponse(
        age,
        gender,
        country,
        region,
        publisherPlatform,
        platformPosition,
        impressionDevice,
        devicePlatform,
        conversionDestination,
        actionDevice,
        List.copyOf(failed));
  }

  private int syncOne(
      Long companyId,
      AdAccount account,
      String token,
      LocalDate from,
      LocalDate to,
      AdInsightBreakdownType type,
      List<String> failed) {
    try {
      String breakdown = type == AdInsightBreakdownType.ACTION_DEVICE ? null : providerBreakdown(type);
      String actionBreakdown =
          type == AdInsightBreakdownType.ACTION_DEVICE ? "action_device" : null;
      var snapshots =
          apiClient.insightBreakdown(
              token,
              account.getExternalAccountId(),
              from,
              to,
              breakdown,
              actionBreakdown);
      return persistenceService.persist(
          companyId, account.getId(), type, from, to, snapshots);
    } catch (RuntimeException exception) {
      failed.add(type.name());
      log.warn(
          "meta_insight_breakdown_sync_failed companyId={} accountId={} breakdown={} error={}",
          companyId,
          account.getId(),
          type,
          exception.getMessage());
      return 0;
    }
  }

  private String providerBreakdown(AdInsightBreakdownType type) {
    return switch (type) {
      case AGE -> "age";
      case GENDER -> "gender";
      case COUNTRY -> "country";
      case REGION -> "region";
      case PUBLISHER_PLATFORM -> "publisher_platform";
      case PLATFORM_POSITION -> "platform_position";
      case IMPRESSION_DEVICE -> "impression_device";
      case DEVICE_PLATFORM -> "device_platform";
      case CONVERSION_DESTINATION -> "conversion_destination";
      case ACTION_DEVICE -> throw new IllegalArgumentException("Action device is an action breakdown");
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
    if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_SYNC_DAYS) {
      throw new BadRequestException("Meta analytics sync is limited to 90 days per request");
    }
  }
}
