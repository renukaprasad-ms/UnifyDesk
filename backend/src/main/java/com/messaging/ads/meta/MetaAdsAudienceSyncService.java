package com.messaging.ads.meta;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.enums.AdConnectionStatus;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.meta.dto.MetaAudienceSyncResponse;
import com.messaging.ads.repository.AdAccountRepository;
import com.messaging.ads.repository.AdProviderConnectionRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
@Service @RequiredArgsConstructor
public class MetaAdsAudienceSyncService {
  private final AdAccountRepository accountRepository;
  private final AdProviderConnectionRepository connectionRepository;
  private final MetaAdsTokenService tokenService;
  private final MetaAdsApiClient apiClient;
  private final MetaAdsAudiencePersistenceService persistenceService;
  public MetaAudienceSyncResponse sync(Long companyId, Long accountId){
    AdAccount account=requireAccount(companyId,accountId);
    String token=token(companyId,account);
    return persistenceService.persist(companyId,accountId,apiClient.customAudiences(token,account.getExternalAccountId()));
  }
  private AdAccount requireAccount(Long companyId,Long accountId){
    AdAccount account=accountRepository.findByIdAndCompanyId(accountId,companyId).orElseThrow(()->new NotFoundException("Ad account not found"));
    if(account.getProvider()!=AdProvider.META_ADS||!account.isManaged()||!account.isAccessible()) throw new BadRequestException("Meta ad account is not enabled for management");
    return account;
  }
  private String token(Long companyId,AdAccount account){
    var connection=connectionRepository.findByIdAndCompanyId(account.getConnectionId(),companyId).orElseThrow(()->new NotFoundException("Meta Ads connection not found"));
    if(connection.getStatus()!=AdConnectionStatus.ACTIVE) throw new BadRequestException("Meta Ads connection is not active");
    return tokenService.read(connection.getId()).token().accessToken();
  }
}
