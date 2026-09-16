package com.messaging.admin.dto;
import com.messaging.admin.enums.*;
import java.util.Set;
public record PlatformAdminResponse(String id,String userId,String name,String email,PlatformAdminRole role,PlatformAdminStatus status,Set<PlatformPermission> permissions) {}
