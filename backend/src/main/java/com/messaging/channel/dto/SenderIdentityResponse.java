package com.messaging.channel.dto;

import com.messaging.channel.enums.SenderIdentityStatus;
import com.messaging.channel.enums.SenderIdentityType;
import java.time.Instant;

public record SenderIdentityResponse(
    Long id,
    Long connectionId,
    SenderIdentityType identityType,
    String value,
    String externalId,
    SenderIdentityStatus status,
    boolean defaultIdentity,
    Instant createdAt,
    Instant updatedAt) {}
