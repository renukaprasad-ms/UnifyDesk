package com.messaging.billing.dto;

import jakarta.validation.constraints.Size;
import java.time.Instant;

public record PlanAssignmentRequest(
    Instant effectiveFrom, Instant effectiveTo, @Size(max = 500) String reason) {}
