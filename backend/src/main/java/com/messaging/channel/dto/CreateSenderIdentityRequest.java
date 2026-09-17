package com.messaging.channel.dto;

import com.messaging.channel.enums.SenderIdentityType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateSenderIdentityRequest(
    @NotNull SenderIdentityType identityType,
    @NotBlank @Size(max = 320) String value,
    @Size(max = 255) String externalId,
    boolean defaultIdentity) {}
