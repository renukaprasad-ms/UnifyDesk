package com.messaging.channel.gmail;

import com.messaging.channel.email.EmailReadStateMessage;
import com.messaging.channel.entity.ChannelConnection;
import com.messaging.common.exception.ServiceUnavailableException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GmailReadStateService {

  private static final int BATCH_SIZE = 250;

  private final GmailTokenService tokenService;
  private final GmailApiClient gmailApiClient;
  private final GmailDistributedQuotaManager quotaManager;

  public void update(
      ChannelConnection connection,
      List<EmailReadStateMessage> messages,
      boolean read,
      boolean background) {
    List<String> ids = uniqueMessageIds(messages);
    if (ids.isEmpty()) return;

    String account = connection.getExternalAccountId();
    String token = tokenService.accessToken(connection);
    GmailQuotaTrafficClass trafficClass =
        background ? GmailQuotaTrafficClass.BACKGROUND : GmailQuotaTrafficClass.INTERACTIVE;

    for (int offset = 0; offset < ids.size(); offset += BATCH_SIZE) {
      int end = Math.min(ids.size(), offset + BATCH_SIZE);
      List<String> batch = ids.subList(offset, end);
      GmailQuotaReservation reservation =
          quotaManager.reserve(
              account, GmailQuotaOperation.MODIFY_MESSAGE, batch.size(), trafficClass);
      if (!reservation.allowed()) {
        throw unavailable();
      }

      try {
        gmailApiClient.updateReadState(token, batch, read);
      } catch (GmailRateLimitException exception) {
        quotaManager.penalize(account, 1, exception.retryAfter());
        throw unavailable();
      }
    }
  }

  private List<String> uniqueMessageIds(List<EmailReadStateMessage> messages) {
    if (messages == null || messages.isEmpty()) return List.of();

    HashSet<String> seen = new HashSet<>(Math.max(16, messages.size() * 2));
    ArrayList<String> ids = new ArrayList<>(messages.size());
    for (EmailReadStateMessage message : messages) {
      if (message == null || !hasText(message.externalMessageId())) continue;
      if (seen.add(message.externalMessageId())) ids.add(message.externalMessageId());
    }
    return ids;
  }

  private ServiceUnavailableException unavailable() {
    return new ServiceUnavailableException(
        "Gmail is temporarily busy. Read-state changes will be retried shortly.");
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
