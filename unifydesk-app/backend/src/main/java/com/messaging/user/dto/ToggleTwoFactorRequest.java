package com.messaging.user.dto;

import jakarta.validation.constraints.NotNull;

public record ToggleTwoFactorRequest(@NotNull Boolean enabled) {}
