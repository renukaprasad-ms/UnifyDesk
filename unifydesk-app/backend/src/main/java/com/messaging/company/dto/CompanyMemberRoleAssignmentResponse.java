package com.messaging.company.dto;

import java.time.Instant;

public record CompanyMemberRoleAssignmentResponse(
    String roleId, String roleName, Instant expiresAt, Instant revokedAt, Instant assignedAt) {}
