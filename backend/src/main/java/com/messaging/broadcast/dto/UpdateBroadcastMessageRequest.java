package com.messaging.broadcast.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateBroadcastMessageRequest(
    @NotNull Long senderId,
    @Size(max = 998) String subject,
    @NotBlank @Size(max = 500000) String bodyText) {}
