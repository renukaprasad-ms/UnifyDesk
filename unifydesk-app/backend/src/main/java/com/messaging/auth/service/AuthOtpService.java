package com.messaging.auth.service;

import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.TooManyRequestsException;
import com.messaging.kafka.event.EventMetadata;
import com.messaging.kafka.event.KafkaEvent;
import com.messaging.kafka.event.OtpRequestedEvent;
import com.messaging.kafka.priority.MessagePriority;
import com.messaging.kafka.producer.EventPublisher;
import com.messaging.redis.config.RedisTtlProperties;
import com.messaging.redis.key.RedisKey;
import com.messaging.redis.service.RateLimitResult;
import com.messaging.redis.service.RedisRateLimitService;
import com.messaging.redis.service.RedisService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthOtpService {

  private static final int OTP_BOUND = 1_000_000;
  private static final int OTP_REQUEST_LIMIT = 5;
  private static final String OTP_SUBJECT = "Verify your email";
  private static final String LOGIN_OTP_SUBJECT = "Your login verification code";
  private static final String PASSWORD_RESET_OTP_SUBJECT = "Reset your password";
  private static final String TWO_FACTOR_SETTINGS_SUBJECT = "Confirm your security settings";

  private final RedisService redisService;
  private final RedisRateLimitService rateLimitService;
  private final RedisTtlProperties ttlProperties;
  private final EventPublisher eventPublisher;
  private final AuthSecurityProperties authSecurityProperties;
  private final SecureRandom secureRandom = new SecureRandom();

  public void issueEmailVerificationOtp(String email) {
    String normalizedEmail = normalizeEmail(email);
    enforceOtpRateLimit(normalizedEmail);
    String otp = newOtp();
    redisService.set(RedisKey.authOtp(normalizedEmail), hash(otp), ttlProperties.getOtp());
    publishOtp(
        normalizedEmail,
        OTP_SUBJECT,
        "Your verification code is %s. It expires in %d minutes."
            .formatted(otp, otpExpiryMinutes()));
  }

  public void issueLoginOtp(String challengeId, String email) {
    String normalizedEmail = normalizeEmail(email);
    enforceOtpRateLimit(normalizedEmail);
    String otp = newOtp();
    redisService.set(RedisKey.loginTwoFactorOtp(challengeId), hash(otp), ttlProperties.getOtp());
    publishOtp(
        normalizedEmail,
        LOGIN_OTP_SUBJECT,
        "Your login verification code is %s. It expires in %d minutes."
            .formatted(otp, otpExpiryMinutes()));
  }

  public void issueTwoFactorSettingsOtp(String challengeId, String email, boolean enabled) {
    String normalizedEmail = normalizeEmail(email);
    enforceOtpRateLimit(normalizedEmail);
    String otp = newOtp();
    redisService.set(RedisKey.twoFactorSettingsOtp(challengeId), hash(otp), ttlProperties.getOtp());
    redisService.set(
        RedisKey.twoFactorSettingsEnabled(challengeId),
        Boolean.toString(enabled),
        ttlProperties.getOtp());
    publishOtp(
        normalizedEmail,
        TWO_FACTOR_SETTINGS_SUBJECT,
        "Your security settings code is %s. It expires in %d minutes."
            .formatted(otp, otpExpiryMinutes()));
  }

  public boolean verifyTwoFactorSettingsOtp(String challengeId, String otp) {
    verifyOtp(
        RedisKey.twoFactorSettingsOtp(challengeId),
        RedisKey.rateLimitOtpVerify("2fa-settings:" + challengeId),
        otp);
    String enabled = redisService.get(RedisKey.twoFactorSettingsEnabled(challengeId), String.class);
    redisService.delete(RedisKey.twoFactorSettingsEnabled(challengeId));
    if (enabled == null) {
      throw new BadRequestException("Invalid or expired OTP");
    }
    return Boolean.parseBoolean(enabled);
  }

  public void enforcePasswordResetRateLimit(String identifier) {
    enforceOtpRateLimit(normalizeIdentifier(identifier));
  }

  public void issuePasswordResetOtp(String challengeId, String email, long userId) {
    String normalizedEmail = normalizeEmail(email);
    String otp = newOtp();
    redisService.set(RedisKey.passwordResetOtp(challengeId), hash(otp), ttlProperties.getOtp());
    redisService.set(
        RedisKey.passwordResetUser(challengeId), Long.toString(userId), ttlProperties.getOtp());
    publishOtp(
        normalizedEmail,
        PASSWORD_RESET_OTP_SUBJECT,
        "Your password reset code is %s. It expires in %d minutes."
            .formatted(otp, otpExpiryMinutes()));
  }

  public void verifyLoginOtp(String challengeId, String otp) {
    verifyOtp(
        RedisKey.loginTwoFactorOtp(challengeId),
        RedisKey.rateLimitOtpVerify("2fa-login:" + challengeId),
        otp);
  }

  public void verifyEmailOtp(String email, String otp) {
    String normalizedEmail = normalizeEmail(email);
    verifyOtp(
        RedisKey.authOtp(normalizedEmail),
        RedisKey.rateLimitOtpVerify("verify-email:" + normalizedEmail),
        otp);
  }

  public long verifyPasswordResetOtp(String challengeId, String otp) {
    String otpKey = RedisKey.passwordResetOtp(challengeId);
    String userKey = RedisKey.passwordResetUser(challengeId);
    String userId = redisService.get(userKey, String.class);
    if (userId == null) {
      throw new BadRequestException("Invalid or expired OTP");
    }

    verifyOtp(
        otpKey,
        RedisKey.rateLimitOtpVerify("password-reset:" + challengeId),
        otp);
    redisService.delete(userKey);

    try {
      return Long.parseLong(userId);
    } catch (NumberFormatException exception) {
      throw new BadRequestException("Invalid or expired OTP");
    }
  }

  private void verifyOtp(String otpKey, String attemptKey, String otp) {
    RateLimitResult attempts =
        rateLimitService.consume(
            attemptKey,
            authSecurityProperties.getOtpVerifyLimit(),
            ttlProperties.getOtpAttempts());
    if (!attempts.allowed()) {
      redisService.delete(otpKey);
      throw new TooManyRequestsException("Too many invalid OTP attempts");
    }

    String storedHash = redisService.get(otpKey, String.class);
    if (storedHash == null
        || !MessageDigest.isEqual(
            storedHash.getBytes(StandardCharsets.UTF_8),
            hash(otp).getBytes(StandardCharsets.UTF_8))) {
      throw new BadRequestException("Invalid or expired OTP");
    }

    redisService.delete(otpKey);
    redisService.delete(attemptKey);
  }

  private void enforceOtpRateLimit(String identifier) {
    RateLimitResult result =
        rateLimitService.consume(
            RedisKey.rateLimitOtp(identifier), OTP_REQUEST_LIMIT, ttlProperties.getOtpAttempts());
    if (!result.allowed()) {
      throw new TooManyRequestsException("Too many OTP requests");
    }
  }

  private void publishOtp(String email, String subject, String body) {
    eventPublisher.publish(
        KafkaEvent.of(
            EventMetadata.create(
                OtpRequestedEventHandler.EVENT_TYPE,
                "v1",
                null,
                null,
                null,
                null,
                MessagePriority.CRITICAL),
            new OtpRequestedEvent(email, "EMAIL", subject, body, false)));
  }

  private String newOtp() {
    return "%06d".formatted(secureRandom.nextInt(OTP_BOUND));
  }

  private long otpExpiryMinutes() {
    return Math.max(1, ttlProperties.getOtp().toMinutes());
  }

  private String normalizeEmail(String email) {
    if (email == null || email.isBlank()) {
      throw new BadRequestException("Email is required");
    }
    return email.trim().toLowerCase(Locale.ROOT);
  }

  private String normalizeIdentifier(String identifier) {
    if (identifier == null || identifier.isBlank()) {
      throw new BadRequestException("Identifier is required");
    }
    return identifier.trim().toLowerCase(Locale.ROOT);
  }

  private String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to hash OTP", exception);
    }
  }
}
