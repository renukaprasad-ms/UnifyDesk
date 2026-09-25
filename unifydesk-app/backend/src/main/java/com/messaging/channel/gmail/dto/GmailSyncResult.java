package com.messaging.channel.gmail.dto;

import java.time.Instant;
import java.util.List;

public record GmailSyncResult(
    String connectionId,
    int fetchedMessages,
    int createdMessages,
    int touchedConversations,
    String historyId,
    Instant syncedAt,
    List<String> conversationIds,
    List<String> messageIds) {

  public GmailSyncResult {
    conversationIds = conversationIds == null ? List.of() : List.copyOf(conversationIds);
    messageIds = messageIds == null ? List.of() : List.copyOf(messageIds);
  }
}
