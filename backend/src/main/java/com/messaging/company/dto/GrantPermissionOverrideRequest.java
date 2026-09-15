package com.messaging.company.dto;

import com.messaging.company.enums.CompanyPermission;
import com.messaging.company.enums.PermissionEffect;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record GrantPermissionOverrideRequest(
    @NotNull CompanyPermission permission,
    @NotNull PermissionEffect effect,
    Instant expiresAt,
    @Size(max = 255) String reason) {}
