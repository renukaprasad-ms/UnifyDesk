package com.messaging.ads.meta;

import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdCampaign;
import com.messaging.ads.enums.AdConnectionStatus;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.meta.dto.MetaAdSetSyncResponse;
import com.messaging.ads.repository.AdAccountRepository;
import com.messaging.ads.repository.AdCampaignRepository;
import com.messaging.ads.repository.AdProviderConnectionRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MetaAdsAdSetSyncService {
  private final AdCampaignRepository campaignRepository;
  private final AdAccountRepository accountRepository;
  private final AdProviderConnectionRepository connectionRepository;
  private final MetaAdsTokenService tokenService;
  private final MetaAdsApiClient apiClient;
  private final MetaAdsAdSetPersistenceService persistenceService;

  public MetaAdSetSyncResponse sync(Long companyId, Long campaignId) {
    AdCampaign campaign =
        campaignRepository
            .findByIdAndCompanyId(campaignId, companyId)
            .orElseThrow(() -> new NotFoundException("Advertising campaign not found"));
    if (campaign.getProvider() != AdProvider.META_ADS || !campaign.isAccessible()) {
      throw new BadRequestException("Campaign is not an accessible Meta campaign");
    }

    AdAccount account =
        accountRepository
            .findByIdAndCompanyId(campaign.getAdAccountId(), companyId)
            .orElseThrow(() -> new NotFoundException("Ad account not found"));
    if (!account.isManaged() || !account.isAccessible()) {
      throw new BadRequestException("Meta ad account is not enabled for management");
    }

    var connection =
        connectionRepository
            .findByIdAndCompanyId(account.getConnectionId(), companyId)
            .orElseThrow(() -> new NotFoundException("Meta Ads connection not found"));
    if (connection.getStatus() != AdConnectionStatus.ACTIVE) {
      throw new BadRequestException("Meta Ads connection is not active");
    }

    String accessToken = tokenService.read(connection.getId()).token().accessToken();
    var adSets =
        apiClient.adSets(
            accessToken, account.getExternalAccountId(), campaign.getExternalCampaignId());
    return persistenceService.persist(companyId, campaignId, adSets);
  }
}
