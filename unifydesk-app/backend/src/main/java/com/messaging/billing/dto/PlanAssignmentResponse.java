package com.messaging.billing.dto;

import java.time.Instant;

public record PlanAssignmentResponse(
    String assignmentId,
    String planId,
    String companyId,
    Instant assignedAt,
    String assignedByUserId,
    Instant effectiveFrom,
    Instant effectiveTo,
    Instant revokedAt,
    String revokedByUserId,
    String reason) {}
