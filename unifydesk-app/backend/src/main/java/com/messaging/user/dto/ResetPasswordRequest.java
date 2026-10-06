package com.messaging.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
    @NotBlank String currentPassword, @NotBlank @Size(min = 12, max = 255) String password) {}
