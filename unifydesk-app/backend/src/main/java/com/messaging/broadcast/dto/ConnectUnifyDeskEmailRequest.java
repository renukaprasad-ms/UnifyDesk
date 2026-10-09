package com.messaging.broadcast.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ConnectUnifyDeskEmailRequest(
    @NotBlank @Size(max = 160) String name,
    @NotBlank @Size(max = 160) String apiKey,
    @NotNull @Positive Long sendingDomainId,
    @NotBlank @Email @Size(max = 320) String fromAddress,
    @Size(max = 255) String displayName,
    @Email @Size(max = 320) String replyToAddress) {}
