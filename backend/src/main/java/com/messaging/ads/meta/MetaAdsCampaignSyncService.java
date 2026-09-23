package com.messaging.ads.meta;

import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.enums.AdConnectionStatus;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.meta.dto.MetaAdsCampaignSyncResponse;
import com.messaging.ads.repository.AdAccountRepository;
import com.messaging.ads.repository.AdProviderConnectionRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MetaAdsCampaignSyncService {

  private final AdAccountRepository accountRepository;
  private final AdProviderConnectionRepository connectionRepository;
  private final MetaAdsTokenService tokenService;
  private final MetaAdsApiClient apiClient;
  private final MetaAdsCampaignPersistenceService persistenceService;

  public MetaAdsCampaignSyncResponse sync(Long companyId, Long adAccountId) {
    AdAccount account =
        accountRepository
            .findByIdAndCompanyId(adAccountId, companyId)
            .orElseThrow(() -> new NotFoundException("Ad account not found"));
    if (account.getProvider() != AdProvider.META_ADS) {
      throw new BadRequestException("Ad account is not a Meta Ads account");
    }
    if (!account.isManaged() || !account.isAccessible()) {
      throw new BadRequestException("Meta ad account is not available for campaign sync");
    }

    var connection =
        connectionRepository
            .findByIdAndCompanyId(account.getConnectionId(), companyId)
            .orElseThrow(() -> new NotFoundException("Meta Ads connection not found"));
    if (connection.getStatus() != AdConnectionStatus.ACTIVE) {
      throw new BadRequestException("Meta Ads connection is not active");
    }

    String accessToken = tokenService.read(connection.getId()).token().accessToken();
    var campaigns = apiClient.campaigns(accessToken, account.getExternalAccountId());
    return persistenceService.persist(companyId, adAccountId, campaigns);
  }
}
