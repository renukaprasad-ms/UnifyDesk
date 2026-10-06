package com.messaging.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CompleteEmailChangeRequest(
    @NotBlank String challengeId,
    @NotBlank @Pattern(regexp = "\\d{6}") String otp) {}
