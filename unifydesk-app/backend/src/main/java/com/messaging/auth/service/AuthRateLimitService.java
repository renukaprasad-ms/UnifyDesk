package com.messaging.auth.service;

import com.messaging.common.exception.TooManyRequestsException;
import com.messaging.redis.config.RedisTtlProperties;
import com.messaging.redis.key.RedisKey;
import com.messaging.redis.service.RateLimitResult;
import com.messaging.redis.service.RedisRateLimitService;
import com.messaging.redis.service.RedisService;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthRateLimitService {

  private final RedisRateLimitService rateLimitService;
  private final RedisService redisService;
  private final RedisTtlProperties ttlProperties;
  private final AuthSecurityProperties properties;

  public void consumeLogin(String ipAddress, String identifier) {
    consumeIfPresent(
        RedisKey::rateLimitLoginIp,
        ipAddress,
        properties.getLoginIpLimit(),
        "Too many login attempts. Try again later");
    consumeIfPresent(
        RedisKey::rateLimitLoginIdentifier,
        normalizeIdentifier(identifier),
        properties.getLoginIdentifierLimit(),
        "Too many login attempts. Try again later");
  }

  public void clearLogin(String ipAddress, String identifier) {
    deleteIfPresent(RedisKey::rateLimitLoginIp, ipAddress);
    deleteIfPresent(RedisKey::rateLimitLoginIdentifier, normalizeIdentifier(identifier));
  }

  public void consumeRegistration(String ipAddress, String email) {
    consumeIfPresent(
        RedisKey::rateLimitRegisterIp,
        ipAddress,
        properties.getRegisterIpLimit(),
        "Too many registration attempts. Try again later");
    consumeIfPresent(
        RedisKey::rateLimitRegisterIdentifier,
        normalizeIdentifier(email),
        properties.getRegisterIdentifierLimit(),
        "Too many registration attempts. Try again later");
  }

  private void consumeIfPresent(
      java.util.function.Function<String, String> keyFactory,
      String value,
      int limit,
      String message) {
    if (value == null || value.isBlank()) {
      return;
    }
    RateLimitResult result =
        rateLimitService.consume(keyFactory.apply(value), limit, ttlProperties.getLoginAttempts());
    if (!result.allowed()) {
      throw new TooManyRequestsException(message);
    }
  }

  private void deleteIfPresent(
      java.util.function.Function<String, String> keyFactory, String value) {
    if (value != null && !value.isBlank()) {
      redisService.delete(keyFactory.apply(value));
    }
  }

  private String normalizeIdentifier(String value) {
    return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
  }
}
