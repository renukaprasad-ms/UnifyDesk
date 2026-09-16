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
  private static final int OTP_LIMIT = 5;
  private static final String OTP_SUBJECT = "Verify your email";
  private static final String LOGIN_OTP_SUBJECT = "Your login verification code";

  private final RedisService redisService;
  private final RedisRateLimitService rateLimitService;
  private final RedisTtlProperties ttlProperties;
  private final EventPublisher eventPublisher;
  private final SecureRandom secureRandom = new SecureRandom();

  public void issueEmailVerificationOtp(String email) {
    String normalizedEmail = normalizeEmail(email);
    RateLimitResult result =
        rateLimitService.consume(
            RedisKey.rateLimitOtp(normalizedEmail), OTP_LIMIT, ttlProperties.getOtpAttempts());
    if (!result.allowed()) {
      throw new TooManyRequestsException("Too many OTP requests");
    }

    String otp = "%06d".formatted(secureRandom.nextInt(OTP_BOUND));
    redisService.set(RedisKey.authOtp(normalizedEmail), hash(otp), ttlProperties.getOtp());

    KafkaEvent<OtpRequestedEvent> event =
        KafkaEvent.of(
            EventMetadata.create(
                OtpRequestedEventHandler.EVENT_TYPE,
                "v1",
                null,
                null,
                null,
                null,
                MessagePriority.CRITICAL),
            new OtpRequestedEvent(
                normalizedEmail,
                "EMAIL",
                OTP_SUBJECT,
                "Your verification code is %s. It expires in %d minutes."
                    .formatted(otp, Math.max(1, ttlProperties.getOtp().toMinutes())),
                false));

    eventPublisher.publish(event);
  }

  public void issueLoginOtp(String challengeId, String email) {
    String normalizedEmail = normalizeEmail(email);
    RateLimitResult result =
        rateLimitService.consume(
            RedisKey.rateLimitOtp(normalizedEmail), OTP_LIMIT, ttlProperties.getOtpAttempts());
    if (!result.allowed()) {
      throw new TooManyRequestsException("Too many OTP requests");
    }

    String otp = "%06d".formatted(secureRandom.nextInt(OTP_BOUND));
    redisService.set(RedisKey.loginTwoFactorOtp(challengeId), hash(otp), ttlProperties.getOtp());

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
            new OtpRequestedEvent(
                normalizedEmail,
                "EMAIL",
                LOGIN_OTP_SUBJECT,
                "Your login verification code is %s. It expires in %d minutes."
                    .formatted(otp, Math.max(1, ttlProperties.getOtp().toMinutes())),
                false)));
  }

  public void verifyLoginOtp(String challengeId, String otp) {
    String key = RedisKey.loginTwoFactorOtp(challengeId);
    String storedHash = redisService.get(key, String.class);
    if (storedHash == null
        || !MessageDigest.isEqual(
            storedHash.getBytes(StandardCharsets.UTF_8),
            hash(otp).getBytes(StandardCharsets.UTF_8))) {
      throw new BadRequestException("Invalid or expired OTP");
    }
    redisService.delete(key);
  }

  public void verifyEmailOtp(String email, String otp) {
    String normalizedEmail = normalizeEmail(email);
    String storedHash = redisService.get(RedisKey.authOtp(normalizedEmail), String.class);
    if (storedHash == null
        || !MessageDigest.isEqual(
            storedHash.getBytes(StandardCharsets.UTF_8),
            hash(otp).getBytes(StandardCharsets.UTF_8))) {
      throw new BadRequestException("Invalid or expired OTP");
    }
    redisService.delete(RedisKey.authOtp(normalizedEmail));
  }

  private String normalizeEmail(String email) {
    if (email == null || email.isBlank()) {
      throw new BadRequestException("Email is required");
    }
    return email.trim().toLowerCase(Locale.ROOT);
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
