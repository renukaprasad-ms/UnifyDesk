package com.messaging.broadcast.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConnectMsg91EmailRequest(
    @NotBlank @Size(max = 160) String name,
    @NotBlank @Size(max = 512) String authKey,
    @NotBlank @Size(max = 255) String domain,
    @NotBlank @Email @Size(max = 320) String fromAddress,
    @Size(max = 255) String displayName,
    @Email @Size(max = 320) String replyToAddress) {}
