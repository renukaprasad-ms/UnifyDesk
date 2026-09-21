package com.messaging.channel.imapsmtp;

import com.messaging.channel.credential.ProviderCredentialService;
import com.messaging.channel.email.EmailFolder;
import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.enums.ChannelConnectionStatus;
import com.messaging.channel.enums.CredentialType;
import com.messaging.channel.enums.ProviderType;
import com.messaging.channel.imapsmtp.ImapSmtpCursorState.FolderCursor;
import com.messaging.channel.imapsmtp.ImapSmtpMailClient.TestedMailbox;
import com.messaging.channel.imapsmtp.dto.ImapSmtpConnectRequest;
import com.messaging.channel.imapsmtp.dto.ImapSmtpConnectionResponse;
import com.messaging.channel.repository.ChannelConnectionRepository;
import com.messaging.channel.service.ChannelConnectionService;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ImapSmtpConnectionService {

  private final ImapSmtpProperties properties;
  private final ImapSmtpMailClient mailClient;
  private final ImapSmtpConnectionPersistenceService persistenceService;
  private final ProviderCredentialService credentialService;
  private final ImapSmtpCredentialCodec credentialCodec;
  private final ChannelConnectionRepository connectionRepository;
  private final ChannelConnectionService channelConnectionService;

  public ImapSmtpConnectionResponse connect(
      Long companyId, Long userId, ImapSmtpConnectRequest request) {
    if (!properties.isEnabled()) {
      throw new BadRequestException("Generic IMAP/SMTP email is not enabled");
    }

    String email = request.email().trim().toLowerCase(Locale.ROOT);
    String username = request.username().trim();
    ImapSmtpStoredCredentials requested =
        new ImapSmtpStoredCredentials(
            email,
            trimToNull(request.displayName()),
            username,
            request.password(),
            request.imapHost().trim(),
            request.imapPort(),
            request.imapSecurity(),
            request.smtpHost().trim(),
            request.smtpPort(),
            request.smtpSecurity(),
            trimToNull(request.sentFolder()));

    TestedMailbox tested = mailClient.test(requested);
    ImapSmtpStoredCredentials resolved =
        new ImapSmtpStoredCredentials(
            requested.email(),
            requested.displayName(),
            requested.username(),
            requested.password(),
            requested.imapHost(),
            requested.imapPort(),
            requested.imapSecurity(),
            requested.smtpHost(),
            requested.smtpPort(),
            requested.smtpSecurity(),
            tested.sentFolder());

    var inboxCursor = mailClient.currentCursor(resolved, EmailFolder.INBOX);
    var sentCursor = mailClient.currentCursor(resolved, EmailFolder.SENT);
    ImapSmtpCursorState cursor =
        new ImapSmtpCursorState(
            new FolderCursor(inboxCursor.uidValidity(), inboxCursor.lastUid()),
            new FolderCursor(sentCursor.uidValidity(), sentCursor.lastUid()));

    ChannelConnection connection =
        persistenceService.persist(companyId, userId, resolved, cursor);
    channelConnectionService.cancelPendingPurge(companyId, connection.getId());
    return response(connection, resolved);
  }

  public ImapSmtpConnectionResponse get(Long companyId, Long connectionId) {
    ChannelConnection connection = requireConnection(companyId, connectionId);
    return response(connection, credentials(connection));
  }

  public ChannelConnection requireActive(Long companyId, Long connectionId) {
    ChannelConnection connection = requireConnection(companyId, connectionId);
    if (connection.getStatus() != ChannelConnectionStatus.ACTIVE) {
      throw new BadRequestException("IMAP/SMTP mailbox is not active");
    }
    return connection;
  }

  public ImapSmtpStoredCredentials credentials(ChannelConnection connection) {
    String payload =
        credentialService.read(connection.getId(), CredentialType.IMAP_SMTP_CREDENTIALS);
    return credentialCodec.decode(payload);
  }

  private ChannelConnection requireConnection(Long companyId, Long connectionId) {
    ChannelConnection connection =
        connectionRepository
            .findByIdAndCompanyId(connectionId, companyId)
            .orElseThrow(() -> new NotFoundException("IMAP/SMTP connection not found"));
    if (connection.getProviderType() != ProviderType.IMAP_SMTP) {
      throw new BadRequestException("Channel connection is not an IMAP/SMTP mailbox");
    }
    return connection;
  }

  private ImapSmtpConnectionResponse response(
      ChannelConnection connection, ImapSmtpStoredCredentials credentials) {
    return new ImapSmtpConnectionResponse(
        connection.getId().toString(),
        credentials.email(),
        connection.getName(),
        connection.getStatus(),
        credentials.imapHost(),
        credentials.imapPort(),
        credentials.imapSecurity(),
        credentials.smtpHost(),
        credentials.smtpPort(),
        credentials.smtpSecurity(),
        credentials.sentFolder());
  }

  private String trimToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
