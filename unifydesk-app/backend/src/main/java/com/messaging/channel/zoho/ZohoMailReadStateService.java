package com.messaging.channel.zoho;

import com.messaging.channel.email.EmailReadStateMessage;
import com.messaging.channel.entity.ChannelConnection;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ZohoMailReadStateService {

  private final ZohoMailTokenService tokenService;
  private final ZohoMailApiClient apiClient;

  public void update(
      ChannelConnection connection, List<EmailReadStateMessage> messages, boolean read) {
    if (messages == null || messages.isEmpty()) return;

    List<String> messageIds =
        messages.stream()
            .filter(Objects::nonNull)
            .map(EmailReadStateMessage::externalMessageId)
            .filter(value -> value != null && !value.isBlank())
            .distinct()
            .toList();
    if (messageIds.isEmpty()) return;

    ZohoMailStoredToken session = tokenService.session(connection);
    apiClient.updateReadState(
        session.accessToken(),
        session.mailBaseUrl(),
        session.accountId(),
        messageIds,
        read);
  }
}
