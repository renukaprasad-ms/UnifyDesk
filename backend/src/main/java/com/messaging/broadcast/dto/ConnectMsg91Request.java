package com.messaging.broadcast.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ConnectMsg91Request(
    @NotBlank @Size(max = 160) String name,
    @NotBlank @Size(max = 512) String authKey,
    @NotBlank @Size(max = 255) String flowId,
    @NotBlank @Size(max = 32) String senderId,
    @Pattern(regexp = "[A-Za-z0-9_]+") @Size(max = 64) String messageVariable,
    @Size(max = 32) String route) {}
