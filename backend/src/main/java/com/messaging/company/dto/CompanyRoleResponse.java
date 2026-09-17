package com.messaging.company.dto;

import java.time.Instant;
import java.util.Set;

public record CompanyRoleResponse(
    Long id,
    Long companyId,
    String name,
    String description,
    boolean systemRole,
    Set<String> permissions,
    Instant createdAt,
    Instant updatedAt) {}
