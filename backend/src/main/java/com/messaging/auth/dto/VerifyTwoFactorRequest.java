package com.messaging.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyTwoFactorRequest(
    @NotBlank String preAuthToken,
    @NotBlank @Pattern(regexp = "\\d{6}") String otp) {}
