package com.messaging.inbox.dto;

import java.util.List;

public record InboxConversationDetailResponse(
    InboxConversationResponse conversation, List<InboxMessageResponse> messages) {}
