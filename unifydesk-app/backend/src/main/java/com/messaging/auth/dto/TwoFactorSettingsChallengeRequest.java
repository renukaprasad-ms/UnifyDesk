package com.messaging.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TwoFactorSettingsChallengeRequest(
    @NotBlank String currentPassword, @NotNull Boolean enabled) {}
