package com.messaging.auth.dto;

public record LoginResponse(
    String status, AuthUserResponse user, String preAuthToken, long expiresInSeconds) {

  public static LoginResponse authenticated(AuthUserResponse user) {
    return new LoginResponse("AUTHENTICATED", user, null, 0);
  }

  public static LoginResponse twoFactorRequired(String preAuthToken, long expiresInSeconds) {
    return new LoginResponse("TWO_FACTOR_REQUIRED", null, preAuthToken, expiresInSeconds);
  }
}
