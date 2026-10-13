package com.messaging.auth.dto;

import com.messaging.user.enums.UserAppearanceMode;

public record AuthUserResponse(
    String userId,
    String name,
    String username,
    String email,
    String profilePictureMediaId,
    boolean verified,
    boolean hasCompanies,
    long companyCount,
    UserAppearanceMode appearanceMode) {

  public AuthUserResponse(
      String userId,
      String name,
      String username,
      String email,
      String profilePictureMediaId,
      boolean verified,
      boolean hasCompanies,
      long companyCount) {
    this(
        userId,
        name,
        username,
        email,
        profilePictureMediaId,
        verified,
        hasCompanies,
        companyCount,
        UserAppearanceMode.SYSTEM);
  }
}
