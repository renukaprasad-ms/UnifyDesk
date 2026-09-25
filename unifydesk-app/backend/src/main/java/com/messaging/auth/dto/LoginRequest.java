package com.messaging.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
    @NotBlank @Size(max = 320) String identifier,
    @NotBlank @Size(min = 8, max = 255) String password) {}
