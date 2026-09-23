package com.messaging.channel.gmail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.messaging.channel.enums.ChannelConnectionStatus;
import com.messaging.channel.enums.ChannelType;
import com.messaging.channel.enums.ProviderType;
import com.messaging.channel.enums.SenderIdentityStatus;
import com.messaging.channel.gmail.GmailApiClient.GmailProfile;
import com.messaging.channel.gmail.GmailApiClient.TokenResponse;
import com.messaging.channel.gmail.GmailOAuthStateService.OAuthState;
import com.messaging.channel.gmail.dto.GmailAuthorizationResponse;
import com.messaging.channel.gmail.dto.GmailConnectionResponse;
import com.messaging.channel.repository.ChannelConnectionRepository;
import com.messaging.channel.service.ChannelConnectionLifecycleService;
import com.messaging.channel.service.ChannelEntitlementService;
import com.messaging.common.exception.BadRequestException;
import com.messaging.company.iam.CompanyPermissionKeys;
import com.messaging.company.service.CompanyAuthorizationService;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GmailConnectionServiceTests {

  private static final Long COMPANY_ID = 101L;
  private static final Long USER_ID = 202L;

  @Mock private GmailOAuthStateService stateService;
  @Mock private GmailApiClient gmailApiClient;
  @Mock private GmailPersistenceService persistenceService;
  @Mock private GmailWatchService watchService;
  @Mock private ChannelConnectionRepository connectionRepository;
  @Mock private ChannelEntitlementService entitlementService;
  @Mock private CompanyAuthorizationService authorizationService;
  @Mock private ChannelConnectionLifecycleService lifecycleService;

  private GmailConnectionService service;

  @BeforeEach
  void setUp() {
    GmailOAuthProperties properties = new GmailOAuthProperties();
    properties.setEnabled(true);
    properties.setClientId("client-id");
    properties.setClientSecret("client-secret");
    properties.setRedirectUri("https://api.example.com/api/v1/oauth/gmail/callback");
    properties.setFrontendBaseUrl("https://app.example.com");

    service =
        new GmailConnectionService(
            properties,
            stateService,
            gmailApiClient,
            persistenceService,
            watchService,
            connectionRepository,
            entitlementService,
            authorizationService,
            lifecycleService);
  }

  @Test
  void createsAuthorizationUsingOneTimeStateAfterEntitlementCheck() {
    when(stateService.create(COMPANY_ID, USER_ID)).thenReturn("oauth-state");
    when(gmailApiClient.authorizationUrl("oauth-state"))
        .thenReturn("https://accounts.google.com/o/oauth2/v2/auth?state=oauth-state");

    GmailAuthorizationResponse response = service.authorization(COMPANY_ID, USER_ID);

    assertThat(response.authorizationUrl()).contains("accounts.google.com");
    verify(entitlementService).assertCanCreateConnection(COMPANY_ID, ChannelType.EMAIL);
    verify(stateService).create(COMPANY_ID, USER_ID);
  }

  @Test
  void completesOAuthOnlyAfterPermissionAndGoogleIdentityValidation() {
    OAuthState oauthState = new OAuthState(COMPANY_ID, USER_ID, Instant.now());
    TokenResponse token =
        new TokenResponse(
            "access-token",
            "refresh-token",
            3600L,
            "Bearer",
            "https://www.googleapis.com/auth/gmail.modify");
    GmailProfile profile = new GmailProfile("owner@example.com", 120L, 40L, "history-1");
    GmailConnectionResponse expected =
        new GmailConnectionResponse(
            "303",
            "owner@example.com",
            ChannelConnectionStatus.ACTIVE,
            new GmailConnectionResponse.Sender(
                "sender-1", "owner@example.com", SenderIdentityStatus.ACTIVE, true));

    when(stateService.consume("oauth-state")).thenReturn(oauthState);
    when(gmailApiClient.exchangeCode("code")).thenReturn(token);
    when(gmailApiClient.profile("access-token")).thenReturn(profile);
    when(connectionRepository.findByCompanyIdAndProviderTypeAndExternalAccountId(
            COMPANY_ID, ProviderType.GMAIL, "owner@example.com"))
        .thenReturn(Optional.empty());
    when(persistenceService.persist(COMPANY_ID, USER_ID, profile, token)).thenReturn(expected);

    GmailConnectionResponse response = service.complete("oauth-state", "code");

    assertThat(response).isSameAs(expected);
    verify(authorizationService)
        .requirePermission(USER_ID, COMPANY_ID, CompanyPermissionKeys.EMAIL_CONNECT);
    verify(entitlementService).assertCanCreateConnection(COMPANY_ID, ChannelType.EMAIL);
    verify(persistenceService).persist(COMPANY_ID, USER_ID, profile, token);
  }

  @Test
  void rejectsOAuthCompletionWhenGoogleDoesNotReturnRefreshToken() {
    OAuthState oauthState = new OAuthState(COMPANY_ID, USER_ID, Instant.now());
    TokenResponse token =
        new TokenResponse(
            "access-token", null, 3600L, "Bearer", "https://www.googleapis.com/auth/gmail.modify");

    when(stateService.consume("oauth-state")).thenReturn(oauthState);
    when(gmailApiClient.exchangeCode("code")).thenReturn(token);

    assertThatThrownBy(() -> service.complete("oauth-state", "code"))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("refresh token");

    verify(authorizationService)
        .requirePermission(USER_ID, COMPANY_ID, CompanyPermissionKeys.EMAIL_CONNECT);
    verify(gmailApiClient, never()).profile("access-token");
    verify(persistenceService, never())
        .persist(
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
  }
}
