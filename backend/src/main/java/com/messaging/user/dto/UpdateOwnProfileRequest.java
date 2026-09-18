package com.messaging.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateOwnProfileRequest(
    @NotBlank @Size(max = 120) String name,
    @NotBlank @Size(min = 3, max = 50) String username) {}
