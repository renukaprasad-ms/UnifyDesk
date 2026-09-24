package com.messaging.ads.google;

import com.messaging.ads.google.GoogleAdsApiClient.TokenResponse;
import com.messaging.ads.google.GoogleAdsOAuthStateService.OAuthState;
import com.messaging.ads.google.dto.GoogleAdsAuthorizationResponse;
import com.messaging.ads.google.dto.GoogleAdsConnectionResponse;
import com.messaging.common.exception.BadRequestException;
import com.messaging.company.iam.CompanyPermissionKeys;
import com.messaging.company.service.CompanyAuthorizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
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
    String stage = "configuration";
    Long companyId = null;
    Long userId = null;

    try {
      requireConfigured();

      stage = "authorization-code-validation";
      if (code == null || code.isBlank()) {
        throw new BadRequestException("Google authorization code is missing");
      }

      stage = "oauth-state-validation";
      OAuthState oauthState = stateService.consume(state);
      companyId = oauthState.companyId();
      userId = oauthState.userId();

      stage = "permission-check";
      authorizationService.requirePermission(
          userId, companyId, CompanyPermissionKeys.ADS_CONNECT);

      stage = "token-exchange";
      TokenResponse token = apiClient.exchangeCode(code);
      if (token.accessToken() == null || token.accessToken().isBlank()) {
        throw new BadRequestException("Google did not return an access token");
      }

      log.info(
          "Google Ads OAuth token exchange succeeded companyId={} userId={} refreshTokenPresent={}",
          companyId,
          userId,
          token.refreshToken() != null && !token.refreshToken().isBlank());

      stage = "profile-fetch";
      var profile = apiClient.profile(token.accessToken());

      stage = "account-discovery";
      var accounts = apiClient.discoverAccounts(token.accessToken());
      log.info(
          "Google Ads account discovery succeeded companyId={} userId={} accountCount={}",
          companyId,
          userId,
          accounts.size());

      stage = "connection-persistence";
      GoogleAdsConnectionResponse response =
          persistenceService.persist(
              companyId,
              userId,
              profile,
              accounts,
              token);

      log.info(
          "Google Ads OAuth connection completed companyId={} userId={} discoveredAccounts={}",
          companyId,
          userId,
          accounts.size());

      return new Completion(response, oauthState.returnTo());
    } catch (RuntimeException exception) {
      log.warn(
          "Google Ads OAuth connection failed stage={} companyId={} userId={} errorType={} message={}",
          stage,
          companyId,
          userId,
          exception.getClass().getSimpleName(),
          exception.getMessage(),
          exception);
      throw exception;
    }
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
