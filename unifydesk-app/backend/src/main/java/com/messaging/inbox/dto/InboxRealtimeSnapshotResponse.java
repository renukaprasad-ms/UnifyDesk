package com.messaging.inbox.dto;

import java.util.List;

public record InboxRealtimeSnapshotResponse(
    List<InboxConversationResponse> conversations,
    List<InboxMessageResponse> messages,
    List<String> missingConversationIds,
    List<String> missingMessageIds) {}
