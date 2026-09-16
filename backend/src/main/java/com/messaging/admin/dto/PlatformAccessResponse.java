package com.messaging.admin.dto;

import com.messaging.admin.enums.PlatformAdminRole;
import com.messaging.admin.enums.PlatformPermission;
import java.util.Set;

public record PlatformAccessResponse(
    boolean platformAdmin, PlatformAdminRole role, Set<PlatformPermission> permissions) {}
