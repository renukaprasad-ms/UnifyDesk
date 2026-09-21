package com.messaging.channel.microsoft;

import com.messaging.channel.email.EmailReadStateMessage;
import com.messaging.channel.entity.ChannelConnection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MicrosoftReadStateService {

  private final MicrosoftTokenService tokenService;
  private final MicrosoftApiClient apiClient;

  public void update(
      ChannelConnection connection, List<EmailReadStateMessage> messages, boolean read) {
    List<String> ids = uniqueMessageIds(messages);
    if (ids.isEmpty()) return;
    apiClient.updateReadState(tokenService.accessToken(connection), ids, read);
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

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
