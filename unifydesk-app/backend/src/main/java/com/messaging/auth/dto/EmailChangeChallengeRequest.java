package com.messaging.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmailChangeChallengeRequest(
    @NotBlank @Email @Size(max = 320) String newEmail,
    @NotBlank @Size(max = 255) String currentPassword) {}
