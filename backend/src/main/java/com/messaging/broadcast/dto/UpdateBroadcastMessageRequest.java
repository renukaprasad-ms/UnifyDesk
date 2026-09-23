package com.messaging.broadcast.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateBroadcastMessageRequest(
    Long templateId,
    @NotNull Long senderId,
    @Size(max = 998) String subject,
    @Size(max = 500000) String bodyText,
    @Size(max = 900000) String bodyHtml) {}
