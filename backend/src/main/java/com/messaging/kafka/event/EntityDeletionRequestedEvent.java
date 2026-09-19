package com.messaging.kafka.event;

import com.messaging.lifecycle.enums.LifecycleEntityType;
import java.time.Instant;

public record EntityDeletionRequestedEvent(
    LifecycleEntityType entityType,
    Long entityId,
    Long companyId,
    Long requestedByUserId,
    Instant requestedAt) {}
