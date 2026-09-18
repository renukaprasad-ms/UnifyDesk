package com.messaging.channel.gmail;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.channel.credential.ProviderCredentialService;
import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.entity.SenderIdentity;
import com.messaging.channel.enums.ChannelConnectionStatus;
import com.messaging.channel.enums.ChannelType;
import com.messaging.channel.enums.CredentialType;
import com.messaging.channel.enums.ProviderType;
import com.messaging.channel.enums.SenderIdentityStatus;
import com.messaging.channel.enums.SenderIdentityType;
import com.messaging.channel.gmail.GmailApiClient.GmailProfile;
import com.messaging.channel.gmail.GmailApiClient.TokenResponse;
import com.messaging.channel.gmail.dto.GmailConnectionResponse;
import com.messaging.channel.repository.ChannelConnectionRepository;
import com.messaging.channel.repository.SenderIdentityRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GmailPersistenceService {

  private final ChannelConnectionRepository connectionRepository;
  private final SenderIdentityRepository senderIdentityRepository;
  private final ProviderCredentialService credentialService;
  private final ObjectMapper objectMapper;

  @Transactional
  public GmailConnectionResponse persist(
      Long companyId, Long userId, GmailProfile profile, TokenResponse token) {
    ChannelConnection connection =
        connectionRepository
            .findByCompanyIdAndProviderTypeAndExternalAccountId(
                companyId, ProviderType.GMAIL, profile.emailAddress())
            .orElseGet(ChannelConnection::new);

    if (connection.getId() == null) {
      connection.setCompanyId(companyId);
      connection.setChannelType(ChannelType.EMAIL);
      connection.setProviderType(ProviderType.GMAIL);
      connection.setCreatedByUserId(userId);
    }

    connection.setName(profile.emailAddress());
    connection.setExternalAccountId(profile.emailAddress());
    connection.setStatus(ChannelConnectionStatus.ACTIVE);
    connection.setLastSyncAt(Instant.now());
    connection = connectionRepository.saveAndFlush(connection);

    Instant expiresAt =
        token.expiresIn() == null || token.expiresIn() <= 0
            ? null
            : Instant.now().plusSeconds(token.expiresIn());

    credentialService.upsert(
        connection,
        CredentialType.OAUTH_TOKEN_SET,
        json(
            new StoredGmailToken(
                token.accessToken(),
                token.refreshToken(),
                token.tokenType(),
                token.scope(),
                profile.emailAddress())),
        expiresAt);

    SenderIdentity sender =
        senderIdentityRepository
            .findByConnectionIdAndIdentityTypeAndExternalId(
                connection.getId(), SenderIdentityType.EMAIL_ADDRESS, profile.emailAddress())
            .orElseGet(SenderIdentity::new);

    if (sender.getId() == null) {
      sender.setConnection(connection);
      sender.setIdentityType(SenderIdentityType.EMAIL_ADDRESS);
      sender.setDefaultIdentity(
          senderIdentityRepository
              .findFirstByConnectionIdAndDefaultIdentityTrue(connection.getId())
              .isEmpty());
    }

    sender.setValue(profile.emailAddress());
    sender.setExternalId(profile.emailAddress());
    sender.setStatus(SenderIdentityStatus.ACTIVE);
    sender = senderIdentityRepository.saveAndFlush(sender);

    return new GmailConnectionResponse(
        connection.getId().toString(),
        profile.emailAddress(),
        connection.getStatus(),
        new GmailConnectionResponse.Sender(
            sender.getId().toString(),
            sender.getValue(),
            sender.getStatus(),
            sender.isDefaultIdentity()));
  }

  private String json(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to serialize Gmail credential", exception);
    }
  }

  public record StoredGmailToken(
      String accessToken,
      String refreshToken,
      String tokenType,
      String scope,
      String emailAddress) {}
}
