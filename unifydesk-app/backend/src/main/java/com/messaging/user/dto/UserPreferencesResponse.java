package com.messaging.user.dto;

import com.messaging.user.enums.UserAppearanceMode;

public record UserPreferencesResponse(UserAppearanceMode appearanceMode) {}
