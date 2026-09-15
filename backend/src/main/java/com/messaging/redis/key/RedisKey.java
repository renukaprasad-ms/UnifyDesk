package com.messaging.redis.key;

import java.util.Locale;

public final class RedisKey {

  private static final String PREFIX = "messaging";

  private RedisKey() {}

  public static String authOtp(String identifier) {
    return join("auth", "otp", normalize(identifier));
  }

  public static String authOtpAttempts(String identifier) {
    return join("auth", "otp-attempts", normalize(identifier));
  }

  public static String authLoginAttempts(String userId) {
    return join("auth", "login-attempts", normalize(userId));
  }

  public static String preRegisterMedia(String mediaId) {
    return join("auth", "pre-register-media", normalize(mediaId));
  }

  public static String session(String sessionId) {
    return join("session", normalize(sessionId));
  }

  public static String companySettings(String companyId) {
    return join("company", normalize(companyId), "settings");
  }

  public static String rateLimitLogin(String ip) {
    return join("rate-limit", "login", normalize(ip));
  }

  public static String rateLimitOtp(String identifier) {
    return join("rate-limit", "otp", normalize(identifier));
  }

  public static String rateLimitSendMessage(String companyId) {
    return join("rate-limit", "send-message", normalize(companyId));
  }

  public static String broadcastProgress(String broadcastId) {
    return join("broadcast", normalize(broadcastId), "progress");
  }

  public static String broadcastSent(String broadcastId) {
    return join("broadcast", normalize(broadcastId), "sent");
  }

  public static String broadcastFailed(String broadcastId) {
    return join("broadcast", normalize(broadcastId), "failed");
  }

  public static String idempotency(String scope, String requestId) {
    return join("idempotency", normalize(scope), normalize(requestId));
  }

  public static String lock(String scope, String resourceId) {
    return join("lock", normalize(scope), normalize(resourceId));
  }

  private static String join(String... parts) {
    return PREFIX + ":" + String.join(":", parts);
  }

  private static String normalize(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Redis key part must not be blank");
    }
    return value.trim().toLowerCase(Locale.ROOT);
  }
}
