package com.messaging.company.dto;

import java.time.Instant;
import java.util.Set;

public record CompanyRoleResponse(
    String id,
    String companyId,
    String name,
    String description,
    boolean systemRole,
    Set<String> permissions,
    Instant createdAt,
    Instant updatedAt) {}
