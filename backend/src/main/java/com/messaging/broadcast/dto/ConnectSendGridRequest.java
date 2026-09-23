package com.messaging.broadcast.dto;

import com.messaging.broadcast.sendgrid.SendGridRegion;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ConnectSendGridRequest(
    @NotBlank @Size(max = 160) String name,
    @NotNull SendGridRegion region,
    @NotBlank @Size(max = 512) String apiKey,
    @NotBlank @Email @Size(max = 320) String fromAddress,
    @Size(max = 255) String displayName,
    @Email @Size(max = 320) String replyToAddress) {}
