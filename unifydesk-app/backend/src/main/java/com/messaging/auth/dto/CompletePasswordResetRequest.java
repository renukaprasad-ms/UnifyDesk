package com.messaging.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompletePasswordResetRequest(@NotBlank @Size(min = 12, max = 255) String password) {}
