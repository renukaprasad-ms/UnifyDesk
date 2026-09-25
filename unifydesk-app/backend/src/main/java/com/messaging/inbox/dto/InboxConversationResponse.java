package com.messaging.inbox.dto;

import com.messaging.channel.enums.ProviderType;
import com.messaging.inbox.enums.ConversationStatus;
import java.time.Instant;
import java.util.List;

public record InboxConversationResponse(
    String id,
    String connectionId,
    String externalConversationId,
    ProviderType providerType,
    String subject,
    String lastMessagePreview,
    Instant lastMessageAt,
    int unreadCount,
    ConversationStatus status,
    List<InboxParticipantResponse> participants) {}
