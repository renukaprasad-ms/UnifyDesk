package com.messaging.ads.google;

import com.messaging.ads.google.GoogleAdsApiClient.TokenResponse;
import com.messaging.ads.google.GoogleAdsOAuthStateService.OAuthState;
import com.messaging.ads.google.dto.GoogleAdsAuthorizationResponse;
import com.messaging.ads.google.dto.GoogleAdsConnectionResponse;
import com.messaging.common.exception.BadRequestException;
import com.messaging.company.iam.CompanyPermissionKeys;
import com.messaging.company.service.CompanyAuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GoogleAdsConnectionService {

  private final GoogleAdsProperties properties;
  private final GoogleAdsOAuthStateService stateService;
  private final GoogleAdsApiClient apiClient;
  private final GoogleAdsPersistenceService persistenceService;
  private final CompanyAuthorizationService authorizationService;

  public GoogleAdsAuthorizationResponse authorization(
      Long companyId, Long userId, String returnTo) {
    requireConfigured();
    String state = stateService.create(companyId, userId, returnTo);
    return new GoogleAdsAuthorizationResponse(apiClient.authorizationUrl(state));
  }

  public Completion complete(String state, String code) {
    requireConfigured();
    if (code == null || code.isBlank()) {
      throw new BadRequestException("Google authorization code is missing");
    }

    OAuthState oauthState = stateService.consume(state);
    authorizationService.requirePermission(
        oauthState.userId(), oauthState.companyId(), CompanyPermissionKeys.ADS_CONNECT);

    TokenResponse token = apiClient.exchangeCode(code);
    if (token.accessToken() == null || token.accessToken().isBlank()) {
      throw new BadRequestException("Google did not return an access token");
    }

    var profile = apiClient.profile(token.accessToken());
    var accounts = apiClient.discoverAccounts(token.accessToken());

    GoogleAdsConnectionResponse response =
        persistenceService.persist(
            oauthState.companyId(),
            oauthState.userId(),
            profile,
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
          "Google Ads is not configured. Configure the Google OAuth client first");
    }
  }

  public record Completion(GoogleAdsConnectionResponse connection, String returnTo) {}
}
