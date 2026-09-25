package com.messaging.ads.meta;

import com.messaging.ads.meta.MetaAdsApiClient.TokenResponse;
import com.messaging.ads.meta.MetaAdsOAuthStateService.OAuthState;
import com.messaging.ads.meta.dto.MetaAdsAuthorizationResponse;
import com.messaging.ads.meta.dto.MetaAdsConnectionResponse;
import com.messaging.common.exception.BadRequestException;
import com.messaging.company.iam.CompanyPermissionKeys;
import com.messaging.company.service.CompanyAuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MetaAdsConnectionService {

  private final MetaAdsProperties properties;
  private final MetaAdsOAuthStateService stateService;
  private final MetaAdsApiClient apiClient;
  private final MetaAdsPersistenceService persistenceService;
  private final CompanyAuthorizationService authorizationService;

  public MetaAdsAuthorizationResponse authorization(
      Long companyId, Long userId, String returnTo) {
    requireConfigured();
    String state = stateService.create(companyId, userId, returnTo);
    return new MetaAdsAuthorizationResponse(apiClient.authorizationUrl(state));
  }

  public Completion complete(String state, String code) {
    requireConfigured();
    if (code == null || code.isBlank()) {
      throw new BadRequestException("Meta authorization code is missing");
    }

    OAuthState oauthState = stateService.consume(state);
    authorizationService.requirePermission(
        oauthState.userId(), oauthState.companyId(), CompanyPermissionKeys.ADS_CONNECT);

    TokenResponse shortLivedToken = apiClient.exchangeCode(code);
    if (shortLivedToken.accessToken() == null || shortLivedToken.accessToken().isBlank()) {
      throw new BadRequestException("Meta did not return an access token");
    }

    TokenResponse token = apiClient.exchangeLongLivedToken(shortLivedToken.accessToken());
    if (token.accessToken() == null || token.accessToken().isBlank()) {
      throw new BadRequestException("Meta did not return a long-lived access token");
    }

    var profile = apiClient.profile(token.accessToken());
    if (profile.id() == null || profile.id().isBlank()) {
      throw new BadRequestException("Meta did not return the authorized user ID");
    }

    var businesses = apiClient.businesses(token.accessToken());
    var accounts = apiClient.adAccounts(token.accessToken());

    MetaAdsConnectionResponse response =
        persistenceService.persist(
            oauthState.companyId(),
            oauthState.userId(),
            profile,
            businesses,
            accounts,
            token);

    return new Completion(response, oauthState.returnTo());
  }

  public String consumeCancelledState(String state) {
    return stateService.consume(state).returnTo();
  }

  public boolean configured() {
    return properties.configured();
  }

  private void requireConfigured() {
    if (!properties.configured()) {
      throw new BadRequestException(
          "Meta Ads is not configured. Configure the Meta Marketing API app first");
    }
  }

  public record Completion(MetaAdsConnectionResponse connection, String returnTo) {}
}
