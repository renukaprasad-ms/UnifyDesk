package com.messaging.broadcast.transport;

import java.time.Instant;

public record BroadcastEmailResult(
    String providerMessageId, String internetMessageId, Instant acceptedAt) {}
