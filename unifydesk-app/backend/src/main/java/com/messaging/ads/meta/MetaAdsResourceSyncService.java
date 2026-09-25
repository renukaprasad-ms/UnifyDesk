package com.messaging.ads.meta;

import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.enums.AdConnectionStatus;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.meta.MetaAdsApiClient.InstagramSummary;
import com.messaging.ads.meta.MetaAdsApiClient.PageSummary;
import com.messaging.ads.meta.MetaAdsApiClient.PixelSummary;
import com.messaging.ads.meta.dto.MetaAdsResourceSyncResponse;
import com.messaging.ads.repository.AdAccountRepository;
import com.messaging.ads.repository.AdProviderConnectionRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MetaAdsResourceSyncService {

  private final AdAccountRepository accountRepository;
  private final AdProviderConnectionRepository connectionRepository;
  private final MetaAdsTokenService tokenService;
  private final MetaAdsApiClient apiClient;
  private final MetaAdsResourcePersistenceService persistenceService;

  public MetaAdsResourceSyncResponse sync(Long companyId, Long adAccountId) {
    AdAccount account =
        accountRepository
            .findByIdAndCompanyId(adAccountId, companyId)
            .orElseThrow(() -> new NotFoundException("Ad account not found"));

    if (account.getProvider() != AdProvider.META_ADS) {
      throw new BadRequestException("Ad account is not a Meta Ads account");
    }
    if (!account.isManaged()) {
      throw new BadRequestException("Select the Meta ad account for management before syncing");
    }
    if (!account.isAccessible()) {
      throw new BadRequestException("Meta ad account is no longer accessible");
    }

    var connection =
        connectionRepository
            .findByIdAndCompanyId(account.getConnectionId(), companyId)
            .orElseThrow(() -> new NotFoundException("Meta Ads connection not found"));
    if (connection.getStatus() != AdConnectionStatus.ACTIVE) {
      throw new BadRequestException("Meta Ads connection is not active");
    }

    String accessToken = tokenService.read(connection.getId()).token().accessToken();
    List<PageSummary> pages = apiClient.promotePages(accessToken, account.getExternalAccountId());
    List<InstagramSummary> instagramAccounts =
        apiClient.instagramAccounts(accessToken, account.getExternalAccountId());
    List<PixelSummary> pixels = apiClient.pixels(accessToken, account.getExternalAccountId());

    return persistenceService.persist(
        companyId, adAccountId, pages, instagramAccounts, pixels);
  }
}
