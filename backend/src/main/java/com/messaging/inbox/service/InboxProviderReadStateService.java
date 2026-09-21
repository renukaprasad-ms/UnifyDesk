package com.messaging.inbox.service;

import com.messaging.channel.email.EmailFolder;
import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.enums.ProviderType;
import com.messaging.channel.gmail.GmailApiClient;
import com.messaging.channel.gmail.GmailDistributedQuotaManager;
import com.messaging.channel.gmail.GmailQuotaOperation;
import com.messaging.channel.gmail.GmailQuotaReservation;
import com.messaging.channel.gmail.GmailQuotaTrafficClass;
import com.messaging.channel.gmail.GmailRateLimitException;
import com.messaging.channel.gmail.GmailTokenService;
import com.messaging.channel.imapsmtp.ImapSmtpConnectionService;
import com.messaging.channel.imapsmtp.ImapSmtpMailClient;
import com.messaging.channel.microsoft.MicrosoftApiClient;
import com.messaging.channel.microsoft.MicrosoftTokenService;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.ServiceUnavailableException;
import com.messaging.inbox.entity.InboxMessage;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InboxProviderReadStateService {

  private final GmailTokenService gmailTokenService;
  private final GmailApiClient gmailApiClient;
  private final GmailDistributedQuotaManager gmailQuotaManager;
  private final MicrosoftTokenService microsoftTokenService;
  private final MicrosoftApiClient microsoftApiClient;
  private final ImapSmtpConnectionService imapConnectionService;
  private final ImapSmtpMailClient imapMailClient;

  public void update(
      ChannelConnection connection,
      List<InboxMessage> messages,
      boolean read,
      boolean background) {
    if (messages == null || messages.isEmpty()) return;

    ProviderType providerType = connection.getProviderType();
    switch (providerType) {
      case GMAIL -> updateGmail(connection, messages, read, background);
      case MICROSOFT -> updateMicrosoft(connection, messages, read);
      case IMAP_SMTP -> updateImap(connection, messages, read);
      default ->
          throw new BadRequestException(
              "Read state is not supported for provider " + providerType);
    }
  }

  private void updateGmail(
      ChannelConnection connection,
      List<InboxMessage> messages,
      boolean read,
      boolean background) {
    List<String> ids =
        messages.stream()
            .map(InboxMessage::getExternalMessageId)
            .filter(this::hasText)
            .distinct()
            .toList();
    if (ids.isEmpty()) return;

    String account = connection.getExternalAccountId();
    GmailQuotaReservation reservation =
        gmailQuotaManager.reserve(
            account,
            GmailQuotaOperation.MODIFY_MESSAGE,
            ids.size(),
            background ? GmailQuotaTrafficClass.BACKGROUND : GmailQuotaTrafficClass.INTERACTIVE);
    if (!reservation.allowed()) {
      throw new ServiceUnavailableException(
          "Gmail is temporarily busy. Read-state changes will be retried shortly.");
    }

    String token = gmailTokenService.accessToken(connection);
    try {
      gmailApiClient.updateReadState(token, ids, read);
    } catch (GmailRateLimitException exception) {
      gmailQuotaManager.penalize(account, 1, exception.retryAfter());
      throw new ServiceUnavailableException(
          "Gmail is temporarily busy. Read-state changes will be retried shortly.");
    }
  }

  private void updateMicrosoft(
      ChannelConnection connection, List<InboxMessage> messages, boolean read) {
    List<String> ids =
        messages.stream()
            .map(InboxMessage::getExternalMessageId)
            .filter(this::hasText)
            .distinct()
            .toList();
    if (ids.isEmpty()) return;

    microsoftApiClient.updateReadState(
        microsoftTokenService.accessToken(connection), ids, read);
  }

  private void updateImap(
      ChannelConnection connection, List<InboxMessage> messages, boolean read) {
    var credentials = imapConnectionService.credentials(connection);
    for (EmailFolder folder : List.of(EmailFolder.INBOX, EmailFolder.SENT)) {
      List<String> ids =
          messages.stream()
              .filter(
                  message ->
                      folder.providerKey().equalsIgnoreCase(message.getProviderFolderKey()))
              .map(InboxMessage::getExternalMessageId)
              .filter(this::hasText)
              .distinct()
              .toList();
      if (!ids.isEmpty()) {
        imapMailClient.updateReadState(credentials, folder, ids, read);
      }
    }
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
