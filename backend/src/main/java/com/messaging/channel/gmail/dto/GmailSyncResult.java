package com.messaging.channel.gmail.dto;

import java.time.Instant;

public record GmailSyncResult(
    String connectionId,
    int fetchedMessages,
    int createdMessages,
    int touchedConversations,
    String historyId,
    Instant syncedAt) {}
