package com.messaging.broadcast.dto;

import com.messaging.broadcast.enums.BroadcastSenderAuthMode;
import jakarta.validation.constraints.*;
import jakarta.validation.constraints.Size;

public record ConnectAmazonSesRequest(
    @NotBlank @Size(max = 160) String name,
    @NotBlank @Size(max = 64) String region,
    @NotNull BroadcastSenderAuthMode authMode,
    @Size(max = 512) String roleArn,
    @Size(max = 255) String externalId,
    @Size(max = 255) String accessKeyId,
    @Size(max = 512) String secretAccessKey,
    @NotBlank @Email @Size(max = 320) String fromAddress,
    @Size(max = 255) String displayName,
    @Email @Size(max = 320) String replyToAddress) {}
