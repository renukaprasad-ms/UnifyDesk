package com.messaging.broadcast.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record ScheduleBroadcastRequest(
    @NotNull Instant scheduledAt,
    BroadcastExpectedSourceDescriptor expectedSourceDescriptor) {}
