package com.messaging.broadcast.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConnectAzureEmailRequest(
    @NotBlank @Size(max = 160) String name,
    @NotBlank @Size(max = 255) String endpoint,
    @NotBlank @Size(max = 2048) String accessKey,
    @NotBlank @Email @Size(max = 320) String fromAddress,
    @Size(max = 255) String displayName,
    @Email @Size(max = 320) String replyToAddress) {}
