package com.messaging.user.dto;

import com.messaging.user.enums.UserAppearanceMode;
import jakarta.validation.constraints.NotNull;

public record UpdateUserPreferencesRequest(@NotNull UserAppearanceMode appearanceMode) {}
