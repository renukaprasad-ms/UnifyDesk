package com.messaging.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeleteOwnAccountRequest(
    @NotBlank @Size(max = 255) String currentPassword) {}
