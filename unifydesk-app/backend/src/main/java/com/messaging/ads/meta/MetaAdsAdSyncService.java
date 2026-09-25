package com.messaging.ads.meta;

import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdSet;
import com.messaging.ads.enums.AdConnectionStatus;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.meta.dto.MetaAdSyncResponse;
import com.messaging.ads.repository.AdAccountRepository;
import com.messaging.ads.repository.AdProviderConnectionRepository;
import com.messaging.ads.repository.AdSetRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MetaAdsAdSyncService {
  private final AdSetRepository adSetRepository;
  private final AdAccountRepository accountRepository;
  private final AdProviderConnectionRepository connectionRepository;
  private final MetaAdsTokenService tokenService;
  private final MetaAdsApiClient apiClient;
  private final MetaAdsAdPersistenceService persistenceService;

  public MetaAdSyncResponse sync(Long companyId, Long adSetId) {
    AdSet adSet = adSetRepository.findByIdAndCompanyId(adSetId, companyId)
        .orElseThrow(() -> new NotFoundException("Advertising ad set not found"));
    if (adSet.getProvider() != AdProvider.META_ADS || !adSet.isAccessible()) {
      throw new BadRequestException("Meta ad set is not available for synchronization");
    }
    AdAccount account = accountRepository.findByIdAndCompanyId(adSet.getAdAccountId(), companyId)
        .orElseThrow(() -> new NotFoundException("Ad account not found"));
    if (!account.isManaged() || !account.isAccessible()) {
      throw new BadRequestException("Meta ad account is not enabled for management");
    }
    var connection = connectionRepository.findByIdAndCompanyId(account.getConnectionId(), companyId)
        .orElseThrow(() -> new NotFoundException("Meta Ads connection not found"));
    if (connection.getStatus() != AdConnectionStatus.ACTIVE) {
      throw new BadRequestException("Meta Ads connection is not active");
    }
    String token = tokenService.read(connection.getId()).token().accessToken();
    return persistenceService.persist(
        companyId,
        adSetId,
        apiClient.ads(token, account.getExternalAccountId(), adSet.getExternalAdSetId()));
  }
}
