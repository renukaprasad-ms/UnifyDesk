package com.messaging.channel.zoho;

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
import com.messaging.channel.repository.ChannelConnectionRepository;
import com.messaging.channel.repository.SenderIdentityRepository;
import com.messaging.channel.zoho.ZohoMailApiClient.TokenResponse;
import com.messaging.channel.zoho.ZohoMailApiClient.ZohoAccount;
import com.messaging.channel.zoho.dto.ZohoMailConnectionResponse;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ZohoMailPersistenceService {

  private final ChannelConnectionRepository connectionRepository;
  private final SenderIdentityRepository senderIdentityRepository;
  private final ProviderCredentialService credentialService;
  private final ObjectMapper objectMapper;

  @Transactional
  public ZohoMailConnectionResponse persist(
      Long companyId,
      Long userId,
      ZohoAccount account,
      TokenResponse token,
      String location,
      String accountsServer,
      String mailApiBaseUrl) {
    String email = normalize(account.email());

    ChannelConnection connection =
        connectionRepository
            .findByCompanyIdAndProviderTypeAndExternalAccountId(
                companyId, ProviderType.ZOHO_MAIL, email)
            .orElseGet(ChannelConnection::new);

    if (connection.getId() == null) {
      connection.setCompanyId(companyId);
      connection.setChannelType(ChannelType.EMAIL);
      connection.setProviderType(ProviderType.ZOHO_MAIL);
      connection.setCreatedByUserId(userId);
    }

    String displayName = trim(account.resolvedDisplayName());
    connection.setName(displayName == null ? email : displayName + " · " + email);
    connection.setExternalAccountId(email);
    connection.setStatus(ChannelConnectionStatus.ACTIVE);
    // Do not backfill mailbox history automatically. Polling starts from connection time.
    connection.setLastSyncAt(Instant.now());
    connection = connectionRepository.saveAndFlush(connection);

    Instant expiresAt =
        token.expiresIn() == null || token.expiresIn() <= 0
            ? null
            : Instant.now().plusSeconds(token.expiresIn());

    ZohoMailStoredToken stored =
        new ZohoMailStoredToken(
            token.accessToken(),
            token.refreshToken(),
            token.tokenType(),
            token.scope(),
            account.accountId(),
            email,
            displayName,
            location,
            accountsServer,
            mailApiBaseUrl);

    credentialService.upsert(
        connection, CredentialType.OAUTH_TOKEN_SET, json(stored), expiresAt);

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

    return new ZohoMailConnectionResponse(
        connection.getId().toString(),
        email,
        displayName,
        location,
        connection.getStatus(),
        new ZohoMailConnectionResponse.Sender(
            sender.getId().toString(),
            sender.getValue(),
            sender.getStatus(),
            sender.isDefaultIdentity()));
  }

  private String json(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to serialize Zoho credential", exception);
    }
  }

  private String normalize(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Zoho account email is missing");
    }
    return value.trim().toLowerCase(java.util.Locale.ROOT);
  }

  private String trim(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
