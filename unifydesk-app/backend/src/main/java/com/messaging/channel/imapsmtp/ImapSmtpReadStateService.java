package com.messaging.channel.imapsmtp;

import com.messaging.channel.email.EmailFolder;
import com.messaging.channel.email.EmailReadStateMessage;
import com.messaging.channel.entity.ChannelConnection;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ImapSmtpReadStateService {

  private final ImapSmtpConnectionService connectionService;
  private final ImapSmtpMailClient mailClient;

  public void update(
      ChannelConnection connection, List<EmailReadStateMessage> messages, boolean read) {
    if (messages == null || messages.isEmpty()) return;

    Map<EmailFolder, Set<String>> idsByFolder = new EnumMap<>(EmailFolder.class);
    idsByFolder.put(EmailFolder.INBOX, new LinkedHashSet<>());
    idsByFolder.put(EmailFolder.SENT, new LinkedHashSet<>());

    for (EmailReadStateMessage message : messages) {
      if (message == null || !hasText(message.externalMessageId())) continue;

      if (EmailFolder.INBOX.providerKey().equalsIgnoreCase(message.providerFolderKey())) {
        idsByFolder.get(EmailFolder.INBOX).add(message.externalMessageId());
      } else if (EmailFolder.SENT.providerKey().equalsIgnoreCase(message.providerFolderKey())) {
        idsByFolder.get(EmailFolder.SENT).add(message.externalMessageId());
      }
    }

    ImapSmtpStoredCredentials credentials = connectionService.credentials(connection);
    for (Map.Entry<EmailFolder, Set<String>> entry : idsByFolder.entrySet()) {
      if (!entry.getValue().isEmpty()) {
        mailClient.updateReadState(credentials, entry.getKey(), List.copyOf(entry.getValue()), read);
      }
    }
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
