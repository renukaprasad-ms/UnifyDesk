package com.messaging.inbox.dto;

public record InboxReadStateResponse(
    boolean read, int updatedMessages, int updatedConversations) {}
