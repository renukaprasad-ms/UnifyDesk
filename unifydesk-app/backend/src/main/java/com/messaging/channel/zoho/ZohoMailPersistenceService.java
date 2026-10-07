package com.messaging.channel.zoho;

import com.messaging.channel.credential.ProviderCredentialService;
import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.entity.SenderIdentity;
import com.messaging.channel.enums.ChannelConnectionStatus;
import com.messaging.channel.enums.ChannelType;
import com.messaging.channel.enums.CredentialType;
import com.messaging.channel.enums.ProviderType;
import com.messaging.channel.enums.SenderIdentityStatus;
import com.messaging.channel.enums.SenderIdentityType;
import com.messaging.channel.repository.ChannelConnectionRepository;
import com.messaging.channel.repository.SenderIdentityRepository;
import com.messaging.channel.zoho.ZohoMailApiClient.TokenResponse;
import com.messaging.channel.zoho.ZohoMailApiClient.ZohoAccount;
import com.messaging.channel.zoho.dto.ZohoMailConnectionResponse;
import com.messaging.inbox.entity.ChannelSyncState;
import com.messaging.inbox.repository.ChannelSyncStateRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ZohoMailPersistenceService {

  private final ChannelConnectionRepository connectionRepository;
  private final SenderIdentityRepository senderIdentityRepository;
  private final ChannelSyncStateRepository syncStateRepository;
  private final ProviderCredentialService credentialService;
  private final ZohoMailTokenService tokenService;
  private final ZohoMailOAuthProperties properties;

  @Transactional
  public ZohoMailConnectionResponse persist(
      Long companyId,
      Long userId,
      ZohoAccount account,
      TokenResponse token,
      String accountsServer,
      String mailBaseUrl,
      String inboxFolderId,
      String sentFolderId) {

    ChannelConnection connection =
        connectionRepository
            .findByCompanyIdAndProviderTypeAndExternalAccountId(
                companyId, ProviderType.ZOHO_MAIL, account.accountId())
            .orElseGet(ChannelConnection::new);

    if (connection.getId() == null) {
      connection.setCompanyId(companyId);
      connection.setChannelType(ChannelType.EMAIL);
      connection.setProviderType(ProviderType.ZOHO_MAIL);
      connection.setCreatedByUserId(userId);
    }

    String email = account.primaryEmailAddress().trim().toLowerCase();
    String displayName =
        firstNonBlank(account.displayName(), account.accountDisplayName(), email);
    connection.setName(displayName.equalsIgnoreCase(email) ? email : displayName + " · " + email);
    connection.setExternalAccountId(account.accountId());
    connection.setStatus(ChannelConnectionStatus.ACTIVE);
    connection = connectionRepository.saveAndFlush(connection);

    String refreshToken = token.refreshToken();
    if ((refreshToken == null || refreshToken.isBlank()) && connection.getId() != null) {
      try {
        refreshToken = tokenService.stored(connection).refreshToken();
      } catch (RuntimeException ignored) {
        // Initial connections must still provide offline access below.
      }
    }
    if (refreshToken == null || refreshToken.isBlank()) {
      throw new IllegalStateException(
          "Zoho did not return a refresh token. Reconnect and approve offline access");
    }

    ZohoMailStoredToken stored =
        new ZohoMailStoredToken(
            token.accessToken(),
            refreshToken,
            token.tokenType(),
            token.scope(),
            accountsServer,
            mailBaseUrl,
            account.accountId(),
            email,
            displayName,
            inboxFolderId,
            sentFolderId);

    Instant expiresAt =
        token.expiresIn() == null || token.expiresIn() <= 0
            ? null
            : Instant.now().plusSeconds(token.expiresIn());
    credentialService.upsert(
        connection, CredentialType.OAUTH_TOKEN_SET, tokenService.serialize(stored), expiresAt);

    SenderIdentity sender =
        senderIdentityRepository
            .findByConnectionIdAndIdentityTypeAndExternalId(
                connection.getId(), SenderIdentityType.EMAIL_ADDRESS, email)
            .orElseGet(SenderIdentity::new);
    if (sender.getId() == null) {
      sender.setConnection(connection);
      sender.setIdentityType(SenderIdentityType.EMAIL_ADDRESS);
      sender.setDefaultIdentity(
          senderIdentityRepository
              .findFirstByConnectionIdAndDefaultIdentityTrue(connection.getId())
              .isEmpty());
    }
    sender.setValue(email);
    sender.setExternalId(email);
    sender.setStatus(SenderIdentityStatus.ACTIVE);
    sender = senderIdentityRepository.saveAndFlush(sender);

    ChannelSyncState state =
        syncStateRepository
            .findByConnectionIdAndProviderType(connection.getId(), ProviderType.ZOHO_MAIL)
            .orElseGet(ChannelSyncState::new);
    if (state.getId() == null) {
      state.setConnection(connection);
      state.setProviderType(ProviderType.ZOHO_MAIL);
      state.setLastIncrementalSyncAt(Instant.now());
    }
    state.setIncrementalClaimedAt(null);
    state.setIncrementalAttemptCount(0);
    state.setIncrementalLastError(null);
    state.setIncrementalNextAttemptAt(Instant.now().plus(properties.getIncrementalPollInterval()));
    syncStateRepository.save(state);

    return new ZohoMailConnectionResponse(
        connection.getId().toString(),
        account.accountId(),
        email,
        displayName,
        connection.getStatus(),
        new ZohoMailConnectionResponse.Sender(
            sender.getId().toString(),
            sender.getValue(),
            sender.getStatus(),
            sender.isDefaultIdentity()));
  }

  private String firstNonBlank(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) return value.trim();
    }
    return "";
  }
}
