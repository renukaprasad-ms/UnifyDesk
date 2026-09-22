package com.messaging.broadcast.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ConnectMsg91EmailRequest(
    @NotBlank @Size(max = 160) String name,
    @NotBlank @Size(max = 512) String authKey,
    @NotBlank @Size(max = 255) String domain,
    @NotBlank @Size(max = 255) String templateId,
    @NotBlank @Email @Size(max = 320) String fromAddress,
    @Size(max = 255) String displayName,
    @Email @Size(max = 320) String replyToAddress,
    @Pattern(regexp = "[A-Za-z0-9_]+") @Size(max = 64) String messageVariable,
    @Pattern(regexp = "[A-Za-z0-9_]+") @Size(max = 64) String subjectVariable) {}
