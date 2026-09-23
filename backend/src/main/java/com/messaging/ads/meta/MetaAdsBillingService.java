package com.messaging.ads.meta;

import com.messaging.ads.dto.AdAccountBillingResponse;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdProviderConnection;
import com.messaging.ads.enums.AdConnectionStatus;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.repository.AdAccountRepository;
import com.messaging.ads.repository.AdProviderConnectionRepository;
import com.messaging.ads.service.AdActivityLogService;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetaAdsBillingService {

  private final AdAccountRepository accountRepository;
  private final AdProviderConnectionRepository connectionRepository;
  private final MetaAdsTokenService tokenService;
  private final MetaAdsApiClient apiClient;
  private final MetaAdsBillingPersistenceService persistenceService;
  private final AdActivityLogService activityLogService;

  public AdAccountBillingResponse sync(Long companyId, Long accountId) {
    Context context = context(companyId, accountId);
    return fetchAndPersist(context);
  }

  public AdAccountBillingResponse updateSpendCap(
      Long companyId,
      Long accountId,
      long spendCapMinor,
      Long actorUserId) {
    Context context = context(companyId, accountId);
    var before =
        apiClient.accountBilling(
            context.accessToken(),
            context.account().getExternalAccountId());

    apiClient.updateAccountSpendCap(
        context.accessToken(),
        context.account().getExternalAccountId(),
        spendCapMinor);

    var after =
        apiClient.accountBilling(
            context.accessToken(),
            context.account().getExternalAccountId());
    AdAccountBillingResponse response =
        persistenceService.persist(
            context.account().getCompanyId(),
            context.account().getId(),
            after);

    try {
      activityLogService.record(
          companyId,
          accountId,
          AdProvider.META_ADS,
          actorUserId,
          "META_ACCOUNT_SPEND_CAP_UPDATED",
          "AD_ACCOUNT",
          context.account().getExternalAccountId(),
          before,
          after,
          Map.of("requestedSpendCapMinor", spendCapMinor));
    } catch (RuntimeException exception) {
      log.error(
          "ad_activity_log_failed companyId={} accountId={} action={} error={}",
          companyId,
          accountId,
          "META_ACCOUNT_SPEND_CAP_UPDATED",
          exception.getMessage(),
          exception);
    }

    return response;
  }

  private AdAccountBillingResponse fetchAndPersist(Context context) {
    var summary =
        apiClient.accountBilling(
            context.accessToken(),
            context.account().getExternalAccountId());

    return persistenceService.persist(
        context.account().getCompanyId(),
        context.account().getId(),
        summary);
  }

  private Context context(Long companyId, Long accountId) {
    AdAccount account =
        accountRepository
            .findByIdAndCompanyId(accountId, companyId)
            .orElseThrow(() -> new NotFoundException("Ad account not found"));

    if (account.getProvider() != AdProvider.META_ADS
        || !account.isManaged()
        || !account.isAccessible()) {
      throw new BadRequestException(
          "Meta ad account is not enabled for billing management");
    }

    AdProviderConnection connection =
        connectionRepository
            .findByIdAndCompanyId(account.getConnectionId(), companyId)
            .orElseThrow(() -> new NotFoundException("Meta Ads connection not found"));

    if (connection.getStatus() != AdConnectionStatus.ACTIVE) {
      throw new BadRequestException("Meta Ads connection is not active");
    }

    String accessToken = tokenService.read(connection.getId()).token().accessToken();
    return new Context(account, accessToken);
  }

  private record Context(AdAccount account, String accessToken) {}
}
