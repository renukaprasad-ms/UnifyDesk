package com.messaging.channel.zoho;

import com.messaging.channel.email.EmailReadStateMessage;
import com.messaging.channel.entity.ChannelConnection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ZohoMailReadStateService {

  private final ZohoMailTokenService tokenService;
  private final ZohoMailApiClient apiClient;

  public void update(
      ChannelConnection connection, List<EmailReadStateMessage> messages, boolean read) {
    List<String> ids = uniqueMessageIds(messages);
    if (ids.isEmpty()) return;

    ZohoMailStoredToken token = tokenService.tokenSet(connection);
    apiClient.updateReadState(
        token.mailApiBaseUrl(), token.accessToken(), token.accountId(), ids, read);
  }

  private List<String> uniqueMessageIds(List<EmailReadStateMessage> messages) {
    if (messages == null || messages.isEmpty()) return List.of();

    HashSet<String> seen = new HashSet<>(Math.max(16, messages.size() * 2));
    ArrayList<String> ids = new ArrayList<>(messages.size());
    for (EmailReadStateMessage message : messages) {
      if (message == null || !hasText(message.externalMessageId())) continue;
      String value = message.externalMessageId().trim();
      if (seen.add(value)) ids.add(value);
    }
    return ids;
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
