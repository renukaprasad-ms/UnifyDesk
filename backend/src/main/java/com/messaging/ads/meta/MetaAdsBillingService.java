package com.messaging.ads.meta;

import com.messaging.ads.dto.AdAccountBillingResponse;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdProviderConnection;
import com.messaging.ads.enums.AdConnectionStatus;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.repository.AdAccountRepository;
import com.messaging.ads.repository.AdProviderConnectionRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MetaAdsBillingService {

  private final AdAccountRepository accountRepository;
  private final AdProviderConnectionRepository connectionRepository;
  private final MetaAdsTokenService tokenService;
  private final MetaAdsApiClient apiClient;
  private final MetaAdsBillingPersistenceService persistenceService;

  public AdAccountBillingResponse sync(Long companyId, Long accountId) {
    Context context = context(companyId, accountId);
    return fetchAndPersist(context);
  }

  public AdAccountBillingResponse updateSpendCap(
      Long companyId, Long accountId, long spendCapMinor) {
    Context context = context(companyId, accountId);

    apiClient.updateAccountSpendCap(
        context.accessToken(),
        context.account().getExternalAccountId(),
        spendCapMinor);

    return fetchAndPersist(context);
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
