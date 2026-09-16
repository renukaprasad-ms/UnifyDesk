package com.messaging.admin.dto;
import com.messaging.admin.enums.PlatformPermission;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.Set;
public record CreatePlatformAdminRequest(@NotBlank @Email String email, Set<PlatformPermission> permissions) {}
