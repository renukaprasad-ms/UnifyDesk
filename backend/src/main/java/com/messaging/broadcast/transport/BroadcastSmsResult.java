package com.messaging.broadcast.transport;

import java.time.Instant;

public record BroadcastSmsResult(String providerMessageId, Instant acceptedAt) {}
