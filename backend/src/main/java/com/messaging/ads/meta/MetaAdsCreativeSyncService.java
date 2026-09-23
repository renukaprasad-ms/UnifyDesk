package com.messaging.ads.meta;

import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.enums.AdConnectionStatus;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.meta.dto.MetaCreativeSyncResponse;
import com.messaging.ads.repository.AdAccountRepository;
import com.messaging.ads.repository.AdProviderConnectionRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MetaAdsCreativeSyncService {
  private final AdAccountRepository accountRepository;
  private final AdProviderConnectionRepository connectionRepository;
  private final MetaAdsTokenService tokenService;
  private final MetaAdsApiClient apiClient;
  private final MetaAdsCreativePersistenceService persistenceService;

  public MetaCreativeSyncResponse sync(Long companyId, Long accountId) {
    AdAccount account = accountRepository.findByIdAndCompanyId(accountId, companyId)
        .orElseThrow(() -> new NotFoundException("Ad account not found"));
    if (account.getProvider() != AdProvider.META_ADS || !account.isManaged() || !account.isAccessible()) {
      throw new BadRequestException("Meta ad account is not enabled for management");
    }
    var connection = connectionRepository.findByIdAndCompanyId(account.getConnectionId(), companyId)
        .orElseThrow(() -> new NotFoundException("Meta Ads connection not found"));
    if (connection.getStatus() != AdConnectionStatus.ACTIVE) {
      throw new BadRequestException("Meta Ads connection is not active");
    }
    String token = tokenService.read(connection.getId()).token().accessToken();
    return persistenceService.persist(companyId, accountId, apiClient.creatives(token, account.getExternalAccountId()));
  }
}
