package com.messaging.company.dto;

import java.time.Instant;

public record CompanyMemberRoleAssignmentResponse(
    Long roleId,
    String roleName,
    Instant expiresAt,
    Instant revokedAt,
    Instant assignedAt) {}
