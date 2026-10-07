package com.messaging.channel.zoho;

import com.messaging.channel.enums.ChannelType;
import com.messaging.channel.enums.ProviderType;
import com.messaging.channel.repository.ChannelConnectionRepository;
import com.messaging.channel.service.ChannelConnectionLifecycleService;
import com.messaging.channel.service.ChannelEntitlementService;
import com.messaging.channel.zoho.ZohoMailApiClient.TokenResponse;
import com.messaging.channel.zoho.ZohoMailApiClient.ZohoAccount;
import com.messaging.channel.zoho.ZohoMailApiClient.ZohoFolder;
import com.messaging.channel.zoho.dto.ZohoMailAuthorizationResponse;
import com.messaging.channel.zoho.dto.ZohoMailConnectionResponse;
import com.messaging.common.exception.BadRequestException;
import com.messaging.company.iam.CompanyPermissionKeys;
import com.messaging.company.service.CompanyAuthorizationService;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ZohoMailConnectionService {

  private final ZohoMailOAuthProperties properties;
  private final ZohoMailOAuthStateService stateService;
  private final ZohoMailApiClient apiClient;
  private final ZohoMailPersistenceService persistenceService;
  private final ChannelConnectionRepository connectionRepository;
  private final ChannelEntitlementService entitlementService;
  private final CompanyAuthorizationService authorizationService;
  private final ChannelConnectionLifecycleService lifecycleService;

  public ZohoMailAuthorizationResponse authorization(
      Long companyId, Long userId, String returnTo) {
    requireConfigured();
    entitlementService.assertCanCreateConnection(companyId, ChannelType.EMAIL);
    String state = stateService.create(companyId, userId, returnTo);
    return new ZohoMailAuthorizationResponse(apiClient.authorizationUrl(state));
  }

  public ZohoMailCompletion completeWithReturn(
      String state, String code, String callbackAccountsServer) {
    requireConfigured();
    if (code == null || code.isBlank()) {
      throw new BadRequestException("Zoho authorization code is missing");
    }

    ZohoMailOAuthStateService.OAuthState oauthState = stateService.consume(state);
    authorizationService.requirePermission(
        oauthState.userId(), oauthState.companyId(), CompanyPermissionKeys.EMAIL_CONNECT);

    String accountsServer = ZohoMailDataCenter.trustedAccountsServer(callbackAccountsServer);
    String mailBaseUrl = ZohoMailDataCenter.mailBaseUrl(accountsServer);
    TokenResponse token = apiClient.exchangeCode(code, accountsServer);

    if (token.accessToken() == null || token.accessToken().isBlank()) {
      throw new BadRequestException("Zoho did not return an access token");
    }

    List<ZohoAccount> accounts = apiClient.accounts(token.accessToken(), mailBaseUrl);
    ZohoAccount account =
        accounts.stream()
            .filter(this::usableAccount)
            .min(
                Comparator.comparing(
                    item -> item.sequence() == null ? Integer.MAX_VALUE : item.sequence()))
            .orElseThrow(
                () ->
                    new BadRequestException(
                        "Zoho did not return an enabled Mail account for this user"));

    List<ZohoFolder> folders =
        apiClient.folders(token.accessToken(), mailBaseUrl, account.accountId());
    String inboxFolderId = requireFolder(folders, "Inbox");
    String sentFolderId = requireFolder(folders, "Sent");

    boolean existing =
        connectionRepository
            .findByCompanyIdAndProviderTypeAndExternalAccountId(
                oauthState.companyId(), ProviderType.ZOHO_MAIL, account.accountId())
            .isPresent();
    if (!existing) {
      entitlementService.assertCanCreateConnection(oauthState.companyId(), ChannelType.EMAIL);
    }

    ZohoMailConnectionResponse response =
        persistenceService.persist(
            oauthState.companyId(),
            oauthState.userId(),
            account,
            token,
            accountsServer,
            mailBaseUrl,
            inboxFolderId,
            sentFolderId);

    lifecycleService.cancelPendingPurge(
        oauthState.companyId(), Long.valueOf(response.connectionId()));

    return new ZohoMailCompletion(response, oauthState.returnTo());
  }

  public String consumeCancelledStateWithReturn(String state) {
    return stateService.consume(state).returnTo();
  }

  public boolean configured() {
    return properties.configured();
  }

  private boolean usableAccount(ZohoAccount account) {
    if (account == null
        || account.accountId() == null
        || account.accountId().isBlank()
        || account.primaryEmailAddress() == null
        || account.primaryEmailAddress().isBlank()) {
      return false;
    }
    if (Boolean.FALSE.equals(account.enabled())) return false;
    return !Boolean.FALSE.equals(account.status());
  }

  private String requireFolder(List<ZohoFolder> folders, String type) {
    return folders.stream()
        .filter(folder -> folder != null && folder.folderId() != null)
        .filter(
            folder ->
                type.equalsIgnoreCase(folder.folderType())
                    || type.equalsIgnoreCase(folder.folderName()))
        .map(ZohoFolder::folderId)
        .findFirst()
        .orElseThrow(
            () -> new BadRequestException("Zoho Mail " + type + " folder was not found"));
  }

  private void requireConfigured() {
    if (!properties.configured()) {
      throw new BadRequestException(
          "Zoho Mail is not configured. Configure Zoho OAuth before connecting an account");
    }
  }

  public record ZohoMailCompletion(
      ZohoMailConnectionResponse connection, String returnTo) {}
}
