package com.messaging.redis.key;

import java.util.Locale;

public final class RedisKey {

  private static final String PREFIX = "messaging";

  private RedisKey() {}

  public static String authOtp(String identifier) {
    return join("auth", "otp", normalize(identifier));
  }

  public static String loginTwoFactorOtp(String challengeId) {
    return join("auth", "2fa-login", normalize(challengeId));
  }

  public static String passwordResetOtp(String challengeId) {
    return join("auth", "password-reset", "otp", normalize(challengeId));
  }

  public static String passwordResetUser(String challengeId) {
    return join("auth", "password-reset", "user", normalize(challengeId));
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
    return rateLimitLoginIp(ip);
  }

  public static String rateLimitLoginIp(String ip) {
    return join("rate-limit", "login", "ip", normalize(ip));
  }

  public static String rateLimitLoginIdentifier(String identifier) {
    return join("rate-limit", "login", "identifier", normalize(identifier));
  }

  public static String rateLimitRegisterIp(String ip) {
    return join("rate-limit", "register", "ip", normalize(ip));
  }

  public static String rateLimitRegisterIdentifier(String identifier) {
    return join("rate-limit", "register", "identifier", normalize(identifier));
  }

  public static String rateLimitOtpVerify(String scope) {
    return join("rate-limit", "otp-verify", normalize(scope));
  }

  public static String passwordResetGrant(String tokenId) {
    return join("auth", "password-reset", "grant", normalize(tokenId));
  }

  public static String twoFactorSettingsOtp(String challengeId) {
    return join("auth", "2fa-settings", "otp", normalize(challengeId));
  }

  public static String twoFactorSettingsEnabled(String challengeId) {
    return join("auth", "2fa-settings", "enabled", normalize(challengeId));
  }

  public static String emailChangeOtp(String challengeId) {
    return join("auth", "email-change", "otp", normalize(challengeId));
  }

  public static String emailChangeTarget(String challengeId) {
    return join("auth", "email-change", "target", normalize(challengeId));
  }

  public static String emailChangeUser(String challengeId) {
    return join("auth", "email-change", "user", normalize(challengeId));
  }

  public static String rateLimitOtp(String identifier) {
    return join("rate-limit", "otp", normalize(identifier));
  }

  public static String rateLimitSendMessage(String companyId) {
    return join("rate-limit", "send-message", normalize(companyId));
  }

  public static String gmailQuotaCooldown(String gmailAccount) {
    return join("gmail", "quota", "cooldown", normalize(gmailAccount));
  }

  public static String gmailQuotaUserTotal(String gmailAccount) {
    return join("gmail", "quota", "user", normalize(gmailAccount), "total");
  }

  public static String gmailQuotaUserBackground(String gmailAccount) {
    return join("gmail", "quota", "user", normalize(gmailAccount), "background");
  }

  public static String gmailQuotaProjectTotal(String projectKey) {
    return join("gmail", "quota", "project", normalize(projectKey), "total");
  }

  public static String gmailQuotaProjectBackground(String projectKey) {
    return join("gmail", "quota", "project", normalize(projectKey), "background");
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
