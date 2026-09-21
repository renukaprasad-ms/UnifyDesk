package com.messaging.channel.imapsmtp;

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
import com.messaging.channel.service.ChannelEntitlementService;
import com.messaging.common.exception.BadRequestException;
import com.messaging.inbox.entity.ChannelSyncState;
import com.messaging.inbox.repository.ChannelSyncStateRepository;
import java.time.Instant;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ImapSmtpConnectionPersistenceService {

  private final ChannelConnectionRepository connectionRepository;
  private final SenderIdentityRepository senderIdentityRepository;
  private final ChannelSyncStateRepository syncStateRepository;
  private final ProviderCredentialService credentialService;
  private final ChannelEntitlementService entitlementService;
  private final ImapSmtpCredentialCodec credentialCodec;
  private final ImapSmtpCursorCodec cursorCodec;

  @Transactional
  public ChannelConnection persist(
      Long companyId,
      Long userId,
      ImapSmtpStoredCredentials credentials,
      ImapSmtpCursorState cursorState) {
    String email = credentials.email().trim().toLowerCase(Locale.ROOT);
    ChannelConnection connection =
        connectionRepository
            .findByCompanyIdAndProviderTypeAndExternalAccountId(
                companyId, ProviderType.IMAP_SMTP, email)
            .orElse(null);

    if (connection != null && connection.getStatus() != ChannelConnectionStatus.DISCONNECTED) {
      throw new BadRequestException("This email account is already connected to the company");
    }

    if (connection == null) {
      entitlementService.assertCanCreateConnection(companyId, ChannelType.EMAIL);
      connection = new ChannelConnection();
      connection.setCompanyId(companyId);
      connection.setChannelType(ChannelType.EMAIL);
      connection.setProviderType(ProviderType.IMAP_SMTP);
      connection.setCreatedByUserId(userId);
    }

    connection.setName(
        credentials.displayName() == null || credentials.displayName().isBlank()
            ? email
            : credentials.displayName().trim());
    connection.setExternalAccountId(email);
    connection.setStatus(ChannelConnectionStatus.ACTIVE);
    connection.setLastSyncAt(Instant.now());
    connection = connectionRepository.save(connection);

    credentialService.upsert(
        connection,
        CredentialType.IMAP_SMTP_CREDENTIALS,
        credentialCodec.encode(credentials),
        null);

    SenderIdentity sender =
        senderIdentityRepository
            .findByConnectionIdAndIdentityTypeAndValue(
                connection.getId(), SenderIdentityType.EMAIL_ADDRESS, email)
            .orElseGet(SenderIdentity::new);
    if (sender.getId() == null) {
      sender.setConnection(connection);
      sender.setIdentityType(SenderIdentityType.EMAIL_ADDRESS);
      sender.setExternalId(email);
    }
    sender.setValue(email);
    sender.setExternalId(email);
    sender.setStatus(SenderIdentityStatus.ACTIVE);
    sender.setDefaultIdentity(true);
    senderIdentityRepository.save(sender);

    ChannelSyncState syncState =
        syncStateRepository
            .findByConnectionIdAndProviderType(connection.getId(), ProviderType.IMAP_SMTP)
            .orElseGet(ChannelSyncState::new);
    if (syncState.getId() == null) {
      syncState.setConnection(connection);
      syncState.setProviderType(ProviderType.IMAP_SMTP);
      syncState.setCursorValue(cursorCodec.encode(cursorState));
    }
    syncState.setIncrementalNextAttemptAt(Instant.now());
    syncStateRepository.save(syncState);

    return connection;
  }
}
