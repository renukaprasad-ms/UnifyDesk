package com.messaging.admin.dto;
import com.messaging.admin.enums.PlatformAdminStatus;
import com.messaging.admin.enums.PlatformPermission;
import jakarta.validation.constraints.NotNull;
import java.util.Set;
public record UpdatePlatformAdminRequest(@NotNull PlatformAdminStatus status, Set<PlatformPermission> permissions) {}
