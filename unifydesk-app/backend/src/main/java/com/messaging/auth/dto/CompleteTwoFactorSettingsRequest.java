package com.messaging.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CompleteTwoFactorSettingsRequest(
    @NotBlank String challengeToken, @NotBlank @Pattern(regexp = "\\d{6}") String otp) {}
