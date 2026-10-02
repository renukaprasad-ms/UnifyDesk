package com.messaging.broadcast.dto;

import com.messaging.broadcast.enums.BroadcastAudienceSourceType;
import java.time.Instant;

public record BroadcastExpectedSourceDescriptor(
    BroadcastAudienceSourceType sourceType,
    String sourceId,
    String sourceVersion,
    Integer filterVersion,
    String filterHash,
    String contactSelectionHash,
    Instant resolvedAt) {}
