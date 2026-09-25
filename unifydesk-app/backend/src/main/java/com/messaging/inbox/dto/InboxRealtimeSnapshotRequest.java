package com.messaging.inbox.dto;

import com.messaging.channel.email.EmailFolder;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record InboxRealtimeSnapshotRequest(
    @NotBlank String connectionId,
    EmailFolder folder,
    String detailConversationId,
    @Size(max = 100) List<@NotBlank String> conversationIds,
    @Size(max = 100) List<@NotBlank String> messageIds) {}
