package com.messaging.broadcast.dto;

import com.messaging.broadcast.enums.BroadcastSenderProvider;
import java.time.Instant;

public record BroadcastTestEmailResponse(
    BroadcastSenderProvider provider,
    String recipient,
    String providerMessageId,
    Instant acceptedAt) {}
