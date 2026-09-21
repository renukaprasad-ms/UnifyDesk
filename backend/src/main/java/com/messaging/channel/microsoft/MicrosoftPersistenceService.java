package com.messaging.channel.microsoft;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.channel.credential.ProviderCredentialService;
import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.entity.SenderIdentity;
import com.messaging.channel.enums.*;
import com.messaging.channel.microsoft.MicrosoftApiClient.MicrosoftProfile;
import com.messaging.channel.microsoft.MicrosoftApiClient.TokenResponse;
import com.messaging.channel.microsoft.dto.MicrosoftConnectionResponse;
import com.messaging.channel.microsoft.entity.MicrosoftSyncState;
import com.messaging.channel.microsoft.enums.MicrosoftSyncStatus;
import com.messaging.channel.microsoft.repository.MicrosoftSyncStateRepository;
import com.messaging.channel.repository.ChannelConnectionRepository;
import com.messaging.channel.repository.SenderIdentityRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MicrosoftPersistenceService {

  private static final List<String> SYNC_FOLDERS = List.of("inbox", "sentitems");

  private final ChannelConnectionRepository connectionRepository;
  private final SenderIdentityRepository senderIdentityRepository;
  private final ProviderCredentialService credentialService;
  private final MicrosoftSyncStateRepository syncStateRepository;
  private final ObjectMapper objectMapper;

  @Transactional
  public MicrosoftConnectionResponse persist(
      Long companyId, Long userId, MicrosoftProfile profile, TokenResponse token) {
    String email = profile.emailAddress();
    ChannelConnection connection =
        connectionRepository
            .findByCompanyIdAndProviderTypeAndExternalAccountId(
                companyId, ProviderType.MICROSOFT, email)
            .orElseGet(ChannelConnection::new);

    if (connection.getId() == null) {
      connection.setCompanyId(companyId);
      connection.setChannelType(ChannelType.EMAIL);
      connection.setProviderType(ProviderType.MICROSOFT);
      connection.setCreatedByUserId(userId);
    }

    connection.setName(
        profile.displayName() == null || profile.displayName().isBlank()
            ? email
            : profile.displayName() + " · " + email);
    connection.setExternalAccountId(email);
    connection.setStatus(ChannelConnectionStatus.ACTIVE);
    connection = connectionRepository.saveAndFlush(connection);

    Instant expiresAt =
        token.expiresIn() == null || token.expiresIn() <= 0
            ? null
            : Instant.now().plusSeconds(token.expiresIn());

    credentialService.upsert(
        connection,
        CredentialType.OAUTH_TOKEN_SET,
        json(
            new MicrosoftStoredToken(
                token.accessToken(),
                token.refreshToken(),
                token.tokenType(),
                token.scope(),
                profile.id(),
                email)),
        expiresAt);

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

    for (String folder : SYNC_FOLDERS) {
      MicrosoftSyncState state =
          syncStateRepository
              .findByConnectionIdAndFolderKey(connection.getId(), folder)
              .orElseGet(MicrosoftSyncState::new);
      if (state.getId() == null) {
        state.setConnection(connection);
        state.setFolderKey(folder);
      }
      if (state.getDeltaUrl() == null) {
        // Connecting a mailbox must not import history automatically. Treat the
        // connection time as the live-sync baseline; webhook/safety delta sync
        // will pick up only new/recent changes after the connection is active.
        state.setStatus(MicrosoftSyncStatus.SYNCED);
        state.setNextAttemptAt(null);
        state.setLastSyncedAt(Instant.now());
      }
      syncStateRepository.save(state);
    }

    return new MicrosoftConnectionResponse(
        connection.getId().toString(),
        email,
        profile.displayName(),
        connection.getStatus(),
        new MicrosoftConnectionResponse.Sender(
            sender.getId().toString(),
            sender.getValue(),
            sender.getStatus(),
            sender.isDefaultIdentity()));
  }

  private String json(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to serialize Microsoft credential", exception);
    }
  }
}
