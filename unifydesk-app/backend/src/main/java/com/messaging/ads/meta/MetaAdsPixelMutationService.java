package com.messaging.ads.meta;

import com.messaging.ads.dto.AdAccountResourceResponse;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.enums.AdConnectionStatus;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.meta.dto.CreateMetaPixelRequest;
import com.messaging.ads.repository.AdAccountRepository;
import com.messaging.ads.repository.AdProviderConnectionRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MetaAdsPixelMutationService {

  private final AdAccountRepository accountRepository;
  private final AdProviderConnectionRepository connectionRepository;
  private final MetaAdsTokenService tokenService;
  private final MetaAdsApiClient apiClient;
  private final MetaAdsResourceSyncService resourceSyncService;

  public AdAccountResourceResponse create(
      Long companyId, Long accountId, CreateMetaPixelRequest request) {
    AdAccount account =
        accountRepository
            .findByIdAndCompanyId(accountId, companyId)
            .orElseThrow(() -> new NotFoundException("Ad account not found"));
    if (account.getProvider() != AdProvider.META_ADS
        || !account.isManaged()
        || !account.isAccessible()) {
      throw new BadRequestException("Meta ad account is not enabled for management");
    }

    var connection =
        connectionRepository
            .findByIdAndCompanyId(account.getConnectionId(), companyId)
            .orElseThrow(() -> new NotFoundException("Meta Ads connection not found"));
    if (connection.getStatus() != AdConnectionStatus.ACTIVE) {
      throw new BadRequestException("Meta Ads connection is not active");
    }

    String token = tokenService.read(connection.getId()).token().accessToken();
    String pixelId =
        apiClient.createPixel(token, account.getExternalAccountId(), request.name().trim());

    return resourceSyncService.sync(companyId, accountId).resources().stream()
        .filter(resource -> resource.externalResourceId().equals(pixelId))
        .findFirst()
        .orElseThrow(
            () ->
                new BadRequestException(
                    "Meta created the pixel but it was not visible during resource synchronization"));
  }
}
